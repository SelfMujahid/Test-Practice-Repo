package com.practice.cryptoapp

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewmodel.compose.viewModel
import coil.compose.AsyncImage
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.Response
import okhttp3.WebSocket
import okhttp3.WebSocketListener
import org.json.JSONArray
import org.json.JSONObject
import java.util.Locale
import java.util.concurrent.TimeUnit

// ============================================================
// COLORS
// ============================================================

private val PurpleMimosa = Color(0xFF9B59B6)
private val PositiveGreen = Color(0xFF16A34A)
private val NegativeRed = Color(0xFFDC2626)

// ============================================================
// DATA
// ============================================================

data class CryptoCoin(
    val symbol: String,
    val name: String,
    val logo: String,
    val price: Double,
    val change24h: Double,
    val volume24h: Double,
    val marketCap: Double,
    val rank: Int
)

data class CoinGeckoMeta(
    val name: String,
    val logo: String,
    val marketCap: Double,
    val marketCapRank: Int
)

data class GlobalStats(
    val totalMarketCap: Double = 0.0,
    val totalVolume: Double = 0.0,
    val btcDominance: Double = 0.0,
    val ethDominance: Double = 0.0
) {
    val altDominance: Double
        get() = (100.0 - btcDominance - ethDominance).coerceAtLeast(0.0)
}

private data class BinanceSeed(
    val symbol: String,
    val lastPrice: Double,
    val change24h: Double,
    val volume24h: Double
)

private data class StreamBatch(
    val symbols: List<String>,
    var socket: WebSocket? = null,
    var reconnectJob: Job? = null
)

// ============================================================
// FLASH STATE
// ============================================================

enum class FlashState {
    None,
    Up,
    Down
}

// ============================================================
// BINANCE WEBSOCKET MANAGER
// ============================================================

class BinanceWebSocketManager {

    private val httpClient = OkHttpClient.Builder()
        .callTimeout(20, TimeUnit.SECONDS)
        .retryOnConnectionFailure(true)
        .build()

    private val wsClient = OkHttpClient.Builder()
        .readTimeout(0, TimeUnit.MILLISECONDS)
        .pingInterval(20, TimeUnit.SECONDS)
        .retryOnConnectionFailure(true)
        .build()

    private val scope = CoroutineScope(Job() + Dispatchers.IO)

    private val coinMap = mutableMapOf<String, CryptoCoin>()
    private val batches = mutableMapOf<Int, StreamBatch>()

    private var stopped = false
    private var bootstrapJob: Job? = null
    private var publishJob: Job? = null
    private var metadataRefreshJob: Job? = null

    private val _coins =
        MutableStateFlow<List<CryptoCoin>>(emptyList())

    val coins: StateFlow<List<CryptoCoin>> =
        _coins.asStateFlow()

    private val _status =
        MutableStateFlow("LOADING")

    val status: StateFlow<String> =
        _status.asStateFlow()

    private val _global =
        MutableStateFlow(GlobalStats())

    val global: StateFlow<GlobalStats> =
        _global.asStateFlow()

    // ========================================================
    // START
    // ========================================================

    fun start() {
        if (bootstrapJob?.isActive == true) return

        stopped = false

        bootstrapJob = scope.launch {
            bootstrap()
        }
    }

    // ========================================================
    // BOOTSTRAP
    // ========================================================

    private fun bootstrap() {

        _status.value = "FETCHING"

        val cgMetaMap = loadCoinGeckoMetaMap()

        val globalStats = loadGlobalStats()

        if (globalStats != null) {
            _global.value = globalStats
        }

        val seeds = loadBinanceSeeds()

        if (seeds.isEmpty()) {
            _status.value = "ERROR"
            return
        }

        synchronized(coinMap) {

            coinMap.clear()

            seeds.forEach { seed ->

                val base =
                    seed.symbol.removeSuffix("USDT")

                val meta =
                    cgMetaMap[base.uppercase()]

                coinMap[base] = CryptoCoin(

                    symbol = base,

                    name =
                        meta?.name
                            ?: base,

                    logo =
                        meta?.logo
                            .orEmpty(),

                    price =
                        seed.lastPrice,

                    change24h =
                        seed.change24h,

                    volume24h =
                        seed.volume24h,

                    marketCap =
                        meta?.marketCap
                            ?: 0.0,

                    rank = 0
                )
            }

            publishNow()
        }

        connectBatches(
            seeds.map { it.symbol }
        )

        startMetadataRefresh()

        _status.value = "LIVE"
    }

    // ========================================================
    // BINANCE REST
    // ========================================================

    private fun loadBinanceSeeds(): List<BinanceSeed> {

        val request =
            Request.Builder()
                .url(
                    "https://api.binance.com/api/v3/ticker/24hr"
                )
                .build()

        return try {

            httpClient
                .newCall(request)
                .execute()
                .use { response ->

                    if (!response.isSuccessful) {
                        return emptyList()
                    }

                    val body =
                        response.body
                            ?.string()
                            .orEmpty()

                    if (body.isBlank()) {
                        return emptyList()
                    }

                    val array =
                        JSONArray(body)

                    val result =
                        ArrayList<BinanceSeed>(
                            array.length()
                        )

                    for (i in 0 until array.length()) {

                        val obj =
                            array.optJSONObject(i)
                                ?: continue

                        val symbol =
                            obj.optString("symbol")

                        if (
                            !symbol.endsWith(
                                "USDT",
                                ignoreCase = true
                            )
                        ) {
                            continue
                        }

                        // Remove leveraged token pairs
                        if (
                            symbol.contains("UPUSDT", true) ||
                            symbol.contains("DOWNUSDT", true) ||
                            symbol.contains("BULLUSDT", true) ||
                            symbol.contains("BEARUSDT", true)
                        ) {
                            continue
                        }

                        val price =
                            obj.optString(
                                "lastPrice"
                            ).toDoubleOrNull()
                                ?: continue

                        val change =
                            obj.optString(
                                "priceChangePercent"
                            ).toDoubleOrNull()
                                ?: 0.0

                        val volume =
                            obj.optString(
                                "quoteVolume"
                            ).toDoubleOrNull()
                                ?: 0.0

                        result.add(
                            BinanceSeed(
                                symbol = symbol,
                                lastPrice = price,
                                change24h = change,
                                volume24h = volume
                            )
                        )
                    }

                    result
                }

        } catch (_: Exception) {

            emptyList()
        }
    }

    // ========================================================
    // COINGECKO
    // ========================================================

    private fun loadCoinGeckoMetaMap():
        Map<String, CoinGeckoMeta> {

        val result =
            mutableMapOf<String, CoinGeckoMeta>()

        fun fetchPage(page: Int) {

            val url =
                "https://api.coingecko.com/api/v3/coins/markets" +
                    "?vs_currency=usd" +
                    "&order=market_cap_desc" +
                    "&per_page=250" +
                    "&page=$page" +
                    "&sparkline=false"

            val request =
                Request.Builder()
                    .url(url)
                    .build()

            try {

                httpClient
                    .newCall(request)
                    .execute()
                    .use { response ->

                        if (!response.isSuccessful) {
                            return
                        }

                        val body =
                            response.body
                                ?.string()
                                .orEmpty()

                        if (body.isBlank()) {
                            return
                        }

                        val array =
                            JSONArray(body)

                        for (
                            i in 0 until array.length()
                        ) {

                            val coin =
                                array.optJSONObject(i)
                                    ?: continue

                            val symbol =
                                coin.optString(
                                    "symbol"
                                ).uppercase()

                            val name =
                                coin.optString("name")

                            val logo =
                                coin.optString("image")

                            val marketCap =
                                coin.optDouble(
                                    "market_cap",
                                    0.0
                                )

                            val rank =
                                coin.optInt(
                                    "market_cap_rank",
                                    Int.MAX_VALUE
                                )

                            val old =
                                result[symbol]

                            if (
                                old == null ||
                                rank < old.marketCapRank
                            ) {

                                result[symbol] =
                                    CoinGeckoMeta(
                                        name = name,
                                        logo = logo,
                                        marketCap = marketCap,
                                        marketCapRank = rank
                                    )
                            }
                        }
                    }

            } catch (_: Exception) {
            }
        }

        // Top 1000 CoinGecko coins
        fetchPage(1)
        fetchPage(2)
        fetchPage(3)
        fetchPage(4)

        return result
    }

    // ========================================================
    // GLOBAL MARKET DATA
    // ========================================================

    private fun loadGlobalStats():
        GlobalStats? {

        val request =
            Request.Builder()
                .url(
                    "https://api.coingecko.com/api/v3/global"
                )
                .build()

        return try {

            httpClient
                .newCall(request)
                .execute()
                .use { response ->

                    if (!response.isSuccessful) {
                        return null
                    }

                    val body =
                        response.body
                            ?.string()
                            .orEmpty()

                    if (body.isBlank()) {
                        return null
                    }

                    val data =
                        JSONObject(body)
                            .optJSONObject("data")
                            ?: return null

                    val marketCap =
                        data
                            .optJSONObject(
                                "total_market_cap"
                            )
                            ?.optDouble(
                                "usd",
                                0.0
                            )
                            ?: 0.0

                    val volume =
                        data
                            .optJSONObject(
                                "total_volume"
                            )
                            ?.optDouble(
                                "usd",
                                0.0
                            )
                            ?: 0.0

                    val dominance =
                        data.optJSONObject(
                            "market_cap_percentage"
                        )

                    val btc =
                        dominance?.optDouble(
                            "btc",
                            0.0
                        )
                            ?: 0.0

                    val eth =
                        dominance?.optDouble(
                            "eth",
                            0.0
                        )
                            ?: 0.0

                    GlobalStats(
                        totalMarketCap = marketCap,
                        totalVolume = volume,
                        btcDominance = btc,
                        ethDominance = eth
                    )
                }

        } catch (_: Exception) {

            null
        }
    }

    // ========================================================
    // COINGECKO REFRESH
    // ========================================================

    private fun startMetadataRefresh() {

        if (
            metadataRefreshJob?.isActive == true
        ) {
            return
        }

        metadataRefreshJob =
            scope.launch {

                while (!stopped) {

                    delay(
                        10 * 60 * 1000L
                    )

                    if (stopped) break

                    val meta =
                        loadCoinGeckoMetaMap()

                    val stats =
                        loadGlobalStats()

                    if (stats != null) {
                        _global.value = stats
                    }

                    synchronized(coinMap) {

                        coinMap.forEach {
                                (symbol, coin) ->

                            val m =
                                meta[
                                    symbol.uppercase()
                                ]
                                    ?: return@forEach

                            coinMap[symbol] =
                                coin.copy(
                                    name =
                                        m.name,
                                    logo =
                                        m.logo,
                                    marketCap =
                                        m.marketCap
                                )
                        }
                    }

                    publishNow()
                }
            }
    }

    // ========================================================
    // WEBSOCKET BATCHES
    // ========================================================

    private fun connectBatches(
        symbols: List<String>
    ) {

        val unique =
            symbols.distinct()

        // 180 coins per WebSocket connection
        unique
            .chunked(180)
            .forEachIndexed {
                    index,
                    batchSymbols ->

                batches[index] =
                    StreamBatch(
                        symbols = batchSymbols
                    )

                openBatch(index)
            }
    }

    // ========================================================
    // OPEN WEBSOCKET
    // ========================================================

    private fun openBatch(
        batchIndex: Int
    ) {

        if (stopped) return

        val batch =
            batches[batchIndex]
                ?: return

        batch.reconnectJob?.cancel()
        batch.reconnectJob = null

        batch.socket?.close(
            1000,
            "Reconnecting"
        )

        batch.socket = null

        // ONLY ONE STREAM:
        // symbol@ticker
        //
        // No:
        // @ticker_1h
        // @ticker_4h
        // @ticker_1d

        val streams =
            batch.symbols
                .joinToString("/") { symbol ->
                    "${symbol.lowercase()}@ticker"
                }

        val url =
            "wss://stream.binance.com:9443/stream?streams=$streams"

        val request =
            Request.Builder()
                .url(url)
                .build()

        batch.socket =
            wsClient.newWebSocket(
                request,
                object : WebSocketListener() {

                    override fun onOpen(
                        webSocket: WebSocket,
                        response: Response
                    ) {

                        _status.value =
                            "LIVE"
                    }

                    override fun onMessage(
                        webSocket: WebSocket,
                        text: String
                    ) {

                        parseTickerMessage(
                            text
                        )
                    }

                    override fun onFailure(
                        webSocket: WebSocket,
                        t: Throwable,
                        response: Response?
                    ) {

                        batch.socket = null

                        _status.value =
                            "RECONNECTING"

                        scheduleReconnect(
                            batchIndex
                        )
                    }

                    override fun onClosed(
                        webSocket: WebSocket,
                        code: Int,
                        reason: String
                    ) {

                        batch.socket = null

                        if (!stopped) {

                            _status.value =
                                "RECONNECTING"

                            scheduleReconnect(
                                batchIndex
                            )
                        }
                    }
                }
            )
    }

    // ========================================================
    // PARSE ONLY @TICKER
    // ========================================================

    private fun parseTickerMessage(
        text: String
    ) {

        try {

            val root =
                JSONObject(text)

            val data =
                root.optJSONObject(
                    "data"
                )
                    ?: return

            val symbolFull =
                data.optString("s")

            if (
                !symbolFull.endsWith(
                    "USDT",
                    ignoreCase = true
                )
            ) {
                return
            }

            val symbol =
                symbolFull.removeSuffix(
                    "USDT"
                )

            val price =
                data.optString(
                    "c"
                ).toDoubleOrNull()
                    ?: return

            // Binance @ticker gives 24h percentage
            val change24h =
                data.optString(
                    "P"
                ).toDoubleOrNull()
                    ?: 0.0

            val volume24h =
                data.optString(
                    "q"
                ).toDoubleOrNull()
                    ?: 0.0

            synchronized(coinMap) {

                val old =
                    coinMap[symbol]
                        ?: return

                coinMap[symbol] =
                    old.copy(
                        price = price,
                        change24h = change24h,
                        volume24h = volume24h
                    )
            }

            schedulePublish()

        } catch (_: Exception) {
        }
    }

    // ========================================================
    // PUBLISH
    // ========================================================

    private fun schedulePublish() {

        if (
            publishJob?.isActive == true
        ) {
            return
        }

        publishJob =
            scope.launch {

                delay(100)

                publishNow()
            }
    }

    private fun publishNow() {

        synchronized(coinMap) {

            /*
             * IMPORTANT:
             * Ranking is ONLY based on market cap.
             *
             * Live Binance price/volume changes
             * cannot change the ranking order.
             */

            val list =
                coinMap.values
                    .sortedWith(
                        compareByDescending<CryptoCoin> {
                            it.marketCap
                        }.thenBy {
                            it.symbol
                        }
                    )
                    .mapIndexed {
                            index,
                            coin ->

                        coin.copy(
                            rank = index + 1
                        )
                    }

            _coins.value = list
        }
    }

    // ========================================================
    // RECONNECT
    // ========================================================

    private fun scheduleReconnect(
        batchIndex: Int
    ) {

        if (stopped) return

        val batch =
            batches[batchIndex]
                ?: return

        if (
            batch.reconnectJob?.isActive == true
        ) {
            return
        }

        batch.reconnectJob =
            scope.launch {

                delay(2000)

                if (!stopped) {
                    openBatch(batchIndex)
                }
            }
    }

    // ========================================================
    // STOP
    // ========================================================

    fun stop() {

        stopped = true

        bootstrapJob?.cancel()
        publishJob?.cancel()
        metadataRefreshJob?.cancel()

        batches.values.forEach { batch ->

            batch.reconnectJob?.cancel()

            batch.socket?.close(
                1000,
                "Application stopped"
            )

            batch.socket = null
        }

        batches.clear()

        scope.cancel()
    }
}

// ============================================================
// VIEW MODEL
// ============================================================

class CryptoViewModel : ViewModel() {

    private val manager =
        BinanceWebSocketManager()

    val coins =
        manager.coins

    val status =
        manager.status

    val global =
        manager.global

    init {
        manager.start()
    }

    override fun onCleared() {

        manager.stop()

        super.onCleared()
    }
}

// ============================================================
// MAIN ACTIVITY
// ============================================================

class MainActivity :
    ComponentActivity() {

    override fun onCreate(
        savedInstanceState: Bundle?
    ) {

        super.onCreate(
            savedInstanceState
        )

        setContent {

            MaterialTheme(
                colorScheme =
                    lightColorScheme(
                        primary =
                            PurpleMimosa,
                        secondary =
                            PurpleMimosa
                    )
            ) {

                CryptoExchangeApp()
            }
        }
    }
}

// ============================================================
// MAIN APP
// ============================================================

@Composable
fun CryptoExchangeApp(
    vm: CryptoViewModel =
        viewModel()
) {

    val coins by
        vm.coins.collectAsState()

    val status by
        vm.status.collectAsState()

    val global by
        vm.global.collectAsState()

    val drawerState =
        rememberDrawerState(
            DrawerValue.Closed
        )

    val scope =
        rememberCoroutineScope()

    var selectedTab by
        rememberSaveable {
            mutableIntStateOf(0)
        }

    ModalNavigationDrawer(

        drawerState =
            drawerState,

        drawerContent = {

            ModalDrawerSheet {

                Spacer(
                    Modifier.height(18.dp)
                )

                Text(
                    "Crypto Exchange",
                    modifier =
                        Modifier.padding(
                            20.dp
                        ),
                    fontSize = 20.sp,
                    fontWeight =
                        FontWeight.Bold
                )

                HorizontalDivider()

                NavigationDrawerItem(

                    label = {
                        Text("Profile")
                    },

                    selected = false,

                    onClick = {
                        scope.launch {
                            drawerState.close()
                        }
                    },

                    icon = {
                        Icon(
                            Icons.Default.Person,
                            null
                        )
                    }
                )

                NavigationDrawerItem(

                    label = {
                        Text("Settings")
                    },

                    selected = false,

                    onClick = {
                        scope.launch {
                            drawerState.close()
                        }
                    },

                    icon = {
                        Icon(
                            Icons.Default.Settings,
                            null
                        )
                    }
                )

                NavigationDrawerItem(

                    label = {
                        Text("Connection")
                    },

                    selected = false,

                    onClick = {
                        scope.launch {
                            drawerState.close()
                        }
                    },

                    icon = {
                        Icon(
                            Icons.Default.Wifi,
                            null
                        )
                    }
                )
            }
        }
    ) {

        Scaffold(

            bottomBar = {

                NavigationBar {

                    val names =
                        listOf(
                            "Home",
                            "Chart",
                            "Trade",
                            "Analysis",
                            "News"
                        )

                    val icons =
                        listOf(
                            Icons.Default.Home,
                            Icons.Default.ShowChart,
                            Icons.Default.SwapHoriz,
                            Icons.Default.Analytics,
                            Icons.Default.Notifications
                        )

                    names.forEachIndexed {
                            index,
                            name ->

                        NavigationBarItem(

                            selected =
                                selectedTab ==
                                    index,

                            onClick = {
                                selectedTab =
                                    index
                            },

                            icon = {
                                Icon(
                                    icons[index],
                                    name
                                )
                            },

                            label = {
                                Text(name)
                            }
                        )
                    }
                }
            }

        ) { padding ->

            Box(
                Modifier.padding(
                    padding
                )
            ) {

                when (selectedTab) {

                    0 -> HomeScreen(
                        coins = coins,
                        status = status,
                        global = global,
                        onMenuClick = {
                            scope.launch {
                                drawerState.open()
                            }
                        }
                    )

                    1 ->
                        PlaceholderScreen(
                            "Chart"
                        )

                    2 ->
                        PlaceholderScreen(
                            "Trade"
                        )

                    3 ->
                        PlaceholderScreen(
                            "Analysis"
                        )

                    4 ->
                        PlaceholderScreen(
                            "News"
                        )
                }
            }
        }
    }
}

// ============================================================
// HOME SCREEN
// ============================================================

@Composable
fun HomeScreen(
    coins: List<CryptoCoin>,
    status: String,
    global: GlobalStats,
    onMenuClick: () -> Unit
) {

    var balance by
        rememberSaveable {
            mutableDoubleStateOf(
                10000.0
            )
        }

    var search by
        rememberSaveable {
            mutableStateOf("")
        }

    var filter by
        rememberSaveable {
            mutableStateOf("ALL")
        }

    var visibleCount by
        rememberSaveable {
            mutableIntStateOf(20)
        }

    // Price flash state
    val flashMap =
        remember {
            mutableStateMapOf<
                String,
                FlashState
                >()
        }

    // Previous prices
    val previousPrices =
        remember {
            mutableStateMapOf<
                String,
                Double
                >()
        }

    // ========================================================
    // PRICE FLASH DETECTION
    // ========================================================

    LaunchedEffect(coins) {

        coins.forEach { coin ->

            val previous =
                previousPrices[
                    coin.symbol
                ]

            if (
                previous != null &&
                previous != coin.price
            ) {

                val direction =
                    if (
                        coin.price >
                        previous
                    ) {
                        FlashState.Up
                    } else {
                        FlashState.Down
                    }

                flashMap[
                    coin.symbol
                ] = direction

                launch {

                    delay(600)

                    if (
                        flashMap[
                            coin.symbol
                        ] == direction
                    ) {

                        flashMap.remove(
                            coin.symbol
                        )
                    }
                }
            }

            previousPrices[
                coin.symbol
            ] = coin.price
        }
    }

    // ========================================================
    // FILTER / SEARCH
    // ========================================================

    val displayCoins =
        remember(
            coins,
            search,
            filter,
            visibleCount
        ) {

            var list = coins

            if (search.isNotBlank()) {

                val query =
                    search.trim()

                list =
                    list.filter {

                        it.symbol.contains(
                            query,
                            ignoreCase = true
                        ) ||

                        it.name.contains(
                            query,
                            ignoreCase = true
                        )
                    }
            }

            list =
                when (filter) {

                    "GAINER" ->
                        list.sortedByDescending {
                            it.change24h
                        }

                    "LOSER" ->
                        list.sortedBy {
                            it.change24h
                        }

                    else ->
                        list.sortedBy {
                            it.rank
                        }
                }

            if (
                search.isBlank()
            ) {
                list.take(
                    visibleCount
                )
            } else {
                list.take(1000)
            }
        }

    // ========================================================
    // LIST
    // ========================================================

    LazyColumn(

        modifier =
            Modifier
                .fillMaxSize()
                .padding(
                    horizontal = 10.dp
                )
    ) {

        // ====================================================
        // HEADER
        // ====================================================

        item {

            Row(

                modifier =
                    Modifier
                        .fillMaxWidth()
                        .padding(
                            top = 8.dp,
                            bottom = 8.dp
                        ),

                verticalAlignment =
                    Alignment.CenterVertically
            ) {

                Icon(

                    Icons.Default.Menu,

                    contentDescription =
                        "Menu",

                    modifier =
                        Modifier
                            .size(28.dp)
                            .clickable {
                                onMenuClick()
                            },

                    tint =
                        PurpleMimosa
                )

                Spacer(
                    Modifier.width(9.dp)
                )

                Column(
                    Modifier.weight(1f)
                ) {

                    Text(
                        "Crypto Exchange",
                        fontSize = 16.sp,
                        fontWeight =
                            FontWeight.Bold
                    )

                    Text(
                        "Binance Live + CoinGecko",
                        fontSize = 9.sp,
                        color =
                            Color.Gray
                    )
                }

                Text(

                    "● $status",

                    fontSize = 9.sp,

                    color =
                        if (
                            status == "LIVE"
                        ) {
                            PurpleMimosa
                        } else {
                            Color.Gray
                        }
                )
            }
        }

        // ====================================================
        // DEMO BALANCE
        // ====================================================

        item {

            Card(

                Modifier
                    .fillMaxWidth()
                    .padding(
                        vertical = 4.dp
                    ),

                shape =
                    RoundedCornerShape(
                        14.dp
                    )
            ) {

                Column(
                    Modifier.padding(12.dp)
                ) {

                    Row(

                        Modifier.fillMaxWidth(),

                        horizontalArrangement =
                            Arrangement.SpaceBetween,

                        verticalAlignment =
                            Alignment.CenterVertically
                    ) {

                        Column {

                            Text(
                                "Demo Balance",
                                fontSize = 9.sp,
                                color =
                                    Color.Gray
                            )

                            Text(
                                "$" +
                                    String.format(
                                        Locale.US,
                                        "%,.2f",
                                        balance
                                    ),
                                fontSize = 20.sp,
                                fontWeight =
                                    FontWeight.Bold
                            )
                        }

                        Button(

                            onClick = {
                                balance =
                                    10000.0
                            },

                            enabled =
                                balance <= 100.0,

                            colors =
                                ButtonDefaults
                                    .buttonColors(
                                        containerColor =
                                            PurpleMimosa
                                    )
                        ) {

                            Text(
                                "Reset"
                            )
                        }
                    }
                }
            }
        }

        // ====================================================
        // GLOBAL MARKET
        // ====================================================

        item {

            Card(

                Modifier
                    .fillMaxWidth()
                    .padding(
                        vertical = 4.dp
                    ),

                shape =
                    RoundedCornerShape(
                        14.dp
                    )
            ) {

                Column(
                    Modifier.padding(12.dp)
                ) {

                    Text(
                        "Global Market",
                        fontWeight =
                            FontWeight.Bold,
                        fontSize = 12.sp
                    )

                    Spacer(
                        Modifier.height(9.dp)
                    )

                    Row(

                        Modifier.fillMaxWidth(),

                        horizontalArrangement =
                            Arrangement.SpaceBetween
                    ) {

                        MarketValue(
                            "Global MC",
                            formatLarge(
                                global.totalMarketCap
                            )
                        )

                        MarketValue(
                            "24h Volume",
                            formatLarge(
                                global.totalVolume
                            )
                        )

                        MarketValue(
                            "BTC Dom.",
                            formatPct(
                                global.btcDominance
                            )
                        )
                    }

                    Spacer(
                        Modifier.height(10.dp)
                    )

                    Row(

                        Modifier.fillMaxWidth(),

                        horizontalArrangement =
                            Arrangement.SpaceBetween
                    ) {

                        MarketValue(
                            "ETH Dom.",
                            formatPct(
                                global.ethDominance
                            )
                        )

                        MarketValue(
                            "Alt Dom.",
                            formatPct(
                                global.altDominance
                            )
                        )

                        MarketValue(
                            "BTC Price",
                            findBtcPrice(
                                coins
                            )
                        )
                    }
                }
            }
        }

        // ====================================================
        // SEARCH
        // ====================================================

        item {

            Spacer(
                Modifier.height(4.dp)
            )

            OutlinedTextField(

                value = search,

                onValueChange = {
                    search = it
                    visibleCount = 20
                },

                modifier =
                    Modifier.fillMaxWidth(),

                singleLine = true,

                shape =
                    RoundedCornerShape(
                        30.dp
                    ),

                placeholder = {
                    Text(
                        "Search crypto...",
                        fontSize = 12.sp
                    )
                },

                leadingIcon = {

                    Icon(
                        Icons.Default.Search,
                        null,
                        tint =
                            PurpleMimosa
                    )
                },

                colors =
                    OutlinedTextFieldDefaults
                        .colors(
                            focusedBorderColor =
                                PurpleMimosa,
                            unfocusedBorderColor =
                                Color.LightGray,
                            focusedLeadingIconColor =
                                PurpleMimosa,
                            cursorColor =
                                PurpleMimosa
                        )
            )

            Spacer(
                Modifier.height(7.dp)
            )

            Row(
                horizontalArrangement =
                    Arrangement.spacedBy(7.dp)
            ) {

                FilterChip(

                    selected =
                        filter == "ALL",

                    onClick = {

                        filter = "ALL"
                        visibleCount = 20
                    },

                    label = {
                        Text(
                            "All",
                            fontSize = 11.sp
                        )
                    },

                    colors =
                        FilterChipDefaults
                            .filterChipColors(
                                selectedContainerColor =
                                    PurpleMimosa
                                        .copy(
                                            alpha = 0.15f
                                        ),
                                selectedLabelColor =
                                    PurpleMimosa
                            )
                )

                FilterChip(

                    selected =
                        filter == "GAINER",

                    onClick = {

                        filter = "GAINER"
                        visibleCount = 20
                    },

                    label = {
                        Text(
                            "Gainer",
                            fontSize = 11.sp
                        )
                    },

                    colors =
                        FilterChipDefaults
                            .filterChipColors(
                                selectedContainerColor =
                                    PurpleMimosa
                                        .copy(
                                            alpha = 0.15f
                                        ),
                                selectedLabelColor =
                                    PurpleMimosa
                            )
                )

                FilterChip(

                    selected =
                        filter == "LOSER",

                    onClick = {

                        filter = "LOSER"
                        visibleCount = 20
                    },

                    label = {
                        Text(
                            "Loser",
                            fontSize = 11.sp
                        )
                    },

                    colors =
                        FilterChipDefaults
                            .filterChipColors(
                                selectedContainerColor =
                                    PurpleMimosa
                                        .copy(
                                            alpha = 0.15f
                                        ),
                                selectedLabelColor =
                                    PurpleMimosa
                            )
                )
            }

            Spacer(
                Modifier.height(5.dp)
            )

            Text(

                "${coins.size} live USDT markets",

                fontSize = 9.sp,

                color =
                    Color.Gray
            )
        }

        // ====================================================
        // COINS
        // ====================================================

        items(

            items = displayCoins,

            key = {
                it.symbol
            }

        ) { coin ->

            CryptoRow(

                coin = coin,

                flash =
                    flashMap[
                        coin.symbol
                    ]
                        ?: FlashState.None
            )
        }

        // ====================================================
        // PAGINATION
        // ====================================================

        if (search.isBlank()) {

            item {

                Row(

                    Modifier
                        .fillMaxWidth()
                        .padding(
                            vertical = 14.dp
                        ),

                    horizontalArrangement =
                        Arrangement.SpaceBetween,

                    verticalAlignment =
                        Alignment.CenterVertically
                ) {

                    OutlinedButton(

                        onClick = {
                            visibleCount =
                                maxOf(
                                    20,
                                    visibleCount - 50
                                )
                        },

                        enabled =
                            visibleCount > 20,

                        shape =
                            RoundedCornerShape(
                                20.dp
                            )
                    ) {

                        Text(
                            "Previous",
                            fontSize = 11.sp
                        )
                    }

                    Text(

                        "${minOf(
                            visibleCount,
                            coins.size
                        )} shown",

                        fontSize = 10.sp
                    )

                    Button(

                        onClick = {
                            visibleCount += 50
                        },

                        enabled =
                            visibleCount <
                                coins.size,

                        colors =
                            ButtonDefaults
                                .buttonColors(
                                    containerColor =
                                        PurpleMimosa
                                ),

                        shape =
                            RoundedCornerShape(
                                20.dp
                            )
                    ) {

                        Text(
                            "Next 50",
                            fontSize = 11.sp
                        )
                    }
                }
            }
        }
    }
}

// ============================================================
// CRYPTO ROW
// ============================================================

@Composable
fun CryptoRow(

    coin: CryptoCoin,

    flash: FlashState

) {

    val priceColor =

        when (flash) {

            FlashState.Up ->
                PositiveGreen

            FlashState.Down ->
                NegativeRed

            FlashState.None ->
                MaterialTheme
                    .colorScheme
                    .onSurface
        }

    Row(

        Modifier
            .fillMaxWidth()
            .padding(
                vertical = 6.dp
            ),

        verticalAlignment =
            Alignment.CenterVertically
    ) {

        // Rank
        Text(

            coin.rank.toString(),

            fontSize = 8.sp,

            color =
                Color.Gray,

            modifier =
                Modifier.width(22.dp)
        )

        // Logo
        if (
            coin.logo.isNotBlank()
        ) {

            AsyncImage(

                model =
                    coin.logo,

                contentDescription =
                    coin.name,

                modifier =
                    Modifier
                        .size(24.dp)
                        .clip(
                            CircleShape
                        ),

                contentScale =
                    ContentScale.Crop
            )

        } else {

            Box(

                Modifier
                    .size(24.dp)
                    .clip(
                        CircleShape
                    ),

                contentAlignment =
                    Alignment.Center
            ) {

                Text(

                    coin.symbol
                        .take(1),

                    fontWeight =
                        FontWeight.Bold,

                    fontSize = 9.sp
                )
            }
        }

        Spacer(
            Modifier.width(7.dp)
        )

        // Name / symbol / 24h
        Column(
            Modifier.weight(1f)
        ) {

            Text(

                coin.name,

                fontWeight =
                    FontWeight.Bold,

                fontSize = 11.sp,

                maxLines = 1
            )

            Text(

                coin.symbol,

                fontSize = 8.sp,

                color =
                    Color.Gray
            )

            // ONLY 24H CHANGE
            Text(

                formatPct(
                    coin.change24h
                ),

                fontSize = 9.sp,

                fontWeight =
                    FontWeight.Medium,

                color =
                    if (
                        coin.change24h >= 0
                    ) {
                        PositiveGreen
                    } else {
                        NegativeRed
                    }
            )
        }

        // Price
        Column(
            horizontalAlignment =
                Alignment.End
        ) {

            Text(

                formatPrice(
                    coin.price
                ),

                fontWeight =
                    FontWeight.Bold,

                fontSize = 10.sp,

                color =
                    priceColor
            )

            Text(

                formatPct(
                    coin.change24h
                ),

                fontSize = 9.sp,

                color =
                    if (
                        coin.change24h >= 0
                    ) {
                        PositiveGreen
                    } else {
                        NegativeRed
                    }
            )
        }
    }

    HorizontalDivider(
        thickness = 0.5.dp
    )
}

// ============================================================
// MARKET VALUE
// ============================================================

@Composable
fun MarketValue(
    title: String,
    value: String
) {

    Column(
        Modifier.width(92.dp)
    ) {

        Text(

            title,

            fontSize = 8.sp,

            color =
                Color.Gray
        )

        Text(

            value,

            fontSize = 10.sp,

            fontWeight =
                FontWeight.Bold
        )
    }
}

// ============================================================
// BTC PRICE
// ============================================================

fun findBtcPrice(
    coins: List<CryptoCoin>
): String {

    val btc =
        coins.firstOrNull {
            it.symbol == "BTC"
        }

    return if (btc != null) {

        "$" +
            formatPrice(
                btc.price
            )

    } else {
        "—"
    }
}

// ============================================================
// PLACEHOLDER
// ============================================================

@Composable
fun PlaceholderScreen(
    title: String
) {

    Box(

        Modifier.fillMaxSize(),

        contentAlignment =
            Alignment.Center
    ) {

        Text(

            title,

            fontSize = 20.sp,

            fontWeight =
                FontWeight.Bold,

            color =
                PurpleMimosa
        )
    }
}

// ============================================================
// FORMATTING
// ============================================================

fun formatPct(
    value: Double
): String {

    return String.format(
        Locale.US,
        "%+.2f%%",
        value
    )
}

// ============================================================
// PRICE FORMAT
// ============================================================

fun formatPrice(
    value: Double
): String {

    if (
        value.isNaN() ||
        value.isInfinite()
    ) {
        return "0"
    }

    return when {

        // $1 or above:
        // 65,700.06348 -> 65,700.06
        // 1.23456 -> 1.23
        value >= 1.0 -> {

            String.format(
                Locale.US,
                "%,.2f",
                value
            )
        }

        // Small values:
        // 0.0536788 -> 0.0536788
        value > 0.0 -> {

            String.format(
                Locale.US,
                "%.8f",
                value
            )
                .trimEnd('0')
                .trimEnd('.')
        }

        else -> {
            "0"
        }
    }
}

// ============================================================
// LARGE NUMBER FORMAT
// ============================================================

fun formatLarge(
    value: Double
): String {

    return when {

        value >=
            1_000_000_000_000 ->

            String.format(
                Locale.US,
                "%.2fT",
                value /
                    1_000_000_000_000
            )

        value >=
            1_000_000_000 ->

            String.format(
                Locale.US,
                "%.2fB",
                value /
                    1_000_000_000
            )

        value >=
            1_000_000 ->

            String.format(
                Locale.US,
                "%.2fM",
                value /
                    1_000_000
            )

        value >=
            1_000 ->

            String.format(
                Locale.US,
                "%.2fK",
                value /
                    1_000
            )

        else ->

            String.format(
                Locale.US,
                "%.2f",
                value
            )
    }
}

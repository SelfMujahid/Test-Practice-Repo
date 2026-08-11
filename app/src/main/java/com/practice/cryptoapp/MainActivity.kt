package com.practice.cryptoapp

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.Image
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
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewmodel.compose.viewModel
import coil.compose.AsyncImage
import com.practice.cryptoapp.ui.TradeScreen
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

val PurpleMimosa = Color(0xFF9B59B6)
private val PositiveGreen = Color(0xFF16A34A)
private val NegativeRed = Color(0xFFDC2626)

// ============================================================
// TIMEFRAMES
// ============================================================

private val TIMEFRAMES = listOf(
    "1h", "2h", "3h", "4h", "5h", "6h", "7h", "8h", "9h",
    "12h", "24h", "2d", "3d", "4d", "5d", "6d", "7d", "15d", "1M"
)

// ============================================================
// DATA MODELS
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
        get() = (100.0 - btcDominance - ethDominance)
            .coerceAtLeast(0.0)
}

private data class BinanceSeed(
    val symbol: String,
    val lastPrice: Double,
    val change24h: Double,
    val volume24h: Double
)

enum class FlashState {
    None,
    Up,
    Down
}

// ============================================================
// BINANCE WEBSOCKET MANAGER
// ============================================================

class BinanceWebSocketManager {

    private val httpClient =
        OkHttpClient.Builder()
            .callTimeout(15, TimeUnit.SECONDS)
            .retryOnConnectionFailure(true)
            .build()

    private val wsClient =
        OkHttpClient.Builder()
            .readTimeout(0, TimeUnit.MILLISECONDS)
            .pingInterval(20, TimeUnit.SECONDS)
            .retryOnConnectionFailure(true)
            .build()

    private val scope =
        CoroutineScope(
            Job() + Dispatchers.IO
        )

    private val allSeeds =
        mutableListOf<BinanceSeed>()

    private val loadedSymbols =
        mutableSetOf<String>()

    private val loadedOrder =
        mutableListOf<String>()

    private val coinMap =
        mutableMapOf<String, CryptoCoin>()

    private val cgMetaCache =
        mutableMapOf<String, CoinGeckoMeta>()

    private var activeWebSocket: WebSocket? = null

    private var bootstrapJob: Job? = null
    private var publishJob: Job? = null
    private var metadataJob: Job? = null
    private var reconnectJob: Job? = null

    private var stopped = false

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

    fun start() {

        if (bootstrapJob?.isActive == true) {
            return
        }

        stopped = false

        bootstrapJob =
            scope.launch {
                bootstrap()
            }
    }

    private suspend fun bootstrap() {

        _status.value = "FETCHING"

        allSeeds.clear()
        allSeeds.addAll(loadBinanceSeeds())

        if (allSeeds.isEmpty()) {
            _status.value = "ERROR"
            return
        }

        loadGlobalStats()?.let {
            _global.value = it
        }

        fetchCoinGeckoPage(1)

        loadMoreCoinsInternal(20)

        reconnectWebSocket()

        _status.value = "LIVE"

        startBackgroundCoinGeckoLoading()
    }

    fun loadMoreCoins(count: Int) {

        if (stopped) return

        scope.launch {

            loadMoreCoinsInternal(count)

            reconnectWebSocket()
        }
    }

    private fun loadMoreCoinsInternal(count: Int) {

        val pending =
            allSeeds
                .asSequence()
                .filter {
                    it.symbol !in loadedSymbols
                }
                .sortedWith(
                    compareBy<BinanceSeed> {
                        cgMetaCache[
                            it.symbol
                                .removeSuffix("USDT")
                                .uppercase()
                        ]?.marketCapRank ?: Int.MAX_VALUE
                    }.thenByDescending {
                        it.volume24h
                    }
                )
                .take(count)
                .toList()

        if (pending.isEmpty()) {
            return
        }

        synchronized(coinMap) {

            pending.forEach { seed ->
                addSeedToCoins(seed)
            }

            publishNow()
        }
    }

    private fun addSeedToCoins(
        seed: BinanceSeed
    ) {

        val base =
            seed.symbol.removeSuffix("USDT")

        val meta =
            cgMetaCache[base.uppercase()]

        coinMap[base] =
            CryptoCoin(

                symbol = base,

                name =
                    meta?.name ?: base,

                logo =
                    meta?.logo ?: "",

                price =
                    seed.lastPrice,

                change24h =
                    seed.change24h,

                volume24h =
                    seed.volume24h,

                marketCap =
                    meta?.marketCap ?: 0.0,

                rank =
                    meta?.marketCapRank
                        ?: Int.MAX_VALUE
            )

        loadedSymbols.add(seed.symbol)

        if (seed.symbol !in loadedOrder) {
            loadedOrder.add(seed.symbol)
        }
    }

    fun reduceCoins(count: Int) {

        if (stopped) return

        scope.launch {

            synchronized(coinMap) {

                if (loadedOrder.size <= 20) {
                    return@synchronized
                }

                val removeCount =
                    count.coerceAtMost(
                        loadedOrder.size - 20
                    )

                repeat(removeCount) {

                    val fullSymbol =
                        loadedOrder.removeLastOrNull()
                            ?: return@repeat

                    val base =
                        fullSymbol.removeSuffix("USDT")

                    loadedSymbols.remove(fullSymbol)
                    coinMap.remove(base)
                }

                publishNow()
            }

            reconnectWebSocket()
        }
    }

    fun searchAndLoadCoin(
        query: String
    ) {

        if (query.isBlank()) {
            return
        }

        val clean =
            query.trim().uppercase()

        val match =
            allSeeds.firstOrNull {

                it.symbol
                    .removeSuffix("USDT")
                    .equals(
                        clean,
                        ignoreCase = true
                    )
            }

        if (
            match == null ||
            match.symbol in loadedSymbols
        ) {
            return
        }

        synchronized(coinMap) {

            addSeedToCoins(match)
            publishNow()
        }

        reconnectWebSocket()
    }

    private fun startBackgroundCoinGeckoLoading() {

        if (metadataJob?.isActive == true) {
            return
        }

        metadataJob =
            scope.launch {

                for (page in 2..4) {

                    if (stopped) break

                    fetchCoinGeckoPage(page)

                    delay(500)

                    updateLoadedCoinMetadata()
                }
            }
    }

    private fun updateLoadedCoinMetadata() {

        synchronized(coinMap) {

            coinMap.keys.forEach { symbol ->

                val meta =
                    cgMetaCache[
                        symbol.uppercase()
                    ] ?: return@forEach

                val old =
                    coinMap[symbol]
                        ?: return@forEach

                coinMap[symbol] =
                    old.copy(
                        name = meta.name,
                        logo = meta.logo,
                        marketCap = meta.marketCap,
                        rank = meta.marketCapRank
                    )
            }

            publishNow()
        }
    }

    private fun fetchCoinGeckoPage(
        page: Int
    ) {

        val url =
            "https://api.coingecko.com/api/v3/coins/markets" +
                    "?vs_currency=usd" +
                    "&order=market_cap_desc" +
                    "&per_page=250" +
                    "&page=$page" +
                    "&sparkline=false"

        try {

            val request =
                Request.Builder()
                    .url(url)
                    .build()

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

                    for (i in 0 until array.length()) {

                        val coin =
                            array.optJSONObject(i)
                                ?: continue

                        val symbol =
                            coin.optString("symbol")
                                .uppercase()

                        if (symbol.isBlank()) {
                            continue
                        }

                        val name =
                            coin.optString("name")

                        val logo =
                            coin.optString("image")

                        val marketCap =
                            coin.optDouble(
                                "market_cap",
                                0.0
                            )

                        val marketCapRank =
                            coin.optInt(
                                "market_cap_rank",
                                Int.MAX_VALUE
                            )

                        val old =
                            cgMetaCache[symbol]

                        if (
                            old == null ||
                            marketCapRank < old.marketCapRank
                        ) {

                            cgMetaCache[symbol] =
                                CoinGeckoMeta(
                                    name = name,
                                    logo = logo,
                                    marketCap = marketCap,
                                    marketCapRank = marketCapRank
                                )
                        }
                    }
                }

        } catch (_: Exception) {
        }
    }

    private fun reconnectWebSocket() {

        if (
            stopped ||
            loadedSymbols.isEmpty()
        ) {
            return
        }

        reconnectJob?.cancel()
        reconnectJob = null

        activeWebSocket?.close(
            1000,
            "Updating stream"
        )

        activeWebSocket = null

        val streams =
            synchronized(loadedSymbols) {

                loadedSymbols.joinToString("/") {
                    "${it.lowercase()}@ticker"
                }
            }

        val url =
            "wss://stream.binance.com:9443/stream?streams=$streams"

        val request =
            Request.Builder()
                .url(url)
                .build()

        activeWebSocket =
            wsClient.newWebSocket(
                request,
                object : WebSocketListener() {

                    override fun onOpen(
                        webSocket: WebSocket,
                        response: Response
                    ) {
                        _status.value = "LIVE"
                    }

                    override fun onMessage(
                        webSocket: WebSocket,
                        text: String
                    ) {
                        parseTickerMessage(text)
                    }

                    override fun onFailure(
                        webSocket: WebSocket,
                        t: Throwable,
                        response: Response?
                    ) {

                        if (!stopped) {

                            activeWebSocket = null

                            _status.value =
                                "RECONNECTING"

                            scheduleReconnect()
                        }
                    }

                    override fun onClosed(
                        webSocket: WebSocket,
                        code: Int,
                        reason: String
                    ) {

                        if (!stopped) {

                            activeWebSocket = null

                            _status.value =
                                "RECONNECTING"

                            scheduleReconnect()
                        }
                    }
                }
            )
    }

    private fun scheduleReconnect() {

        if (
            stopped ||
            reconnectJob?.isActive == true
        ) {
            return
        }

        reconnectJob =
            scope.launch {

                delay(2500)

                if (!stopped) {
                    reconnectWebSocket()
                }
            }
    }

    private fun parseTickerMessage(
        text: String
    ) {

        try {

            val root =
                JSONObject(text)

            val data =
                root.optJSONObject("data")
                    ?: return

            val fullSymbol =
                data.optString("s")

            if (
                !fullSymbol.endsWith(
                    "USDT",
                    ignoreCase = true
                )
            ) {
                return
            }

            val symbol =
                fullSymbol.removeSuffix("USDT")

            val price =
                data
                    .optString("c")
                    .toDoubleOrNull()
                    ?: return

            val change24h =
                data
                    .optString("P")
                    .toDoubleOrNull()
                    ?: 0.0

            val volume24h =
                data
                    .optString("q")
                    .toDoubleOrNull()
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

    private fun schedulePublish() {

        if (publishJob?.isActive == true) {
            return
        }

        publishJob =
            scope.launch {

                delay(150)

                publishNow()
            }
    }

    private fun publishNow() {

        synchronized(coinMap) {

            val list =
                coinMap
                    .values
                    .sortedWith(
                        compareBy<CryptoCoin> {
                            it.rank
                        }.thenBy {
                            it.symbol
                        }
                    )

            _coins.value = list
        }
    }

    private fun loadBinanceSeeds():
        List<BinanceSeed> {

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

                        if (
                            symbol.contains(
                                "UPUSDT",
                                true
                            ) ||
                            symbol.contains(
                                "DOWNUSDT",
                                true
                            ) ||
                            symbol.contains(
                                "BULLUSDT",
                                true
                            ) ||
                            symbol.contains(
                                "BEARUSDT",
                                true
                            )
                        ) {
                            continue
                        }

                        val price =
                            obj
                                .optString("lastPrice")
                                .toDoubleOrNull()
                                ?: continue

                        val change =
                            obj
                                .optString(
                                    "priceChangePercent"
                                )
                                .toDoubleOrNull()
                                ?: 0.0

                        val volume =
                            obj
                                .optString("quoteVolume")
                                .toDoubleOrNull()
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

                    result.sortedByDescending {
                        it.volume24h
                    }
                }

        } catch (_: Exception) {

            emptyList()
        }
    }

    private fun loadGlobalStats():
        GlobalStats? {

        return try {

            val request =
                Request.Builder()
                    .url(
                        "https://api.coingecko.com/api/v3/global"
                    )
                    .build()

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

                    val totalMarketCap =
                        data
                            .optJSONObject(
                                "total_market_cap"
                            )
                            ?.optDouble(
                                "usd",
                                0.0
                            )
                            ?: 0.0

                    val totalVolume =
                        data
                            .optJSONObject(
                                "total_volume"
                            )
                            ?.optDouble(
                                "usd",
                                0.0
                            )
                            ?: 0.0

                    val percentages =
                        data.optJSONObject(
                            "market_cap_percentage"
                        )

                    val btc =
                        percentages
                            ?.optDouble(
                                "btc",
                                0.0
                            )
                            ?: 0.0

                    val eth =
                        percentages
                            ?.optDouble(
                                "eth",
                                0.0
                            )
                            ?: 0.0

                    GlobalStats(
                        totalMarketCap =
                            totalMarketCap,
                        totalVolume =
                            totalVolume,
                        btcDominance =
                            btc,
                        ethDominance =
                            eth
                    )
                }

        } catch (_: Exception) {

            null
        }
    }

    fun hasMoreCoins(): Boolean {
        return loadedSymbols.size < allSeeds.size
    }

    fun stop() {

        stopped = true

        bootstrapJob?.cancel()
        publishJob?.cancel()
        metadataJob?.cancel()
        reconnectJob?.cancel()

        activeWebSocket?.close(
            1000,
            "Stopped"
        )

        activeWebSocket = null

        scope.cancel()
    }
}

// ============================================================
// VIEW MODEL
// ============================================================

class CryptoViewModel :
    ViewModel() {

    val manager =
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
                            PurpleMimosa
                    )
            ) {

                var showWelcome by
                    remember {
                        mutableStateOf(true)
                    }

                LaunchedEffect(Unit) {

                    delay(2000)

                    showWelcome = false
                }

                if (showWelcome) {

                    WelcomeScreen()

                } else {

                    CryptoExchangeApp()
                }
            }
        }
    }
}

// ============================================================
// WELCOME
// ============================================================

@Composable
fun WelcomeScreen() {

    Box(
        Modifier.fillMaxSize()
    ) {

        Image(

            painter =
                painterResource(
                    id =
                        R.drawable.welcome_image
                ),

            contentDescription =
                "Welcome",

            modifier =
                Modifier.fillMaxSize(),

            contentScale =
                ContentScale.Crop
        )
    }
}

// ============================================================
// NAVIGATION
// ============================================================

sealed class NavItem(
    val title: String,
    val icon: ImageVector
) {

    object Home :
        NavItem(
            "Home",
            Icons.Default.Home
        )

    object Chart :
        NavItem(
            "Chart",
            Icons.Default.ShowChart
        )

    object Trade :
        NavItem(
            "Trade",
            Icons.Default.SwapHoriz
        )

    object Analysis :
        NavItem(
            "Analysis",
            Icons.Default.Assessment
        )

    object News :
        NavItem(
            "News",
            Icons.Default.Article
        )
}

// ============================================================
// APP
// ============================================================

@Composable
fun CryptoExchangeApp(
    vm: CryptoViewModel =
        viewModel()
) {

    var selectedTab by
        rememberSaveable {
            mutableStateOf("Home")
        }

    val coins by
        vm.coins.collectAsState()

    val status by
        vm.status.collectAsState()

    val global by
        vm.global.collectAsState()

    Scaffold(

        bottomBar = {

            NavigationBar {

                val items =
                    listOf(
                        NavItem.Home,
                        NavItem.Chart,
                        NavItem.Trade,
                        NavItem.Analysis,
                        NavItem.News
                    )

                items.forEach { item ->

                    NavigationBarItem(

                        selected =
                            selectedTab ==
                                item.title,

                        onClick = {
                            selectedTab =
                                item.title
                        },

                        icon = {
                            Icon(
                                item.icon,
                                contentDescription =
                                    item.title
                            )
                        },

                        label = {
                            Text(
                                item.title,
                                fontSize = 10.sp
                            )
                        },

                        colors =
                            NavigationBarItemDefaults
                                .colors(
                                    selectedIconColor =
                                        PurpleMimosa,
                                    selectedTextColor =
                                        PurpleMimosa
                                )
                    )
                }
            }
        }

    ) { padding ->

        Box(
            Modifier.padding(padding)
        ) {

            when (selectedTab) {

    "Home" ->

        HomeScreen(
            coins =
                coins,
            status =
                status,
            global =
                global,
            vm =
                vm
        )

    "Trade" -> {
        val tradeVm: com.practice.cryptoapp.ui.TradeViewModel = viewModel()
        TradeScreen(vm = tradeVm)
    }

    else ->

        DummyTabScreen(
            title =
                selectedTab
        )
}

        }
    }
}

// ============================================================
// DUMMY
// ============================================================

@Composable
fun DummyTabScreen(
    title: String
) {

    Box(
        Modifier.fillMaxSize(),
        contentAlignment =
            Alignment.Center
    ) {

        Text(
            "$title Screen Coming Soon",
            fontSize =
                16.sp,
            fontWeight =
                FontWeight.Bold
        )
    }
}

// ============================================================
// HOME
// ============================================================

@Composable
fun HomeScreen(
    coins: List<CryptoCoin>,
    status: String,
    global: GlobalStats,
    vm: CryptoViewModel
) {

    val balance by
    DemoAccountStore.balance.collectAsState()

    val initialBalance =
        10000.0

    var search by
        rememberSaveable {
            mutableStateOf("")
        }

    var filter by
        rememberSaveable {
            mutableStateOf("ALL")
        }

    var selectedTimeframe by
        rememberSaveable {
            mutableStateOf("24h")
        }

    val flashMap =
        remember {
            mutableStateMapOf<
                String,
                FlashState
            >()
        }

    val previousPrices =
        remember {
            mutableStateMapOf<
                String,
                Double
            >()
        }

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

                    delay(200)

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

    LaunchedEffect(search) {

        if (search.isBlank()) {
            return@LaunchedEffect
        }

        delay(500)

        vm.manager.searchAndLoadCoin(
            search
        )
    }

    val displayCoins =
        remember(
            coins,
            search,
            filter
        ) {

            var list =
                coins

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
        }

    LazyColumn(
        modifier =
            Modifier
                .fillMaxSize()
                .padding(
                    horizontal = 10.dp
                )
    ) {

        item {

            HeaderSection()

            BalanceCard(
                balance =
                    balance,
                initialBalance =
                    initialBalance,
                selectedTimeframe =
                    selectedTimeframe,
                onTimeframeSelected = {
                    selectedTimeframe =
                        it
                },
                onReset = {
                    DemoAccountStore.reset()
                }
            )

            GlobalStatsCard(
                global
            )

            Spacer(
                Modifier.height(8.dp)
            )

            OutlinedTextField(

                value =
                    search,

                onValueChange = {
                    search = it
                },

                modifier =
                    Modifier.fillMaxWidth(),

                singleLine =
                    true,

                shape =
                    RoundedCornerShape(
                        30.dp
                    ),

                placeholder = {
                    Text(
                        "Search crypto..."
                    )
                },

                leadingIcon = {
                    Icon(
                        Icons.Default.Search,
                        null,
                        tint =
                            PurpleMimosa
                    )
                }
            )

            Spacer(
                Modifier.height(8.dp)
            )

            FilterChipsSection(
                filter
            ) {
                filter = it
            }

            Spacer(
                Modifier.height(4.dp)
            )

            Text(
                "${coins.size} Active Coins Loaded",
                fontSize =
                    10.sp,
                color =
                    Color.Gray
            )
        }

        items(
            items =
                displayCoins,
            key = {
                it.symbol
            }
        ) { coin ->

            CryptoRow(
                coin =
                    coin,
                flash =
                    flashMap[
                        coin.symbol
                    ]
                        ?: FlashState.None
            )
        }

        if (search.isBlank()) {

            item {

                Row(
                    Modifier
                        .fillMaxWidth()
                        .padding(
                            vertical = 14.dp
                        ),
                    horizontalArrangement =
                        Arrangement.SpaceEvenly
                ) {

                    Button(
                        onClick = {
                            vm.manager
                                .reduceCoins(50)
                        },
                        enabled =
                            coins.size > 20,
                        colors =
                            ButtonDefaults
                                .buttonColors(
                                    containerColor =
                                        Color.DarkGray
                                )
                    ) {
                        Text(
                            "Previous 50"
                        )
                    }

                    Button(
                        onClick = {
                            vm.manager
                                .loadMoreCoins(50)
                        },
                        enabled =
                            vm.manager
                                .hasMoreCoins(),
                        colors =
                            ButtonDefaults
                                .buttonColors(
                                    containerColor =
                                        PurpleMimosa
                                )
                    ) {
                        Text(
                            "Next 50"
                        )
                    }
                }
            }
        }
    }
}

// ============================================================
// HEADER
// ============================================================

@Composable
fun HeaderSection() {

    Row(
        Modifier
            .fillMaxWidth()
            .padding(
                vertical = 8.dp
            ),
        verticalAlignment =
            Alignment.CenterVertically
    ) {

        Column(
            Modifier.weight(1f)
        ) {

            Text(
                "Test Exchange",
                fontSize =
                    16.sp,
                fontWeight =
                    FontWeight.Bold
            )
        }
    }
}

// ============================================================
// BALANCE
// ============================================================

@Composable
fun BalanceCard(
    balance: Double,
    initialBalance: Double,
    selectedTimeframe: String,
    onTimeframeSelected:
        (String) -> Unit,
    onReset: () -> Unit
) {

    var expanded by
        remember {
            mutableStateOf(false)
        }

    val factor =
        when (selectedTimeframe) {
            "1h" -> 0.1
            "2h" -> 0.2
            "3h" -> 0.3
            "4h" -> 0.4
            "5h" -> 0.5
            "6h" -> 0.6
            "7h" -> 0.7
            "8h" -> 0.8
            "9h" -> 0.9
            "12h" -> 1.1
            "24h" -> 1.5
            "2d" -> 2.0
            "3d" -> 2.8
            "4d" -> 3.5
            "5d" -> 4.2
            "6d" -> 5.0
            "7d" -> 6.0
            "15d" -> 8.5
            "1M" -> 12.0
            else -> 1.0
        }

    val pnlPercent =
        (
            (
                (
                    balance -
                        initialBalance
                ) /
                    initialBalance
            ) * 100
        ) * factor

    Card(
        Modifier
            .fillMaxWidth()
            .padding(
                vertical = 4.dp
            ),
        shape =
            RoundedCornerShape(
                12.dp
            )
    ) {

        Row(
            Modifier
                .padding(12.dp)
                .fillMaxWidth(),
            horizontalArrangement =
                Arrangement.SpaceBetween,
            verticalAlignment =
                Alignment.CenterVertically
        ) {

            Column {

                Text(
                    "Demo Balance",
                    fontSize =
                        9.sp,
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
                    fontSize =
                        18.sp,
                    fontWeight =
                        FontWeight.Bold
                )

                Row(
                    verticalAlignment =
                        Alignment.CenterVertically
                ) {

                    Box {

                        TextButton(
                            onClick = {
                                expanded = true
                            },
                            contentPadding =
                                PaddingValues(
                                    0.dp
                                )
                        ) {

                            Text(
                                "PNL ($selectedTimeframe) ▼: ",
                                fontSize =
                                    10.sp,
                                color =
                                    PurpleMimosa
                            )
                        }

                        DropdownMenu(
                            expanded =
                                expanded,
                            onDismissRequest = {
                                expanded = false
                            }
                        ) {

                            TIMEFRAMES.forEach {
                                    timeframe ->

                                DropdownMenuItem(
                                    text = {
                                        Text(
                                            timeframe,
                                            fontSize =
                                                12.sp
                                        )
                                    },
                                    onClick = {
                                        onTimeframeSelected(
                                            timeframe
                                        )
                                        expanded =
                                            false
                                    }
                                )
                            }
                        }
                    }

                    Text(
                        formatPct(
                            pnlPercent
                        ),
                        fontSize =
                            10.sp,
                        fontWeight =
                            FontWeight.Bold,
                        color =
                            if (
                                pnlPercent >= 0
                            ) {
                                PositiveGreen
                            } else {
                                NegativeRed
                            }
                    )
                }
            }

            Button(
                onClick =
                    onReset,
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

// ============================================================
// GLOBAL MARKET
// ============================================================

@Composable
fun GlobalStatsCard(
    global: GlobalStats
) {

    Card(
        Modifier
            .fillMaxWidth()
            .padding(
                vertical = 4.dp
            ),
        shape =
            RoundedCornerShape(
                12.dp
            )
    ) {

        Column(
            Modifier.padding(
                10.dp
            )
        ) {

            Text(
                "Global Market Overview",
                fontWeight =
                    FontWeight.Bold,
                fontSize =
                    11.sp
            )

            Spacer(
                Modifier.height(
                    6.dp
                )
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
                    "BTC Dom",
                    formatPct(
                        global.btcDominance
                    )
                )
            }

            Spacer(
                Modifier.height(
                    6.dp
                )
            )

            Row(
                Modifier.fillMaxWidth(),
                horizontalArrangement =
                    Arrangement.SpaceBetween
            ) {

                MarketValue(
                    "ETH Dom",
                    formatPct(
                        global.ethDominance
                    )
                )

                MarketValue(
                    "ALT Dom",
                    formatPct(
                        global.altDominance
                    )
                )

                MarketValue(
                    "Markets",
                    "LIVE"
                )
            }
        }
    }
}

// ============================================================
// FILTERS
// ============================================================

@Composable
fun FilterChipsSection(
    selectedFilter: String,
    onSelect:
        (String) -> Unit
) {

    Row(
        horizontalArrangement =
            Arrangement.spacedBy(
                8.dp
            )
    ) {

        listOf(
            "ALL",
            "GAINER",
            "LOSER"
        ).forEach { filter ->

            FilterChip(

                selected =
                    selectedFilter ==
                        filter,

                onClick = {
                    onSelect(
                        filter
                    )
                },

                shape =
                    CircleShape,

                label = {
                    Text(
                        filter,
                        fontSize =
                            11.sp,
                        fontWeight =
                            FontWeight.Medium
                    )
                },

                colors =
                    FilterChipDefaults
                        .filterChipColors(
                            selectedContainerColor =
                                PurpleMimosa,
                            selectedLabelColor =
                                Color.White
                        )
            )
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
                vertical = 8.dp
            ),
        verticalAlignment =
            Alignment.CenterVertically
    ) {

        Text(
            coin.rank
                .takeIf {
                    it != Int.MAX_VALUE
                }
                ?.toString()
                ?: "—",
            fontSize =
                9.sp,
            color =
                Color.Gray,
            modifier =
                Modifier.width(
                    24.dp
                )
        )

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
                        .size(26.dp)
                        .clip(
                            CircleShape
                        ),
                contentScale =
                    ContentScale.Crop
            )

        } else {

            Box(
                Modifier
                    .size(26.dp)
                    .clip(
                        CircleShape
                    ),
                contentAlignment =
                    Alignment.Center
            ) {

                Text(
                    coin.symbol.take(
                        1
                    ),
                    fontWeight =
                        FontWeight.Bold,
                    fontSize =
                        10.sp
                )
            }
        }

        Spacer(
            Modifier.width(
                8.dp
            )
        )

        Column(
            Modifier.weight(
                1f
            )
        ) {

            Text(
                coin.name,
                fontWeight =
                    FontWeight.Bold,
                fontSize =
                    12.sp,
                maxLines =
                    1
            )

            Text(
                coin.symbol,
                fontSize =
                    9.sp,
                color =
                    Color.Gray,
                maxLines =
                    1
            )
        }

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
                fontSize =
                    11.sp,
                color =
                    priceColor
            )

            Text(
                formatPct(
                    coin.change24h
                ),
                fontSize =
                    10.sp,
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
    }

    HorizontalDivider(
        thickness =
            0.5.dp
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

    Column {

        Text(
            title,
            fontSize =
                8.sp,
            color =
                Color.Gray
        )

        Text(
            value,
            fontSize =
                10.sp,
            fontWeight =
                FontWeight.Bold
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

fun formatPrice(
    value: Double
): String {

    if (
        value.isNaN() ||
        value.isInfinite()
    ) {
        return "0"
    }

    return if (
        value >= 1.0
    ) {

        String.format(
            Locale.US,
            "%,.2f",
            value
        )

    } else {

        String.format(
            Locale.US,
            "%.8f",
            value
        )
            .trimEnd('0')
            .trimEnd('.')
    }
}

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

// ============================================================
// DEMO ACCOUNT STORE
// ============================================================

object DemoAccountStore {
    private val _balance = MutableStateFlow(10000.0)
    val balance: StateFlow<Double> = _balance.asStateFlow()

    fun reset() {
        _balance.value = 10000.0
    }

    fun updateBalance(newBalance: Double) {
        _balance.value = newBalance
    }
}

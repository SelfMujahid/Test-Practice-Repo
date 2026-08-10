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


// =====================================================
// COLORS
// =====================================================

private val UpGreen = Color(0xFF008000)
private val DownRed = Color.Red
private val NormalPriceColor = Color.Black


// =====================================================
// DATA
// =====================================================

data class CryptoCoin(
    val symbol: String,
    val name: String,
    val logo: String,
    val price: Double,

    val change1h: Double,
    val change4h: Double,
    val change24h: Double,
    val change1d: Double,
    val change7d: Double,

    val volume24h: Double,
    val marketCap: Double,
    val rank: Int
)

data class CoinGeckoMeta(
    val name: String,
    val logo: String,
    val marketCap: Double,
    val marketCapRank: Int,
    val change1h: Double,
    val change7d: Double
)

data class GlobalStats(
    val totalMarketCap: Double = 0.0,
    val totalVolume: Double = 0.0,
    val btcDominance: Double = 0.0,
    val ethDominance: Double = 0.0,
    val marketCapChange24h: Double = 0.0,

    // Extra global changes
    val marketCapChange1h: Double = 0.0,
    val marketCapChange4h: Double = 0.0,
    val marketCapChange7d: Double = 0.0
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


// =====================================================
// FLASH STATE
//
// IMPORTANT:
// This is NOT private because a public composable uses it.
// This fixes:
// "Function public exposes its private-in-file parameter type FlashState"
// =====================================================

enum class FlashState {
    None,
    Up,
    Down
}


// =====================================================
// BINANCE WEBSOCKET MANAGER
// =====================================================

class BinanceWebSocketManager {

    private val httpClient = OkHttpClient.Builder()
        .callTimeout(20, TimeUnit.SECONDS)
        .build()

    private val wsClient = OkHttpClient.Builder()
        .readTimeout(0, TimeUnit.MILLISECONDS)
        .pingInterval(20, TimeUnit.SECONDS)
        .retryOnConnectionFailure(true)
        .build()

    private val scope =
        CoroutineScope(Job() + Dispatchers.IO)

    private val coinMap =
        mutableMapOf<String, CryptoCoin>()

    private val batches =
        mutableMapOf<Int, StreamBatch>()

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


    // =================================================
    // START
    // =================================================

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


    // =================================================
    // BOOTSTRAP
    // =================================================

    private fun bootstrap() {

        _status.value = "FETCHING"

        val cgMetaMap =
            loadCoinGeckoMetaMap()

        val globalStats =
            loadGlobalStats()

        if (globalStats != null) {
            _global.value = globalStats
        }

        val seeds =
            loadBinanceSeeds()

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

                coinMap[base] =
                    CryptoCoin(

                        symbol = base,

                        name =
                            meta?.name
                                ?: base,

                        logo =
                            meta?.logo
                                .orEmpty(),

                        price =
                            seed.lastPrice,

                        change1h =
                            meta?.change1h
                                ?: 0.0,

                        change4h =
                            0.0,

                        change24h =
                            seed.change24h,

                        change1d =
                            0.0,

                        change7d =
                            meta?.change7d
                                ?: 0.0,

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
            seeds.map {
                it.symbol
            }
        )

        startMetadataRefresh()

        _status.value = "LIVE"
    }


    // =================================================
    // BINANCE REST
    // =================================================

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

                    val arr =
                        JSONArray(body)

                    val result =
                        ArrayList<BinanceSeed>(
                            arr.length()
                        )

                    for (i in 0 until arr.length()) {

                        val obj =
                            arr.optJSONObject(i)
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
                                symbol,
                                price,
                                change,
                                volume
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


    // =================================================
    // COINGECKO
    // =================================================

    private fun loadCoinGeckoMetaMap():
            Map<String, CoinGeckoMeta> {

        val output =
            mutableMapOf<String, CoinGeckoMeta>()

        fun fetchPage(page: Int) {

            val url =
                "https://api.coingecko.com/api/v3/coins/markets" +
                    "?vs_currency=usd" +
                    "&order=market_cap_desc" +
                    "&per_page=250" +
                    "&page=$page" +
                    "&sparkline=false" +
                    "&price_change_percentage=1h,24h,7d"

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

                        val arr =
                            JSONArray(body)

                        for (
                            i in 0 until arr.length()
                        ) {

                            val coin =
                                arr.optJSONObject(i)
                                    ?: continue

                            val symbol =
                                coin
                                    .optString("symbol")
                                    .uppercase()

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

                            val change1h =
                                coin.optDouble(
                                    "price_change_percentage_1h_in_currency",
                                    0.0
                                )

                            val change7d =
                                coin.optDouble(
                                    "price_change_percentage_7d_in_currency",
                                    0.0
                                )

                            val current =
                                output[symbol]

                            if (
                                current == null ||
                                rank <
                                current.marketCapRank
                            ) {

                                output[symbol] =
                                    CoinGeckoMeta(
                                        name =
                                            name,

                                        logo =
                                            logo,

                                        marketCap =
                                            marketCap,

                                        marketCapRank =
                                            rank,

                                        change1h =
                                            change1h,

                                        change7d =
                                            change7d
                                    )
                            }
                        }
                    }

            } catch (_: Exception) {
            }
        }

        fetchPage(1)
        fetchPage(2)
        fetchPage(3)
        fetchPage(4)

        return output
    }


    // =================================================
    // GLOBAL STATS
    // =================================================

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

                    val root =
                        JSONObject(body)
                            .optJSONObject("data")
                            ?: return null

                    val marketCap =
                        root
                            .optJSONObject(
                                "total_market_cap"
                            )
                            ?.optDouble(
                                "usd",
                                0.0
                            )
                            ?: 0.0

                    val volume =
                        root
                            .optJSONObject(
                                "total_volume"
                            )
                            ?.optDouble(
                                "usd",
                                0.0
                            )
                            ?: 0.0

                    val percentages =
                        root.optJSONObject(
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

                    val change24h =
                        root.optDouble(
                            "market_cap_change_percentage_24h_usd",
                            0.0
                        )

                    GlobalStats(
                        totalMarketCap =
                            marketCap,

                        totalVolume =
                            volume,

                        btcDominance =
                            btc,

                        ethDominance =
                            eth,

                        marketCapChange24h =
                            change24h
                    )
                }

        } catch (_: Exception) {

            null
        }
    }


    // =================================================
    // COINGECKO AUTO REFRESH
    // =================================================

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

                    if (stopped) {
                        break
                    }

                    val meta =
                        loadCoinGeckoMetaMap()

                    val stats =
                        loadGlobalStats()

                    if (stats != null) {
                        _global.value =
                            stats
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
                                        m.marketCap,

                                    change1h =
                                        m.change1h,

                                    change7d =
                                        m.change7d
                                )
                        }
                    }

                    publishNow()
                }
            }
    }


    // =================================================
    // WEBSOCKET
    //
    // IMPORTANT:
    // Binance does NOT have:
    // @ticker_1h
    // @ticker_4h
    // @ticker_1d
    //
    // Valid kline streams are:
    // @kline_1h
    // @kline_4h
    // @kline_1d
    // =================================================

    private fun connectBatches(
        symbols: List<String>
    ) {

        val unique =
            symbols.distinct()

        unique
            .chunked(180)
            .forEachIndexed {
                    index,
                    batchSymbols ->

                batches[index] =
                    StreamBatch(
                        batchSymbols
                    )

                openBatch(index)
            }
    }


    private fun openBatch(
        batchIndex: Int
    ) {

        if (stopped) {
            return
        }

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

        val streams =
            buildList {

                batch.symbols.forEach { symbol ->

                    val lower =
                        symbol.lowercase()

                    // LIVE PRICE
                    add(
                        "$lower@ticker"
                    )

                    // 1 HOUR
                    add(
                        "$lower@kline_1h"
                    )

                    // 4 HOURS
                    add(
                        "$lower@kline_4h"
                    )

                    // 1 DAY
                    add(
                        "$lower@kline_1d"
                    )
                }

            }.joinToString("/")

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
                        _status.value = "LIVE"
                    }

                    override fun onMessage(
                        webSocket: WebSocket,
                        text: String
                    ) {
                        parseCombinedMessage(text)
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


    // =================================================
    // PARSE WEBSOCKET
    // =================================================

    private fun parseCombinedMessage(
        text: String
    ) {

        try {

            val root =
                JSONObject(text)

            val data =
                root.optJSONObject("data")
                    ?: return

            val stream =
                root.optString("stream")

            // -----------------------------------------
            // NORMAL LIVE TICKER
            // -----------------------------------------

            if (
                stream.endsWith("@ticker")
            ) {

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
                    fullSymbol.removeSuffix(
                        "USDT"
                    )

                val price =
                    data.optString("c")
                        .toDoubleOrNull()
                        ?: return

                val change =
                    data.optString("P")
                        .toDoubleOrNull()
                        ?: 0.0

                val volume =
                    data.optString("q")
                        .toDoubleOrNull()
                        ?: 0.0

                synchronized(coinMap) {

                    val old =
                        coinMap[symbol]
                            ?: return

                    coinMap[symbol] =
                        old.copy(

                            price =
                                price,

                            change24h =
                                change,

                            volume24h =
                                volume
                        )
                }

                schedulePublish()

                return
            }


            // -----------------------------------------
            // KLINE
            // -----------------------------------------

            if (
                stream.contains("@kline_")
            ) {

                val kline =
                    data.optJSONObject("k")
                        ?: return

                val fullSymbol =
                    kline.optString("s")

                if (
                    !fullSymbol.endsWith(
                        "USDT",
                        ignoreCase = true
                    )
                ) {
                    return
                }

                val symbol =
                    fullSymbol.removeSuffix(
                        "USDT"
                    )

                val interval =
                    kline.optString("i")

                val open =
                    kline.optString("o")
                        .toDoubleOrNull()
                        ?: return

                val close =
                    kline.optString("c")
                        .toDoubleOrNull()
                        ?: return

                if (open <= 0.0) {
                    return
                }

                val change =
                    ((close - open) / open) * 100.0

                synchronized(coinMap) {

                    val old =
                        coinMap[symbol]
                            ?: return

                    val updated =
                        when (interval) {

                            "1h" ->
                                old.copy(
                                    price =
                                        close,
                                    change1h =
                                        change
                                )

                            "4h" ->
                                old.copy(
                                    price =
                                        close,
                                    change4h =
                                        change
                                )

                            "1d" ->
                                old.copy(
                                    price =
                                        close,
                                    change1d =
                                        change
                                )

                            else ->
                                old
                        }

                    coinMap[symbol] =
                        updated
                }

                schedulePublish()
            }

        } catch (_: Exception) {
        }
    }


    // =================================================
    // PUBLISH
    // =================================================

    private fun schedulePublish() {

        if (
            publishJob?.isActive == true
        ) {
            return
        }

        publishJob =
            scope.launch {

                delay(120)

                publishNow()
            }
    }


    private fun publishNow() {

        synchronized(coinMap) {

            val list =
                coinMap.values

                    // IMPORTANT:
                    // Ranking is MARKET CAP.
                    //
                    // Price updates cannot move rank.
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

            _coins.value =
                list
        }
    }


    // =================================================
    // RECONNECT
    // =================================================

    private fun scheduleReconnect(
        batchIndex: Int
    ) {

        if (stopped) {
            return
        }

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
                    openBatch(
                        batchIndex
                    )
                }
            }
    }


    // =================================================
    // STOP
    // =================================================

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


// =====================================================
// VIEW MODEL
// =====================================================

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


// =====================================================
// MAIN ACTIVITY
// =====================================================

class MainActivity :
    ComponentActivity() {

    override fun onCreate(
        savedInstanceState: Bundle?
    ) {

        super.onCreate(
            savedInstanceState
        )

        setContent {

            MaterialTheme {

                CryptoExchangeApp()
            }
        }
    }
}


// =====================================================
// APP
// =====================================================

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
                        Modifier.padding(20.dp),

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
                                selectedTab == index,

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
                Modifier.padding(padding)
            ) {

                when (selectedTab) {

                    0 ->
                        HomeScreen(
                            coins =
                                coins,

                            status =
                                status,

                            global =
                                global,

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


// =====================================================
// HOME
// =====================================================

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


    // -----------------------------------------------
    // PRICE FLASH STATE
    // -----------------------------------------------

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

                    // EXACTLY 0.6 SECOND
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


    // -----------------------------------------------
    // FILTER / SEARCH
    // -----------------------------------------------

    val displayCoins =
        remember(
            coins,
            search,
            filter,
            visibleCount
        ) {

            var list =
                coins

            if (
                search.isNotBlank()
            ) {

                val q =
                    search.trim()

                list =
                    list.filter {

                        it.symbol.contains(
                            q,
                            ignoreCase = true
                        ) ||

                        it.name.contains(
                            q,
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


    LazyColumn(

        modifier =
            Modifier
                .fillMaxSize()
                .padding(12.dp)

    ) {


        // =================================================
        // HEADER
        // =================================================

        item {

            Row(

                modifier =
                    Modifier
                        .fillMaxWidth()
                        .padding(
                            bottom = 10.dp
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
                            .size(32.dp)
                            .clickable {
                                onMenuClick()
                            }
                )

                Spacer(
                    Modifier.width(10.dp)
                )

                Column(
                    Modifier.weight(1f)
                ) {

                    Text(
                        "Crypto Exchange",

                        fontSize =
                            17.sp,

                        fontWeight =
                            FontWeight.Bold
                    )

                    Text(
                        "Binance Live + CoinGecko",

                        fontSize =
                            10.sp,

                        color =
                            Color.Gray
                    )
                }

                Text(

                    "● $status",

                    fontSize =
                        10.sp,

                    color =
                        if (
                            status ==
                            "LIVE"
                        ) {
                            UpGreen
                        } else {
                            Color(0xFFFF9800)
                        }
                )
            }
        }


        // =================================================
        // DEMO BALANCE
        // =================================================

        item {

            Card(

                Modifier
                    .fillMaxWidth()
                    .padding(
                        vertical = 5.dp
                    )

            ) {

                Column(
                    Modifier.padding(14.dp)
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

                                fontSize =
                                    10.sp,

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
                                    22.sp,

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
                                balance <= 100.0

                        ) {

                            Text(
                                "Reset"
                            )
                        }
                    }

                    Spacer(
                        Modifier.height(12.dp)
                    )

                    val periods =
                        listOf(
                            "1h",
                            "4h",
                            "8h",
                            "12h",
                            "24h",
                            "1d",
                            "2d",
                            "3d",
                            "4d",
                            "5d",
                            "6d",
                            "7d",
                            "15d",
                            "30d"
                        )

                    periods
                        .chunked(4)
                        .forEach { row ->

                            Row(
                                Modifier.fillMaxWidth()
                            ) {

                                row.forEach { period ->

                                    Column(

                                        Modifier.weight(
                                            1f
                                        ),

                                        horizontalAlignment =
                                            Alignment.CenterHorizontally

                                    ) {

                                        Text(
                                            period,
                                            fontSize =
                                                10.sp,
                                            color =
                                                Color.Gray
                                        )

                                        Text(
                                            "+0.00%",
                                            fontSize =
                                                11.sp,
                                            color =
                                                UpGreen
                                        )
                                    }
                                }
                            }

                            Spacer(
                                Modifier.height(7.dp)
                            )
                        }
                }
            }
        }


        // =================================================
        // GLOBAL MARKET
        // =================================================

        item {

            Card(

                Modifier
                    .fillMaxWidth()
                    .padding(
                        vertical = 5.dp
                    )

            ) {

                Column(
                    Modifier.padding(14.dp)
                ) {

                    Text(
                        "Global Market",

                        fontWeight =
                            FontWeight.Bold,

                        fontSize =
                            12.sp
                    )

                    Spacer(
                        Modifier.height(10.dp)
                    )


                    Row(

                        Modifier.fillMaxWidth(),

                        horizontalArrangement =
                            Arrangement.SpaceBetween
                    ) {

                        GlobalMarketValue(
                            "Global MC",
                            formatLarge(
                                global.totalMarketCap
                            ),
                            global.marketCapChange24h
                        )

                        GlobalMarketValue(
                            "24h Volume",
                            formatLarge(
                                global.totalVolume
                            ),
                            null
                        )

                        GlobalMarketValue(
                            "BTC Dom.",
                            formatPct(
                                global.btcDominance
                            ),
                            null
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

                        GlobalMarketValue(
                            "ETH Dom.",
                            formatPct(
                                global.ethDominance
                            ),
                            null
                        )

                        GlobalMarketValue(
                            "Alt Dom.",
                            formatPct(
                                global.altDominance
                            ),
                            null
                        )

                        GlobalMarketValue(
                            "24h Chg",
                            formatPct(
                                global.marketCapChange24h
                            ),
                            global.marketCapChange24h
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

                        GlobalMarketValue(
                            "1h",
                            formatPct(
                                global.marketCapChange1h
                            ),
                            global.marketCapChange1h
                        )

                        GlobalMarketValue(
                            "4h",
                            formatPct(
                                global.marketCapChange4h
                            ),
                            global.marketCapChange4h
                        )

                        GlobalMarketValue(
                            "7d",
                            formatPct(
                                global.marketCapChange7d
                            ),
                            global.marketCapChange7d
                        )
                    }
                }
            }
        }


        // =================================================
        // SEARCH
        // =================================================

        item {

            OutlinedTextField(

                value =
                    search,

                onValueChange = {

                    search = it

                    visibleCount = 20
                },

                modifier =
                    Modifier.fillMaxWidth(),

                singleLine =
                    true,

                shape =
                    RoundedCornerShape(28.dp),

                placeholder = {
                    Text(
                        "Search crypto..."
                    )
                },

                leadingIcon = {
                    Icon(
                        Icons.Default.Search,
                        null
                    )
                }
            )


            Spacer(
                Modifier.height(8.dp)
            )


            Row(

                horizontalArrangement =
                    Arrangement.spacedBy(8.dp)
            ) {

                FilterChip(

                    selected =
                        filter == "ALL",

                    onClick = {

                        filter = "ALL"

                        visibleCount = 20
                    },

                    label = {
                        Text("All")
                    }
                )

                FilterChip(

                    selected =
                        filter == "GAINER",

                    onClick = {

                        filter = "GAINER"

                        visibleCount = 20
                    },

                    label = {
                        Text("Gainer")
                    }
                )

                FilterChip(

                    selected =
                        filter == "LOSER",

                    onClick = {

                        filter = "LOSER"

                        visibleCount = 20
                    },

                    label = {
                        Text("Loser")
                    }
                )
            }


            Spacer(
                Modifier.height(6.dp)
            )


            Text(

                "${coins.size} live USDT markets",

                fontSize =
                    10.sp,

                color =
                    Color.Gray
            )
        }


        // =================================================
        // COIN LIST
        // =================================================

        items(

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


        // =================================================
        // PAGINATION
        // =================================================

        if (
            search.isBlank()
        ) {

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
                            visibleCount > 20

                    ) {

                        Text(
                            "Previous"
                        )
                    }


                    Text(

                        "${minOf(
                            visibleCount,
                            coins.size
                        )} shown",

                        fontSize =
                            11.sp
                    )


                    Button(

                        onClick = {

                            visibleCount += 50
                        },

                        enabled =
                            visibleCount <
                                coins.size

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


// =====================================================
// GLOBAL MARKET VALUE
// =====================================================

@Composable
fun GlobalMarketValue(

    title: String,

    value: String,

    change: Double?

) {

    Column(
        Modifier.width(92.dp)
    ) {

        Text(

            title,

            fontSize =
                9.sp,

            color =
                Color.Gray
        )

        Text(

            value,

            fontSize =
                11.sp,

            fontWeight =
                FontWeight.Bold
        )

        if (change != null) {

            Text(

                formatPct(change),

                fontSize =
                    9.sp,

                color =
                    changeColor(change)
            )
        }
    }
}


// =====================================================
// CRYPTO ROW
// =====================================================

@Composable
fun CryptoRow(

    coin: CryptoCoin,

    flash: FlashState

) {

    // -----------------------------------------------
    // PRICE FLASH
    //
    // Up = GREEN
    // Down = RED
    // None = BLACK
    //
    // LaunchedEffect above removes it after 600ms.
    // -----------------------------------------------

    val priceColor =
        when (flash) {

            FlashState.Up ->
                UpGreen

            FlashState.Down ->
                DownRed

            FlashState.None ->
                NormalPriceColor
        }


    Row(

        Modifier
            .fillMaxWidth()
            .padding(
                vertical = 7.dp
            ),

        verticalAlignment =
            Alignment.CenterVertically
    ) {


        // -------------------------------------------
        // RANK
        // -------------------------------------------

        Text(

            coin.rank.toString(),

            fontSize =
                9.sp,

            color =
                Color.Gray,

            modifier =
                Modifier.width(26.dp)
        )


        // -------------------------------------------
        // LOGO
        // -------------------------------------------

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
                        .size(28.dp)
                        .clip(
                            CircleShape
                        ),

                contentScale =
                    ContentScale.Crop
            )

        } else {

            Box(

                Modifier
                    .size(28.dp)
                    .clip(
                        CircleShape
                    ),

                contentAlignment =
                    Alignment.Center

            ) {

                Text(

                    coin.symbol.take(1),

                    fontWeight =
                        FontWeight.Bold,

                    fontSize =
                        10.sp
                )
            }
        }


        Spacer(
            Modifier.width(8.dp)
        )


        // -------------------------------------------
        // NAME + SYMBOL + CHANGES
        // -------------------------------------------

        Column(
            Modifier.weight(1f)
        ) {

            Text(

                coin.name,

                fontWeight =
                    FontWeight.Bold,

                fontSize =
                    12.sp
            )

            Text(

                coin.symbol,

                fontSize =
                    9.sp,

                color =
                    Color.Gray
            )


            // ---------------------------------------
            // INDIVIDUAL CHANGE COLORS
            // ---------------------------------------

            Row {

                ChangeText(
                    "1h",
                    coin.change1h
                )

                Spacer(
                    Modifier.width(6.dp)
                )

                ChangeText(
                    "4h",
                    coin.change4h
                )

                Spacer(
                    Modifier.width(6.dp)
                )

                ChangeText(
                    "24h",
                    coin.change24h
                )

                Spacer(
                    Modifier.width(6.dp)
                )

                ChangeText(
                    "7d",
                    coin.change7d
                )
            }
        }


        // -------------------------------------------
        // PRICE
        // -------------------------------------------

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


            // 24H CHANGE

            Text(

                formatPct(
                    coin.change24h
                ),

                fontSize =
                    10.sp,

                color =
                    changeColor(
                        coin.change24h
                    )
            )
        }
    }


    HorizontalDivider(
        thickness =
            0.5.dp
    )
}


// =====================================================
// CHANGE TEXT
// =====================================================

@Composable
fun ChangeText(

    label: String,

    value: Double

) {

    Row {

        Text(

            "$label ",

            fontSize =
                9.sp,

            color =
                Color.Gray
        )

        Text(

            formatPct(value),

            fontSize =
                9.sp,

            color =
                changeColor(value),

            fontWeight =
                FontWeight.Medium
        )
    }
}


// =====================================================
// CHANGE COLOR
// =====================================================

fun changeColor(
    value: Double
): Color {

    return when {

        value > 0.0 ->
            UpGreen

        value < 0.0 ->
            DownRed

        else ->
            Color.Gray
    }
}


// =====================================================
// PLACEHOLDER
// =====================================================

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

            fontSize =
                20.sp,

            fontWeight =
                FontWeight.Bold
        )
    }
}


// =====================================================
// FORMAT PERCENT
// =====================================================

fun formatPct(
    value: Double
): String {

    return String.format(
        Locale.US,
        "%+.2f%%",
        value
    )
}


// =====================================================
// FORMAT PRICE
//
// >= $1:
//     65,700.06
//
// < $1:
//     0.0536788
//
// Trailing zeroes removed.
// =====================================================

fun formatPrice(
    value: Double
): String {

    if (value >= 1.0) {

        return String.format(
            Locale.US,
            "%,.2f",
            value
        )
    }

    return String.format(
        Locale.US,
        "%.8f",
        value
    )
        .trimEnd('0')
        .trimEnd('.')
}


// =====================================================
// FORMAT LARGE
// =====================================================

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
                value / 1_000
            )

        else ->

            String.format(
                Locale.US,
                "%.2f",
                value
            )
    }
}

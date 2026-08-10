package com.practice.cryptoapp

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewmodel.compose.viewModel
import coil.compose.AsyncImage
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
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
// MODEL
// ============================================================

data class CryptoCoin(
    val symbol: String,
    val price: Double,
    val change24h: Double,
    val volume24h: Double,
    val rank: Int
)

// ============================================================
// BINANCE WEBSOCKET MANAGER
//
// IMPORTANT:
//
// NO REST API
//
// NO MANUAL COIN LIST
//
// Binance !ticker@arr stream automatically sends
// ticker information for Binance markets.
// ============================================================

class BinanceWebSocketManager {

    private val client =
        OkHttpClient.Builder()
            .readTimeout(
                0,
                TimeUnit.MILLISECONDS
            )
            .pingInterval(
                20,
                TimeUnit.SECONDS
            )
            .retryOnConnectionFailure(true)
            .build()

    private var socket: WebSocket? = null

    private var stopped = false

    private var reconnectJob: Job? = null

    private val scope =
        CoroutineScope(
            SupervisorJob() +
                Dispatchers.IO
        )

    /*
     * All received coins are stored here.
     *
     * No coin names are manually entered.
     */

    private val tickerMap =
        mutableMapOf<String, CryptoCoin>()

    private val _coins =
        MutableStateFlow<List<CryptoCoin>>(
            emptyList()
        )

    val coins: StateFlow<List<CryptoCoin>> =
        _coins.asStateFlow()

    private val _status =
        MutableStateFlow(
            "CONNECTING"
        )

    val status: StateFlow<String> =
        _status.asStateFlow()

    // ========================================================
    // START
    // ========================================================

    fun start() {

        if (socket != null) {
            return
        }

        stopped = false

        connect()
    }

    // ========================================================
    // CONNECT
    // ========================================================

    private fun connect() {

        if (stopped) {
            return
        }

        /*
         * Binance ALL MARKET ticker WebSocket.
         *
         * No REST API.
         *
         * No manual symbol list.
         */

        val request =
            Request.Builder()
                .url(
                    "wss://stream.binance.com:9443/ws/!ticker@arr"
                )
                .build()

        _status.value =
            "CONNECTING"

        socket =
            client.newWebSocket(
                request,

                object :
                    WebSocketListener() {

                    // ========================================
                    // OPEN
                    // ========================================

                    override fun onOpen(
                        webSocket: WebSocket,
                        response: Response
                    ) {

                        _status.value =
                            "LIVE"
                    }

                    // ========================================
                    // MESSAGE
                    // ========================================

                    override fun onMessage(
                        webSocket: WebSocket,
                        text: String
                    ) {

                        parseMessage(
                            text
                        )
                    }

                    // ========================================
                    // FAILURE
                    // ========================================

                    override fun onFailure(
                        webSocket: WebSocket,
                        t: Throwable,
                        response: Response?
                    ) {

                        socket = null

                        _status.value =
                            "RECONNECTING"

                        scheduleReconnect()
                    }

                    // ========================================
                    // CLOSED
                    // ========================================

                    override fun onClosed(
                        webSocket: WebSocket,
                        code: Int,
                        reason: String
                    ) {

                        socket = null

                        if (!stopped) {

                            _status.value =
                                "RECONNECTING"

                            scheduleReconnect()
                        }
                    }
                }
            )
    }

    // ========================================================
    // PARSE BINANCE MESSAGE
    // ========================================================

    private fun parseMessage(
        text: String
    ) {

        try {

            /*
             * Binance !ticker@arr returns:
             *
             * [
             *   {
             *     "e":"24hrTicker",
             *     "s":"BTCUSDT",
             *     "c":"64990.46",
             *     "P":"0.031",
             *     "q":"..."
             *   },
             *   ...
             * ]
             */

            val array =
                JSONArray(
                    text
                )

            var changed =
                false

            for (
                i in 0 until array.length()
            ) {

                val obj =
                    array.optJSONObject(
                        i
                    )
                        ?: continue

                if (
                    updateCoin(
                        obj
                    )
                ) {

                    changed = true
                }
            }

            if (changed) {

                publishCoins()
            }

        } catch (_: Exception) {

            /*
             * Ignore invalid/unexpected
             * Binance messages.
             */
        }
    }

    // ========================================================
    // UPDATE COIN
    // ========================================================

    private fun updateCoin(
        obj: JSONObject
    ): Boolean {

        val fullSymbol =
            obj.optString(
                "s"
            )

        if (
            fullSymbol.isBlank()
        ) {

            return false
        }

        /*
         * We want USDT markets.
         *
         * BTCUSDT -> BTC
         * ETHUSDT -> ETH
         */

        if (
            !fullSymbol.endsWith(
                "USDT",
                ignoreCase = true
            )
        ) {

            return false
        }

        val symbol =
            fullSymbol
                .removeSuffix(
                    "USDT"
                )

        if (
            symbol.isBlank()
        ) {

            return false
        }

        val price =
            obj.optString(
                "c"
            )
                .toDoubleOrNull()
                ?: return false

        val change24h =
            obj.optString(
                "P"
            )
                .toDoubleOrNull()
                ?: 0.0

        val volume24h =
            obj.optString(
                "q"
            )
                .toDoubleOrNull()
                ?: 0.0

        synchronized(
            tickerMap
        ) {

            tickerMap[
                symbol
            ] =
                CryptoCoin(

                    symbol =
                        symbol,

                    price =
                        price,

                    change24h =
                        change24h,

                    volume24h =
                        volume24h,

                    rank =
                        0
                )
        }

        return true
    }

    // ========================================================
    // PUBLISH
    // ========================================================

    private fun publishCoins() {

        synchronized(
            tickerMap
        ) {

            /*
             * Rank coins by 24h quote volume.
             *
             * Highest volume = rank 1.
             */

            val sorted =
                tickerMap.values
                    .sortedByDescending {

                        it.volume24h
                    }

            _coins.value =
                sorted.mapIndexed {

                        index,
                        coin ->

                    coin.copy(
                        rank =
                            index + 1
                    )
                }
        }
    }

    // ========================================================
    // RECONNECT
    // ========================================================

    private fun scheduleReconnect() {

        if (stopped) {
            return
        }

        if (
            reconnectJob?.isActive == true
        ) {

            return
        }

        reconnectJob =
            scope.launch {

                delay(
                    2000
                )

                if (!stopped) {

                    connect()
                }
            }
    }

    // ========================================================
    // STOP
    // ========================================================

    fun stop() {

        stopped = true

        reconnectJob?.cancel()

        socket?.close(
            1000,
            "Application stopped"
        )

        socket = null

        scope.cancel()
    }
}

// ============================================================
// VIEWMODEL
//
// WebSocket yahan maintain hota hai.
//
// Home -> Trade -> Home karne se HomeScreen destroy/create
// hone ke bawajood ViewModel connection ko maintain karta hai.
// ============================================================

class CryptoViewModel :
    ViewModel() {

    private val manager =
        BinanceWebSocketManager()

    val coins =
        manager.coins

    val status =
        manager.status

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

            MaterialTheme {

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

    val drawerState =
        rememberDrawerState(
            DrawerValue.Closed
        )

    val scope =
        rememberCoroutineScope()

    /*
     * Bottom navigation:
     *
     * 0 Home
     * 1 Chart
     * 2 Trade
     * 3 Analysis
     * 4 News
     */

    var selectedTab
            by rememberSaveable {

                mutableIntStateOf(
                    0
                )
            }

    ModalNavigationDrawer(

        drawerState =
            drawerState,

        drawerContent = {

            ModalDrawerSheet {

                Spacer(
                    Modifier.height(
                        18.dp
                    )
                )

                Text(

                    "Crypto Exchange",

                    modifier =
                        Modifier.padding(
                            20.dp
                        ),

                    fontSize =
                        20.sp,

                    fontWeight =
                        FontWeight.Bold
                )

                HorizontalDivider()

                // ============================================
                // PROFILE
                // ============================================

                NavigationDrawerItem(

                    label = {
                        Text(
                            "Profile"
                        )
                    },

                    selected =
                        false,

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

                // ============================================
                // SETTINGS
                // ============================================

                NavigationDrawerItem(

                    label = {
                        Text(
                            "Settings"
                        )
                    },

                    selected =
                        false,

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

                // ============================================
                // CONNECTION
                // ============================================

                NavigationDrawerItem(

                    label = {
                        Text(
                            "Connection"
                        )
                    },

                    selected =
                        false,

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

                // ============================================
                // DEMO ACCOUNT
                // ============================================

                NavigationDrawerItem(

                    label = {
                        Text(
                            "Demo Account"
                        )
                    },

                    selected =
                        false,

                    onClick = {

                        scope.launch {
                            drawerState.close()
                        }
                    },

                    icon = {

                        Icon(
                            Icons.Default.AccountBalanceWallet,
                            null
                        )
                    }
                )
            }
        }

    ) {

        Scaffold(

            // =================================================
            // BOTTOM NAVIGATION
            // =================================================

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

                                    contentDescription =
                                        name
                                )
                            },

                            label = {

                                Text(
                                    name
                                )
                            }
                        )
                    }
                }
            }

        ) { padding ->

            Box(

                Modifier
                    .fillMaxSize()
                    .padding(
                        padding
                    )
            ) {

                when (
                    selectedTab
                ) {

                    0 -> {

                        HomeScreen(

                            coins =
                                coins,

                            status =
                                status,

                            onMenuClick = {

                                scope.launch {

                                    drawerState.open()
                                }
                            }
                        )
                    }

                    1 -> {

                        PlaceholderScreen(
                            "Chart"
                        )
                    }

                    2 -> {

                        PlaceholderScreen(
                            "Trade"
                        )
                    }

                    3 -> {

                        PlaceholderScreen(
                            "Analysis"
                        )
                    }

                    4 -> {

                        PlaceholderScreen(
                            "News"
                        )
                    }
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

    onMenuClick: () -> Unit

) {

    // ========================================================
    // DEMO BALANCE
    // ========================================================

    var balance
            by rememberSaveable {

                mutableDoubleStateOf(
                    10000.0
                )
            }

    // ========================================================
    // SEARCH
    // ========================================================

    var search
            by rememberSaveable {

                mutableStateOf(
                    ""
                )
            }

    // ========================================================
    // FILTER
    // ========================================================

    var filter
            by rememberSaveable {

                mutableStateOf(
                    "ALL"
                )
            }

    // ========================================================
    // PAGINATION
    // ========================================================

    var visibleCount
            by rememberSaveable {

                mutableIntStateOf(
                    20
                )
            }

    // ========================================================
    // FILTER + SORT
    // ========================================================

    val displayCoins =
        remember(

            coins,

            search,

            filter,

            visibleCount

        ) {

            var list =
                coins

            // ------------------------------------------------
            // SEARCH
            // ------------------------------------------------

            if (
                search.isNotBlank()
            ) {

                list =
                    list.filter {

                        it.symbol.contains(
                            search,
                            ignoreCase = true
                        )
                    }
            }

            // ------------------------------------------------
            // FILTER
            // ------------------------------------------------

            list =
                when (
                    filter
                ) {

                    "GAINER" -> {

                        list.sortedByDescending {

                            it.change24h
                        }
                    }

                    "LOSER" -> {

                        list.sortedBy {

                            it.change24h
                        }
                    }

                    else -> {

                        list.sortedBy {

                            it.rank
                        }
                    }
                }

            // ------------------------------------------------
            // PAGINATION
            // ------------------------------------------------

            if (
                search.isBlank()
            ) {

                list.take(
                    visibleCount
                )

            } else {

                list
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
                    12.dp
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
                            bottom = 10.dp
                        ),

                verticalAlignment =
                    Alignment.CenterVertically

            ) {

                // --------------------------------------------
                // MENU
                // --------------------------------------------

                Icon(

                    Icons.Default.Menu,

                    contentDescription =
                        "Menu",

                    modifier =
                        Modifier
                            .size(
                                32.dp
                            )
                            .clickable {

                                onMenuClick()
                            }
                )

                Spacer(
                    Modifier.width(
                        10.dp
                    )
                )

                // --------------------------------------------
                // APP TITLE
                // --------------------------------------------

                Column(

                    Modifier.weight(
                        1f
                    )
                ) {

                    Text(

                        "Crypto Exchange",

                        fontSize =
                            19.sp,

                        fontWeight =
                            FontWeight.Bold
                    )

                    Text(

                        "Live Binance WSS",

                        fontSize =
                            11.sp,

                        color =
                            Color.Gray
                    )
                }

                // --------------------------------------------
                // CONNECTION STATUS
                // --------------------------------------------

                Text(

                    "● $status",

                    fontSize =
                        11.sp,

                    color =
                        if (
                            status ==
                                "LIVE"
                        ) {

                            Color(
                                0xFF008000
                            )

                        } else {

                            Color(
                                0xFFFF9800
                            )
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
                        vertical = 5.dp
                    )

            ) {

                Column(

                    Modifier.padding(
                        14.dp
                    )
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
                                    12.sp,

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
                                    25.sp,

                                fontWeight =
                                    FontWeight.Bold
                            )
                        }

                        // ------------------------------------
                        // RESET
                        // ------------------------------------

                        Button(

                            onClick = {

                                balance =
                                    10000.0
                            },

                            /*
                             * Reset only works when balance
                             * is <= $100.
                             */

                            enabled =
                                balance <=
                                    100.0

                        ) {

                            Text(
                                "Reset"
                            )
                        }
                    }

                    Spacer(
                        Modifier.height(
                            12.dp
                        )
                    )

                    // ----------------------------------------
                    // PROFIT / LOSS PERIODS
                    // ----------------------------------------

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
                        .chunked(
                            4
                        )
                        .forEach { row ->

                            Row(
                                Modifier.fillMaxWidth()
                            ) {

                                row.forEach {

                                    Column(

                                        Modifier.weight(
                                            1f
                                        ),

                                        horizontalAlignment =
                                            Alignment.CenterHorizontally

                                    ) {

                                        Text(

                                            it,

                                            fontSize =
                                                11.sp,

                                            color =
                                                Color.Gray
                                        )

                                        Text(

                                            "+0.00%",

                                            fontSize =
                                                12.sp
                                        )
                                    }
                                }
                            }

                            Spacer(
                                Modifier.height(
                                    7.dp
                                )
                            )
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
                        vertical = 5.dp
                    )

            ) {

                Column(

                    Modifier.padding(
                        14.dp
                    )
                ) {

                    Text(

                        "Global Market",

                        fontWeight =
                            FontWeight.Bold
                    )

                    Spacer(
                        Modifier.height(
                            10.dp
                        )
                    )

                    Row(

                        Modifier.fillMaxWidth(),

                        horizontalArrangement =
                            Arrangement.SpaceBetween

                    ) {

                        MarketValue(
                            "Global MC",
                            "—"
                        )

                        MarketValue(
                            "BTC Dom.",
                            "—"
                        )

                        MarketValue(
                            "ETH Dom.",
                            "—"
                        )
                    }

                    Spacer(
                        Modifier.height(
                            10.dp
                        )
                    )

                    Row(

                        Modifier.fillMaxWidth(),

                        horizontalArrangement =
                            Arrangement.SpaceBetween

                    ) {

                        MarketValue(
                            "Alt Dom.",
                            "—"
                        )

                        MarketValue(
                            "24h Volume",
                            "—"
                        )

                        MarketValue(
                            "24h Change",
                            "—"
                        )
                    }
                }
            }
        }

        // ====================================================
        // SEARCH + FILTER
        // ====================================================

        item {

            OutlinedTextField(

                value =
                    search,

                onValueChange = {

                    search =
                        it

                    visibleCount =
                        20
                },

                modifier =
                    Modifier.fillMaxWidth(),

                singleLine =
                    true,

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
                Modifier.height(
                    8.dp
                )
            )

            Row(

                horizontalArrangement =
                    Arrangement.spacedBy(
                        8.dp
                    )
            ) {

                // --------------------------------------------
                // ALL
                // --------------------------------------------

                FilterChip(

                    selected =
                        filter ==
                            "ALL",

                    onClick = {

                        filter =
                            "ALL"

                        visibleCount =
                            20
                    },

                    label = {

                        Text(
                            "All"
                        )
                    }
                )

                // --------------------------------------------
                // GAINER
                // --------------------------------------------

                FilterChip(

                    selected =
                        filter ==
                            "GAINER",

                    onClick = {

                        filter =
                            "GAINER"

                        visibleCount =
                            20
                    },

                    label = {

                        Text(
                            "Gainer"
                        )
                    }
                )

                // --------------------------------------------
                // LOSER
                // --------------------------------------------

                FilterChip(

                    selected =
                        filter ==
                            "LOSER",

                    onClick = {

                        filter =
                            "LOSER"

                        visibleCount =
                            20
                    },

                    label = {

                        Text(
                            "Loser"
                        )
                    }
                )
            }

            Spacer(
                Modifier.height(
                    6.dp
                )
            )

            Text(

                "${coins.size} live USDT markets",

                fontSize =
                    11.sp,

                color =
                    Color.Gray
            )
        }

        // ====================================================
        // CRYPTO LIST
        // ====================================================

        items(

            displayCoins,

            key = {

                it.symbol
            }

        ) { coin ->

            CryptoRow(
                coin
            )
        }

        // ====================================================
        // NEXT / PREVIOUS
        // ====================================================

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

                    // ----------------------------------------
                    // PREVIOUS
                    // ----------------------------------------

                    OutlinedButton(

                        onClick = {

                            visibleCount =
                                maxOf(

                                    20,

                                    visibleCount -
                                        50
                                )
                        },

                        enabled =
                            visibleCount >
                                20

                    ) {

                        Text(
                            "Previous"
                        )
                    }

                    // ----------------------------------------
                    // COUNT
                    // ----------------------------------------

                    Text(

                        "${minOf(
                            visibleCount,
                            coins.size
                        )} shown",

                        fontSize =
                            12.sp
                    )

                    // ----------------------------------------
                    // NEXT
                    // ----------------------------------------

                    Button(

                        onClick = {

                            visibleCount +=
                                50
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

// ============================================================
// CRYPTO ROW
// ============================================================

@Composable
fun CryptoRow(
    coin: CryptoCoin
) {

    Row(

        Modifier
            .fillMaxWidth()
            .padding(
                vertical = 9.dp
            ),

        verticalAlignment =
            Alignment.CenterVertically

    ) {

        // ====================================================
        // RANK
        // ====================================================

        Text(

            "#${coin.rank}",

            fontSize =
                10.sp,

            color =
                Color.Gray,

            modifier =
                Modifier.width(
                    38.dp
                )
        )

        // ====================================================
        // TEMPORARY LOGO
        // ====================================================

        /*
         * Binance ticker mein CoinGecko logo URL nahi hota.
         *
         * Isliye abhi symbol ka first letter.
         *
         * CoinGecko logo mapping next step mein add karenge.
         */

        Box(

            Modifier
                .size(
                    32.dp
                )
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
                    FontWeight.Bold
            )
        }

        Spacer(
            Modifier.width(
                8.dp
            )
        )

        // ====================================================
        // NAME / SYMBOL
        // ====================================================

        Column(

            Modifier.weight(
                1f
            )
        ) {

            Text(

                coin.symbol,

                fontWeight =
                    FontWeight.Bold
            )

            Text(

                "${coin.symbol}/USDT",

                fontSize =
                    10.sp,

                color =
                    Color.Gray
            )
        }

        // ====================================================
        // PRICE
        // ====================================================

        Column(

            horizontalAlignment =
                Alignment.End
        ) {

            Text(

                formatPrice(
                    coin.price
                ),

                fontWeight =
                    FontWeight.Bold
            )

            // ----------------------------------------------
            // 24H CHANGE
            // ----------------------------------------------

            Text(

                String.format(

                    Locale.US,

                    "%+.2f%%",

                    coin.change24h
                ),

                fontSize =
                    11.sp,

                color =
                    if (
                        coin.change24h >=
                            0
                    ) {

                        Color(
                            0xFF008000
                        )

                    } else {

                        Color.Red
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

    Column(

        Modifier.width(
            100.dp
        )
    ) {

        Text(

            title,

            fontSize =
                10.sp,

            color =
                Color.Gray
        )

        Text(

            value,

            fontSize =
                12.sp,

            fontWeight =
                FontWeight.Bold
        )
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

            fontSize =
                20.sp,

            fontWeight =
                FontWeight.Bold
        )
    }
}

// ============================================================
// PRICE FORMAT
// ============================================================

fun formatPrice(
    value: Double
): String {

    return if (
        value >= 1.0
    ) {

        String.format(

            Locale.US,

            "%,.4f",

            value
        )

    } else {

        String.format(

            Locale.US,

            "%.8f",

            value
        )
            .trimEnd(
                '0'
            )
            .trimEnd(
                '.'
            )
    }
}

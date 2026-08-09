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
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewmodel.compose.viewModel
import coil.compose.AsyncImage
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
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
// DATA MODEL
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
// This object lives outside HomeScreen.
// Therefore Home -> Trade -> Home does NOT create a new
// WebSocket connection.
//
// Binance prices are received ONLY through WebSocket.
// ============================================================

class BinanceWebSocketManager {

    private val client = OkHttpClient.Builder()
        .readTimeout(0, TimeUnit.MILLISECONDS)
        .pingInterval(20, TimeUnit.SECONDS)
        .build()

    private var socket: WebSocket? = null

    private var stopped = false
    private var reconnectJobRunning = false

    private val tickerMap = mutableMapOf<String, CryptoCoin>()

    private val _coins = MutableStateFlow<List<CryptoCoin>>(emptyList())
    val coins: StateFlow<List<CryptoCoin>> = _coins.asStateFlow()

    private val _connectionStatus = MutableStateFlow("CONNECTING")
    val connectionStatus: StateFlow<String> =
        _connectionStatus.asStateFlow()

    fun start() {

        if (socket != null) return

        stopped = false

        connect()
    }

    private fun connect() {

        if (stopped) return

        val request = Request.Builder()
            .url(
                "wss://stream.binance.com:9443/ws/!ticker@arr"
            )
            .build()

        socket = client.newWebSocket(
            request,
            object : WebSocketListener() {

                override fun onOpen(
                    webSocket: WebSocket,
                    response: Response
                ) {
                    _connectionStatus.value = "LIVE"
                }

                override fun onMessage(
                    webSocket: WebSocket,
                    text: String
                ) {

                    try {

                        val array = JSONArray(text)

                        for (i in 0 until array.length()) {

                            val obj = array.getJSONObject(i)

                            updateCoin(obj)
                        }

                        publish()

                    } catch (_: Exception) {
                    }
                }

                override fun onFailure(
                    webSocket: WebSocket,
                    t: Throwable,
                    response: Response?
                ) {

                    socket = null

                    _connectionStatus.value =
                        "RECONNECTING"

                    reconnect()
                }

                override fun onClosed(
                    webSocket: WebSocket,
                    code: Int,
                    reason: String
                ) {

                    socket = null

                    if (!stopped) {
                        _connectionStatus.value =
                            "RECONNECTING"

                        reconnect()
                    }
                }
            }
        )
    }

    private fun updateCoin(obj: JSONObject) {

        val fullSymbol =
            obj.optString("s")

        // Only USDT markets
        if (!fullSymbol.endsWith("USDT")) {
            return
        }

        val symbol =
            fullSymbol.removeSuffix("USDT")

        val price =
            obj.optString("c")
                .toDoubleOrNull()
                ?: return

        val change24h =
            obj.optString("P")
                .toDoubleOrNull()
                ?: 0.0

        val volume =
            obj.optString("q")
                .toDoubleOrNull()
                ?: 0.0

        synchronized(tickerMap) {

            tickerMap[symbol] =
                CryptoCoin(
                    symbol = symbol,
                    price = price,
                    change24h = change24h,
                    volume24h = volume,
                    rank = 0
                )
        }
    }

    private fun publish() {

        synchronized(tickerMap) {

            val sorted =
                tickerMap.values
                    .sortedByDescending {
                        it.volume24h
                    }
                    .mapIndexed { index, coin ->

                        coin.copy(
                            rank = index + 1
                        )
                    }

            _coins.value = sorted
        }
    }

    private fun reconnect() {

        if (reconnectJobRunning) {
            return
        }

        reconnectJobRunning = true

        CoroutineScope(Dispatchers.IO).launch {

            delay(2000)

            reconnectJobRunning = false

            if (!stopped) {

                connect()
            }
        }
    }

    fun stop() {

        stopped = true

        socket?.close(
            1000,
            "Application stopped"
        )

        socket = null
    }
}

// ============================================================
// VIEW MODEL
//
// MainActivity ke lifetime mein WSS connection maintain hota
// rahega. HomeScreen change hone se connection nahi tootega.
// ============================================================

class CryptoViewModel : ViewModel() {

    private val websocket =
        BinanceWebSocketManager()

    val coins =
        websocket.coins

    val connectionStatus =
        websocket.connectionStatus

    init {

        websocket.start()
    }

    override fun onCleared() {

        websocket.stop()

        super.onCleared()
    }
}

// ============================================================
// MAIN ACTIVITY
// ============================================================

class MainActivity : ComponentActivity() {

    override fun onCreate(
        savedInstanceState: Bundle?
    ) {

        super.onCreate(
            savedInstanceState
        )

        setContent {

            MaterialTheme {

                MainApp()
            }
        }
    }
}

// ============================================================
// MAIN APP
// ============================================================

@Composable
fun MainApp(
    viewModel: CryptoViewModel =
        viewModel()
) {

    val coins by
    viewModel.coins.collectAsState()

    val connectionStatus by
    viewModel.connectionStatus.collectAsState()

    val drawerState =
        rememberDrawerState(
            DrawerValue.Closed
        )

    val scope =
        rememberCoroutineScope()

    var selectedTab
            by rememberSaveable {
                mutableIntStateOf(0)
            }

    ModalNavigationDrawer(

        drawerState = drawerState,

        drawerContent = {

            ModalDrawerSheet {

                Text(
                    text = "Crypto Exchange",
                    modifier = Modifier.padding(
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
                        Text("WebSocket Status")
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
                modifier =
                    Modifier.padding(padding)
            ) {

                when (selectedTab) {

                    0 -> {

                        HomeScreen(
                            coins =
                                coins,
                            connectionStatus =
                                connectionStatus,
                            onMenuClick = {

                                scope.launch {
                                    drawerState.open()
                                }
                            }
                        )
                    }

                    2 -> {

                        PlaceholderScreen(
                            "Trade Screen"
                        )
                    }

                    1 -> {

                        PlaceholderScreen(
                            "Chart Screen"
                        )
                    }

                    3 -> {

                        PlaceholderScreen(
                            "Analysis Screen"
                        )
                    }

                    4 -> {

                        PlaceholderScreen(
                            "News Screen"
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

    connectionStatus: String,

    onMenuClick: () -> Unit

) {

    var balance
            by rememberSaveable {
                mutableDoubleStateOf(
                    10000.0
                )
            }

    var search
            by rememberSaveable {
                mutableStateOf("")
            }

    var filter
            by rememberSaveable {
                mutableStateOf("ALL")
            }

    var visibleCount
            by rememberSaveable {
                mutableIntStateOf(20)
            }

    val filteredCoins =
        remember(
            coins,
            search,
            filter,
            visibleCount
        ) {

            var list = coins

            if (search.isNotBlank()) {

                list =
                    list.filter {

                        it.symbol.contains(
                            search,
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

            if (search.isBlank()) {

                list.take(
                    visibleCount
                )

            } else {

                list
            }
        }

    LazyColumn(

        modifier =
            Modifier
                .fillMaxSize()
                .padding(12.dp)

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
                            bottom = 12.dp
                        ),

                verticalAlignment =
                    Alignment.CenterVertically

            ) {

                Icon(

                    imageVector =
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
                    modifier =
                        Modifier.weight(1f)
                ) {

                    Text(
                        "Crypto Exchange",
                        fontSize = 19.sp,
                        fontWeight =
                            FontWeight.Bold
                    )

                    Text(
                        "Binance WebSocket",
                        fontSize = 11.sp,
                        color =
                            Color.Gray
                    )
                }

                Text(

                    text =
                        "● $connectionStatus",

                    fontSize = 11.sp,

                    color =
                        if (
                            connectionStatus ==
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

                modifier =
                    Modifier
                        .fillMaxWidth()
                        .padding(
                            vertical = 6.dp
                        )

            ) {

                Column(
                    modifier =
                        Modifier.padding(14.dp)
                ) {

                    Row(

                        modifier =
                            Modifier.fillMaxWidth(),

                        horizontalArrangement =
                            Arrangement.SpaceBetween,

                        verticalAlignment =
                            Alignment.CenterVertically

                    ) {

                        Column {

                            Text(
                                "Demo Balance",
                                fontSize = 12.sp,
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

                                fontSize = 25.sp,

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
                                balance <=
                                    100.0

                        ) {

                            Text("Reset")
                        }
                    }

                    Spacer(
                        Modifier.height(12.dp)
                    )

                    // 14 TIMEFRAMES

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

                                modifier =
                                    Modifier.fillMaxWidth(),

                                horizontalArrangement =
                                    Arrangement.SpaceBetween

                            ) {

                                row.forEach {

                                    Column(

                                        modifier =
                                            Modifier
                                                .weight(1f),

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
                                                12.sp,
                                            fontWeight =
                                                FontWeight.SemiBold
                                        )
                                    }
                                }
                            }

                            Spacer(
                                Modifier.height(8.dp)
                            )
                        }
                }
            }
        }

        // ====================================================
        // MARKET SUMMARY
        // ====================================================

        item {

            Card(

                modifier =
                    Modifier
                        .fillMaxWidth()
                        .padding(
                            vertical = 6.dp
                        )

            ) {

                Column(
                    modifier =
                        Modifier.padding(14.dp)
                ) {

                    Text(
                        "Global Market",
                        fontWeight =
                            FontWeight.Bold
                    )

                    Spacer(
                        Modifier.height(8.dp)
                    )

                    Row(
                        Modifier.fillMaxWidth(),
                        horizontalArrangement =
                            Arrangement.SpaceBetween
                    ) {

                        MarketItem(
                            "Global MC",
                            "—"
                        )

                        MarketItem(
                            "BTC Dom.",
                            "—"
                        )

                        MarketItem(
                            "ETH Dom.",
                            "—"
                        )
                    }

                    Spacer(
                        Modifier.height(8.dp)
                    )

                    Row(
                        Modifier.fillMaxWidth(),
                        horizontalArrangement =
                            Arrangement.SpaceBetween
                    ) {

                        MarketItem(
                            "Alt Dom.",
                            "—"
                        )

                        MarketItem(
                            "24h Volume",
                            "—"
                        )

                        MarketItem(
                            "24h Change",
                            "—"
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
                Modifier.height(6.dp)
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
                    },

                    label = {
                        Text("Loser")
                    }
                )
            }

            Spacer(
                Modifier.height(8.dp)
            )

            Text(
                "${coins.size} Binance USDT markets",
                fontSize = 11.sp,
                color = Color.Gray
            )
        }

        // ====================================================
        // COIN LIST
        // ====================================================

        items(

            items = filteredCoins,

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

        if (search.isBlank()) {

            item {

                Row(

                    modifier =
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
                                    visibleCount -
                                        50
                                )
                        },

                        enabled =
                            visibleCount > 20

                    ) {

                        Text("Previous")
                    }

                    Text(
                        "Showing ${minOf(
                            visibleCount,
                            coins.size
                        )}"
                    )

                    Button(

                        onClick = {

                            visibleCount += 50
                        },

                        enabled =
                            visibleCount <
                                coins.size

                    ) {

                        Text("Next 50")
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

        modifier =
            Modifier
                .fillMaxWidth()
                .padding(
                    vertical = 9.dp
                ),

        verticalAlignment =
            Alignment.CenterVertically

    ) {

        Text(

            "#${coin.rank}",

            fontSize = 10.sp,

            color =
                Color.Gray,

            modifier =
                Modifier.width(38.dp)
        )

        CoinLogo(
            coin.symbol
        )

        Spacer(
            Modifier.width(8.dp)
        )

        Column(
            modifier =
                Modifier.weight(1f)
        ) {

            Text(
                coin.symbol,
                fontWeight =
                    FontWeight.Bold
            )

            Text(
                "${coin.symbol}/USDT",
                fontSize = 10.sp,
                color =
                    Color.Gray
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
                    FontWeight.Bold
            )

            Text(

                formatPercent(
                    coin.change24h
                ),

                fontSize = 11.sp,

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
        thickness = 0.5.dp
    )
}

// ============================================================
// COINGECKO LOGOS
//
// These are ONLY used for logos.
// Binance remains the source for live prices.
// ============================================================

@Composable
fun CoinLogo(
    symbol: String
) {

    val url =
        coingeckoLogo(symbol)

    if (url != null) {

        AsyncImage(

            model = url,

            contentDescription =
                symbol,

            modifier =
                Modifier
                    .size(32.dp)
                    .clip(
                        CircleShape
                    ),

            contentScale =
                ContentScale.Crop
        )

    } else {

        Box(

            modifier =
                Modifier
                    .size(32.dp)
                    .clip(
                        CircleShape
                    ),

            contentAlignment =
                Alignment.Center

        ) {

            Text(
                symbol.take(1),
                fontWeight =
                    FontWeight.Bold
            )
        }
    }
}

fun coingeckoLogo(
    symbol: String
): String? {

    return when (
        symbol.uppercase()
    ) {

        "BTC" ->
            "https://assets.coingecko.com/coins/images/1/large/bitcoin.png"

        "ETH" ->
            "https://assets.coingecko.com/coins/images/279/large/ethereum.png"

        "BNB" ->
            "https://assets.coingecko.com/coins/images/825/large/bnb-icon2_2x.png"

        "XRP" ->
            "https://assets.coingecko.com/coins/images/44/large/xrp-symbol-white-128.png"

        "SOL" ->
            "https://assets.coingecko.com/coins/images/4128/large/solana.png"

        "DOGE" ->
            "https://assets.coingecko.com/coins/images/5/large/dogecoin.png"

        "ADA" ->
            "https://assets.coingecko.com/coins/images/975/large/cardano.png"

        "AVAX" ->
            "https://assets.coingecko.com/coins/images/12559/large/Avalanche_Circle_RedWhite_Trans.png"

        "DOT" ->
            "https://assets.coingecko.com/coins/images/12171/large/polkadot.png"

        "TRX" ->
            "https://assets.coingecko.com/coins/images/1094/large/tron-logo.png"

        "LINK" ->
            "https://assets.coingecko.com/coins/images/877/large/chainlink-new-logo.png"

        "LTC" ->
            "https://assets.coingecko.com/coins/images/2/large/litecoin.png"

        "ATOM" ->
            "https://assets.coingecko.com/coins/images/1481/large/cosmos_hub.png"

        "UNI" ->
            "https://assets.coingecko.com/coins/images/12504/large/uni.jpg"

        "ETC" ->
            "https://assets.coingecko.com/coins/images/453/large/ethereum-classic-logo.png"

        "FIL" ->
            "https://assets.coingecko.com/coins/images/12817/large/filecoin.png"

        "NEAR" ->
            "https://assets.coingecko.com/coins/images/10365/large/near.jpg"

        "APT" ->
            "https://assets.coingecko.com/coins/images/26455/large/aptos_round.png"

        "OP" ->
            "https://assets.coingecko.com/coins/images/25244/large/Optimism.png"

        else -> null
    }
}

// ============================================================
// MARKET ITEM
// ============================================================

@Composable
fun MarketItem(
    title: String,
    value: String
) {

    Column(
        modifier =
            Modifier.width(105.dp)
    ) {

        Text(
            title,
            fontSize = 10.sp,
            color =
                Color.Gray
        )

        Text(
            value,
            fontSize = 12.sp,
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

        modifier =
            Modifier.fillMaxSize(),

        contentAlignment =
            Alignment.Center

    ) {

        Text(
            title,
            fontSize = 20.sp,
            fontWeight =
                FontWeight.Bold
        )
    }
}

// ============================================================
// FORMATTING
// ============================================================

fun formatPercent(
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

    return if (value >= 1) {

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
            .trimEnd('0')
            .trimEnd('.')
    }
}

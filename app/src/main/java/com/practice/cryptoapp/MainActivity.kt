package com.practice.cryptoapp

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.Dispatchers
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

data class CryptoCoin(
    val symbol: String,
    val price: Double,
    val change24h: Double,
    val volume: Double,
    val rank: Int
)

class BinanceWebSocketManager {

    private val client = OkHttpClient.Builder()
        .readTimeout(0, TimeUnit.MILLISECONDS)
        .pingInterval(20, TimeUnit.SECONDS)
        .build()

    private var webSocket: WebSocket? = null

    private var stopped = false

    private val coins = mutableMapOf<String, CryptoCoin>()

    private val _coins =
        MutableStateFlow<List<CryptoCoin>>(emptyList())

    val coinsFlow: StateFlow<List<CryptoCoin>> =
        _coins.asStateFlow()

    private val _connection =
        MutableStateFlow("Connecting...")

    val connectionFlow: StateFlow<String> =
        _connection.asStateFlow()

    private val _error =
        MutableStateFlow<String?>(null)

    val errorFlow: StateFlow<String?> =
        _error.asStateFlow()

    fun start() {

        stopped = false

        connect()
    }

    private fun connect() {

        if (stopped) {
            return
        }

        _connection.value = "Connecting..."

        val request = Request.Builder()
            .url(
                "wss://stream.binance.com:9443/ws/btcusdt@ticker"
            )
            .build()

        webSocket =
            client.newWebSocket(

                request,

                object : WebSocketListener() {

                    override fun onOpen(
                        webSocket: WebSocket,
                        response: Response
                    ) {

                        _connection.value = "LIVE"

                        _error.value = null

                        /*
                         * Pehle BTCUSDT se connection verify
                         * ho raha hai.
                         *
                         * Baad mein isi connection par
                         * Binance SUBSCRIBE method se
                         * multiple coins subscribe karenge.
                         */

                        subscribeMoreSymbols(
                            webSocket
                        )
                    }

                    override fun onMessage(
                        webSocket: WebSocket,
                        text: String
                    ) {

                        try {

                            val json =
                                JSONObject(text)

                            val symbol =
                                json.optString("s")

                            if (symbol.isBlank()) {
                                return
                            }

                            val price =
                                json.optString("c")
                                    .toDoubleOrNull()
                                    ?: return

                            val change =
                                json.optString("P")
                                    .toDoubleOrNull()
                                    ?: 0.0

                            val volume =
                                json.optString("q")
                                    .toDoubleOrNull()
                                    ?: 0.0

                            synchronized(coins) {

                                coins[symbol] =
                                    CryptoCoin(

                                        symbol =
                                            symbol.removeSuffix(
                                                "USDT"
                                            ),

                                        price =
                                            price,

                                        change24h =
                                            change,

                                        volume =
                                            volume,

                                        rank = 0
                                    )

                                updateRanks()
                            }

                        } catch (e: Exception) {

                            _error.value =
                                "Ticker parsing error: ${e.message}"
                        }
                    }

                    override fun onClosing(
                        webSocket: WebSocket,
                        code: Int,
                        reason: String
                    ) {

                        _connection.value =
                            "Reconnecting..."

                        webSocket.close(
                            code,
                            reason
                        )
                    }

                    override fun onClosed(
                        webSocket: WebSocket,
                        code: Int,
                        reason: String
                    ) {

                        if (!stopped) {

                            reconnect()
                        }
                    }

                    override fun onFailure(
                        webSocket: WebSocket,
                        t: Throwable,
                        response: Response?
                    ) {

                        _connection.value =
                            "Reconnecting..."

                        _error.value =
                            t.message
                                ?: "WebSocket connection failed"

                        reconnect()
                    }
                }
            )
    }

    private fun subscribeMoreSymbols(
        webSocket: WebSocket
    ) {

        /*
         * TEST SET
         *
         * Connection establish hone ke baad
         * multiple Binance markets subscribe
         * kiye ja rahe hain.
         *
         * REST API ki zaroorat nahi.
         */

        val symbols = listOf(
            "btcusdt",
            "ethusdt",
            "bnbusdt",
            "solusdt",
            "xrpusdt",
            "dogeusdt",
            "adausdt",
            "avaxusdt",
            "dotusdt",
            "trxusdt",
            "linkusdt",
            "maticusdt",
            "ltcusdt",
            "atomusdt",
            "uniusdt",
            "etcusdt",
            "filusdt",
            "nearusdt",
            "aptusdt",
            "opusdt"
        )

        val params =
            JSONArray()

        symbols.forEach {

            params.put(
                "${it}@ticker"
            )
        }

        val request =
            JSONObject()

        request.put(
            "method",
            "SUBSCRIBE"
        )

        request.put(
            "params",
            params
        )

        request.put(
            "id",
            System.currentTimeMillis()
        )

        webSocket.send(
            request.toString()
        )
    }

    private fun updateRanks() {

        val sorted =
            coins.values
                .sortedByDescending {
                    it.volume
                }

        val ranked =
            sorted.mapIndexed {
                    index,
                    coin ->

                coin.copy(
                    rank = index + 1
                )
            }

        _coins.value =
            ranked
    }

    private fun reconnect() {

        if (stopped) {
            return
        }

        Thread {

            try {

                Thread.sleep(2000)

            } catch (_: Exception) {
            }

            if (!stopped) {

                connect()
            }

        }.start()
    }

    fun stop() {

        stopped = true

        webSocket?.close(
            1000,
            "Stopped"
        )

        webSocket = null

        client.dispatcher
            .executorService
            .shutdown()
    }
}

class CryptoViewModel : ViewModel() {

    private val manager =
        BinanceWebSocketManager()

    val coins =
        manager.coinsFlow

    val connection =
        manager.connectionFlow

    val error =
        manager.errorFlow

    init {

        manager.start()
    }

    override fun onCleared() {

        manager.stop()

        super.onCleared()
    }
}

class MainActivity : ComponentActivity() {

    override fun onCreate(
        savedInstanceState: Bundle?
    ) {

        super.onCreate(
            savedInstanceState
        )

        setContent {

            MaterialTheme {

                MainAppScreen()
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MainAppScreen() {

    val drawerState =
        rememberDrawerState(
            DrawerValue.Closed
        )

    val scope =
        rememberCoroutineScope()

    var selectedTab by remember {
        mutableIntStateOf(0)
    }

    ModalNavigationDrawer(

        drawerState =
            drawerState,

        drawerContent = {

            ModalDrawerSheet {

                Spacer(
                    Modifier.height(16.dp)
                )

                Text(

                    text =
                        "Crypto App Menu",

                    modifier =
                        Modifier.padding(16.dp),

                    fontSize =
                        20.sp,

                    fontWeight =
                        FontWeight.Bold
                )

                HorizontalDivider()

                NavigationDrawerItem(

                    label = {
                        Text(
                            "Profile & Settings"
                        )
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
                        Text(
                            "API Configuration"
                        )
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
                        Text(
                            "Realtime Connection"
                        )
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
                Modifier.padding(padding)
            ) {

                when (selectedTab) {

                    0 -> {

                        HomeScreen(

                            onMenuClick = {

                                scope.launch {

                                    drawerState.open()
                                }
                            }
                        )
                    }

                    2 -> {

                        com.practice.cryptoapp.ui
                            .TradeScreen()
                    }

                    else -> {

                        PlaceholderScreen(
                            "Screen Under Construction"
                        )
                    }
                }
            }
        }
    }
}

@Composable
fun PlaceholderScreen(
    title: String
) {

    Box(

        Modifier.fillMaxSize(),

        contentAlignment =
            Alignment.Center
    ) {

        Text(title)
    }
}

@Composable
fun HomeScreen(
    onMenuClick: () -> Unit
) {

    val viewModel =
        remember {
            CryptoViewModel()
        }

    val coins by
        viewModel.coins.collectAsState()

    val connection by
        viewModel.connection.collectAsState()

    val error by
        viewModel.error.collectAsState()

    var search by remember {
        mutableStateOf("")
    }

    var filter by remember {
        mutableStateOf("ALL")
    }

    var visibleCount by remember {
        mutableIntStateOf(20)
    }

    var balance by remember {
        mutableDoubleStateOf(10000.0)
    }

    val filtered =
        remember(
            coins,
            search,
            filter,
            visibleCount
        ) {

            var list =
                coins.filter {

                    it.symbol.contains(
                        search,
                        ignoreCase = true
                    )
                }

            list =
                when (filter) {

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

        item {

            Row(

                Modifier
                    .fillMaxWidth()
                    .padding(
                        bottom = 12.dp
                    ),

                verticalAlignment =
                    Alignment.CenterVertically
            ) {

                Icon(

                    Icons.Default.Menu,

                    "Menu",

                    Modifier
                        .size(32.dp)
                        .clickable {
                            onMenuClick()
                        }
                )

                Spacer(
                    Modifier.width(8.dp)
                )

                Column(
                    Modifier.weight(1f)
                ) {

                    Text(

                        "Crypto Exchange Demo",

                        fontSize =
                            18.sp,

                        fontWeight =
                            FontWeight.Bold
                    )

                    Text(

                        "Binance WebSocket ONLY",

                        fontSize =
                            11.sp,

                        color =
                            Color.Gray
                    )
                }

                Text(

                    "● $connection",

                    fontSize =
                        11.sp,

                    color =
                        if (
                            connection ==
                            "LIVE"
                        ) {

                            Color(0xFF008000)

                        } else {

                            Color(0xFFFF8C00)
                        }
                )
            }
        }

        item {

            Card(

                Modifier
                    .fillMaxWidth()
                    .padding(
                        vertical = 6.dp
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
                                fontSize =
                                    12.sp,
                                color =
                                    Color.Gray
                            )

                            Text(

                                "$${
                                    String.format(
                                        Locale.US,
                                        "%.2f",
                                        balance
                                    )
                                }",

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
                                balance <=
                                    100.0
                        ) {

                            Text("Reset")
                        }
                    }
                }
            }
        }

        item {

            Card(

                Modifier
                    .fillMaxWidth()
                    .padding(
                        vertical = 6.dp
                    )
            ) {

                Column(
                    Modifier.padding(12.dp)
                ) {

                    Text(

                        "Binance Realtime",

                        fontWeight =
                            FontWeight.Bold
                    )

                    Spacer(
                        Modifier.height(6.dp)
                    )

                    Text(

                        "${coins.size} markets",

                        fontSize =
                            12.sp
                    )

                    error?.let {

                        Text(

                            it,

                            fontSize =
                                11.sp,

                            color =
                                Color.Red
                        )
                    }
                }
            }
        }

        item {

            OutlinedTextField(

                value =
                    search,

                onValueChange = {
                    search = it
                },

                modifier =
                    Modifier.fillMaxWidth(),

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
                },

                singleLine = true
            )

            Spacer(
                Modifier.height(6.dp)
            )

            Row(

                horizontalArrangement =
                    Arrangement.spacedBy(8.dp)
            ) {

                FilterChip(

                    selected =
                        filter == "ALL",

                    onClick = {
                        filter =
                            "ALL"
                    },

                    label = {
                        Text("All")
                    }
                )

                FilterChip(

                    selected =
                        filter == "GAINER",

                    onClick = {
                        filter =
                            "GAINER"
                    },

                    label = {
                        Text("Gainer")
                    }
                )

                FilterChip(

                    selected =
                        filter == "LOSER",

                    onClick = {
                        filter =
                            "LOSER"
                    },

                    label = {
                        Text("Loser")
                    }
                )
            }
        }

        if (coins.isEmpty()) {

            item {

                Box(

                    Modifier
                        .fillMaxWidth()
                        .height(180.dp),

                    contentAlignment =
                        Alignment.Center
                ) {

                    Column(

                        horizontalAlignment =
                            Alignment.CenterHorizontally
                    ) {

                        CircularProgressIndicator()

                        Spacer(
                            Modifier.height(10.dp)
                        )

                        Text(
                            "Connecting to Binance...",
                            fontSize =
                                12.sp
                        )
                    }
                }
            }

        } else {

            itemsIndexed(

                filtered,

                key = {
                        _,
                        coin ->

                    coin.symbol
                }

            ) { _, coin ->

                CryptoRow(
                    coin
                )

                HorizontalDivider(
                    thickness =
                        0.5.dp
                )
            }

            if (search.isBlank()) {

                item {

                    Row(

                        Modifier
                            .fillMaxWidth()
                            .padding(
                                vertical = 12.dp
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

                            Text(
                                "Previous 50"
                            )
                        }

                        Text(
                            "Showing $visibleCount"
                        )

                        Button(

                            onClick = {

                                visibleCount +=
                                    50
                            }
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
}

@Composable
fun CryptoRow(
    coin: CryptoCoin
) {

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

            "#${coin.rank}",

            fontSize =
                11.sp,

            color =
                Color.Gray,

            modifier =
                Modifier.width(38.dp)
        )

        Box(

            Modifier
                .size(30.dp)
                .clip(CircleShape),

            contentAlignment =
                Alignment.Center
        ) {

            Text(

                coin.symbol.take(1),

                fontWeight =
                    FontWeight.Bold
            )
        }

        Spacer(
            Modifier.width(8.dp)
        )

        Column(
            Modifier.weight(1f)
        ) {

            Text(

                "${coin.symbol}/USDT",

                fontWeight =
                    FontWeight.Bold
            )

            Text(

                "24h volume",

                fontSize =
                    10.sp,

                color =
                    Color.Gray
            )
        }

        Column(
            horizontalAlignment =
                Alignment.End
        ) {

            Text(

                "${
                    String.format(
                        Locale.US,
                        "%.8f",
                        coin.price
                    )
                        .trimEnd('0')
                        .trimEnd('.')
                }",

                fontWeight =
                    FontWeight.Bold
            )

            Text(

                "${
                    if (
                        coin.change24h >= 0
                    ) "+"
                    else ""
                }${
                    String.format(
                        Locale.US,
                        "%.2f",
                        coin.change24h
                    )
                }%",

                color =
                    if (
                        coin.change24h >= 0
                    ) {

                        Color(0xFF008000)

                    } else {

                        Color.Red
                    },

                fontSize =
                    11.sp
            )
        }
    }
}

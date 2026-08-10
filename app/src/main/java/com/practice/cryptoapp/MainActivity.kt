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
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import okhttp3.*
import org.json.JSONArray
import org.json.JSONObject
import java.util.Locale
import java.util.concurrent.TimeUnit

// ============================================================
// MODEL
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

// ============================================================
// BINANCE WSS
//
// NO BINANCE REST API
// NO MANUAL COIN LIST
//
// Binance !ticker@arr automatically sends many markets.
// ============================================================

class BinanceWebSocketManager {

    private val client =
        OkHttpClient.Builder()
            .readTimeout(0, TimeUnit.MILLISECONDS)
            .pingInterval(20, TimeUnit.SECONDS)
            .build()

    private var webSocket: WebSocket? = null

    private var stopped = false
    private var reconnectJob: Job? = null

    private val tickerMap =
        mutableMapOf<String, CryptoCoin>()

    private val _coins =
        MutableStateFlow<List<CryptoCoin>>(emptyList())

    val coins: StateFlow<List<CryptoCoin>> =
        _coins.asStateFlow()

    private val _status =
        MutableStateFlow("CONNECTING")

    val status: StateFlow<String> =
        _status.asStateFlow()

    // --------------------------------------------------------
    // START
    // --------------------------------------------------------

    fun start() {

        if (webSocket != null) return

        stopped = false

        connect()
    }

    // --------------------------------------------------------
    // CONNECT
    // --------------------------------------------------------

    private fun connect() {

        if (stopped) return

        val request =
            Request.Builder()
                .url(
                    "wss://stream.binance.com:9443/ws/!ticker@arr"
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

                        _status.value = "LIVE"
                    }

                    override fun onMessage(
                        webSocket: WebSocket,
                        text: String
                    ) {

                        parseMessage(text)
                    }

                    override fun onFailure(
                        webSocket: WebSocket,
                        t: Throwable,
                        response: Response?
                    ) {

                        this@BinanceWebSocketManager.webSocket =
                            null

                        _status.value =
                            "RECONNECTING"

                        scheduleReconnect()
                    }

                    override fun onClosed(
                        webSocket: WebSocket,
                        code: Int,
                        reason: String
                    ) {

                        this@BinanceWebSocketManager.webSocket =
                            null

                        if (!stopped) {

                            _status.value =
                                "RECONNECTING"

                            scheduleReconnect()
                        }
                    }
                }
            )
    }

    // --------------------------------------------------------
    // PARSE BINANCE !ticker@arr
    // --------------------------------------------------------

    private fun parseMessage(
        text: String
    ) {

        try {

            val array =
                JSONArray(text)

            var changed =
                false

            for (i in 0 until array.length()) {

                val obj =
                    array.optJSONObject(i)
                        ?: continue

                val symbol =
                    obj.optString("s")

                // Only USDT spot markets
                if (
                    !symbol.endsWith(
                        "USDT",
                        ignoreCase = true
                    )
                ) {
                    continue
                }

                // Remove leveraged token markets
                if (
                    symbol.contains("UPUSDT") ||
                    symbol.contains("DOWNUSDT") ||
                    symbol.contains("BULLUSDT") ||
                    symbol.contains("BEARUSDT")
                ) {
                    continue
                }

                val price =
                    obj.optString("c")
                        .toDoubleOrNull()
                        ?: continue

                val change =
                    obj.optString("P")
                        .toDoubleOrNull()
                        ?: 0.0

                val volume =
                    obj.optString("q")
                        .toDoubleOrNull()
                        ?: 0.0

                val old =
                    tickerMap[symbol]

                tickerMap[symbol] =
                    CryptoCoin(
                        symbol =
                            symbol.removeSuffix(
                                "USDT"
                            ),

                        name =
                            old?.name
                                ?: symbol.removeSuffix(
                                    "USDT"
                                ),

                        logo =
                            old?.logo
                                ?: "",

                        price =
                            price,

                        change24h =
                            change,

                        volume24h =
                            volume,

                        marketCap =
                            old?.marketCap
                                ?: 0.0,

                        rank =
                            old?.rank
                                ?: 0
                    )

                changed = true
            }

            if (changed) {

                publish()
            }

        } catch (e: Exception) {

            // Ignore malformed/unexpected message
        }
    }

    // --------------------------------------------------------
    // PUBLISH
    // --------------------------------------------------------

    private fun publish() {

        synchronized(tickerMap) {

            val list =
                tickerMap.values
                    .sortedByDescending {
                        it.volume24h
                    }
                    .mapIndexed {
                        index,
                        coin ->

                        coin.copy(
                            rank =
                                index + 1
                        )
                    }

            _coins.value =
                list
        }
    }

    // --------------------------------------------------------
    // RECONNECT
    // --------------------------------------------------------

    private fun scheduleReconnect() {

        if (
            stopped ||
            reconnectJob?.isActive == true
        ) {
            return
        }

        reconnectJob =
            CoroutineScope(
                Dispatchers.IO
            ).launch {

                delay(2000)

                if (!stopped) {

                    connect()
                }
            }
    }

    // --------------------------------------------------------
    // STOP
    // --------------------------------------------------------

    fun stop() {

        stopped = true

        reconnectJob?.cancel()

        reconnectJob = null

        webSocket?.close(
            1000,
            "Application stopped"
        )

        webSocket = null
    }
}

// ============================================================
// VIEWMODEL
//
// ViewModel ke andar WSS rahega.
// Home -> Trade -> Home par WSS dobara create nahi hoga.
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
// APP
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

    var selectedTab
            by rememberSaveable {

                mutableIntStateOf(0)
            }

    ModalNavigationDrawer(

        drawerState =
            drawerState,

        drawerContent = {

            ModalDrawerSheet {

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
                Modifier.padding(padding)
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

                list =
                    list.filter {

                        it.symbol.contains(
                            search,
                            ignoreCase = true
                        ) ||

                        it.name.contains(
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

            if (
                search.isBlank()
            ) {

                list.take(
                    visibleCount
                )

            } else {

                list.take(
                    1000
                )
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
                            19.sp,
                        fontWeight =
                            FontWeight.Bold
                    )

                    Text(
                        "Binance Live WebSocket",
                        fontSize =
                            11.sp,
                        color =
                            Color.Gray
                    )
                }

                Text(

                    "● $status",

                    fontSize =
                        11.sp,

                    color =
                        if (
                            status == "LIVE"
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

                        Button(

                            onClick = {

                                balance =
                                    10000.0
                            },

                            enabled =
                                balance <= 100.0

                        ) {

                            Text("Reset")
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
                                Modifier.height(7.dp)
                            )
                        }
                }
            }
        }

        // ====================================================
        // MARKET
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
                    Modifier.padding(14.dp)
                ) {

                    Text(
                        "Global Market",
                        fontWeight =
                            FontWeight.Bold
                    )

                    Spacer(
                        Modifier.height(10.dp)
                    )

                    Row(
                        Modifier.fillMaxWidth(),
                        horizontalArrangement =
                            Arrangement.SpaceBetween
                    ) {

                        MarketValue(
                            "Live Markets",
                            coins.size.toString()
                        )

                        MarketValue(
                            "BTC",
                            findCoin(
                                coins,
                                "BTC"
                            )
                                ?.let {
                                    "$" +
                                        formatPrice(
                                            it.price
                                        )
                                }
                                ?: "—"
                        )

                        MarketValue(
                            "24h Volume",
                            formatLarge(
                                coins.sumOf {
                                    it.volume24h
                                }
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
                Modifier.height(8.dp)
            )

            OutlinedTextField(

                value =
                    search,

                onValueChange = {

                    search = it

                    visibleCount =
                        20
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

                        filter =
                            "ALL"

                        visibleCount =
                            20
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

                        visibleCount =
                            20
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

                        visibleCount =
                            20
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
                    11.sp,

                color =
                    Color.Gray
            )
        }

        // ====================================================
        // COINS
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
        // PAGINATION
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

                        Text("Previous")
                    }

                    Text(

                        "${minOf(
                            visibleCount,
                            coins.size
                        )} shown",

                        fontSize =
                            12.sp
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

            fontSize =
                10.sp,

            color =
                Color.Gray,

            modifier =
                Modifier.width(38.dp)
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
                        .size(32.dp)
                        .clip(
                            CircleShape
                        ),

                contentScale =
                    ContentScale.Crop
            )

        } else {

            Box(

                Modifier
                    .size(32.dp)
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
                        FontWeight.Bold
                )
            }
        }

        Spacer(
            Modifier.width(8.dp)
        )

        Column(
            Modifier.weight(1f)
        ) {

            Text(
                coin.name,
                fontWeight =
                    FontWeight.Bold
            )

            Text(
                coin.symbol,
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

                formatPrice(
                    coin.price
                ),

                fontWeight =
                    FontWeight.Bold
            )

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
                        coin.change24h >= 0
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
        Modifier.width(100.dp)
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
// HELPERS
// ============================================================

fun findCoin(
    coins: List<CryptoCoin>,
    symbol: String
): CryptoCoin? {

    return coins.firstOrNull {
        it.symbol.equals(
            symbol,
            ignoreCase = true
        )
    }
}

fun formatPrice(
    value: Double
): String {

    if (value >= 1.0) {

        return String.format(
            Locale.US,
            "%,.4f",
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

fun formatLarge(
    value: Double
): String {

    return when {

        value >= 1_000_000_000_000 ->
            String.format(
                Locale.US,
                "%.2fT",
                value / 1_000_000_000_000
            )

        value >= 1_000_000_000 ->
            String.format(
                Locale.US,
                "%.2fB",
                value / 1_000_000_000
            )

        value >= 1_000_000 ->
            String.format(
                Locale.US,
                "%.2fM",
                value / 1_000_000
            )

        value >= 1_000 ->
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

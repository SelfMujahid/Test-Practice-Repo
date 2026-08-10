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

data class CryptoCoin(
    val symbol: String,
    val price: Double,
    val change24h: Double,
    val volume24h: Double,
    val rank: Int
)

private data class MarketSeed(
    val symbol: String,
    val lastPrice: Double,
    val change24h: Double,
    val volume24h: Double
)

private data class StreamBatch(
    var symbols: List<String>,
    var socket: WebSocket? = null,
    var reconnectJob: Job? = null
)

class BinanceWebSocketManager {

    private val httpClient = OkHttpClient.Builder()
        .callTimeout(15, TimeUnit.SECONDS)
        .build()

    private val wsClient = OkHttpClient.Builder()
        .readTimeout(0, TimeUnit.MILLISECONDS)
        .pingInterval(20, TimeUnit.SECONDS)
        .retryOnConnectionFailure(true)
        .build()

    private val scope = CoroutineScope(Job() + Dispatchers.IO)

    private val tickerMap = mutableMapOf<String, CryptoCoin>()
    private val batches = mutableMapOf<Int, StreamBatch>()

    private var stopped = false
    private var bootstrapJob: Job? = null
    private var publishJob: Job? = null

    private val _coins = MutableStateFlow<List<CryptoCoin>>(emptyList())
    val coins: StateFlow<List<CryptoCoin>> = _coins.asStateFlow()

    private val _status = MutableStateFlow("LOADING")
    val status: StateFlow<String> = _status.asStateFlow()

    fun start() {
        if (bootstrapJob?.isActive == true) return
        stopped = false
        bootstrapJob = scope.launch { bootstrap() }
    }

    private fun bootstrap() {
        _status.value = "FETCHING"

        val seeds = loadBinanceSeeds()
        if (seeds.isEmpty()) {
            _status.value = "ERROR"
            return
        }

        synchronized(tickerMap) {
            tickerMap.clear()
            seeds.forEach { seed ->
                val sym = seed.symbol.removeSuffix("USDT")
                tickerMap[sym] = CryptoCoin(
                    symbol = sym,
                    price = seed.lastPrice,
                    change24h = seed.change24h,
                    volume24h = seed.volume24h,
                    rank = 0
                )
            }
        }

        publishNow()
        _status.value = "CONNECTING"

        connectBatches(
            seeds.map { it.symbol }
        )
    }

    private fun loadBinanceSeeds(): List<MarketSeed> {
        val req = Request.Builder()
            .url("https://api.binance.com/api/v3/ticker/24hr")
            .build()

        return try {
            httpClient.newCall(req).execute().use { res ->
                if (!res.isSuccessful) return emptyList()

                val body = res.body?.string().orEmpty()
                if (body.isBlank()) return emptyList()

                val arr = JSONArray(body)
                val out = ArrayList<MarketSeed>(arr.length())

                for (i in 0 until arr.length()) {
                    val obj = arr.optJSONObject(i) ?: continue
                    val symbol = obj.optString("symbol")
                    if (!symbol.endsWith("USDT", ignoreCase = true)) continue
                    if (
                        symbol.contains("UPUSDT") ||
                        symbol.contains("DOWNUSDT") ||
                        symbol.contains("BULLUSDT") ||
                        symbol.contains("BEARUSDT")
                    ) continue

                    val lastPrice = obj.optString("lastPrice").toDoubleOrNull() ?: continue
                    val change = obj.optString("priceChangePercent").toDoubleOrNull() ?: 0.0
                    val volume = obj.optString("quoteVolume").toDoubleOrNull() ?: 0.0

                    out.add(
                        MarketSeed(
                            symbol = symbol,
                            lastPrice = lastPrice,
                            change24h = change,
                            volume24h = volume
                        )
                    )
                }

                out.sortedByDescending { it.volume24h }
            }
        } catch (_: Exception) {
            emptyList()
        }
    }

    private fun connectBatches(symbols: List<String>) {
        val unique = symbols.distinct()

        // 80 streams per connection keeps URLs manageable.
        unique.chunked(80).forEachIndexed { index, batchSymbols ->
            batches[index] = StreamBatch(batchSymbols)
            openBatch(index)
        }
    }

    private fun openBatch(batchIndex: Int) {
        if (stopped) return

        val batch = batches[batchIndex] ?: return
        batch.reconnectJob?.cancel()
        batch.reconnectJob = null

        batch.socket?.close(1000, "Reconnecting")
        batch.socket = null

        val streams = batch.symbols.joinToString("/") { "${it.lowercase()}@ticker" }
        val url = "wss://stream.binance.com:9443/stream?streams=$streams"

        val request = Request.Builder()
            .url(url)
            .build()

        batch.socket = wsClient.newWebSocket(
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
                    _status.value = "RECONNECTING"
                    scheduleReconnect(batchIndex)
                }

                override fun onClosed(
                    webSocket: WebSocket,
                    code: Int,
                    reason: String
                ) {
                    batch.socket = null
                    if (!stopped) {
                        _status.value = "RECONNECTING"
                        scheduleReconnect(batchIndex)
                    }
                }
            }
        )
    }

    private fun parseCombinedMessage(text: String) {
        try {
            val root = JSONObject(text)
            val data = root.optJSONObject("data") ?: return

            val fullSymbol = data.optString("s")
            if (!fullSymbol.endsWith("USDT", ignoreCase = true)) return

            val symbol = fullSymbol.removeSuffix("USDT")
            val price = data.optString("c").toDoubleOrNull() ?: return
            val change = data.optString("P").toDoubleOrNull() ?: 0.0
            val volume = data.optString("q").toDoubleOrNull() ?: 0.0

            synchronized(tickerMap) {
                val old = tickerMap[symbol]
                tickerMap[symbol] = CryptoCoin(
                    symbol = symbol,
                    price = price,
                    change24h = change,
                    volume24h = volume,
                    rank = old?.rank ?: 0
                )
            }

            schedulePublish()
        } catch (_: Exception) {
        }
    }

    private fun schedulePublish() {
        if (publishJob?.isActive == true) return
        publishJob = scope.launch {
            delay(120)
            publishNow()
        }
    }

    private fun publishNow() {
        synchronized(tickerMap) {
            val list = tickerMap.values
                .sortedByDescending { it.volume24h }
                .mapIndexed { index, coin ->
                    coin.copy(rank = index + 1)
                }

            _coins.value = list
        }
    }

    private fun scheduleReconnect(batchIndex: Int) {
        if (stopped) return

        val batch = batches[batchIndex] ?: return
        if (batch.reconnectJob?.isActive == true) return

        batch.reconnectJob = scope.launch {
            delay(2000)
            if (!stopped) {
                openBatch(batchIndex)
            }
        }
    }

    fun stop() {
        stopped = true
        bootstrapJob?.cancel()
        publishJob?.cancel()

        batches.values.forEach { batch ->
            batch.reconnectJob?.cancel()
            batch.socket?.close(1000, "Application stopped")
            batch.socket = null
        }
        batches.clear()

        scope.cancel()
    }
}

class CryptoViewModel : ViewModel() {

    private val manager = BinanceWebSocketManager()

    val coins = manager.coins
    val status = manager.status

    init {
        manager.start()
    }

    override fun onCleared() {
        manager.stop()
        super.onCleared()
    }
}

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            MaterialTheme {
                CryptoExchangeApp()
            }
        }
    }
}

@Composable
fun CryptoExchangeApp(
    vm: CryptoViewModel = viewModel()
) {
    val coins by vm.coins.collectAsState()
    val status by vm.status.collectAsState()

    val drawerState = rememberDrawerState(DrawerValue.Closed)
    val scope = rememberCoroutineScope()

    var selectedTab by rememberSaveable { mutableIntStateOf(0) }

    ModalNavigationDrawer(
        drawerState = drawerState,
        drawerContent = {
            ModalDrawerSheet {
                Spacer(Modifier.height(18.dp))
                Text(
                    "Crypto Exchange",
                    modifier = Modifier.padding(20.dp),
                    fontSize = 20.sp,
                    fontWeight = FontWeight.Bold
                )
                HorizontalDivider()
                NavigationDrawerItem(
                    label = { Text("Profile") },
                    selected = false,
                    onClick = { scope.launch { drawerState.close() } },
                    icon = { Icon(Icons.Default.Person, null) }
                )
                NavigationDrawerItem(
                    label = { Text("Settings") },
                    selected = false,
                    onClick = { scope.launch { drawerState.close() } },
                    icon = { Icon(Icons.Default.Settings, null) }
                )
                NavigationDrawerItem(
                    label = { Text("Connection") },
                    selected = false,
                    onClick = { scope.launch { drawerState.close() } },
                    icon = { Icon(Icons.Default.Wifi, null) }
                )
            }
        }
    ) {
        Scaffold(
            bottomBar = {
                NavigationBar {
                    val names = listOf("Home", "Chart", "Trade", "Analysis", "News")
                    val icons = listOf(
                        Icons.Default.Home,
                        Icons.Default.ShowChart,
                        Icons.Default.SwapHoriz,
                        Icons.Default.Analytics,
                        Icons.Default.Notifications
                    )

                    names.forEachIndexed { index, name ->
                        NavigationBarItem(
                            selected = selectedTab == index,
                            onClick = { selectedTab = index },
                            icon = { Icon(icons[index], name) },
                            label = { Text(name) }
                        )
                    }
                }
            }
        ) { padding ->
            Box(Modifier.padding(padding)) {
                when (selectedTab) {
                    0 -> HomeScreen(
                        coins = coins,
                        status = status,
                        onMenuClick = { scope.launch { drawerState.open() } }
                    )
                    1 -> PlaceholderScreen("Chart")
                    2 -> PlaceholderScreen("Trade")
                    3 -> PlaceholderScreen("Analysis")
                    4 -> PlaceholderScreen("News")
                }
            }
        }
    }
}

@Composable
fun HomeScreen(
    coins: List<CryptoCoin>,
    status: String,
    onMenuClick: () -> Unit
) {
    var balance by rememberSaveable { mutableDoubleStateOf(10000.0) }
    var search by rememberSaveable { mutableStateOf("") }
    var filter by rememberSaveable { mutableStateOf("ALL") }
    var visibleCount by rememberSaveable { mutableIntStateOf(20) }

    val displayCoins = remember(coins, search, filter, visibleCount) {
        var list = coins

        if (search.isNotBlank()) {
            val q = search.trim()
            list = list.filter {
                it.symbol.contains(q, ignoreCase = true)
            }
        }

        list = when (filter) {
            "GAINER" -> list.sortedByDescending { it.change24h }
            "LOSER" -> list.sortedBy { it.change24h }
            else -> list.sortedBy { it.rank }
        }

        if (search.isBlank()) list.take(visibleCount) else list
    }

    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .padding(12.dp)
    ) {

        item {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(bottom = 10.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(
                    Icons.Default.Menu,
                    contentDescription = "Menu",
                    modifier = Modifier
                        .size(32.dp)
                        .clickable { onMenuClick() }
                )

                Spacer(Modifier.width(10.dp))

                Column(Modifier.weight(1f)) {
                    Text(
                        "Crypto Exchange",
                        fontSize = 19.sp,
                        fontWeight = FontWeight.Bold
                    )
                    Text(
                        "Binance Live WebSocket",
                        fontSize = 11.sp,
                        color = Color.Gray
                    )
                }

                Text(
                    "● $status",
                    fontSize = 11.sp,
                    color = if (status == "LIVE") Color(0xFF008000) else Color(0xFFFF9800)
                )
            }
        }

        item {
            Card(
                Modifier
                    .fillMaxWidth()
                    .padding(vertical = 5.dp)
            ) {
                Column(Modifier.padding(14.dp)) {
                    Row(
                        Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column {
                            Text(
                                "Demo Balance",
                                fontSize = 12.sp,
                                color = Color.Gray
                            )
                            Text(
                                "$" + String.format(Locale.US, "%,.2f", balance),
                                fontSize = 25.sp,
                                fontWeight = FontWeight.Bold
                            )
                        }

                        Button(
                            onClick = { balance = 10000.0 },
                            enabled = balance <= 100.0
                        ) {
                            Text("Reset")
                        }
                    }

                    Spacer(Modifier.height(12.dp))

                    val periods = listOf(
                        "1h", "4h", "8h", "12h",
                        "24h", "1d", "2d", "3d",
                        "4d", "5d", "6d", "7d",
                        "15d", "30d"
                    )

                    periods.chunked(4).forEach { row ->
                        Row(Modifier.fillMaxWidth()) {
                            row.forEach {
                                Column(
                                    Modifier.weight(1f),
                                    horizontalAlignment = Alignment.CenterHorizontally
                                ) {
                                    Text(
                                        it,
                                        fontSize = 11.sp,
                                        color = Color.Gray
                                    )
                                    Text(
                                        "+0.00%",
                                        fontSize = 12.sp
                                    )
                                }
                            }
                        }
                        Spacer(Modifier.height(7.dp))
                    }
                }
            }
        }

        item {
            Card(
                Modifier
                    .fillMaxWidth()
                    .padding(vertical = 5.dp)
            ) {
                Column(Modifier.padding(14.dp)) {
                    Text(
                        "Global Market",
                        fontWeight = FontWeight.Bold
                    )
                    Spacer(Modifier.height(10.dp))
                    Row(
                        Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        MarketValue("Markets", coins.size.toString())
                        MarketValue("BTC", formatCoinValue(coins, "BTC"))
                        MarketValue("24h Volume", formatLarge(coins.sumOf { it.volume24h }))
                    }
                }
            }
        }

        item {
            OutlinedTextField(
                value = search,
                onValueChange = {
                    search = it
                    visibleCount = 20
                },
                modifier = Modifier.fillMaxWidth(),
                singleLine = true,
                placeholder = { Text("Search crypto...") },
                leadingIcon = { Icon(Icons.Default.Search, null) }
            )

            Spacer(Modifier.height(8.dp))

            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                FilterChip(
                    selected = filter == "ALL",
                    onClick = {
                        filter = "ALL"
                        visibleCount = 20
                    },
                    label = { Text("All") }
                )
                FilterChip(
                    selected = filter == "GAINER",
                    onClick = {
                        filter = "GAINER"
                        visibleCount = 20
                    },
                    label = { Text("Gainer") }
                )
                FilterChip(
                    selected = filter == "LOSER",
                    onClick = {
                        filter = "LOSER"
                        visibleCount = 20
                    },
                    label = { Text("Loser") }
                )
            }

            Spacer(Modifier.height(6.dp))

            Text(
                "${coins.size} live USDT markets",
                fontSize = 11.sp,
                color = Color.Gray
            )
        }

        items(
            displayCoins,
            key = { it.symbol }
        ) { coin ->
            CryptoRow(coin)
        }

        if (search.isBlank()) {
            item {
                Row(
                    Modifier
                        .fillMaxWidth()
                        .padding(vertical = 14.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    OutlinedButton(
                        onClick = {
                            visibleCount = maxOf(20, visibleCount - 50)
                        },
                        enabled = visibleCount > 20
                    ) {
                        Text("Previous")
                    }

                    Text(
                        "${minOf(visibleCount, coins.size)} shown",
                        fontSize = 12.sp
                    )

                    Button(
                        onClick = {
                            visibleCount += 50
                        },
                        enabled = visibleCount < coins.size
                    ) {
                        Text("Next 50")
                    }
                }
            }
        }
    }
}

@Composable
fun CryptoRow(coin: CryptoCoin) {
    Row(
        Modifier
            .fillMaxWidth()
            .padding(vertical = 9.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            "#${coin.rank}",
            fontSize = 10.sp,
            color = Color.Gray,
            modifier = Modifier.width(38.dp)
        )

        Box(
            Modifier
                .size(32.dp)
                .clip(CircleShape),
            contentAlignment = Alignment.Center
        ) {
            Text(
                coin.symbol.take(1),
                fontWeight = FontWeight.Bold
            )
        }

        Spacer(Modifier.width(8.dp))

        Column(Modifier.weight(1f)) {
            Text(
                coin.symbol,
                fontWeight = FontWeight.Bold
            )
            Text(
                "${coin.symbol}/USDT",
                fontSize = 10.sp,
                color = Color.Gray
            )
        }

        Column(horizontalAlignment = Alignment.End) {
            Text(
                formatPrice(coin.price),
                fontWeight = FontWeight.Bold
            )

            Text(
                String.format(Locale.US, "%+.2f%%", coin.change24h),
                fontSize = 11.sp,
                color = if (coin.change24h >= 0) Color(0xFF008000) else Color.Red
            )
        }
    }

    HorizontalDivider(thickness = 0.5.dp)
}

@Composable
fun MarketValue(title: String, value: String) {
    Column(Modifier.width(100.dp)) {
        Text(
            title,
            fontSize = 10.sp,
            color = Color.Gray
        )
        Text(
            value,
            fontSize = 12.sp,
            fontWeight = FontWeight.Bold
        )
    }
}

@Composable
fun PlaceholderScreen(title: String) {
    Box(
        Modifier.fillMaxSize(),
        contentAlignment = Alignment.Center
    ) {
        Text(
            title,
            fontSize = 20.sp,
            fontWeight = FontWeight.Bold
        )
    }
}

fun formatCoinValue(coins: List<CryptoCoin>, symbol: String): String {
    val c = coins.firstOrNull { it.symbol.equals(symbol, ignoreCase = true) } ?: return "—"
    return "$" + formatPrice(c.price)
}

fun formatPrice(value: Double): String {
    return if (value >= 1.0) {
        String.format(Locale.US, "%,.4f", value)
    } else {
        String.format(Locale.US, "%.8f", value)
            .trimEnd('0')
            .trimEnd('.')
    }
}

fun formatLarge(value: Double): String {
    return when {
        value >= 1_000_000_000_000 -> String.format(Locale.US, "%.2fT", value / 1_000_000_000_000)
        value >= 1_000_000_000 -> String.format(Locale.US, "%.2fB", value / 1_000_000_000)
        value >= 1_000_000 -> String.format(Locale.US, "%.2fM", value / 1_000_000)
        value >= 1_000 -> String.format(Locale.US, "%.2fK", value / 1_000)
        else -> String.format(Locale.US, "%.2f", value)
    }
}

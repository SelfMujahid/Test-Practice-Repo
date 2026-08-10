package com.practice.cryptoapp

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Menu
import androidx.compose.material.icons.filled.Search
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
        get() = (100.0 - btcDominance - ethDominance).coerceAtLeast(0.0)
}

private data class BinanceSeed(
    val symbol: String,
    val lastPrice: Double,
    val change24h: Double,
    val volume24h: Double
)

enum class FlashState { None, Up, Down }

// ============================================================
// WEBSOCKET & ON-DEMAND DATA MANAGER
// ============================================================

class BinanceWebSocketManager {

    private val httpClient = OkHttpClient.Builder()
        .callTimeout(15, TimeUnit.SECONDS)
        .retryOnConnectionFailure(true)
        .build()

    private val wsClient = OkHttpClient.Builder()
        .readTimeout(0, TimeUnit.MILLISECONDS)
        .pingInterval(20, TimeUnit.SECONDS)
        .retryOnConnectionFailure(true)
        .build()

    private val scope = CoroutineScope(Job() + Dispatchers.IO)

    private val allSeeds = mutableListOf<BinanceSeed>()
    private val loadedSymbols = mutableSetOf<String>()
    private val coinMap = mutableMapOf<String, CryptoCoin>()
    private val cgMetaCache = mutableMapOf<String, CoinGeckoMeta>()
    private var activeWebSocket: WebSocket? = null

    private var stopped = false
    private var bootstrapJob: Job? = null
    private var publishJob: Job? = null

    private val _coins = MutableStateFlow<List<CryptoCoin>>(emptyList())
    val coins: StateFlow<List<CryptoCoin>> = _coins.asStateFlow()

    private val _status = MutableStateFlow("LOADING")
    val status: StateFlow<String> = _status.asStateFlow()

    private val _global = MutableStateFlow(GlobalStats())
    val global: StateFlow<GlobalStats> = _global.asStateFlow()

    fun start() {
        if (bootstrapJob?.isActive == true) return
        stopped = false
        bootstrapJob = scope.launch { bootstrap() }
    }

    private suspend fun bootstrap() {
        _status.value = "FETCHING"

        loadGlobalStats()?.let { _global.value = it }
        fetchCoinGeckoMetaForPage(1)

        allSeeds.clear()
        allSeeds.addAll(loadBinanceSeeds())

        if (allSeeds.isEmpty()) {
            _status.value = "ERROR"
            return
        }

        loadMoreCoins(20)
        _status.value = "LIVE"
    }

    fun loadMoreCoins(count: Int) {
        scope.launch {
            val pendingSeeds = allSeeds.filter { it.symbol !in loadedSymbols }.take(count)
            if (pendingSeeds.isEmpty()) return@launch

            val missingMeta = pendingSeeds.map { it.symbol.removeSuffix("USDT").uppercase() }
                .filter { it !in cgMetaCache }

            if (missingMeta.isNotEmpty()) {
                fetchCoinGeckoMetaOnDemand()
            }

            synchronized(coinMap) {
                pendingSeeds.forEach { seed ->
                    val base = seed.symbol.removeSuffix("USDT")
                    val meta = cgMetaCache[base.uppercase()]

                    coinMap[base] = CryptoCoin(
                        symbol = base,
                        name = meta?.name ?: base,
                        logo = meta?.logo.orEmpty(),
                        price = seed.lastPrice,
                        change24h = seed.change24h,
                        volume24h = seed.volume24h,
                        marketCap = meta?.marketCap ?: 0.0,
                        rank = meta?.marketCapRank ?: 0
                    )
                    loadedSymbols.add(seed.symbol)
                }
                publishNow()
            }
            reconnectWebSocketForActiveCoins()
        }
    }

    fun searchAndLoadCoin(query: String) {
        if (query.isBlank()) return
        val cleanQuery = query.trim().uppercase()
        val match = allSeeds.firstOrNull { 
            it.symbol.removeSuffix("USDT").equals(cleanQuery, ignoreCase = true) 
        }

        if (match != null && match.symbol !in loadedSymbols) {
            scope.launch {
                val base = match.symbol.removeSuffix("USDT")
                if (base.uppercase() !in cgMetaCache) {
                    fetchCoinGeckoMetaOnDemand()
                }

                synchronized(coinMap) {
                    val meta = cgMetaCache[base.uppercase()]
                    coinMap[base] = CryptoCoin(
                        symbol = base,
                        name = meta?.name ?: base,
                        logo = meta?.logo.orEmpty(),
                        price = match.lastPrice,
                        change24h = match.change24h,
                        volume24h = match.volume24h,
                        marketCap = meta?.marketCap ?: 0.0,
                        rank = meta?.marketCapRank ?: 0
                    )
                    loadedSymbols.add(match.symbol)
                    publishNow()
                }
                reconnectWebSocketForActiveCoins()
            }
        }
    }

    private fun reconnectWebSocketForActiveCoins() {
        if (stopped) return

        activeWebSocket?.close(1000, "Updating Stream")
        activeWebSocket = null

        val streams = loadedSymbols.joinToString("/") { "${it.lowercase()}@ticker" }
        val url = "wss://stream.binance.com:9443/stream?streams=$streams"
        val request = Request.Builder().url(url).build()

        activeWebSocket = wsClient.newWebSocket(request, object : WebSocketListener() {
            override fun onOpen(webSocket: WebSocket, response: Response) {
                _status.value = "LIVE"
            }

            override fun onMessage(webSocket: WebSocket, text: String) {
                parseTickerMessage(text)
            }

            override fun onFailure(webSocket: WebSocket, t: Throwable, response: Response?) {
                _status.value = "RECONNECTING"
            }
        })
    }

    private fun parseTickerMessage(text: String) {
        try {
            val root = JSONObject(text)
            val data = root.optJSONObject("data") ?: return
            val symbolFull = data.optString("s")
            val symbol = symbolFull.removeSuffix("USDT")

            val price = data.optString("c").toDoubleOrNull() ?: return
            val change24h = data.optString("P").toDoubleOrNull() ?: 0.0
            val volume24h = data.optString("q").toDoubleOrNull() ?: 0.0

            synchronized(coinMap) {
                val old = coinMap[symbol] ?: return
                coinMap[symbol] = old.copy(price = price, change24h = change24h, volume24h = volume24h)
            }
            schedulePublish()
        } catch (_: Exception) {}
    }

    private fun schedulePublish() {
        if (publishJob?.isActive == true) return
        publishJob = scope.launch {
            delay(150)
            publishNow()
        }
    }

    private fun publishNow() {
        synchronized(coinMap) {
            val list = coinMap.values
                .sortedWith(compareByDescending<CryptoCoin> { it.marketCap }.thenBy { it.symbol })
                .mapIndexed { index, coin -> 
                    if (coin.rank == 0) coin.copy(rank = index + 1) else coin 
                }

            _coins.value = list
        }
    }

    private fun loadBinanceSeeds(): List<BinanceSeed> {
        val request = Request.Builder().url("https://api.binance.com/api/v3/ticker/24hr").build()
        return try {
            httpClient.newCall(request).execute().use { response ->
                if (!response.isSuccessful) return emptyList()
                val body = response.body?.string().orEmpty()
                if (body.isBlank()) return emptyList()

                val array = JSONArray(body)
                val result = ArrayList<BinanceSeed>(array.length())

                for (i in 0 until array.length()) {
                    val obj = array.optJSONObject(i) ?: continue
                    val symbol = obj.optString("symbol")

                    if (!symbol.endsWith("USDT", true) || symbol.contains("UPUSDT", true) || symbol.contains("DOWNUSDT", true)) continue

                    val price = obj.optString("lastPrice").toDoubleOrNull() ?: continue
                    val change = obj.optString("priceChangePercent").toDoubleOrNull() ?: 0.0
                    val volume = obj.optString("quoteVolume").toDoubleOrNull() ?: 0.0

                    result.add(BinanceSeed(symbol, price, change, volume))
                }
                result
            }
        } catch (_: Exception) { emptyList() }
    }

    private fun fetchCoinGeckoMetaForPage(page: Int) {
        val url = "https://api.coingecko.com/api/v3/coins/markets?vs_currency=usd&order=market_cap_desc&per_page=250&page=$page&sparkline=false"
        try {
            httpClient.newCall(Request.Builder().url(url).build()).execute().use { response ->
                if (response.isSuccessful) {
                    val array = JSONArray(response.body?.string().orEmpty())
                    for (i in 0 until array.length()) {
                        val coin = array.optJSONObject(i) ?: continue
                        val symbol = coin.optString("symbol").uppercase()
                        cgMetaCache[symbol] = CoinGeckoMeta(
                            name = coin.optString("name"),
                            logo = coin.optString("image"),
                            marketCap = coin.optDouble("market_cap", 0.0),
                            marketCapRank = coin.optInt("market_cap_rank", Int.MAX_VALUE)
                        )
                    }
                }
            }
        } catch (_: Exception) {}
    }

    private fun fetchCoinGeckoMetaOnDemand() {
        fetchCoinGeckoMetaForPage(2)
    }

    private fun loadGlobalStats(): GlobalStats? {
        return try {
            httpClient.newCall(Request.Builder().url("https://api.coingecko.com/api/v3/global").build()).execute().use { response ->
                if (!response.isSuccessful) return null
                val data = JSONObject(response.body?.string().orEmpty()).optJSONObject("data") ?: return null
                GlobalStats(
                    totalMarketCap = data.optJSONObject("total_market_cap")?.optDouble("usd", 0.0) ?: 0.0,
                    totalVolume = data.optJSONObject("total_volume")?.optDouble("usd", 0.0) ?: 0.0,
                    btcDominance = data.optJSONObject("market_cap_percentage")?.optDouble("btc", 0.0) ?: 0.0,
                    ethDominance = data.optJSONObject("market_cap_percentage")?.optDouble("eth", 0.0) ?: 0.0
                )
            }
        } catch (_: Exception) { null }
    }

    fun stop() {
        stopped = true
        bootstrapJob?.cancel()
        publishJob?.cancel()
        activeWebSocket?.close(1000, "Stopped")
        scope.cancel()
    }
}

// ============================================================
// VIEW MODEL & MAIN ACTIVITY
// ============================================================

class CryptoViewModel : ViewModel() {
    val manager = BinanceWebSocketManager()
    val coins = manager.coins
    val status = manager.status
    val global = manager.global

    init { manager.start() }

    override fun onCleared() {
        manager.stop()
        super.onCleared()
    }
}

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            MaterialTheme(colorScheme = lightColorScheme(primary = PurpleMimosa)) {
                CryptoExchangeApp()
            }
        }
    }
}

// ============================================================
// COMPOSABLE UI
// ============================================================

@Composable
fun CryptoExchangeApp(vm: CryptoViewModel = viewModel()) {
    val coins by vm.coins.collectAsState()
    val status by vm.status.collectAsState()
    val global by vm.global.collectAsState()

    Scaffold { padding ->
        Box(Modifier.padding(padding)) {
            HomeScreen(coins = coins, status = status, global = global, vm = vm)
        }
    }
}

@Composable
fun HomeScreen(
    coins: List<CryptoCoin>,
    status: String,
    global: GlobalStats,
    vm: CryptoViewModel
) {
    var balance by rememberSaveable { mutableDoubleStateOf(10000.0) }
    val initialBalance = 10000.0
    var search by rememberSaveable { mutableStateOf("") }
    var filter by rememberSaveable { mutableStateOf("ALL") }

    val flashMap = remember { mutableStateMapOf<String, FlashState>() }
    val previousPrices = remember { mutableStateMapOf<String, Double>() }

    // 0.3 second (300ms) Flash Color duration logic
    LaunchedEffect(coins) {
        coins.forEach { coin ->
            val previous = previousPrices[coin.symbol]
            if (previous != null && previous != coin.price) {
                val direction = if (coin.price > previous) FlashState.Up else FlashState.Down
                flashMap[coin.symbol] = direction
                launch {
                    delay(300) // 0.3 second flash time
                    if (flashMap[coin.symbol] == direction) flashMap.remove(coin.symbol)
                }
            }
            previousPrices[coin.symbol] = coin.price
        }
    }

    val displayCoins = remember(coins, search, filter) {
        var list = coins
        if (search.isNotBlank()) {
            vm.manager.searchAndLoadCoin(search)
            list = list.filter {
                it.symbol.contains(search, true) || it.name.contains(search, true)
            }
        }

        when (filter) {
            "GAINER" -> list.sortedByDescending { it.change24h }
            "LOSER" -> list.sortedBy { it.change24h }
            else -> list.sortedBy { it.rank }
        }
    }

    LazyColumn(modifier = Modifier.fillMaxSize().padding(horizontal = 10.dp)) {
        item {
            HeaderSection(status)
            BalanceCard(balance, initialBalance) { balance = initialBalance }
            GlobalStatsCard(global)

            Spacer(Modifier.height(8.dp))
            OutlinedTextField(
                value = search,
                onValueChange = { search = it },
                modifier = Modifier.fillMaxWidth(),
                singleLine = true,
                shape = RoundedCornerShape(30.dp),
                placeholder = { Text("Search crypto...") },
                leadingIcon = { Icon(Icons.Default.Search, null, tint = PurpleMimosa) }
            )
            Spacer(Modifier.height(8.dp))
            FilterChipsSection(filter) { filter = it }
            Spacer(Modifier.height(4.dp))
            Text("${coins.size} Active Coins Loaded", fontSize = 10.sp, color = Color.Gray)
        }

        items(items = displayCoins, key = { it.symbol }) { coin ->
            CryptoRow(coin = coin, flash = flashMap[coin.symbol] ?: FlashState.None)
        }

        if (search.isBlank()) {
            item {
                Row(
                    Modifier.fillMaxWidth().padding(vertical = 14.dp),
                    horizontalArrangement = Arrangement.Center
                ) {
                    Button(
                        onClick = { vm.manager.loadMoreCoins(50) },
                        colors = ButtonDefaults.buttonColors(containerColor = PurpleMimosa)
                    ) {
                        Text("Load Next 50 Coins")
                    }
                }
            }
        }
    }
}

// RESTORED Top Bar (Previous/Back Button & 3 Lines Menu Icon)
@Composable
fun HeaderSection(status: String) {
    Row(
        Modifier.fillMaxWidth().padding(vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        IconButton(onClick = { /* Previous / Back Action */ }) {
            Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
        }
        IconButton(onClick = { /* Menu Drawer Action */ }) {
            Icon(Icons.Default.Menu, contentDescription = "Menu")
        }
        Column(Modifier.weight(1f).padding(start = 4.dp)) {
            Text("Crypto Exchange", fontSize = 16.sp, fontWeight = FontWeight.Bold)
            Text("Live Trading Hub", fontSize = 9.sp, color = Color.Gray)
        }
        Text("● $status", fontSize = 10.sp, color = if (status == "LIVE") PositiveGreen else Color.Gray)
    }
}

// RESTORED Demo Balance Percent Gain/Loss Card
@Composable
fun BalanceCard(balance: Double, initialBalance: Double, onReset: () -> Unit) {
    val pnlPercent = ((balance - initialBalance) / initialBalance) * 100

    Card(
        Modifier.fillMaxWidth().padding(vertical = 4.dp),
        shape = RoundedCornerShape(12.dp)
    ) {
        Row(
            Modifier.padding(12.dp).fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column {
                Text("Demo Balance", fontSize = 9.sp, color = Color.Gray)
                Text("$" + String.format(Locale.US, "%,.2f", balance), fontSize = 18.sp, fontWeight = FontWeight.Bold)
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text("PNL 24h: ", fontSize = 10.sp, color = Color.Gray)
                    Text(
                        formatPct(pnlPercent),
                        fontSize = 10.sp,
                        fontWeight = FontWeight.Bold,
                        color = if (pnlPercent >= 0) PositiveGreen else NegativeRed
                    )
                }
            }
            Button(
                onClick = onReset,
                colors = ButtonDefaults.buttonColors(containerColor = PurpleMimosa)
            ) {
                Text("Reset")
            }
        }
    }
}

// RESTORED Complete Global Market Stats Section
@Composable
fun GlobalStatsCard(global: GlobalStats) {
    Card(Modifier.fillMaxWidth().padding(vertical = 4.dp), shape = RoundedCornerShape(12.dp)) {
        Column(Modifier.padding(10.dp)) {
            Text("Global Market Overview", fontWeight = FontWeight.Bold, fontSize = 11.sp)
            Spacer(Modifier.height(6.dp))
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                MarketValue("Global MC", formatLarge(global.totalMarketCap))
                MarketValue("24h Volume", formatLarge(global.totalVolume))
                MarketValue("BTC Dom", formatPct(global.btcDominance))
            }
            Spacer(Modifier.height(6.dp))
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                MarketValue("ETH Dom", formatPct(global.ethDominance))
                MarketValue("ALT Dom", formatPct(global.altDominance))
                MarketValue("Market Status", "Bullish")
            }
        }
    }
}

// ROUNDED Pill / Capsule Filter Buttons
@Composable
fun FilterChipsSection(selectedFilter: String, onSelect: (String) -> Unit) {
    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        listOf("ALL", "GAINER", "LOSER").forEach { filter ->
            FilterChip(
                selected = selectedFilter == filter,
                onClick = { onSelect(filter) },
                shape = CircleShape, // Fully Round
                label = { Text(filter, fontSize = 11.sp, fontWeight = FontWeight.Medium) },
                colors = FilterChipDefaults.filterChipColors(
                    selectedContainerColor = PurpleMimosa,
                    selectedLabelColor = Color.White
                )
            )
        }
    }
}

@Composable
fun CryptoRow(coin: CryptoCoin, flash: FlashState) {
    val priceColor = when (flash) {
        FlashState.Up -> PositiveGreen
        FlashState.Down -> NegativeRed
        FlashState.None -> MaterialTheme.colorScheme.onSurface
    }

    Row(
        Modifier.fillMaxWidth().padding(vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(coin.rank.toString(), fontSize = 9.sp, color = Color.Gray, modifier = Modifier.width(24.dp))

        if (coin.logo.isNotBlank()) {
            AsyncImage(
                model = coin.logo,
                contentDescription = coin.name,
                modifier = Modifier.size(26.dp).clip(CircleShape),
                contentScale = ContentScale.Crop
            )
        } else {
            Box(Modifier.size(26.dp).clip(CircleShape), contentAlignment = Alignment.Center) {
                Text(coin.symbol.take(1), fontWeight = FontWeight.Bold, fontSize = 10.sp)
            }
        }

        Spacer(Modifier.width(8.dp))

        Column(Modifier.weight(1f)) {
            Text(coin.name, fontWeight = FontWeight.Bold, fontSize = 12.sp, maxLines = 1)
            Text(coin.symbol, fontSize = 9.sp, color = Color.Gray)
        }

        Column(horizontalAlignment = Alignment.End) {
            Text(formatPrice(coin.price), fontWeight = FontWeight.Bold, fontSize = 11.sp, color = priceColor)
            Text(
                formatPct(coin.change24h),
                fontSize = 10.sp,
                fontWeight = FontWeight.Medium,
                color = if (coin.change24h >= 0) PositiveGreen else NegativeRed
            )
        }
    }
    HorizontalDivider(thickness = 0.5.dp)
}

@Composable
fun MarketValue(title: String, value: String) {
    Column {
        Text(title, fontSize = 8.sp, color = Color.Gray)
        Text(value, fontSize = 10.sp, fontWeight = FontWeight.Bold)
    }
}

fun formatPct(value: Double) = String.format(Locale.US, "%+.2f%%", value)

fun formatPrice(value: Double): String {
    if (value.isNaN() || value.isInfinite()) return "0"
    return if (value >= 1.0) String.format(Locale.US, "%,.2f", value)
    else String.format(Locale.US, "%.8f", value).trimEnd('0').trimEnd('.')
}

fun formatLarge(value: Double): String {
    return when {
        value >= 1_000_000_000_000 -> String.format(Locale.US, "%.2fT", value / 1_000_000_000_000)
        value >= 1_000_000_000 -> String.format(Locale.US, "%.2fB", value / 1_000_000_000)
        value >= 1_000_000 -> String.format(Locale.US, "%.2fM", value / 1_000_000)
        else -> String.format(Locale.US, "%.2f", value)
    }
}

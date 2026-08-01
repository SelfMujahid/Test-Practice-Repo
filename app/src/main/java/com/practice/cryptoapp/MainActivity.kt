package com.practice.cryptoapp

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.background
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
import coil.compose.AsyncImage
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import okhttp3.*
import org.json.JSONArray

// Dynamic Compose State Model for Zero-Lag Realtime Updates
class CryptoCoin(
    val rank: Int,
    val symbol: String,
    initialPrice: Double,
    initialChange24h: Double,
    val volume: Double,
    val logoUrl: String
) {
    var price by mutableDoubleStateOf(initialPrice)
    var change24h by mutableDoubleStateOf(initialChange24h)
}

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
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
    val drawerState = rememberDrawerState(initialValue = DrawerValue.Closed)
    val scope = rememberCoroutineScope()
    var selectedTab by remember { mutableIntStateOf(0) }

    ModalNavigationDrawer(
        drawerState = drawerState,
        drawerContent = {
            ModalDrawerSheet {
                Spacer(modifier = Modifier.height(16.dp))
                Text("Crypto App Menu", modifier = Modifier.padding(16.dp), fontSize = 20.sp, fontWeight = FontWeight.Bold)
                HorizontalDivider()
                NavigationDrawerItem(
                    label = { Text("Profile & Settings") },
                    selected = false,
                    onClick = { scope.launch { drawerState.close() } },
                    icon = { Icon(Icons.Default.Person, contentDescription = null) }
                )
                NavigationDrawerItem(
                    label = { Text("API Configuration") },
                    selected = false,
                    onClick = { scope.launch { drawerState.close() } },
                    icon = { Icon(Icons.Default.Settings, contentDescription = null) }
                )
            }
        }
    ) {
        Scaffold(
            bottomBar = {
                NavigationBar {
                    val navItems = listOf("Home", "Chart", "Trade", "Analysis", "News")
                    val icons = listOf(Icons.Default.Home, Icons.Default.ShowChart, Icons.Default.SwapHoriz, Icons.Default.Analytics, Icons.Default.Newspaper)
                    
                    navItems.forEachIndexed { index, item ->
                        NavigationBarItem(
                            selected = selectedTab == index,
                            onClick = { selectedTab = index },
                            icon = { Icon(icons[index], contentDescription = item) },
                            label = { Text(item) }
                        )
                    }
                }
            }
        ) { paddingValues ->
            Box(modifier = Modifier.padding(paddingValues)) {
                when (selectedTab) {
                    0 -> HomeScreen(onMenuClick = { scope.launch { drawerState.open() } })
                    2 -> com.practice.cryptoapp.ui.TradeScreen()
                    else -> Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                        Text("Screen Under Construction", fontSize = 18.sp)
                    }
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HomeScreen(onMenuClick: () -> Unit) {
    val scope = rememberCoroutineScope()
    var searchQuery by remember { mutableStateOf("") }
    var filterType by remember { mutableStateOf("ALL") }
    
    // Top 10 initial limit
    var visibleLimit by remember { mutableIntStateOf(10) }
    var demoBalance by remember { mutableDoubleStateOf(10000.0) }
    
    var allCoins by remember { mutableStateOf<List<CryptoCoin>>(emptyList()) }
    val coinLookupMap = remember { mutableStateMapOf<String, CryptoCoin>() }
    var isLoading by remember { mutableStateOf(true) }

    // Fetch Base Coins Data + Direct State-Linked WebSocket Connection
    DisposableEffect(Unit) {
        val client = OkHttpClient()

        // 1. Initial REST API Load
        scope.launch(Dispatchers.IO) {
            try {
                val request = Request.Builder().url("https://api.binance.com/api/v3/ticker/24hr").build()
                client.newCall(request).execute().use { response ->
                    if (response.isSuccessful) {
                        val body = response.body?.string() ?: ""
                        val jsonArray = JSONArray(body)
                        val tempList = mutableListOf<CryptoCoin>()

                        for (i in 0 until jsonArray.length()) {
                            val obj = jsonArray.getJSONObject(i)
                            val symbol = obj.getString("symbol")
                            if (symbol.endsWith("USDT")) {
                                val cleanSymbol = symbol.replace("USDT", "")
                                val coin = CryptoCoin(
                                    rank = 0,
                                    symbol = cleanSymbol,
                                    initialPrice = obj.getDouble("lastPrice"),
                                    initialChange24h = obj.getDouble("priceChangePercent"),
                                    volume = obj.getDouble("quoteVolume"),
                                    logoUrl = "https://assets.coingecko.com/coins/images/1/large/${cleanSymbol.lowercase()}.png"
                                )
                                tempList.add(coin)
                                coinLookupMap[cleanSymbol] = coin
                            }
                        }
                        val sorted = tempList.sortedByDescending { it.volume }
                            .mapIndexed { idx, item -> 
                                CryptoCoin(
                                    rank = idx + 1,
                                    symbol = item.symbol,
                                    initialPrice = item.price,
                                    initialChange24h = item.change24h,
                                    volume = item.volume,
                                    logoUrl = item.logoUrl
                                ).also { coinLookupMap[item.symbol] = it }
                            }

                        withContext(Dispatchers.Main) {
                            allCoins = sorted
                            isLoading = false
                        }
                    }
                }
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }

        // 2. Realtime WebSocket Stream Updating State Properties Directly
        val wsRequest = Request.Builder().url("wss://stream.binance.com:9443/ws/!ticker@arr").build()
        val webSocket = client.newWebSocket(wsRequest, object : WebSocketListener() {
            override fun onMessage(webSocket: WebSocket, text: String) {
                try {
                    val jsonArray = JSONArray(text)
                    for (i in 0 until jsonArray.length()) {
                        val obj = jsonArray.getJSONObject(i)
                        val symbol = obj.getString("s")
                        if (symbol.endsWith("USDT")) {
                            val cleanSymbol = symbol.replace("USDT", "")
                            val coin = coinLookupMap[cleanSymbol]
                            if (coin != null) {
                                val newPrice = obj.getString("c").toDoubleOrNull() ?: coin.price
                                val newChange = obj.getString("P").toDoubleOrNull() ?: coin.change24h
                                
                                // Direct State Mutation triggers instant UI tick update
                                coin.price = newPrice
                                coin.change24h = newChange
                            }
                        }
                    }
                } catch (e: Exception) {
                    e.printStackTrace()
                }
            }
        })

        onDispose {
            webSocket.close(1000, "Screen Closed")
        }
    }

    // Filter Logic
    val filteredCoins = remember(searchQuery, filterType, visibleLimit, allCoins) {
        var list = allCoins.filter { 
            it.symbol.contains(searchQuery, ignoreCase = true)
        }
        list = when (filterType) {
            "GAINER" -> list.sortedByDescending { it.change24h }
            "LOSER" -> list.sortedBy { it.change24h }
            else -> list.sortedBy { it.rank }
        }
        
        if (searchQuery.isNotBlank()) {
            list
        } else {
            list.take(visibleLimit)
        }
    }

    val timeframes = listOf("1h", "4h", "8h", "12h", "24h", "1d", "2d", "3d", "4d", "5d", "6d", "7d", "15d", "30d")

    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .padding(12.dp)
    ) {
        // Top Header
        item {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(bottom = 12.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Box(modifier = Modifier.clickable { onMenuClick() }) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            imageVector = Icons.Default.CurrencyBitcoin,
                            contentDescription = "App Logo",
                            tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(32.dp)
                        )
                        Spacer(modifier = Modifier.width(4.dp))
                        Column {
                            Box(modifier = Modifier.size(12.dp, 2.dp).background(Color.Gray))
                            Spacer(modifier = Modifier.height(2.dp))
                            Box(modifier = Modifier.size(12.dp, 2.dp).background(Color.Gray))
                            Spacer(modifier = Modifier.height(2.dp))
                            Box(modifier = Modifier.size(12.dp, 2.dp).background(Color.Gray))
                        }
                    }
                }
                Spacer(modifier = Modifier.weight(1f))
                Text("Binance Realtime Stream ⚡", fontSize = 18.sp, fontWeight = FontWeight.Bold)
            }
        }

        // Demo Balance Section
        item {
            Card(
                modifier = Modifier.fillMaxWidth().padding(vertical = 6.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer)
            ) {
                Column(modifier = Modifier.padding(12.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column {
                            Text("Demo Balance", fontSize = 12.sp, color = Color.Gray)
                            Text("$${String.format("%.2f", demoBalance)}", fontSize = 22.sp, fontWeight = FontWeight.Bold)
                        }
                        Button(
                            onClick = { demoBalance = 10000.0 },
                            enabled = demoBalance <= 100.0
                        ) {
                            Text("Reset")
                        }
                    }
                    Spacer(modifier = Modifier.height(10.dp))
                    Text("P/L Statistics:", fontSize = 11.sp, fontWeight = FontWeight.SemiBold)
                    
                    LazyRow(modifier = Modifier.padding(top = 4.dp)) {
                        items(timeframes) { tf ->
                            Card(
                                modifier = Modifier.padding(end = 6.dp),
                                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
                            ) {
                                Column(modifier = Modifier.padding(6.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                                    Text(tf, fontSize = 10.sp, color = Color.Gray)
                                    Text(
                                        text = "0.0%",
                                        fontSize = 11.sp,
                                        fontWeight = FontWeight.Bold,
                                        color = Color.Gray
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }

        // Global Market Overview Section
        item {
            Card(
                modifier = Modifier.fillMaxWidth().padding(vertical = 6.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.secondaryContainer)
            ) {
                Column(modifier = Modifier.padding(12.dp)) {
                    Text("Global Market Overview", fontSize = 13.sp, fontWeight = FontWeight.Bold)
                    Spacer(modifier = Modifier.height(6.dp))
                    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                        StatItem("Market Cap", "$2.41T", "+1.8%")
                        StatItem("24h Vol", "$84.2B", "-0.5%")
                        StatItem("BTC Dom", "54.2%", "+0.3%")
                    }
                    Spacer(modifier = Modifier.height(6.dp))
                    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                        StatItem("ETH Dom", "16.8%", "-0.1%")
                        StatItem("ALT Dom", "29.0%", "+0.2%")
                        StatItem("Global 24h", "+1.2%", "+1.2%")
                    }
                }
            }
        }

        // Search & Filters Section
        item {
            OutlinedTextField(
                value = searchQuery,
                onValueChange = { searchQuery = it },
                modifier = Modifier.fillMaxWidth().padding(vertical = 6.dp),
                placeholder = { Text("Search Binance Coins (e.g. BTC, ETH)...") },
                leadingIcon = { Icon(Icons.Default.Search, contentDescription = null) },
                singleLine = true
            )
            Row(
                modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                FilterChip(selected = filterType == "ALL", onClick = { filterType = "ALL" }, label = { Text("All Coins") })
                FilterChip(selected = filterType == "GAINER", onClick = { filterType = "GAINER" }, label = { Text("Top Gainers") })
                FilterChip(selected = filterType == "LOSER", onClick = { filterType = "LOSER" }, label = { Text("Top Losers") })
            }
        }

        // Live Cryptos Display
        if (isLoading) {
            item {
                Box(modifier = Modifier.fillMaxWidth().height(200.dp), contentAlignment = Alignment.Center) {
                    CircularProgressIndicator()
                }
            }
        } else {
            itemsIndexed(filteredCoins, key = { _, coin -> coin.symbol }) { _, coin ->
                CryptoRow(coin)
                HorizontalDivider(thickness = 0.5.dp)
            }

            // Pagination Buttons (Next 50 / Previous)
            if (searchQuery.isBlank()) {
                item {
                    Row(
                        modifier = Modifier.fillMaxWidth().padding(vertical = 16.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        OutlinedButton(
                            onClick = { if (visibleLimit > 10) visibleLimit -= 50 },
                            enabled = visibleLimit > 10
                        ) {
                            Icon(Icons.Default.ArrowBack, contentDescription = null)
                            Spacer(modifier = Modifier.width(4.dp))
                            Text("Previous")
                        }

                        Text(
                            text = "Showing $visibleLimit coins",
                            fontSize = 12.sp,
                            color = Color.Gray
                        )

                        Button(
                            onClick = { visibleLimit += 50 }
                        ) {
                            Text("Next 50")
                            Spacer(modifier = Modifier.width(4.dp))
                            Icon(Icons.Default.ArrowForward, contentDescription = null)
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun CryptoRow(coin: CryptoCoin) {
    Row(
        modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text("#${coin.rank}", fontSize = 11.sp, color = Color.Gray, modifier = Modifier.width(32.dp))
        
        AsyncImage(
            model = coin.logoUrl,
            contentDescription = coin.symbol,
            modifier = Modifier.size(30.dp).clip(CircleShape),
            error = androidx.compose.ui.res.painterResource(id = android.R.drawable.stat_notify_error)
        )

        Spacer(modifier = Modifier.width(8.dp))
        Column(modifier = Modifier.weight(1.2f)) {
            Text("${coin.symbol}/USDT", fontWeight = FontWeight.Bold, fontSize = 14.sp)
            Text("Vol: $${String.format("%.0f", coin.volume / 1000000)}M", fontSize = 10.sp, color = Color.Gray)
        }
        Column(horizontalAlignment = Alignment.End, modifier = Modifier.weight(1.5f)) {
            // Directly observed Compose State Property (Price updates real-time instantly!)
            Text("$${coin.price}", fontWeight = FontWeight.Bold, fontSize = 13.sp)
            
            val isPositive = coin.change24h >= 0
            Text(
                text = "${if (isPositive) "+" else ""}${String.format("%.2f", coin.change24h)}%",
                fontSize = 11.sp,
                color = if (isPositive) Color(0xFF008000) else Color.Red
            )
        }
    }
}

@Composable
fun StatItem(title: String, value: String, change: String) {
    Column {
        Text(title, fontSize = 10.sp, color = Color.Gray)
        Text(value, fontSize = 12.sp, fontWeight = FontWeight.Bold)
        Text(
            change,
            fontSize = 10.sp,
            color = if (change.startsWith("+")) Color(0xFF008000) else Color.Red
        )
    }
}

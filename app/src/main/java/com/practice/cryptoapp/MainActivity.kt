package com.practice.cryptoapp

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
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
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import okhttp3.*
import org.json.JSONArray

data class CryptoCoin(
    val rank: Int,
    val symbol: String,
    val price: Double,
    val change24h: Double,
    val volume: Double
)

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
    
    // Pagination states: Shuru mein Top 10 show honge
    var pageSize by remember { mutableIntStateOf(10) }
    var demoBalance by remember { mutableDoubleStateOf(10000.0) }
    
    var allCoins by remember { mutableStateOf<List<CryptoCoin>>(emptyList()) }
    var isLoading by remember { mutableStateOf(true) }

    // 1. Initial REST API Load & Live WSS Updates
    DisposableEffect(Unit) {
        val client = OkHttpClient()

        // Fetch Initial Data via REST API
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
                                tempList.add(
                                    CryptoCoin(
                                        rank = 0,
                                        symbol = symbol.replace("USDT", ""),
                                        price = obj.getDouble("lastPrice"),
                                        change24h = obj.getDouble("priceChangePercent"),
                                        volume = obj.getDouble("quoteVolume")
                                    )
                                )
                            }
                        }
                        val sorted = tempList.sortedByDescending { it.volume }
                            .mapIndexed { idx, item -> item.copy(rank = idx + 1) }

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

        // 2. WebSocket Realtime Stream Connection
        val wsRequest = Request.Builder().url("wss://stream.binance.com:9443/ws/!ticker@arr").build()
        val webSocket = client.newWebSocket(wsRequest, object : WebSocketListener() {
            override fun onMessage(webSocket: WebSocket, text: String) {
                scope.launch(Dispatchers.IO) {
                    try {
                        val jsonArray = JSONArray(text)
                        val liveMap = mutableMapOf<String, Pair<Double, Double>>()

                        for (i in 0 until jsonArray.length()) {
                            val obj = jsonArray.getJSONObject(i)
                            val symbol = obj.getString("s")
                            if (symbol.endsWith("USDT")) {
                                val cleanSymbol = symbol.replace("USDT", "")
                                val price = obj.getString("c").toDoubleOrNull() ?: 0.0
                                val change = obj.getString("P").toDoubleOrNull() ?: 0.0
                                liveMap[cleanSymbol] = Pair(price, change)
                            }
                        }

                        if (allCoins.isNotEmpty()) {
                            val updatedList = allCoins.map { coin ->
                                val liveData = liveMap[coin.symbol]
                                if (liveData != null) {
                                    coin.copy(price = liveData.first, change24h = liveData.second)
                                } else {
                                    coin
                                }
                            }
                            withContext(Dispatchers.Main) {
                                allCoins = updatedList
                            }
                        }
                    } catch (e: Exception) {
                        e.printStackTrace()
                    }
                }
            }
        })

        onDispose {
            webSocket.close(1000, "Screen Closed")
        }
    }

    // Search & Pagination Logic
    val displayedCoins = remember(searchQuery, filterType, pageSize, allCoins) {
        var list = allCoins.filter { 
            it.symbol.contains(searchQuery, ignoreCase = true)
        }
        list = when (filterType) {
            "GAINER" -> list.sortedByDescending { it.change24h }
            "LOSER" -> list.sortedBy { it.change24h }
            else -> list.sortedBy { it.rank }
        }
        
        // Agar user search kar raha hai toh poori search list dikhayein, warna pagination (10 -> 50 -> 100) apply karein
        if (searchQuery.isNotBlank()) {
            list
        } else {
            list.take(pageSize)
        }
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
                    .padding(bottom = 12.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Box(modifier = Modifier.clickable { onMenuClick() }) {
                    Icon(
                        imageVector = Icons.Default.CurrencyBitcoin,
                        contentDescription = "App Logo",
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(32.dp)
                    )
                }
                Spacer(modifier = Modifier.width(8.dp))
                Text("Crypto Live Market ⚡", fontSize = 18.sp, fontWeight = FontWeight.Bold)
            }
        }

        // Demo Balance Card
        item {
            Card(
                modifier = Modifier.fillMaxWidth().padding(vertical = 6.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth().padding(14.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column {
                        Text("Demo Balance", fontSize = 12.sp, color = Color.Gray)
                        Text("$${String.format("%.2f", demoBalance)}", fontSize = 22.sp, fontWeight = FontWeight.Bold)
                    }
                    Button(onClick = { demoBalance = 10000.0 }) {
                        Text("Reset")
                    }
                }
            }
        }

        // Search & Filter Section
        item {
            OutlinedTextField(
                value = searchQuery,
                onValueChange = { searchQuery = it },
                modifier = Modifier.fillMaxWidth().padding(vertical = 6.dp),
                placeholder = { Text("Search specific coin (e.g. BTC, ETH)...") },
                leadingIcon = { Icon(Icons.Default.Search, contentDescription = null) },
                singleLine = true
            )
            Row(
                modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                FilterChip(selected = filterType == "ALL", onClick = { filterType = "ALL" }, label = { Text("All") })
                FilterChip(selected = filterType == "GAINER", onClick = { filterType = "GAINER" }, label = { Text("Top Gainers") })
                FilterChip(selected = filterType == "LOSER", onClick = { filterType = "LOSER" }, label = { Text("Top Losers") })
            }
        }

        // Coin List Items
        if (isLoading) {
            item {
                Box(modifier = Modifier.fillMaxWidth().height(200.dp), contentAlignment = Alignment.Center) {
                    CircularProgressIndicator()
                }
            }
        } else {
            itemsIndexed(displayedCoins) { _, coin ->
                CryptoRow(coin)
                HorizontalDivider(thickness = 0.5.dp)
            }

            // Pagination Buttons (Next / Previous) - Sirf tab dikhenge jab search na ho raha ho
            if (searchQuery.isBlank()) {
                item {
                    Row(
                        modifier = Modifier.fillMaxWidth().padding(vertical = 16.dp),
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        OutlinedButton(
                            onClick = { if (pageSize > 10) pageSize -= 50 },
                            enabled = pageSize > 10
                        ) {
                            Icon(Icons.Default.ArrowBack, contentDescription = null)
                            Spacer(modifier = Modifier.width(4.dp))
                            Text("Previous")
                        }

                        Text(
                            text = "Showing $pageSize coins",
                            modifier = Modifier.align(Alignment.CenterVertically),
                            fontSize = 12.sp,
                            color = Color.Gray
                        )

                        Button(
                            onClick = { pageSize += 50 }
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
        
        Box(
            modifier = Modifier
                .size(30.dp)
                .clip(CircleShape)
                .background(MaterialTheme.colorScheme.primaryContainer),
            contentAlignment = Alignment.Center
        ) {
            Text(
                text = coin.symbol.take(1),
                fontWeight = FontWeight.Bold,
                fontSize = 12.sp,
                color = MaterialTheme.colorScheme.onPrimaryContainer
            )
        }

        Spacer(modifier = Modifier.width(8.dp))
        Column(modifier = Modifier.weight(1.2f)) {
            Text("${coin.symbol}/USDT", fontWeight = FontWeight.Bold, fontSize = 14.sp)
            Text("Vol: $${String.format("%.0f", coin.volume / 1000000)}M", fontSize = 10.sp, color = Color.Gray)
        }
        Column(horizontalAlignment = Alignment.End, modifier = Modifier.weight(1.5f)) {
            // Price live WSS se update hogi
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

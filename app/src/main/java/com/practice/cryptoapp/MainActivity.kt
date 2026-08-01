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
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request

// Data Models for Binance
data class BinanceTicker(
    val symbol: String,
    val lastPrice: String,
    val priceChangePercent: String,
    val quoteVolume: String
)

data class CryptoCoin(
    val rank: Int,
    val symbol: String,
    val price: Double,
    val change24h: Double,
    val volume: Double,
    val logoUrl: String
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
    var searchQuery by remember { mutableStateOf("") }
    var filterType by remember { mutableStateOf("ALL") }
    var visibleCount by remember { mutableIntStateOf(20) }
    var demoBalance by remember { mutableDoubleStateOf(10000.0) }
    
    var allCoins by remember { mutableStateOf<List<CryptoCoin>>(emptyList()) }
    var isLoading by remember { mutableStateOf(true) }

    // Live Fetch & Auto Re-Sync Jab Bhi Screen Par Wapas Aayein
    LaunchedEffect(Unit) {
        fetchBinanceMarketData { list ->
            allCoins = list
            isLoading = false
        }
    }

    // Dynamic Filter Logic
    val filteredCoins = remember(searchQuery, filterType, visibleCount, allCoins) {
        var list = allCoins.filter { 
            it.symbol.contains(searchQuery, ignoreCase = true)
        }
        list = when (filterType) {
            "GAINER" -> list.sortedByDescending { it.change24h }
            "LOSER" -> list.sortedBy { it.change24h }
            else -> list.sortedBy { it.rank }
        }
        list.take(visibleCount)
    }

    val timeframes = listOf("1h", "4h", "8h", "12h", "24h", "1d", "2d", "3d", "4d", "5d", "6d", "7d", "15d", "30d")

    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .padding(12.dp)
    ) {
        // --- 1. Header ---
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
                Text("Binance Live Stream", fontSize = 18.sp, fontWeight = FontWeight.Bold)
            }
        }

        // --- 2. Demo Balance Section (Default Neutral 0.0% Gray P/L) ---
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

        // --- 3. Global Stats ---
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

        // --- 4. Search & Filters ---
        item {
            OutlinedTextField(
                value = searchQuery,
                onValueChange = { searchQuery = it },
                modifier = Modifier.fillMaxWidth().padding(vertical = 6.dp),
                placeholder = { Text("Search Binance Coins...") },
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

        // --- 5. Binance Crypto List ---
        if (isLoading) {
            item {
                Box(modifier = Modifier.fillMaxWidth().height(150.dp), contentAlignment = Alignment.Center) {
                    CircularProgressIndicator()
                }
            }
        } else {
            itemsIndexed(filteredCoins) { _, coin ->
                CryptoRow(coin)
                HorizontalDivider(thickness = 0.5.dp)
            }

            // --- 6. Pagination ---
            item {
                Row(
                    modifier = Modifier.fillMaxWidth().padding(vertical = 12.dp),
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    OutlinedButton(
                        onClick = { if (visibleCount > 20) visibleCount -= 50 },
                        enabled = visibleCount > 20
                    ) {
                        Text("Previous 50")
                    }
                    Button(
                        onClick = { visibleCount += 50 }
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
        modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text("#${coin.rank}", fontSize = 11.sp, color = Color.Gray, modifier = Modifier.width(32.dp))
        AsyncImage(
            model = coin.logoUrl,
            contentDescription = coin.symbol,
            modifier = Modifier.size(30.dp).clip(CircleShape)
        )
        Spacer(modifier = Modifier.width(8.dp))
        Column(modifier = Modifier.weight(1.2f)) {
            Text("${coin.symbol}/USDT", fontWeight = FontWeight.Bold, fontSize = 14.sp)
            Text("Vol: $${String.format("%.0f", coin.volume / 1000000)}M", fontSize = 10.sp, color = Color.Gray)
        }
        Column(horizontalAlignment = Alignment.End, modifier = Modifier.weight(1.5f)) {
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

// Background Network Helper for Auto-Sync
suspend fun fetchBinanceMarketData(onDataLoaded: (List<CryptoCoin>) -> Unit) {
    withContext(Dispatchers.IO) {
        try {
            val client = OkHttpClient()
            val request = Request.Builder()
                .url("https://api.binance.com/api/v3/ticker/24hr")
                .build()

            client.newCall(request).execute().use { response ->
                if (response.isSuccessful) {
                    val body = response.body?.string()
                    val type = object : TypeToken<List<BinanceTicker>>() {}.type
                    val rawList: List<BinanceTicker> = Gson().fromJson(body, type)

                    val usdtPairs = rawList.filter { it.symbol.endsWith("USDT") }
                        .sortedByDescending { it.quoteVolume.toDoubleOrNull() ?: 0.0 }

                    val mappedCoins = usdtPairs.mapIndexed { index, ticker ->
                        val cleanSymbol = ticker.symbol.replace("USDT", "")
                        CryptoCoin(
                            rank = index + 1,
                            symbol = cleanSymbol,
                            price = ticker.lastPrice.toDoubleOrNull() ?: 0.0,
                            change24h = ticker.priceChangePercent.toDoubleOrNull() ?: 0.0,
                            volume = ticker.quoteVolume.toDoubleOrNull() ?: 0.0,
                            logoUrl = "https://assets.coingecko.com/coins/images/1/large/${cleanSymbol.lowercase()}.png"
                        )
                    }

                    withContext(Dispatchers.Main) {
                        onDataLoaded(mappedCoins)
                    }
                }
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }
}

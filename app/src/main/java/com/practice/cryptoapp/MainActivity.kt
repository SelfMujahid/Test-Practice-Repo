package com.practice.cryptoapp

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import kotlinx.coroutines.launch

// --- Data Models ---
data class CryptoCoin(
    val rank: Int,
    val name: String,
    val symbol: String,
    val price: Double,
    val change1h: Double,
    val change4h: Double,
    val change24h: Double,
    val change7d: Double,
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
                NavigationDrawerItem(
                    label = { Text("Alerts & Notifications") },
                    selected = false,
                    onClick = { scope.launch { drawerState.close() } },
                    icon = { Icon(Icons.Default.Notifications, contentDescription = null) }
                )
            }
        }
    ) {
        Scaffold(
            bottomBar = {
                NavigationBar(containerColor = MaterialTheme.colorScheme.surfaceVariant) {
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
                    1 -> PlaceholderScreen("Chart Screen")
                    2 -> PlaceholderScreen("Trade Screen")
                    3 -> PlaceholderScreen("Analysis Screen")
                    4 -> PlaceholderScreen("News Screen")
                }
            }
        }
    }
}

@Composable
fun HomeScreen(onMenuClick: () -> Unit) {
    var searchQuery by remember { mutableStateOf("") }
    var filterType by remember { mutableStateOf("ALL") } // ALL, GAINER, LOSER
    var visibleCount by remember { mutableIntStateOf(20) }
    var demoBalance by remember { mutableDoubleStateOf(10000.0) }

    // Dummy Crypto Data (CoinGecko URLs)
    val allCoins = remember {
        listOf(
            CryptoCoin(1, "Bitcoin", "BTC", 64230.50, 0.4, 1.2, 3.5, 8.2, "https://assets.coingecko.com/coins/images/1/large/bitcoin.png"),
            CryptoCoin(2, "Ethereum", "ETH", 3450.20, -0.2, 0.8, -1.5, 4.1, "https://assets.coingecko.com/coins/images/279/large/ethereum.png"),
            CryptoCoin(3, "Binance Coin", "BNB", 580.10, 0.1, -0.5, 5.4, 2.3, "https://assets.coingecko.com/coins/images/825/large/bnb-icon2_2x.png"),
            CryptoCoin(4, "Solana", "SOL", 145.80, 1.2, 3.1, -4.2, 12.5, "https://assets.coingecko.com/coins/images/4128/large/solana.png"),
            CryptoCoin(5, "Ripple", "XRP", 0.52, -0.1, 0.2, -0.8, -2.1, "https://assets.coingecko.com/coins/images/44/large/xrp-symbol-white-128.png"),
            CryptoCoin(6, "Cardano", "ADA", 0.38, 0.5, 1.1, 2.8, -1.4, "https://assets.coingecko.com/coins/images/975/large/cardano.png")
        )
    }

    val filteredCoins = remember(searchQuery, filterType, visibleCount) {
        var list = allCoins.filter { 
            it.name.contains(searchQuery, ignoreCase = true) || it.symbol.contains(searchQuery, ignoreCase = true) 
        }
        list = when (filterType) {
            "GAINER" -> list.sortedByDescending { it.change24h }
            "LOSER" -> list.sortedBy { it.change24h }
            else -> list.sortedBy { it.rank }
        }
        list.take(visibleCount)
    }

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
                        Image(
                            painter = painterResource(id = R.drawable.app_logo),
                            contentDescription = "App Logo",
                            modifier = Modifier.size(36.dp)
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
                Text("Crypto Tracker", fontSize = 18.sp, fontWeight = FontWeight.Bold)
            }
        }

        // --- 2. Demo Balance Section ---
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
                    Spacer(modifier = Modifier.height(8.dp))
                    Text("P/L Statistics:", fontSize = 11.sp, fontWeight = FontWeight.SemiBold)
                    
                    val timeframes = listOf("1h", "4h", "8h", "12h", "24h", "1d", "2d", "3d", "4d", "5d", "6d", "7d", "15d", "30d")
                    LazyRow(modifier = Modifier.padding(top = 4.dp)) {
                        items(timeframes) { time ->
                            val percent = remember { (-5..10).random() + 0.5 }
                            Card(
                                modifier = Modifier.padding(end = 6.dp),
                                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
                            ) {
                                Column(modifier = Modifier.padding(6.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                                    Text(time, fontSize = 10.sp, color = Color.Gray)
                                    Text(
                                        "${if (percent >= 0) "+" else ""}$percent%",
                                        fontSize = 11.sp,
                                        fontWeight = FontWeight.Bold,
                                        color = if (percent >= 0) Color(0xFF008000) else Color.Red
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }

        // --- 3. Global Marketcap & Stats Section ---
        item {
            Card(
                modifier = Modifier.fillMaxWidth().padding(vertical = 6.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.secondaryContainer)
            ) {
                Column(modifier = Modifier.padding(12.dp)) {
                    Text("Global Market Overview", fontSize = 14.sp, fontWeight = FontWeight.Bold)
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

        // --- 4. Search & Filter Bar ---
        item {
            OutlinedTextField(
                value = searchQuery,
                onValueChange = { searchQuery = it },
                modifier = Modifier.fillMaxWidth().padding(vertical = 6.dp),
                placeholder = { Text("Search Crypto...") },
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

        // --- 5. Crypto List Items ---
        items(filteredCoins) { coin ->
            CryptoRow(coin)
            HorizontalDivider(modifier = Modifier.padding(vertical = 4.dp), thickness = 0.5.dp)
        }

        // --- 6. Pagination (Next / Previous) ---
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

@Composable
fun CryptoRow(coin: CryptoCoin) {
    Row(
        modifier = Modifier.fillMaxWidth().padding(vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text("#${coin.rank}", fontSize = 11.sp, color = Color.Gray, modifier = Modifier.width(26.dp))
        AsyncImage(
            model = coin.logoUrl,
            contentDescription = coin.name,
            modifier = Modifier.size(32.dp).clip(CircleShape)
        )
        Spacer(modifier = Modifier.width(8.dp))
        Column(modifier = Modifier.weight(1.2f)) {
            Text(coin.symbol, fontWeight = FontWeight.Bold, fontSize = 14.sp)
            Text(coin.name, fontSize = 11.sp, color = Color.Gray)
        }
        Column(horizontalAlignment = Alignment.End, modifier = Modifier.weight(1.5f)) {
            Text("$${coin.price}", fontWeight = FontWeight.Bold, fontSize = 13.sp)
            Row {
                ChangeText("24h: ", coin.change24h)
                Spacer(modifier = Modifier.width(4.dp))
                ChangeText("7d: ", coin.change7d)
            }
        }
    }
}

@Composable
fun ChangeText(label: String, value: Double) {
    val isPositive = value >= 0
    Text(
        text = "$label${if (isPositive) "+" else ""}$value%",
        fontSize = 10.sp,
        color = if (isPositive) Color(0xFF008000) else Color.Red
    )
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

@Composable
fun PlaceholderScreen(title: String) {
    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        Text(text = title, fontSize = 22.sp, fontWeight = FontWeight.Bold)
    }
}

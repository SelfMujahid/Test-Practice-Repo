package com.practice.cryptoapp

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.HorizontalDivider
import androidx.compose.foundation.layout.LazyColumn
import androidx.compose.foundation.layout.LazyRow
import androidx.compose.foundation.layout.Modifier
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Analytics
import androidx.compose.material.icons.filled.CurrencyBitcoin
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Menu
import androidx.compose.material.icons.filled.Notifications
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.ShowChart
import androidx.compose.material.icons.filled.SwapHoriz
import androidx.compose.material.icons.filled.Person
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.ModalDrawerSheet
import androidx.compose.material3.ModalNavigationDrawer
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationDrawerItem
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.rememberDrawerState
import androidx.compose.material3.DrawerValue
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableDoubleStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
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
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.Response
import okhttp3.WebSocket
import okhttp3.WebSocketListener
import org.json.JSONArray
import kotlin.math.abs

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
                Text(
                    text = "Crypto App Menu",
                    modifier = Modifier.padding(16.dp),
                    fontSize = 20.sp,
                    fontWeight = FontWeight.Bold
                )
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
                    val icons = listOf(
                        Icons.Default.Home,
                        Icons.Default.ShowChart,
                        Icons.Default.SwapHoriz,
                        Icons.Default.Analytics,
                        Icons.Default.Notifications
                    )

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
                    2 -> TradeScreen()
                    1, 3, 4 -> PlaceholderScreen(title = "Screen Under Construction")
                }
            }
        }
    }
}

@Composable
fun PlaceholderScreen(title: String) {
    Box(
        modifier = Modifier.fillMaxSize(),
        contentAlignment = Alignment.Center
    ) {
        Text(title, fontSize = 18.sp)
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HomeScreen(onMenuClick: () -> Unit) {
    val scope = rememberCoroutineScope()
    var searchQuery by remember { mutableStateOf("") }
    var filterType by remember { mutableStateOf("ALL") }
    var visibleCount by remember { mutableIntStateOf(20) }
    var demoBalance by remember { mutableDoubleStateOf(10000.0) }

    var allCoins by remember { mutableStateOf<List<CryptoCoin>>(emptyList()) }
    var isLoading by remember { mutableStateOf(true) }
    var errorText by remember { mutableStateOf<String?>(null) }

    // Latest state reference for WebSocket callbacks
    val latestCoinsState = rememberUpdatedState(allCoins)

    DisposableEffect(Unit) {
        val client = OkHttpClient()
        var webSocket: WebSocket? = null

        // Initial REST load
        scope.launch(Dispatchers.IO) {
            try {
                val request = Request.Builder()
                    .url("https://api.binance.com/api/v3/ticker/24hr")
                    .build()

                client.newCall(request).execute().use { response: Response ->
                    if (response.isSuccessful) {
                        val body = response.body?.string().orEmpty()
                        val jsonArray = JSONArray(body)

                        val tempList = mutableListOf<CryptoCoin>()

                        for (i in 0 until jsonArray.length()) {
                            val obj = jsonArray.getJSONObject(i)
                            val symbol = obj.getString("symbol")

                            if (symbol.endsWith("USDT")) {
                                val price = obj.getString("lastPrice").toDoubleOrNull() ?: 0.0
                                val change = obj.getString("priceChangePercent").toDoubleOrNull() ?: 0.0
                                val volume = obj.getString("quoteVolume").toDoubleOrNull() ?: 0.0

                                tempList.add(
                                    CryptoCoin(
                                        rank = 0,
                                        symbol = symbol.removeSuffix("USDT"),
                                        price = price,
                                        change24h = change,
                                        volume = volume
                                    )
                                )
                            }
                        }

                        val sorted = tempList
                            .sortedByDescending { it.volume }
                            .mapIndexed { index, coin -> coin.copy(rank = index + 1) }

                        withContext(Dispatchers.Main) {
                            allCoins = sorted
                            isLoading = false
                            errorText = null
                        }
                    } else {
                        withContext(Dispatchers.Main) {
                            isLoading = false
                            errorText = "REST API failed: ${response.code}"
                        }
                    }
                }
            } catch (e: Exception) {
                withContext(Dispatchers.Main) {
                    isLoading = false
                    errorText = "REST error: ${e.message}"
                }
            }
        }

        // Realtime WebSocket
        val wsRequest = Request.Builder()
            .url("wss://stream.binance.com:9443/ws/!ticker@arr")
            .build()

        webSocket = client.newWebSocket(wsRequest, object : WebSocketListener() {
            override fun onMessage(webSocket: WebSocket, text: String) {
                scope.launch(Dispatchers.Main) {
                    try {
                        val jsonArray = JSONArray(text)
                        val liveMap = mutableMapOf<String, Pair<Double, Double>>()

                        for (i in 0 until jsonArray.length()) {
                            val obj = jsonArray.getJSONObject(i)
                            val symbol = obj.getString("s")

                            if (symbol.endsWith("USDT")) {
                                val cleanSymbol = symbol.removeSuffix("USDT")
                                val price = obj.getString("c").toDoubleOrNull() ?: 0.0
                                val change = obj.getString("P").toDoubleOrNull() ?: 0.0
                                liveMap[cleanSymbol] = price to change
                            }
                        }

                        val currentCoins = latestCoinsState.value
                        if (currentCoins.isNotEmpty()) {
                            allCoins = currentCoins.map { coin ->
                                val liveData = liveMap[coin.symbol]
                                if (liveData != null) {
                                    coin.copy(
                                        price = liveData.first,
                                        change24h = liveData.second
                                    )
                                } else {
                                    coin
                                }
                            }
                        }
                    } catch (_: Exception) {
                        // ignore parsing errors
                    }
                }
            }

            override fun onFailure(webSocket: WebSocket, t: Throwable, response: Response?) {
                scope.launch(Dispatchers.Main) {
                    errorText = "WebSocket error: ${t.message}"
                }
            }
        })

        onDispose {
            webSocket?.close(1000, "Screen Closed")
            client.dispatcher.executorService.shutdown()
        }
    }

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
        item {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(bottom = 12.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Box(modifier = Modifier.clickable { onMenuClick() }) {
                    Icon(
                        imageVector = Icons.Default.Menu,
                        contentDescription = "Menu",
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(32.dp)
                    )
                }
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    text = "Binance Realtime Stream ⚡",
                    fontSize = 18.sp,
                    fontWeight = FontWeight.Bold
                )
            }
        }

        item {
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 6.dp),
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.primaryContainer
                )
            ) {
                Column(modifier = Modifier.padding(12.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column {
                            Text("Demo Balance", fontSize = 12.sp, color = Color.Gray)
                            Text(
                                text = "$${String.format("%.2f", demoBalance)}",
                                fontSize = 22.sp,
                                fontWeight = FontWeight.Bold
                            )
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
                                colors = CardDefaults.cardColors(
                                    containerColor = MaterialTheme.colorScheme.surface
                                )
                            ) {
                                Column(
                                    modifier = Modifier.padding(6.dp),
                                    horizontalAlignment = Alignment.CenterHorizontally
                                ) {
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

        item {
            OutlinedTextField(
                value = searchQuery,
                onValueChange = { searchQuery = it },
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 6.dp),
                placeholder = { Text("Search Binance Coins...") },
                leadingIcon = { Icon(Icons.Default.Search, contentDescription = null) },
                singleLine = true
            )

            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 4.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                FilterChip(
                    selected = filterType == "ALL",
                    onClick = { filterType = "ALL" },
                    label = { Text("All Coins") }
                )
                FilterChip(
                    selected = filterType == "GAINER",
                    onClick = { filterType = "GAINER" },
                    label = { Text("Top Gainers") }
                )
                FilterChip(
                    selected = filterType == "LOSER",
                    onClick = { filterType = "LOSER" },
                    label = { Text("Top Losers") }
                )
            }

            errorText?.let {
                Spacer(modifier = Modifier.height(8.dp))
                Text(text = it, color = Color.Red, fontSize = 12.sp)
            }
        }

        if (isLoading) {
            item {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(200.dp),
                    contentAlignment = Alignment.Center
                ) {
                    CircularProgressIndicator()
                }
            }
        } else {
            itemsIndexed(filteredCoins) { _, coin ->
                CryptoRow(coin)
                HorizontalDivider(thickness = 0.5.dp)
            }

            item {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 12.dp),
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    OutlinedButton(
                        onClick = { if (visibleCount > 20) visibleCount -= 50 },
                        enabled = visibleCount > 20
                    ) {
                        Text("Previous 50")
                    }
                    Button(onClick = { visibleCount += 50 }) {
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
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            text = "#${coin.rank}",
            fontSize = 11.sp,
            color = Color.Gray,
            modifier = Modifier.width(32.dp)
        )

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
            Text(
                text = "${coin.symbol}/USDT",
                fontWeight = FontWeight.Bold,
                fontSize = 14.sp
            )
            Text(
                text = "Vol: $${String.format("%.0f", coin.volume / 1_000_000)}M",
                fontSize = 10.sp,
                color = Color.Gray
            )
        }

        Column(
            horizontalAlignment = Alignment.End,
            modifier = Modifier.weight(1.5f)
        ) {
            Text(
                text = "$${String.format("%.6f", coin.price)}",
                fontWeight = FontWeight.Bold,
                fontSize = 13.sp
            )

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
fun TradeScreen() {
    Box(
        modifier = Modifier.fillMaxSize(),
        contentAlignment = Alignment.Center
    ) {
        Text("Trade Screen")
    }
}
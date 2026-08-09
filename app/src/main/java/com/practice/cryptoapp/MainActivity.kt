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
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.Response
import okhttp3.WebSocket
import okhttp3.WebSocketListener
import org.json.JSONArray
import java.util.Locale

class CryptoCoin(
    val symbol: String,
    initialPrice: Double,
    initialChange24h: Double,
    initialVolume: Double
) {
    var price by mutableDoubleStateOf(initialPrice)
    var change24h by mutableDoubleStateOf(initialChange24h)
    var volume by mutableDoubleStateOf(initialVolume)
    var rank by mutableIntStateOf(0)
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

    val drawerState = rememberDrawerState(
        initialValue = DrawerValue.Closed
    )

    val scope = rememberCoroutineScope()

    var selectedTab by remember {
        mutableIntStateOf(0)
    }

    ModalNavigationDrawer(

        drawerState = drawerState,

        drawerContent = {

            ModalDrawerSheet {

                Spacer(
                    modifier = Modifier.height(16.dp)
                )

                Text(
                    text = "Crypto App Menu",
                    modifier = Modifier.padding(16.dp),
                    fontSize = 20.sp,
                    fontWeight = FontWeight.Bold
                )

                HorizontalDivider()

                NavigationDrawerItem(
                    label = {
                        Text("Profile & Settings")
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
                            contentDescription = null
                        )
                    }
                )

                NavigationDrawerItem(
                    label = {
                        Text("API Configuration")
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
                            contentDescription = null
                        )
                    }
                )

                NavigationDrawerItem(
                    label = {
                        Text("Realtime Connection")
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
                            contentDescription = null
                        )
                    }
                )
            }
        }

    ) {

        Scaffold(

            bottomBar = {

                NavigationBar {

                    val navItems = listOf(
                        "Home",
                        "Chart",
                        "Trade",
                        "Analysis",
                        "News"
                    )

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

                            onClick = {
                                selectedTab = index
                            },

                            icon = {
                                Icon(
                                    icons[index],
                                    contentDescription = item
                                )
                            },

                            label = {
                                Text(item)
                            }
                        )
                    }
                }
            }

        ) { paddingValues ->

            Box(
                modifier = Modifier.padding(
                    paddingValues
                )
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
                        com.practice.cryptoapp.ui.TradeScreen()
                    }

                    else -> {

                        PlaceholderScreen(
                            title = "Screen Under Construction"
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
        modifier = Modifier.fillMaxSize(),
        contentAlignment = Alignment.Center
    ) {

        Text(
            text = title,
            fontSize = 18.sp
        )
    }
}

@Composable
fun HomeScreen(
    onMenuClick: () -> Unit
) {

    val scope = rememberCoroutineScope()

    var searchQuery by remember {
        mutableStateOf("")
    }

    var filterType by remember {
        mutableStateOf("ALL")
    }

    var visibleCount by remember {
        mutableIntStateOf(20)
    }

    var demoBalance by remember {
        mutableDoubleStateOf(10000.0)
    }

    var allCoins by remember {
        mutableStateOf<List<CryptoCoin>>(
            emptyList()
        )
    }

    var connectionState by remember {
        mutableStateOf("Connecting...")
    }

    var errorText by remember {
        mutableStateOf<String?>(null)
    }

    DisposableEffect(Unit) {

        val client = OkHttpClient.Builder()
            .build()

        var webSocket: WebSocket? = null

        var closed = false

        fun connectWebSocket() {

            if (closed) {
                return
            }

            val request = Request.Builder()

                /*
                 * IMPORTANT:
                 *
                 * Market data comes ONLY from
                 * Binance WebSocket.
                 *
                 * NO REST API is used here.
                 */

                .url(
                    "wss://stream.binance.com:9443/ws/!ticker@arr"
                )

                .build()

            webSocket = client.newWebSocket(

                request,

                object : WebSocketListener() {

                    override fun onOpen(
                        webSocket: WebSocket,
                        response: Response
                    ) {

                        scope.launch(
                            Dispatchers.Main
                        ) {

                            connectionState = "LIVE"

                            errorText = null
                        }
                    }

                    override fun onMessage(
                        webSocket: WebSocket,
                        text: String
                    ) {

                        try {

                            val jsonArray =
                                JSONArray(text)

                            /*
                             * Existing coins ko map mein rakhein
                             */
                            val currentCoins =
                                allCoins.associateBy {
                                    it.symbol
                                }.toMutableMap()

                            /*
                             * Binance !ticker@arr
                             * complete ticker array bhejta hai.
                             */
                            for (
                                i in 0 until jsonArray.length()
                            ) {

                                val obj =
                                    jsonArray.getJSONObject(i)

                                val fullSymbol =
                                    obj.optString("s")

                                /*
                                 * Sirf USDT markets.
                                 */
                                if (
                                    !fullSymbol.endsWith("USDT")
                                ) {
                                    continue
                                }

                                val symbol =
                                    fullSymbol.removeSuffix(
                                        "USDT"
                                    )

                                val price =
                                    obj.optString("c")
                                        .toDoubleOrNull()
                                        ?: continue

                                val change24h =
                                    obj.optString("P")
                                        .toDoubleOrNull()
                                        ?: 0.0

                                val volume =
                                    obj.optString("q")
                                        .toDoubleOrNull()
                                        ?: 0.0

                                val existing =
                                    currentCoins[symbol]

                                if (existing == null) {

                                    /*
                                     * Pehli baar coin WSS
                                     * se mila.
                                     */
                                    currentCoins[symbol] =
                                        CryptoCoin(
                                            symbol = symbol,
                                            initialPrice = price,
                                            initialChange24h =
                                                change24h,
                                            initialVolume =
                                                volume
                                        )

                                } else {

                                    /*
                                     * LIVE UPDATE
                                     */
                                    existing.price =
                                        price

                                    existing.change24h =
                                        change24h

                                    existing.volume =
                                        volume
                                }
                            }

                            /*
                             * Volume ke hisaab se rank.
                             */
                            val sortedCoins =
                                currentCoins.values
                                    .sortedByDescending {
                                        it.volume
                                    }
                                    .onEachIndexed {
                                            index,
                                            coin ->

                                        coin.rank =
                                            index + 1
                                    }

                            scope.launch(
                                Dispatchers.Main
                            ) {

                                allCoins =
                                    sortedCoins
                            }

                        } catch (e: Exception) {

                            scope.launch(
                                Dispatchers.Main
                            ) {

                                errorText =
                                    "WebSocket data error"
                            }
                        }
                    }

                    override fun onClosing(
                        webSocket: WebSocket,
                        code: Int,
                        reason: String
                    ) {

                        webSocket.close(
                            code,
                            reason
                        )

                        scope.launch(
                            Dispatchers.Main
                        ) {

                            connectionState =
                                "Reconnecting..."
                        }
                    }

                    override fun onFailure(
                        webSocket: WebSocket,
                        t: Throwable,
                        response: Response?
                    ) {

                        scope.launch(
                            Dispatchers.Main
                        ) {

                            connectionState =
                                "Reconnecting..."

                            errorText =
                                "WebSocket disconnected"
                        }

                        /*
                         * Automatic reconnect.
                         */
                        if (!closed) {

                            scope.launch {

                                delay(2000)

                                connectWebSocket()
                            }
                        }
                    }
                }
            )
        }

        /*
         * App screen open hote hi
         * WebSocket connection.
         */
        connectWebSocket()

        onDispose {

            closed = true

            webSocket?.close(
                1000,
                "Screen Closed"
            )

            client.dispatcher
                .executorService
                .shutdown()
        }
    }

    /*
     * Search + All/Gainer/Loser
     */
    val filteredCoins =
        remember(
            searchQuery,
            filterType,
            visibleCount,
            allCoins
        ) {

            var list =
                allCoins.filter {

                    it.symbol.contains(
                        searchQuery,
                        ignoreCase = true
                    )
                }

            list =
                when (filterType) {

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

            if (searchQuery.isBlank()) {

                list.take(
                    visibleCount
                )

            } else {

                list
            }
        }

    val timeframes = listOf(
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

    LazyColumn(

        modifier = Modifier
            .fillMaxSize()
            .padding(12.dp)
    ) {

        /*
         * HEADER
         */
        item {

            Row(

                modifier = Modifier
                    .fillMaxWidth()
                    .padding(
                        bottom = 12.dp
                    ),

                verticalAlignment =
                    Alignment.CenterVertically
            ) {

                Box(

                    modifier =
                        Modifier.clickable {
                            onMenuClick()
                        }
                ) {

                    Icon(

                        imageVector =
                            Icons.Default.Menu,

                        contentDescription =
                            "Menu",

                        modifier =
                            Modifier.size(32.dp)
                    )
                }

                Spacer(
                    modifier =
                        Modifier.width(8.dp)
                )

                Column(
                    modifier =
                        Modifier.weight(1f)
                ) {

                    Text(

                        text =
                            "Crypto Exchange Demo",

                        fontSize = 18.sp,

                        fontWeight =
                            FontWeight.Bold
                    )

                    Text(

                        text =
                            "Binance WebSocket",

                        fontSize = 11.sp,

                        color = Color.Gray
                    )
                }

                Text(

                    text =
                        "● $connectionState",

                    fontSize = 11.sp,

                    color =
                        if (
                            connectionState ==
                            "LIVE"
                        ) {
                            Color(0xFF008000)
                        } else {
                            Color(0xFFFF8C00)
                        }
                )
            }
        }

        /*
         * DEMO BALANCE
         */
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
                        Modifier.padding(12.dp)
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
                                text =
                                    "Demo Balance",

                                fontSize =
                                    12.sp,

                                color =
                                    Color.Gray
                            )

                            Text(

                                text =
                                    "$${
                                        String.format(
                                            Locale.US,
                                            "%.2f",
                                            demoBalance
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

                                demoBalance =
                                    10000.0
                            },

                            enabled =
                                demoBalance <= 100.0
                        ) {

                            Text("Reset")
                        }
                    }

                    Spacer(
                        modifier =
                            Modifier.height(10.dp)
                    )

                    Text(
                        text =
                            "P/L Statistics",

                        fontSize =
                            11.sp,

                        fontWeight =
                            FontWeight.SemiBold
                    )

                    LazyRow(

                        modifier =
                            Modifier.padding(
                                top = 4.dp
                            )
                    ) {

                        items(timeframes) { timeframe ->

                            Card(

                                modifier =
                                    Modifier.padding(
                                        end = 6.dp
                                    )
                            ) {

                                Column(

                                    modifier =
                                        Modifier.padding(
                                            7.dp
                                        ),

                                    horizontalAlignment =
                                        Alignment.CenterHorizontally
                                ) {

                                    Text(
                                        timeframe,
                                        fontSize = 10.sp,
                                        color = Color.Gray
                                    )

                                    Text(
                                        "0.0%",
                                        fontSize = 11.sp,
                                        fontWeight =
                                            FontWeight.Bold
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }

        /*
         * TEMPORARY MARKET OVERVIEW
         *
         * Is step mein hum sirf WSS verify
         * kar rahe hain.
         */
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
                        Modifier.padding(12.dp)
                ) {

                    Text(
                        text =
                            "Global Market Overview",

                        fontSize =
                            13.sp,

                        fontWeight =
                            FontWeight.Bold
                    )

                    Spacer(
                        modifier =
                            Modifier.height(6.dp)
                    )

                    Text(
                        text =
                            "Realtime Binance WebSocket",

                        fontSize =
                            11.sp,

                        color =
                            Color.Gray
                    )

                    Spacer(
                        modifier =
                            Modifier.height(4.dp)
                    )

                    Text(
                        text =
                            "${allCoins.size} USDT markets received",

                        fontSize =
                            12.sp
                    )
                }
            }
        }

        /*
         * SEARCH
         */
        item {

            OutlinedTextField(

                value =
                    searchQuery,

                onValueChange = {
                    searchQuery = it
                },

                modifier =
                    Modifier
                        .fillMaxWidth()
                        .padding(
                            vertical = 6.dp
                        ),

                placeholder = {
                    Text(
                        "Search Binance Coins..."
                    )
                },

                leadingIcon = {

                    Icon(
                        Icons.Default.Search,
                        contentDescription =
                            null
                    )
                },

                singleLine = true
            )

            Row(

                modifier =
                    Modifier
                        .fillMaxWidth()
                        .padding(
                            vertical = 4.dp
                        ),

                horizontalArrangement =
                    Arrangement.spacedBy(8.dp)
            ) {

                FilterChip(

                    selected =
                        filterType == "ALL",

                    onClick = {
                        filterType =
                            "ALL"
                    },

                    label = {
                        Text("All")
                    }
                )

                FilterChip(

                    selected =
                        filterType == "GAINER",

                    onClick = {
                        filterType =
                            "GAINER"
                    },

                    label = {
                        Text("Gainer")
                    }
                )

                FilterChip(

                    selected =
                        filterType == "LOSER",

                    onClick = {
                        filterType =
                            "LOSER"
                    },

                    label = {
                        Text("Loser")
                    }
                )
            }

            errorText?.let {

                Text(
                    text = it,
                    color = Color.Red,
                    fontSize = 11.sp
                )
            }
        }

        /*
         * LOADING
         */
        if (allCoins.isEmpty()) {

            item {

                Box(

                    modifier =
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
                            modifier =
                                Modifier.height(10.dp)
                        )

                        Text(
                            "Waiting for Binance WebSocket...",
                            fontSize = 12.sp,
                            color = Color.Gray
                        )
                    }
                }
            }

        } else {

            /*
             * COIN LIST
             */
            itemsIndexed(

                items =
                    filteredCoins,

                key = { _, coin ->
                    coin.symbol
                }

            ) { _, coin ->

                CryptoRow(
                    coin = coin
                )

                HorizontalDivider(
                    thickness =
                        0.5.dp
                )
            }

            /*
             * PAGINATION
             */
            if (searchQuery.isBlank()) {

                item {

                    Row(

                        modifier =
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
                                        visibleCount - 50
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

                            text =
                                "Showing $visibleCount",

                            fontSize =
                                12.sp,

                            color =
                                Color.Gray
                        )

                        Button(

                            onClick = {

                                visibleCount += 50
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

        modifier =
            Modifier
                .fillMaxWidth()
                .padding(
                    vertical = 8.dp
                ),

        verticalAlignment =
            Alignment.CenterVertically
    ) {

        Text(

            text =
                "#${coin.rank}",

            fontSize =
                11.sp,

            color =
                Color.Gray,

            modifier =
                Modifier.width(38.dp)
        )

        /*
         * Temporary logo placeholder.
         *
         * CoinGecko logos next step mein
         * properly connect karenge.
         */
        Box(

            modifier =
                Modifier
                    .size(30.dp)
                    .clip(CircleShape),

            contentAlignment =
                Alignment.Center
        ) {

            Text(

                text =
                    coin.symbol
                        .take(1),

                fontWeight =
                    FontWeight.Bold,

                fontSize =
                    13.sp
            )
        }

        Spacer(
            modifier =
                Modifier.width(8.dp)
        )

        Column(

            modifier =
                Modifier.weight(1.2f)
        ) {

            Text(

                text =
                    "${coin.symbol}/USDT",

                fontWeight =
                    FontWeight.Bold,

                fontSize =
                    14.sp
            )

            Text(

                text =
                    "Vol: $${
                        String.format(
                            Locale.US,
                            "%.0f",
                            coin.volume / 1_000_000
                        )
                    }M",

                fontSize =
                    10.sp,

                color =
                    Color.Gray
            )
        }

        Column(

            horizontalAlignment =
                Alignment.End,

            modifier =
                Modifier.weight(1.5f)
        ) {

            Text(

                text =
                    "$${
                        String.format(
                            Locale.US,
                            "%.8f",
                            coin.price
                        )
                            .trimEnd('0')
                            .trimEnd('.')
                    }",

                fontWeight =
                    FontWeight.Bold,

                fontSize =
                    13.sp
            )

            val positive =
                coin.change24h >= 0

            Text(

                text =
                    "${
                        if (positive) "+"
                        else ""
                    }${
                        String.format(
                            Locale.US,
                            "%.2f",
                            coin.change24h
                        )
                    }%",

                fontSize =
                    11.sp,

                color =
                    if (positive) {
                        Color(0xFF008000)
                    } else {
                        Color.Red
                    }
            )
        }
    }
}

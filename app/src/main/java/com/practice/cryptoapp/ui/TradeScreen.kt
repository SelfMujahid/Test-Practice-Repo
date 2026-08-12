package com.practice.cryptoapp.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.viewmodel.compose.viewModel
import coil.compose.AsyncImage
import com.practice.cryptoapp.*
import java.util.Locale

private val Green = Color(0xFF16A34A)
private val Red = Color(0xFFDC2626)
private val ScreenBackground = Color(0xFFF5F5F7)
private val CardBackground = Color.White
private val RoundedCorner = RoundedCornerShape(50.dp)

@Composable
fun TradeScreen(
    vm: TradeViewModel = viewModel(),
    cryptoVm: CryptoViewModel
) {
    val coins by cryptoVm.coins.collectAsState()
    val symbol by vm.symbol.collectAsState()

    val allStaticCoins = remember {
        cryptoVm.manager.getAllSymbolsData().map { seed ->
            val base = seed.symbol.removeSuffix("USDT")
            val meta = cryptoVm.manager.getCoinMeta(base)
            CryptoCoin(
                symbol = base,
                name = meta?.name ?: base,
                logo = meta?.logo ?: "",
                price = seed.lastPrice,
                change24h = seed.change24h,
                volume24h = seed.volume24h,
                marketCap = meta?.marketCap ?: 0.0,
                rank = meta?.marketCapRank ?: Int.MAX_VALUE
            )
        }
    }

    val selectedCoin = remember(coins, allStaticCoins, symbol) {
        coins.find { (it.symbol + "USDT").equals(symbol, ignoreCase = true) }
            ?: allStaticCoins.find { (it.symbol + "USDT").equals(symbol, ignoreCase = true) }
            ?: CryptoCoin(symbol = symbol.removeSuffix("USDT"), name = symbol, logo = "", price = 0.0, change24h = 0.0, volume24h = 0.0, marketCap = 0.0, rank = Int.MAX_VALUE)
    }
    val currentPrice = selectedCoin.price
    val change24h = selectedCoin.change24h
    val coinName = selectedCoin.name
    val coinLogo = selectedCoin.logo

    val mode by vm.mode.collectAsState()
    val orderType by vm.orderType.collectAsState()
    val side by vm.side.collectAsState()
    val leverage by vm.leverage.collectAsState()
    val marginType by vm.marginType.collectAsState()
    val limitPrice by vm.limitPrice.collectAsState()
    val balance by vm.balance.collectAsState()
    val bids by vm.bids.collectAsState()
    val asks by vm.asks.collectAsState()
    val position by vm.position.collectAsState()
    val pendingOrders by vm.pendingOrders.collectAsState()
    val history by vm.history.collectAsState()
    val message by vm.message.collectAsState()

    val pnl = vm.unrealizedPnl(currentPrice)

    var showPairMenu by rememberSaveable { mutableStateOf(false) }
    var showLeverage by rememberSaveable { mutableStateOf(false) }
    var selectedBottomTab by rememberSaveable { mutableStateOf(TradeBottomTab.POSITION) }
    var pairSearch by rememberSaveable { mutableStateOf("") }

    val defaultList = remember(coins, allStaticCoins) {
        val liveSymbols = coins.map { it.symbol }.toSet()
        val livePart = coins.sortedWith(compareBy(CryptoCoin::rank).thenBy(CryptoCoin::symbol))
        val restPart = allStaticCoins.filter { it.symbol !in liveSymbols }
            .map { it.copy(price = 0.0, change24h = 0.0, logo = "", marketCap = 0.0, rank = Int.MAX_VALUE) }
        livePart + restPart
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(ScreenBackground)
            .verticalScroll(rememberScrollState())
    ) {
        ModeBar(mode = mode, onSelect = vm::setMode)

        MarketHeader(
            symbol = symbol,
            name = coinName,
            logo = coinLogo,
            price = currentPrice,
            change24h = change24h,
            onPairClick = { showPairMenu = true }
        )

        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 7.dp),
            horizontalArrangement = Arrangement.spacedBy(7.dp)
        ) {
            OrderPanel(
                modifier = Modifier.weight(1f),
                balance = balance,
                price = currentPrice,
                side = side,
                orderType = orderType,
                leverage = leverage,
                limitPrice = limitPrice,
                position = position,
                onSideChange = vm::setSide,
                onOrderTypeChange = vm::setOrderType,
                onLeverageClick = { showLeverage = true },
                onLimitPriceChange = vm::setLimitPrice,
                onOpen = { qty -> vm.openOrder(currentPrice, qty) },
                coinSymbol = selectedCoin.symbol,
                marginType = marginType,
                onMarginTypeChange = vm::setMarginType
            )
            OrderBookPanel(
                modifier = Modifier.weight(1f),
                symbol = symbol,
                currentPrice = currentPrice,
                bids = bids,
                asks = asks
            )
        }

        BottomTradeTabs(
            selected = selectedBottomTab,
            onSelected = { selectedBottomTab = it }
        )

        BottomTradeContent(
            selected = selectedBottomTab,
            position = position,
            pendingOrders = pendingOrders,
            history = history,
            currentPrice = currentPrice,
            pnl = pnl,
            onClose = { vm.closePosition(currentPrice) },
            onCancelPending = vm::cancelPending
        )

        if (message.isNotBlank()) {
            Text(
                text = message,
                modifier = Modifier.fillMaxWidth().padding(horizontal = 10.dp, vertical = 4.dp),
                fontSize = 10.sp,
                color = PurpleMimosa
            )
        }
    }

    // --- Pair selector dialog ---
    if (showPairMenu) {
        val isSearchActive = pairSearch.isNotBlank()
        val displayCoins = remember(defaultList, allStaticCoins, pairSearch, isSearchActive) {
            if (isSearchActive) {
                allStaticCoins.filter {
                    it.symbol.contains(pairSearch, true) ||
                    it.name.contains(pairSearch, true) ||
                    (it.symbol + "USDT").contains(pairSearch, true)
                }
            } else defaultList
        }

        AlertDialog(
            onDismissRequest = { showPairMenu = false },
            title = { Text("Select Futures Pair", fontSize = 14.sp) },
            text = {
                Column {
                    OutlinedTextField(
                        value = pairSearch,
                        onValueChange = { pairSearch = it.uppercase(Locale.US) },
                        modifier = Modifier.fillMaxWidth(),
                        singleLine = true,
                        shape = RoundedCornerShape(50),
                        placeholder = { Text("Search...", fontSize = 12.sp) }
                    )
                    Spacer(Modifier.height(8.dp))
                    LazyColumn(modifier = Modifier.heightIn(max = 400.dp)) {
                        items(displayCoins, key = { it.symbol }) { coin ->
                            val isLive = coins.any { it.symbol == coin.symbol }
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clickable {
                                        vm.setSymbol(coin.symbol + "USDT")
                                        pairSearch = ""
                                        showPairMenu = false
                                    }
                                    .padding(vertical = 6.dp, horizontal = 4.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                if (coin.logo.isNotBlank() && (isLive || isSearchActive)) {
                                    AsyncImage(
                                        model = coin.logo,
                                        contentDescription = coin.name,
                                        modifier = Modifier.size(24.dp).clip(CircleShape),
                                        contentScale = ContentScale.Crop
                                    )
                                } else {
                                    Box(
                                        modifier = Modifier.size(24.dp).clip(CircleShape).background(Color.LightGray),
                                        contentAlignment = Alignment.Center
                                    ) { Text(coin.symbol.take(1), fontSize = 11.sp, fontWeight = FontWeight.Bold) }
                                }

                                Spacer(Modifier.width(6.dp))
                                Column(modifier = Modifier.weight(1f)) {
                                    Text(coin.name.ifBlank { coin.symbol }, fontWeight = FontWeight.Bold, fontSize = 11.sp, maxLines = 1)
                                    Text(coin.symbol + "USDT", fontSize = 9.sp, color = Color.Gray)
                                    if ((isLive || isSearchActive) && coin.rank != Int.MAX_VALUE) {
                                        Text("Rank #${coin.rank}", fontSize = 8.sp, color = Color.Gray)
                                    }
                                }
                                if (isLive || isSearchActive) {
                                    Column(horizontalAlignment = Alignment.End) {
                                        Text(formatPrice(coin.price), fontWeight = FontWeight.Bold, fontSize = 11.sp)
                                        Text(formatPct(coin.change24h), fontSize = 10.sp, fontWeight = FontWeight.Bold,
                                            color = if (coin.change24h >= 0) Green else Red)
                                    }
                                }
                            }
                            HorizontalDivider()
                        }
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = {
                    if (pairSearch.isNotBlank()) vm.setSymbol(pairSearch)
                    pairSearch = ""
                    showPairMenu = false
                }) { Text("Apply", color = PurpleMimosa, fontSize = 12.sp) }
            }
        )
    }

    // --- Leverage dialog ---
    if (showLeverage) {
        AlertDialog(
            onDismissRequest = { showLeverage = false },
            title = { Text("Leverage", fontSize = 14.sp) },
            text = {
                Column {
                    listOf(1, 2, 3, 5, 10, 20, 25, 50).forEach { value ->
                        Row(
                            modifier = Modifier.fillMaxWidth().clickable {
                                vm.setLeverage(value)
                                showLeverage = false
                            }.padding(10.dp),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text("${value}x", fontSize = 12.sp)
                            if (value == leverage) Icon(Icons.Default.Check, null, tint = PurpleMimosa, modifier = Modifier.size(16.dp))
                        }
                    }
                }
            },
            confirmButton = {}
        )
    }
}

// ============================================================
// MODE BAR
// ============================================================
@Composable
private fun ModeBar(mode: TradeMode, onSelect: (TradeMode) -> Unit) {
    Row(
        modifier = Modifier.fillMaxWidth().background(CardBackground).padding(horizontal = 8.dp, vertical = 6.dp),
        horizontalArrangement = Arrangement.spacedBy(6.dp)
    ) {
        ModeButton("Spot", mode == TradeMode.SPOT) { onSelect(TradeMode.SPOT) }
        ModeButton("Future", mode == TradeMode.FUTURES) { onSelect(TradeMode.FUTURES) }
        ModeButton("Bot", mode == TradeMode.BOT) { onSelect(TradeMode.BOT) }
    }
}

@Composable
private fun RowScope.ModeButton(title: String, selected: Boolean, onClick: () -> Unit) {
    Button(
        onClick = onClick,
        modifier = Modifier.weight(1f),
        shape = RoundedCorner,
        contentPadding = PaddingValues(vertical = 4.dp),
        colors = ButtonDefaults.buttonColors(
            containerColor = if (selected) PurpleMimosa else Color(0xFFE9E9ED),
            contentColor = if (selected) Color.White else Color.DarkGray
        )
    ) { Text(text = title, fontSize = 9.sp) }
}

// ============================================================
// MARKET HEADER
// ============================================================
@Composable
private fun MarketHeader(
    symbol: String,
    name: String,
    logo: String,
    price: Double,
    change24h: Double,
    onPairClick: () -> Unit
) {
    Card(
        modifier = Modifier.fillMaxWidth().padding(horizontal = 7.dp, vertical = 4.dp),
        shape = RoundedCornerShape(10.dp),
        colors = CardDefaults.cardColors(containerColor = CardBackground)
    ) {
        Row(modifier = Modifier.fillMaxWidth().padding(8.dp), verticalAlignment = Alignment.CenterVertically) {
            if (logo.isNotBlank()) {
                AsyncImage(model = logo, contentDescription = name, modifier = Modifier.size(24.dp).clip(CircleShape), contentScale = ContentScale.Crop)
            } else {
                Box(Modifier.size(24.dp).clip(CircleShape), contentAlignment = Alignment.Center) {
                    Text(symbol.take(1), fontWeight = FontWeight.Bold, fontSize = 10.sp)
                }
            }
            Spacer(Modifier.width(8.dp))
            Column(modifier = Modifier.weight(1f)) {
                TextButton(onClick = onPairClick, contentPadding = PaddingValues(0.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(symbol, fontSize = 13.sp, fontWeight = FontWeight.Bold, color = Color.Black)
                        Icon(Icons.Default.KeyboardArrowDown, null, modifier = Modifier.size(16.dp))
                    }
                }
                Text(name, fontSize = 8.sp, color = Color.Gray)
            }
            Column(horizontalAlignment = Alignment.End) {
                Text(if (price > 0) formatPrice(price) else "—", fontWeight = FontWeight.Bold, fontSize = 12.sp)
                Text(formatPct(change24h), fontSize = 10.sp, fontWeight = FontWeight.Bold,
                    color = if (change24h >= 0) Green else Red)
            }
        }
    }
}

// ============================================================
// ORDER PANEL (fully local amount, cross/isolated functional)
// ============================================================
@Composable
private fun OrderPanel(
    modifier: Modifier,
    balance: Double,
    price: Double,
    side: PositionSide,
    orderType: TradeOrderType,
    leverage: Int,
    limitPrice: String,
    position: DemoPosition?,
    onSideChange: (PositionSide) -> Unit,
    onOrderTypeChange: (TradeOrderType) -> Unit,
    onLeverageClick: () -> Unit,
    onLimitPriceChange: (String) -> Unit,
    onOpen: (qty: Double) -> Unit,
    coinSymbol: String,
    marginType: MarginType,
    onMarginTypeChange: (MarginType) -> Unit
) {
    var showOrderTypeMenu by remember { mutableStateOf(false) }
    var showCrossMenu by remember { mutableStateOf(false) }
    var amountUnit by remember { mutableStateOf(true) } // true = coin, false = USDT
    var coinAmount by remember { mutableStateOf("0.001") }
    var usdtAmount by remember { mutableStateOf("") }

    // Percent buttons handler – local calculation
    fun setPercent(percent: Int) {
        val safePercent = percent.coerceIn(0, 100)
        val marginToUse = balance * safePercent / 100.0
        val lev = leverage.coerceAtLeast(1)
        if (price > 0.0) {
            val qty = (marginToUse * lev) / price
            val qtyStr = String.format(Locale.US, "%.6f", qty)
            if (amountUnit) {
                coinAmount = qtyStr
                usdtAmount = String.format(Locale.US, "%.2f", qty * price)
            } else {
                usdtAmount = String.format(Locale.US, "%.2f", marginToUse * lev)
                coinAmount = qtyStr
            }
        }
    }

    val displayAmount = if (amountUnit) coinAmount else usdtAmount
    val conversionText = remember(displayAmount, amountUnit, price) {
        if (amountUnit) {
            val usdt = if (price > 0) (displayAmount.toDoubleOrNull() ?: 0.0) * price else 0.0
            "≈ ${"%,.2f".format(usdt)} USDT"
        } else {
            val coin = if (price > 0) (displayAmount.toDoubleOrNull() ?: 0.0) / price else 0.0
            "≈ ${"%.6f".format(coin)} $coinSymbol"
        }
    }

    Card(
        modifier = modifier,
        shape = RoundedCornerShape(10.dp),
        colors = CardDefaults.cardColors(containerColor = CardBackground)
    ) {
        Column(modifier = Modifier.fillMaxWidth().padding(8.dp)) {
            Text("Place Order", fontSize = 11.sp, fontWeight = FontWeight.Bold)
            Spacer(Modifier.height(5.dp))

            // LONG / SHORT
            Row(modifier = Modifier.fillMaxWidth()) {
                SideButton("Long", side == PositionSide.LONG, Green) { onSideChange(PositionSide.LONG) }
                Spacer(Modifier.width(4.dp))
                SideButton("Short", side == PositionSide.SHORT, Red) { onSideChange(PositionSide.SHORT) }
            }

            Spacer(Modifier.height(5.dp))

            // Order type, Leverage, Cross (single row)
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                // Market/Limit dropdown
                Box(modifier = Modifier.weight(1f)) {
                    Button(
                        onClick = { showOrderTypeMenu = true },
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCorner,
                        colors = ButtonDefaults.buttonColors(
                            containerColor = PurpleMimosa,
                            contentColor = Color.White
                        ),
                        contentPadding = PaddingValues(vertical = 5.dp)
                    ) { Text(if (orderType == TradeOrderType.MARKET) "Market" else "Limit", fontSize = 8.sp) }

                    DropdownMenu(
                        expanded = showOrderTypeMenu,
                        onDismissRequest = { showOrderTypeMenu = false }
                    ) {
                        DropdownMenuItem(
                            text = { Text("Market") },
                            onClick = {
                                onOrderTypeChange(TradeOrderType.MARKET)
                                showOrderTypeMenu = false
                            }
                        )
                        DropdownMenuItem(
                            text = { Text("Limit") },
                            onClick = {
                                onOrderTypeChange(TradeOrderType.LIMIT)
                                showOrderTypeMenu = false
                            }
                        )
                    }
                }

                // Leverage button
                OutlinedButton(
                    onClick = onLeverageClick,
                    modifier = Modifier.weight(1f),
                    shape = RoundedCorner,
                    contentPadding = PaddingValues(vertical = 5.dp)
                ) { Text("${leverage}x", fontSize = 8.sp) }

                // Cross/Isolated dropdown
                Box(modifier = Modifier.weight(1f)) {
                    OutlinedButton(
                        onClick = { showCrossMenu = true },
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCorner,
                        contentPadding = PaddingValues(vertical = 5.dp)
                    ) {
                        Text(
                            text = if (marginType == MarginType.CROSS) "Cross" else "Isolated",
                            fontSize = 8.sp
                        )
                    }

                    DropdownMenu(
                        expanded = showCrossMenu,
                        onDismissRequest = { showCrossMenu = false }
                    ) {
                        DropdownMenuItem(
                            text = { Text("Cross") },
                            onClick = {
                                onMarginTypeChange(MarginType.CROSS)
                                showCrossMenu = false
                            }
                        )
                        DropdownMenuItem(
                            text = { Text("Isolated") },
                            onClick = {
                                onMarginTypeChange(MarginType.ISOLATED)
                                showCrossMenu = false
                            }
                        )
                    }
                }
            }

            Spacer(Modifier.height(5.dp))

            // Limit price input (if limit)
            if (orderType == TradeOrderType.LIMIT) {
                OutlinedTextField(
                    value = limitPrice,
                    onValueChange = onLimitPriceChange,
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true,
                    shape = RoundedCorner,
                    label = { Text("Price", fontSize = 9.sp) },
                    textStyle = LocalTextStyle.current.copy(fontSize = 10.sp)
                )
                Spacer(Modifier.height(4.dp))
            }

            // Amount input (fully local, no slider)
            OutlinedTextField(
                value = displayAmount,
                onValueChange = { input ->
                    if (input.isEmpty() || input.matches(Regex("^\\d*(\\.\\d*)?$"))) {
                        if (amountUnit) coinAmount = input else usdtAmount = input
                    }
                },
                modifier = Modifier.fillMaxWidth().height(34.dp),
                singleLine = true,
                shape = RoundedCorner,
                label = { Text("Amount", fontSize = 9.sp) },
                trailingIcon = {
                    Text(
                        text = if (amountUnit) coinSymbol else "USDT",
                        fontSize = 10.sp,
                        color = Color.Gray,
                        modifier = Modifier.clickable { amountUnit = !amountUnit }
                    )
                },
                textStyle = LocalTextStyle.current.copy(fontSize = 10.sp)
            )

            // Conversion line
            Text(
                text = conversionText,
                fontSize = 8.sp,
                color = Color.Gray,
                modifier = Modifier.padding(start = 4.dp)
            )
            Spacer(Modifier.height(4.dp))

            // Percent buttons
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                listOf(25, 50, 75, 100).forEach { p ->
                    TextButton(onClick = { setPercent(p) }, contentPadding = PaddingValues(horizontal = 4.dp)) {
                        Text("$p%", fontSize = 7.sp, color = PurpleMimosa)
                    }
                }
            }

            Spacer(Modifier.height(3.dp))

            // Size & Margin (calculated locally)
            val qty = (displayAmount.toDoubleOrNull() ?: 0.0).let {
                if (amountUnit) it else if (price > 0) it / price else 0.0
            }
            val executionPrice = if (orderType == TradeOrderType.LIMIT) limitPrice.toDoubleOrNull() ?: price else price
            val notional = executionPrice * qty
            val margin = if (leverage > 0) notional / leverage else notional

            SmallStat("Size", "%,.2f".format(notional) + " USDT")
            Spacer(Modifier.height(2.dp))
            SmallStat("Margin", "%,.2f".format(margin) + " USDT")

            Spacer(Modifier.height(6.dp))

            // Available balance
            Text(
                text = "Available: ${"%,.2f".format(balance)} USDT",
                fontSize = 9.sp,
                color = Color.Gray
            )
            Spacer(Modifier.height(4.dp))

            // Open button
            Button(
                onClick = {
                    if (qty > 0) onOpen(qty)
                },
                modifier = Modifier.fillMaxWidth(),
                enabled = price > 0.0 && qty > 0.0 && position == null,
                shape = RoundedCorner,
                colors = ButtonDefaults.buttonColors(containerColor = if (side == PositionSide.LONG) Green else Red),
                contentPadding = PaddingValues(vertical = 8.dp)
            ) {
                Text(if (side == PositionSide.LONG) "OPEN LONG" else "OPEN SHORT", fontSize = 9.sp)
            }
        }
    }
}

// ============================================================
// ORDER BOOK PANEL (unchanged)
// ============================================================
@Composable
private fun OrderBookPanel(
    modifier: Modifier,
    symbol: String,
    currentPrice: Double,
    bids: List<OrderBookLevel>,
    asks: List<OrderBookLevel>
) {
    Card(
        modifier = modifier,
        shape = RoundedCornerShape(10.dp),
        colors = CardDefaults.cardColors(containerColor = CardBackground)
    ) {
        Column(modifier = Modifier.fillMaxWidth().padding(6.dp)) {
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                Text("Order Book", fontSize = 10.sp, fontWeight = FontWeight.Bold)
                Text(symbol, fontSize = 8.sp, color = Color.Gray)
            }
            Spacer(Modifier.height(4.dp))
            Row(modifier = Modifier.fillMaxWidth()) {
                Text("USDT", modifier = Modifier.weight(1f), fontSize = 7.sp, color = Color.Gray)
                Text("Price", modifier = Modifier.weight(1f), textAlign = TextAlign.Center, fontSize = 7.sp, color = Color.Gray)
                Text("Amount", modifier = Modifier.weight(1f), textAlign = TextAlign.End, fontSize = 7.sp, color = Color.Gray)
            }
            Spacer(Modifier.height(2.dp))
            asks.take(6).asReversed().forEach { level -> OrderBookLine(level, Red) }
            Box(
                modifier = Modifier.fillMaxWidth().background(Color(0xFFF3EDF7), RoundedCornerShape(5.dp)).padding(vertical = 4.dp)
            ) {
                Text(
                    text = if (currentPrice > 0) formatPrice(currentPrice) else "—",
                    modifier = Modifier.fillMaxWidth(),
                    textAlign = TextAlign.Center,
                    fontSize = 10.sp,
                    fontWeight = FontWeight.Bold,
                    color = PurpleMimosa
                )
            }
            Spacer(Modifier.height(2.dp))
            bids.take(6).forEach { level -> OrderBookLine(level, Green) }
        }
    }
}

@Composable
private fun OrderBookLine(level: OrderBookLevel, color: Color) {
    val usdtValue = level.price * level.quantity
    Row(modifier = Modifier.fillMaxWidth().padding(vertical = 1.dp)) {
        Text(String.format(Locale.US, "%.2f", usdtValue), modifier = Modifier.weight(1f), fontSize = 7.sp, color = Color.Gray)
        Text(formatPrice(level.price), modifier = Modifier.weight(1f), textAlign = TextAlign.Center, fontSize = 7.sp, color = color)
        Text(String.format(Locale.US, "%.4f", level.quantity), modifier = Modifier.weight(1f), textAlign = TextAlign.End, fontSize = 7.sp, color = Color.Gray)
    }
}

// ============================================================
// BOTTOM TABS & CONTENT
// ============================================================
private enum class TradeBottomTab { POSITION, PENDING, HISTORY }

@Composable
private fun BottomTradeTabs(selected: TradeBottomTab, onSelected: (TradeBottomTab) -> Unit) {
    Row(
        modifier = Modifier.fillMaxWidth().background(CardBackground).padding(horizontal = 7.dp, vertical = 4.dp),
        horizontalArrangement = Arrangement.spacedBy(4.dp)
    ) {
        BottomTabButton("Active Position", selected == TradeBottomTab.POSITION) { onSelected(TradeBottomTab.POSITION) }
        BottomTabButton("Pending", selected == TradeBottomTab.PENDING) { onSelected(TradeBottomTab.PENDING) }
        BottomTabButton("History", selected == TradeBottomTab.HISTORY) { onSelected(TradeBottomTab.HISTORY) }
    }
}

@Composable
private fun RowScope.BottomTabButton(title: String, selected: Boolean, onClick: () -> Unit) {
    TextButton(onClick = onClick, modifier = Modifier.weight(1f)) {
        Text(title, fontSize = 8.sp, fontWeight = if (selected) FontWeight.Bold else FontWeight.Normal, color = if (selected) PurpleMimosa else Color.Gray)
    }
}

@Composable
private fun BottomTradeContent(
    selected: TradeBottomTab,
    position: DemoPosition?,
    pendingOrders: List<PendingOrder>,
    history: List<TradeHistory>,
    currentPrice: Double,
    pnl: Double,
    onClose: () -> Unit,
    onCancelPending: (Long) -> Unit
) {
    when (selected) {
        TradeBottomTab.POSITION -> ActivePositionContent(position, currentPrice, pnl, onClose)
        TradeBottomTab.PENDING -> PendingContent(pendingOrders, onCancelPending)
        TradeBottomTab.HISTORY -> HistoryContent(history)
    }
}

// ============================================================
// ACTIVE POSITION, PENDING, HISTORY (unchanged)
// ============================================================
@Composable
private fun ActivePositionContent(position: DemoPosition?, currentPrice: Double, pnl: Double, onClose: () -> Unit) {
    Card(
        modifier = Modifier.fillMaxWidth().padding(horizontal = 7.dp, vertical = 3.dp),
        shape = RoundedCornerShape(9.dp),
        colors = CardDefaults.cardColors(containerColor = CardBackground)
    ) {
        if (position == null) {
            Text("No active position", modifier = Modifier.fillMaxWidth().padding(10.dp), textAlign = TextAlign.Center, fontSize = 9.sp, color = Color.Gray)
        } else {
            Column(modifier = Modifier.padding(8.dp)) {
                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                    Text(if (position.side == PositionSide.LONG) "LONG" else "SHORT", fontWeight = FontWeight.Bold, color = if (position.side == PositionSide.LONG) Green else Red, fontSize = 10.sp)
                    Text("${position.symbol} ${position.leverage}x", fontSize = 8.sp)
                }
                Spacer(Modifier.height(4.dp))
                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                    BottomValue("Entry", formatPrice(position.entryPrice))
                    BottomValue("Mark", formatPrice(currentPrice))
                    BottomValue("Qty", "%.6f".format(position.quantity))
                    BottomValue("PnL", TradeViewModel.formatMoney(pnl), if (pnl >= 0) Green else Red)
                }
                Spacer(Modifier.height(5.dp))
                Button(onClick = onClose, modifier = Modifier.fillMaxWidth(), colors = ButtonDefaults.buttonColors(containerColor = Red), shape = RoundedCorner, contentPadding = PaddingValues(vertical = 6.dp)) {
                    Text("CLOSE POSITION", fontSize = 8.sp)
                }
            }
        }
    }
}

@Composable
private fun PendingContent(orders: List<PendingOrder>, onCancel: (Long) -> Unit) {
    Card(
        modifier = Modifier.fillMaxWidth().padding(horizontal = 7.dp, vertical = 3.dp),
        shape = RoundedCornerShape(9.dp),
        colors = CardDefaults.cardColors(containerColor = CardBackground)
    ) {
        if (orders.isEmpty()) {
            Text("No pending orders", modifier = Modifier.fillMaxWidth().padding(10.dp), textAlign = TextAlign.Center, fontSize = 9.sp, color = Color.Gray)
        } else {
            Column(modifier = Modifier.padding(6.dp)) {
                orders.forEach { order ->
                    PendingRow(order) { onCancel(order.id) }
                    HorizontalDivider()
                }
            }
        }
    }
}

@Composable
private fun PendingRow(order: PendingOrder, onCancel: () -> Unit) {
    Row(modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp), verticalAlignment = Alignment.CenterVertically) {
        Column(modifier = Modifier.weight(1f)) {
            Text("${order.symbol} ${if (order.side == PositionSide.LONG) "LONG" else "SHORT"}", fontSize = 8.sp, fontWeight = FontWeight.Bold)
            Text("Limit ${formatPrice(order.price)} • ${"%.6f".format(order.quantity)}", fontSize = 7.sp, color = Color.Gray)
        }
        Text("${order.leverage}x", fontSize = 8.sp)
        TextButton(onClick = onCancel) { Text("Cancel", fontSize = 7.sp, color = Red) }
    }
}

@Composable
private fun HistoryContent(history: List<TradeHistory>) {
    Card(
        modifier = Modifier.fillMaxWidth().padding(horizontal = 7.dp, vertical = 3.dp),
        shape = RoundedCornerShape(9.dp),
        colors = CardDefaults.cardColors(containerColor = CardBackground)
    ) {
        if (history.isEmpty()) {
            Text("No trading history", modifier = Modifier.fillMaxWidth().padding(10.dp), textAlign = TextAlign.Center, fontSize = 9.sp, color = Color.Gray)
        } else {
            LazyColumn(modifier = Modifier.heightIn(max = 140.dp)) {
                items(items = history, key = { it.id }) { item ->
                    HistoryRow(item)
                    HorizontalDivider()
                }
            }
        }
    }
}

@Composable
private fun HistoryRow(item: TradeHistory) {
    Row(modifier = Modifier.fillMaxWidth().padding(horizontal = 7.dp, vertical = 5.dp), verticalAlignment = Alignment.CenterVertically) {
        Column(modifier = Modifier.weight(1f)) {
            Text("${item.symbol} ${if (item.side == PositionSide.LONG) "LONG" else "SHORT"}", fontSize = 8.sp, fontWeight = FontWeight.Bold)
            Text("${formatPrice(item.entryPrice)} → ${formatPrice(item.exitPrice)}", fontSize = 7.sp, color = Color.Gray)
        }
        Text(TradeViewModel.formatMoney(item.pnl), fontSize = 9.sp, fontWeight = FontWeight.Bold, color = if (item.pnl >= 0) Green else Red)
    }
}

// ============================================================
// SMALL COMPONENTS
// ============================================================
@Composable
private fun RowScope.SideButton(text: String, selected: Boolean, color: Color, onClick: () -> Unit) {
    Button(
        onClick = onClick,
        modifier = Modifier.weight(1f),
        shape = RoundedCorner,
        colors = ButtonDefaults.buttonColors(
            containerColor = if (selected) color else Color(0xFFE7E7EA),
            contentColor = if (selected) Color.White else Color.DarkGray
        ),
        contentPadding = PaddingValues(vertical = 5.dp)
    ) { Text(text, fontSize = 8.sp) }
}

@Composable
private fun SmallOrderType(text: String, selected: Boolean, onClick: () -> Unit) {
    TextButton(onClick = onClick, contentPadding = PaddingValues(horizontal = 4.dp, vertical = 0.dp)) {
        Text(text, fontSize = 8.sp, fontWeight = if (selected) FontWeight.Bold else FontWeight.Normal, color = if (selected) PurpleMimosa else Color.Gray)
    }
}

@Composable
private fun SmallStat(label: String, value: String) {
    Column {
        Text(label, fontSize = 7.sp, color = Color.Gray)
        Text(value, fontSize = 9.sp, fontWeight = FontWeight.Bold)
    }
}

@Composable
private fun BottomValue(label: String, value: String, color: Color = Color.Black) {
    Column {
        Text(label, fontSize = 7.sp, color = Color.Gray)
        Text(value, fontSize = 8.sp, fontWeight = FontWeight.Bold, color = color)
    }
}

// ============================================================
// FORMATTING
// ============================================================
private fun formatPrice(value: Double): String {
    if (value.isNaN() || value.isInfinite()) return "0"
    return if (value >= 1.0) "%,.2f".format(value) else "%.8f".format(value).trimEnd('0').trimEnd('.')
}
private fun formatPct(value: Double): String = "%+.2f%%".format(value)

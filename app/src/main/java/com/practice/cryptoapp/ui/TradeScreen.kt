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
private val RoundedCorner = RoundedCornerShape(12.dp)

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
    val quantity by vm.quantity.collectAsState()
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
                quantity = quantity,
                limitPrice = limitPrice,
                position = position,
                onSideChange = vm::setSide,
                onOrderTypeChange = vm::setOrderType,
                onLeverageClick = { showLeverage = true },
                onQuantityChange = vm::setQuantity,
                onPercent = { percent -> vm.setQuantityPercent(percent, currentPrice) },
                onLimitPriceChange = vm::setLimitPrice,
                onOpen = { vm.openOrder(currentPrice) }
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

    // --- Pair selector dialog (unchanged but rounded search field already 50) ---
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
            title = { Text("Select Futures Pair") },
            text = {
                Column {
                    OutlinedTextField(
                        value = pairSearch,
                        onValueChange = { pairSearch = it.uppercase(Locale.US) },
                        modifier = Modifier.fillMaxWidth(),
                        singleLine = true,
                        shape = RoundedCornerShape(50), // already round
                        placeholder = { Text("Search...") }
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
                                    .padding(vertical = 8.dp, horizontal = 4.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                if (coin.logo.isNotBlank() && (isLive || isSearchActive)) {
                                    AsyncImage(
                                        model = coin.logo,
                                        contentDescription = coin.name,
                                        modifier = Modifier.size(28.dp).clip(CircleShape),
                                        contentScale = ContentScale.Crop
                                    )
                                } else {
                                    Box(
                                        modifier = Modifier.size(28.dp).clip(CircleShape).background(Color.LightGray),
                                        contentAlignment = Alignment.Center
                                    ) { Text(coin.symbol.take(1), fontSize = 12.sp, fontWeight = FontWeight.Bold) }
                                }

                                Spacer(Modifier.width(8.dp))
                                Column(modifier = Modifier.weight(1f)) {
                                    Text(coin.name.ifBlank { coin.symbol }, fontWeight = FontWeight.Bold, fontSize = 13.sp, maxLines = 1)
                                    Text(coin.symbol + "USDT", fontSize = 10.sp, color = Color.Gray)
                                    if ((isLive || isSearchActive) && coin.rank != Int.MAX_VALUE) {
                                        Text("Rank #${coin.rank}", fontSize = 9.sp, color = Color.Gray)
                                    }
                                }
                                if (isLive || isSearchActive) {
                                    Column(horizontalAlignment = Alignment.End) {
                                        Text(formatPrice(coin.price), fontWeight = FontWeight.Bold, fontSize = 12.sp)
                                        Text(formatPct(coin.change24h), fontSize = 11.sp, fontWeight = FontWeight.Bold,
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
                }) { Text("Apply", color = PurpleMimosa) }
            }
        )
    }

    // --- Leverage dialog (unchanged) ---
    if (showLeverage) {
        AlertDialog(
            onDismissRequest = { showLeverage = false },
            title = { Text("Leverage") },
            text = {
                Column {
                    listOf(1, 2, 3, 5, 10, 20, 25, 50).forEach { value ->
                        Row(
                            modifier = Modifier.fillMaxWidth().clickable {
                                vm.setLeverage(value)
                                showLeverage = false
                            }.padding(12.dp),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text("${value}x")
                            if (value == leverage) Icon(Icons.Default.Check, null, tint = PurpleMimosa)
                        }
                    }
                }
            },
            confirmButton = {}
        )
    }
}

// ============================================================
// MODE BAR (rounded buttons)
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
        colors = ButtonDefaults.buttonColors(
            containerColor = if (selected) PurpleMimosa else Color(0xFFE9E9ED),
            contentColor = if (selected) Color.White else Color.DarkGray
        )
    ) { Text(text = title, fontSize = 11.sp) }
}

// ============================================================
// MARKET HEADER (unchanged)
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
        Row(modifier = Modifier.fillMaxWidth().padding(9.dp), verticalAlignment = Alignment.CenterVertically) {
            if (logo.isNotBlank()) {
                AsyncImage(model = logo, contentDescription = name, modifier = Modifier.size(28.dp).clip(CircleShape), contentScale = ContentScale.Crop)
            } else {
                Box(Modifier.size(28.dp).clip(CircleShape), contentAlignment = Alignment.Center) {
                    Text(symbol.take(1), fontWeight = FontWeight.Bold, fontSize = 10.sp)
                }
            }
            Spacer(Modifier.width(8.dp))
            Column(modifier = Modifier.weight(1f)) {
                TextButton(onClick = onPairClick, contentPadding = PaddingValues(0.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(symbol, fontSize = 15.sp, fontWeight = FontWeight.Bold, color = Color.Black)
                        Icon(Icons.Default.KeyboardArrowDown, null, modifier = Modifier.size(18.dp))
                    }
                }
                Text(name, fontSize = 9.sp, color = Color.Gray)
            }
            Column(horizontalAlignment = Alignment.End) {
                Text(if (price > 0) formatPrice(price) else "—", fontWeight = FontWeight.Bold, fontSize = 14.sp)
                Text(formatPct(change24h), fontSize = 12.sp, fontWeight = FontWeight.Bold,
                    color = if (change24h >= 0) Green else Red)
            }
        }
    }
}

// ============================================================
// ORDER PANEL (new layout: Long/Short, Market/Leverage/Limit)
// ============================================================
@Composable
private fun OrderPanel(
    modifier: Modifier,
    balance: Double,
    price: Double,
    side: PositionSide,
    orderType: TradeOrderType,
    leverage: Int,
    quantity: Double,
    limitPrice: String,
    position: DemoPosition?,
    onSideChange: (PositionSide) -> Unit,
    onOrderTypeChange: (TradeOrderType) -> Unit,
    onLeverageClick: () -> Unit,
    onQuantityChange: (Double) -> Unit,
    onPercent: (Int) -> Unit,
    onLimitPriceChange: (String) -> Unit,
    onOpen: () -> Unit
) {
    Card(
        modifier = modifier,
        shape = RoundedCornerShape(10.dp),
        colors = CardDefaults.cardColors(containerColor = CardBackground)
    ) {
        Column(modifier = Modifier.fillMaxWidth().padding(9.dp)) {
            Text("Place Order", fontSize = 12.sp, fontWeight = FontWeight.Bold)
            Spacer(Modifier.height(7.dp))

            // LONG / SHORT
            Row(modifier = Modifier.fillMaxWidth()) {
                SideButton("Long", side == PositionSide.LONG, Green) { onSideChange(PositionSide.LONG) }
                Spacer(Modifier.width(5.dp))
                SideButton("Short", side == PositionSide.SHORT, Red) { onSideChange(PositionSide.SHORT) }
            }

            Spacer(Modifier.height(7.dp))

            // MARKET / LEVERAGE / LIMIT (one row)
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(5.dp)) {
                // Market button
                Button(
                    onClick = { onOrderTypeChange(TradeOrderType.MARKET) },
                    modifier = Modifier.weight(1f),
                    shape = RoundedCorner,
                    colors = ButtonDefaults.buttonColors(
                        containerColor = if (orderType == TradeOrderType.MARKET) PurpleMimosa else Color(0xFFE9E9ED),
                        contentColor = if (orderType == TradeOrderType.MARKET) Color.White else Color.DarkGray
                    ),
                    contentPadding = PaddingValues(vertical = 6.dp)
                ) { Text("Market", fontSize = 10.sp) }

                // Leverage button
                OutlinedButton(
                    onClick = onLeverageClick,
                    modifier = Modifier.weight(1f),
                    shape = RoundedCorner,
                    contentPadding = PaddingValues(vertical = 6.dp)
                ) { Text("${leverage}x", fontSize = 10.sp) }

                // Limit button
                Button(
                    onClick = { onOrderTypeChange(TradeOrderType.LIMIT) },
                    modifier = Modifier.weight(1f),
                    shape = RoundedCorner,
                    colors = ButtonDefaults.buttonColors(
                        containerColor = if (orderType == TradeOrderType.LIMIT) PurpleMimosa else Color(0xFFE9E9ED),
                        contentColor = if (orderType == TradeOrderType.LIMIT) Color.White else Color.DarkGray
                    ),
                    contentPadding = PaddingValues(vertical = 6.dp)
                ) { Text("Limit", fontSize = 10.sp) }
            }

            Spacer(Modifier.height(7.dp))

            // Available balance
            SmallStat("Available", "%,.2f".format(balance) + " USDT")
            Spacer(Modifier.height(6.dp))

            // Limit price (if limit order selected)
            if (orderType == TradeOrderType.LIMIT) {
                OutlinedTextField(
                    value = limitPrice,
                    onValueChange = onLimitPriceChange,
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true,
                    shape = RoundedCorner,
                    label = { Text("Price", fontSize = 10.sp) },
                    textStyle = LocalTextStyle.current.copy(fontSize = 11.sp)
                )
                Spacer(Modifier.height(6.dp))
            }

            // Amount input
            var amountText by rememberSaveable { mutableStateOf(quantity.toString()) }
            LaunchedEffect(quantity) {
                val current = amountText.toDoubleOrNull()
                if (current == null || kotlin.math.abs(current - quantity) > 0.000000001)
                    amountText = quantity.toString()
            }
            OutlinedTextField(
                value = amountText,
                onValueChange = { input ->
                    if (input.isEmpty() || input.matches(Regex("^\\d*(\\.\\d*)?$"))) {
                        amountText = input
                        input.toDoubleOrNull()?.let { if (it > 0.0) onQuantityChange(it) }
                    }
                },
                modifier = Modifier.fillMaxWidth(),
                singleLine = true,
                shape = RoundedCorner,
                label = { Text("Amount", fontSize = 10.sp) },
                trailingIcon = { Text("BTC", fontSize = 9.sp) },
                textStyle = LocalTextStyle.current.copy(fontSize = 11.sp)
            )
            Spacer(Modifier.height(5.dp))

            // Percentages
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                listOf(25, 50, 75, 100).forEach { p ->
                    TextButton(onClick = { onPercent(p) }, contentPadding = PaddingValues(horizontal = 4.dp)) {
                        Text("$p%", fontSize = 9.sp, color = PurpleMimosa)
                    }
                }
            }

            Slider(
                value = quantity.toFloat().coerceIn(0.000001f, 1.0f),
                onValueChange = { onQuantityChange(it.toDouble()) },
                valueRange = 0.000001f..1.0f,
                colors = SliderDefaults.colors(thumbColor = PurpleMimosa, activeTrackColor = PurpleMimosa)
            )

            Spacer(Modifier.height(4.dp))

            val executionPrice = if (orderType == TradeOrderType.LIMIT) limitPrice.toDoubleOrNull() ?: price else price
            val notional = executionPrice * quantity
            val margin = if (leverage > 0) notional / leverage else notional

            SmallStat("Size", "%,.2f".format(notional) + " USDT")
            Spacer(Modifier.height(3.dp))
            SmallStat("Margin", "%,.2f".format(margin) + " USDT")
            Spacer(Modifier.height(8.dp))

            Button(
                onClick = onOpen,
                modifier = Modifier.fillMaxWidth(),
                enabled = price > 0.0 && quantity > 0.0 && position == null,
                shape = RoundedCorner,
                colors = ButtonDefaults.buttonColors(containerColor = if (side == PositionSide.LONG) Green else Red),
                contentPadding = PaddingValues(vertical = 10.dp)
            ) {
                Text(if (side == PositionSide.LONG) "OPEN LONG" else "OPEN SHORT", fontSize = 11.sp)
            }
        }
    }
}

// ============================================================
// ORDER BOOK PANEL (unchanged, live bids/asks)
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
        Column(modifier = Modifier.fillMaxWidth().padding(8.dp)) {
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                Text("Order Book", fontSize = 12.sp, fontWeight = FontWeight.Bold)
                Text(symbol, fontSize = 9.sp, color = Color.Gray)
            }
            Spacer(Modifier.height(5.dp))
            Row(modifier = Modifier.fillMaxWidth()) {
                Text("USDT", modifier = Modifier.weight(1f), fontSize = 8.sp, color = Color.Gray)
                Text("Price", modifier = Modifier.weight(1f), textAlign = TextAlign.Center, fontSize = 8.sp, color = Color.Gray)
                Text("Amount", modifier = Modifier.weight(1f), textAlign = TextAlign.End, fontSize = 8.sp, color = Color.Gray)
            }
            Spacer(Modifier.height(3.dp))
            asks.take(6).asReversed().forEach { level -> OrderBookLine(level, Red) }
            Box(
                modifier = Modifier.fillMaxWidth().background(Color(0xFFF3EDF7), RoundedCornerShape(5.dp)).padding(vertical = 5.dp)
            ) {
                Text(
                    text = if (currentPrice > 0) formatPrice(currentPrice) else "—",
                    modifier = Modifier.fillMaxWidth(),
                    textAlign = TextAlign.Center,
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Bold,
                    color = PurpleMimosa
                )
            }
            Spacer(Modifier.height(3.dp))
            bids.take(6).forEach { level -> OrderBookLine(level, Green) }
        }
    }
}

@Composable
private fun OrderBookLine(level: OrderBookLevel, color: Color) {
    val usdtValue = level.price * level.quantity
    Row(modifier = Modifier.fillMaxWidth().padding(vertical = 2.dp)) {
        Text(String.format(Locale.US, "%.2f", usdtValue), modifier = Modifier.weight(1f), fontSize = 8.sp, color = Color.Gray)
        Text(formatPrice(level.price), modifier = Modifier.weight(1f), textAlign = TextAlign.Center, fontSize = 8.sp, color = color)
        Text(String.format(Locale.US, "%.4f", level.quantity), modifier = Modifier.weight(1f), textAlign = TextAlign.End, fontSize = 8.sp, color = Color.Gray)
    }
}

// ============================================================
// BOTTOM TABS & CONTENT (unchanged)
// ============================================================
private enum class TradeBottomTab { POSITION, PENDING, HISTORY }

@Composable
private fun BottomTradeTabs(selected: TradeBottomTab, onSelected: (TradeBottomTab) -> Unit) {
    Row(
        modifier = Modifier.fillMaxWidth().background(CardBackground).padding(horizontal = 7.dp, vertical = 5.dp),
        horizontalArrangement = Arrangement.spacedBy(5.dp)
    ) {
        BottomTabButton("Active Position", selected == TradeBottomTab.POSITION) { onSelected(TradeBottomTab.POSITION) }
        BottomTabButton("Pending", selected == TradeBottomTab.PENDING) { onSelected(TradeBottomTab.PENDING) }
        BottomTabButton("History", selected == TradeBottomTab.HISTORY) { onSelected(TradeBottomTab.HISTORY) }
    }
}

@Composable
private fun RowScope.BottomTabButton(title: String, selected: Boolean, onClick: () -> Unit) {
    TextButton(onClick = onClick, modifier = Modifier.weight(1f)) {
        Text(title, fontSize = 9.sp, fontWeight = if (selected) FontWeight.Bold else FontWeight.Normal, color = if (selected) PurpleMimosa else Color.Gray)
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
// ACTIVE POSITION, PENDING, HISTORY (unchanged but buttons rounded where applicable)
// ============================================================
@Composable
private fun ActivePositionContent(position: DemoPosition?, currentPrice: Double, pnl: Double, onClose: () -> Unit) {
    Card(
        modifier = Modifier.fillMaxWidth().padding(horizontal = 7.dp, vertical = 4.dp),
        shape = RoundedCornerShape(9.dp),
        colors = CardDefaults.cardColors(containerColor = CardBackground)
    ) {
        if (position == null) {
            Text("No active position", modifier = Modifier.fillMaxWidth().padding(14.dp), textAlign = TextAlign.Center, fontSize = 10.sp, color = Color.Gray)
        } else {
            Column(modifier = Modifier.padding(10.dp)) {
                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                    Text(if (position.side == PositionSide.LONG) "LONG" else "SHORT", fontWeight = FontWeight.Bold, color = if (position.side == PositionSide.LONG) Green else Red)
                    Text("${position.symbol} ${position.leverage}x", fontSize = 10.sp)
                }
                Spacer(Modifier.height(6.dp))
                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                    BottomValue("Entry", formatPrice(position.entryPrice))
                    BottomValue("Mark", formatPrice(currentPrice))
                    BottomValue("Qty", "%.6f".format(position.quantity))
                    BottomValue("PnL", TradeViewModel.formatMoney(pnl), if (pnl >= 0) Green else Red)
                }
                Spacer(Modifier.height(7.dp))
                Button(onClick = onClose, modifier = Modifier.fillMaxWidth(), colors = ButtonDefaults.buttonColors(containerColor = Red), shape = RoundedCorner) {
                    Text("CLOSE POSITION", fontSize = 10.sp)
                }
            }
        }
    }
}

@Composable
private fun PendingContent(orders: List<PendingOrder>, onCancel: (Long) -> Unit) {
    Card(
        modifier = Modifier.fillMaxWidth().padding(horizontal = 7.dp, vertical = 4.dp),
        shape = RoundedCornerShape(9.dp),
        colors = CardDefaults.cardColors(containerColor = CardBackground)
    ) {
        if (orders.isEmpty()) {
            Text("No pending orders", modifier = Modifier.fillMaxWidth().padding(14.dp), textAlign = TextAlign.Center, fontSize = 10.sp, color = Color.Gray)
        } else {
            Column(modifier = Modifier.padding(8.dp)) {
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
    Row(modifier = Modifier.fillMaxWidth().padding(vertical = 6.dp), verticalAlignment = Alignment.CenterVertically) {
        Column(modifier = Modifier.weight(1f)) {
            Text("${order.symbol} ${if (order.side == PositionSide.LONG) "LONG" else "SHORT"}", fontSize = 9.sp, fontWeight = FontWeight.Bold)
            Text("Limit ${formatPrice(order.price)} • ${"%.6f".format(order.quantity)}", fontSize = 8.sp, color = Color.Gray)
        }
        Text("${order.leverage}x", fontSize = 9.sp)
        TextButton(onClick = onCancel) { Text("Cancel", fontSize = 9.sp, color = Red) }
    }
}

@Composable
private fun HistoryContent(history: List<TradeHistory>) {
    Card(
        modifier = Modifier.fillMaxWidth().padding(horizontal = 7.dp, vertical = 4.dp),
        shape = RoundedCornerShape(9.dp),
        colors = CardDefaults.cardColors(containerColor = CardBackground)
    ) {
        if (history.isEmpty()) {
            Text("No trading history", modifier = Modifier.fillMaxWidth().padding(14.dp), textAlign = TextAlign.Center, fontSize = 10.sp, color = Color.Gray)
        } else {
            LazyColumn(modifier = Modifier.heightIn(max = 160.dp)) {
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
    Row(modifier = Modifier.fillMaxWidth().padding(horizontal = 9.dp, vertical = 7.dp), verticalAlignment = Alignment.CenterVertically) {
        Column(modifier = Modifier.weight(1f)) {
            Text("${item.symbol} ${if (item.side == PositionSide.LONG) "LONG" else "SHORT"}", fontSize = 9.sp, fontWeight = FontWeight.Bold)
            Text("${formatPrice(item.entryPrice)} → ${formatPrice(item.exitPrice)}", fontSize = 8.sp, color = Color.Gray)
        }
        Text(TradeViewModel.formatMoney(item.pnl), fontSize = 10.sp, fontWeight = FontWeight.Bold, color = if (item.pnl >= 0) Green else Red)
    }
}

// ============================================================
// SMALL COMPONENTS (rounded SideButton)
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
        contentPadding = PaddingValues(vertical = 7.dp)
    ) { Text(text, fontSize = 10.sp) }
}

@Composable
private fun SmallOrderType(text: String, selected: Boolean, onClick: () -> Unit) {
    TextButton(onClick = onClick, contentPadding = PaddingValues(horizontal = 4.dp, vertical = 0.dp)) {
        Text(text, fontSize = 9.sp, fontWeight = if (selected) FontWeight.Bold else FontWeight.Normal, color = if (selected) PurpleMimosa else Color.Gray)
    }
}

@Composable
private fun SmallStat(label: String, value: String) {
    Column {
        Text(label, fontSize = 8.sp, color = Color.Gray)
        Text(value, fontSize = 10.sp, fontWeight = FontWeight.Bold)
    }
}

@Composable
private fun BottomValue(label: String, value: String, color: Color = Color.Black) {
    Column {
        Text(label, fontSize = 8.sp, color = Color.Gray)
        Text(value, fontSize = 9.sp, fontWeight = FontWeight.Bold, color = color)
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

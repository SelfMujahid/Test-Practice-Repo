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

// ============================================================
// TRADE SCREEN
// ============================================================

@Composable
fun TradeScreen(
    vm: TradeViewModel = viewModel(),
    cryptoVm: CryptoViewModel
) {
    // Market data from Home
    val coins by cryptoVm.coins.collectAsState()
    val symbol by vm.symbol.collectAsState()

    val selectedCoin = remember(coins, symbol) {
        coins.find { (it.symbol + "USDT").equals(symbol, ignoreCase = true) }
    }
    val currentPrice = selectedCoin?.price ?: 0.0
    val change24h = selectedCoin?.change24h ?: 0.0
    val coinName = selectedCoin?.name ?: ""
    val coinLogo = selectedCoin?.logo ?: ""

    // Form state from TradeViewModel
    val mode by vm.mode.collectAsState()
    val orderType by vm.orderType.collectAsState()
    val side by vm.side.collectAsState()
    val leverage by vm.leverage.collectAsState()
    val quantity by vm.quantity.collectAsState()
    val limitPrice by vm.limitPrice.collectAsState()
    val balance by vm.balance.collectAsState()
    val position by vm.position.collectAsState()
    val pendingOrders by vm.pendingOrders.collectAsState()
    val history by vm.history.collectAsState()
    val message by vm.message.collectAsState()

    val pnl = vm.unrealizedPnl(currentPrice)

    var showPairMenu by rememberSaveable { mutableStateOf(false) }
    var showLeverage by rememberSaveable { mutableStateOf(false) }
    var selectedBottomTab by rememberSaveable { mutableStateOf(TradeBottomTab.POSITION) }
    var pairSearch by rememberSaveable { mutableStateOf("") }

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

            // Placeholder instead of order book (right side empty)
            Spacer(modifier = Modifier.weight(1f))
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
        val allPairs = coins.map { it.symbol + "USDT" }
        val filteredPairs = allPairs.filter { it.contains(pairSearch, ignoreCase = true) }

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
                        placeholder = { Text("BTCUSDT") }
                    )
                    Spacer(Modifier.height(10.dp))
                    filteredPairs.forEach { pair ->
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable {
                                    vm.setSymbol(pair)
                                    pairSearch = ""
                                    showPairMenu = false
                                }
                                .padding(vertical = 11.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(text = pair, fontWeight = FontWeight.Medium)
                        }
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = {
                    if (pairSearch.isNotBlank()) vm.setSymbol(pairSearch)
                    pairSearch = ""
                    showPairMenu = false
                }) {
                    Text("Apply", color = PurpleMimosa)
                }
            }
        )
    }

    // --- Leverage dialog ---
    if (showLeverage) {
        AlertDialog(
            onDismissRequest = { showLeverage = false },
            title = { Text("Leverage") },
            text = {
                Column {
                    listOf(1, 2, 3, 5, 10, 20, 25, 50).forEach { value ->
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable {
                                    vm.setLeverage(value)
                                    showLeverage = false
                                }
                                .padding(12.dp),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text("${value}x")
                            if (value == leverage) {
                                Icon(Icons.Default.Check, null, tint = PurpleMimosa)
                            }
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
        shape = RoundedCornerShape(7.dp),
        colors = ButtonDefaults.buttonColors(
            containerColor = if (selected) PurpleMimosa else Color(0xFFE9E9ED),
            contentColor = if (selected) Color.White else Color.DarkGray
        )
    ) {
        Text(text = title, fontSize = 11.sp)
    }
}

// ============================================================
// MARKET HEADER (simplified)
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
        Row(
            modifier = Modifier.fillMaxWidth().padding(9.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            // Logo
            if (logo.isNotBlank()) {
                AsyncImage(
                    model = logo,
                    contentDescription = name,
                    modifier = Modifier.size(28.dp).clip(CircleShape),
                    contentScale = ContentScale.Crop
                )
            } else {
                Box(Modifier.size(28.dp).clip(CircleShape), contentAlignment = Alignment.Center) {
                    Text(symbol.take(1), fontWeight = FontWeight.Bold, fontSize = 10.sp)
                }
            }

            Spacer(Modifier.width(8.dp))

            // Symbol + name
            Column(modifier = Modifier.weight(1f)) {
                TextButton(onClick = onPairClick, contentPadding = PaddingValues(0.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(symbol, fontSize = 15.sp, fontWeight = FontWeight.Bold, color = Color.Black)
                        Icon(Icons.Default.KeyboardArrowDown, null, modifier = Modifier.size(18.dp))
                    }
                }
                Text(name, fontSize = 9.sp, color = Color.Gray)
            }

            // Price + 24h change
            Column(horizontalAlignment = Alignment.End) {
                Text(if (price > 0) formatPrice(price) else "—", fontWeight = FontWeight.Bold, fontSize = 14.sp)
                Text(formatPct(change24h), fontSize = 12.sp, fontWeight = FontWeight.Bold,
                    color = if (change24h >= 0) Green else Red
                )
            }
        }
    }
}

// ============================================================
// ORDER PANEL
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

            // LONG/SHORT
            Row(modifier = Modifier.fillMaxWidth()) {
                SideButton("Long", side == PositionSide.LONG, Green) { onSideChange(PositionSide.LONG) }
                Spacer(Modifier.width(5.dp))
                SideButton("Short", side == PositionSide.SHORT, Red) { onSideChange(PositionSide.SHORT) }
            }

            Spacer(Modifier.height(7.dp))

            // MARKET/LIMIT
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                SmallOrderType("Market", orderType == TradeOrderType.MARKET) { onOrderTypeChange(TradeOrderType.MARKET) }
                SmallOrderType("Limit", orderType == TradeOrderType.LIMIT) { onOrderTypeChange(TradeOrderType.LIMIT) }
            }

            Spacer(Modifier.height(7.dp))

            // Leverage
            OutlinedButton(onClick = onLeverageClick, modifier = Modifier.fillMaxWidth(), contentPadding = PaddingValues(vertical = 6.dp)) {
                Text("${leverage}x", fontSize = 10.sp)
            }

            Spacer(Modifier.height(7.dp))

            // Available
            SmallStat("Available", "%,.2f".format(balance) + " USDT")

            Spacer(Modifier.height(6.dp))

            // Limit price input (if limit)
            if (orderType == TradeOrderType.LIMIT) {
                OutlinedTextField(
                    value = limitPrice,
                    onValueChange = onLimitPriceChange,
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true,
                    label = { Text("Price", fontSize = 10.sp) },
                    textStyle = LocalTextStyle.current.copy(fontSize = 11.sp)
                )
                Spacer(Modifier.height(6.dp))
            }

            // Amount
            var amountText by rememberSaveable { mutableStateOf(quantity.toString()) }
            LaunchedEffect(quantity) {
                if (amountText.toDoubleOrNull() == null || kotlin.math.abs(amountText.toDoubleOrNull()!! - quantity) > 0.000000001)
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
                colors = ButtonDefaults.buttonColors(containerColor = if (side == PositionSide.LONG) Green else Red),
                contentPadding = PaddingValues(vertical = 10.dp)
            ) {
                Text(if (side == PositionSide.LONG) "OPEN LONG" else "OPEN SHORT", fontSize = 11.sp)
            }
        }
    }
}

// ============================================================
// BOTTOM TABS
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
        Text(
            title,
            fontSize = 9.sp,
            fontWeight = if (selected) FontWeight.Bold else FontWeight.Normal,
            color = if (selected) PurpleMimosa else Color.Gray
        )
    }
}

// ============================================================
// BOTTOM CONTENT
// ============================================================
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
// ACTIVE POSITION
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
                    Text(
                        if (position.side == PositionSide.LONG) "LONG" else "SHORT",
                        fontWeight = FontWeight.Bold,
                        color = if (position.side == PositionSide.LONG) Green else Red
                    )
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
                Button(onClick = onClose, modifier = Modifier.fillMaxWidth(), colors = ButtonDefaults.buttonColors(containerColor = Red)) {
                    Text("CLOSE POSITION", fontSize = 10.sp)
                }
            }
        }
    }
}

// ============================================================
// PENDING
// ============================================================
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

// ============================================================
// HISTORY
// ============================================================
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
// SMALL COMPONENTS
// ============================================================
@Composable
private fun RowScope.SideButton(text: String, selected: Boolean, color: Color, onClick: () -> Unit) {
    Button(
        onClick = onClick,
        modifier = Modifier.weight(1f),
        colors = ButtonDefaults.buttonColors(
            containerColor = if (selected) color else Color(0xFFE7E7EA),
            contentColor = if (selected) Color.White else Color.DarkGray
        ),
        shape = RoundedCornerShape(7.dp),
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

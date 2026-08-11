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

        // Main content
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

            // Placeholder for order book or just empty space
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

    // Pair selector dialog
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

    // Leverage dialog
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
// MODE BAR (unchanged but uses TradeMode)
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
// MARKET HEADER – simplified
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
// ORDER PANEL (same as before, uses currentPrice from coin)
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
    // ... (same as previous version, no changes needed – it already uses "price" from parameter)
}

// ============================================================
// BOTTOM TABS & CONTENT (unchanged, except PnL and close use currentPrice)
// ============================================================
// Keep them as they were; they already receive currentPrice, pnl, etc.

// ============================================================
// REST OF THE UI (ActivePositionContent, PendingContent, HistoryContent, etc.)
// Keep them unchanged.
// ============================================================

// ============================================================
// FORMATTING (formatPrice, formatPct, formatSignedPct)
// ============================================================
private fun formatPrice(value: Double): String {
    if (value.isNaN() || value.isInfinite()) return "0"
    return if (value >= 1.0) {
        "%,.2f".format(value)
    } else {
        "%.8f".format(value).trimEnd('0').trimEnd('.')
    }
}

private fun formatPct(value: Double): String = "%+.2f%%".format(value)

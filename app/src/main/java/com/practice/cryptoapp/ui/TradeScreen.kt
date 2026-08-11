package com.practice.cryptoapp.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.practice.cryptoapp.DemoAccountStore
import com.practice.cryptoapp.DemoPosition
import com.practice.cryptoapp.OrderBookLevel
import com.practice.cryptoapp.PendingOrder
import com.practice.cryptoapp.PositionSide
import com.practice.cryptoapp.PurpleMimosa
import com.practice.cryptoapp.TradeHistory
import com.practice.cryptoapp.TradeMode
import com.practice.cryptoapp.TradeOrderType
import com.practice.cryptoapp.TradeViewModel
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
    vm: TradeViewModel = viewModel()
) {
    val mode by vm.mode.collectAsState()
    val symbol by vm.symbol.collectAsState()
    val price by vm.price.collectAsState()
    val high24h by vm.high24h.collectAsState()
    val low24h by vm.low24h.collectAsState()
    val change24h by vm.change24h.collectAsState()

    val fundingRate by vm.fundingRate.collectAsState()
    val fundingCountdown by vm.fundingCountdown.collectAsState()

    val bids by vm.bids.collectAsState()
    val asks by vm.asks.collectAsState()

    val orderType by vm.orderType.collectAsState()
    val side by vm.side.collectAsState()
    val leverage by vm.leverage.collectAsState()
    val quantity by vm.quantity.collectAsState()
    val limitPrice by vm.limitPrice.collectAsState()

    // FIXED: Collect position/order states from the ViewModel (which internally delegates to DemoAccountStore)
    val balance by vm.balance.collectAsState()
    val position by vm.position.collectAsState()
    val pendingOrders by vm.pendingOrders.collectAsState()
    val history by vm.history.collectAsState()

    val message by vm.message.collectAsState()

    val pnl = vm.unrealizedPnl()

    var showPairMenu by rememberSaveable {
        mutableStateOf(false)
    }

    var showLeverage by rememberSaveable {
        mutableStateOf(false)
    }

    var selectedBottomTab by rememberSaveable {
        mutableStateOf(TradeBottomTab.POSITION)
    }

    var pairSearch by rememberSaveable {
        mutableStateOf("")
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(ScreenBackground)
            .verticalScroll(rememberScrollState())
    ) {

        // ====================================================
        // MODE BAR
        // ====================================================

        ModeBar(
            mode = mode,
            onSelect = vm::setMode
        )

        // ====================================================
        // MARKET HEADER
        // ====================================================

        MarketHeader(
            symbol = symbol,
            price = price,
            change24h = change24h,
            high24h = high24h,
            low24h = low24h,
            fundingRate = fundingRate,
            fundingCountdown = fundingCountdown,
            onPairClick = {
                showPairMenu = true
            }
        )

        // ====================================================
        // MAIN SECTION
        // ====================================================

        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 7.dp),
            horizontalArrangement = Arrangement.spacedBy(7.dp)
        ) {

            // LEFT
            OrderPanel(
                modifier = Modifier.weight(1f),
                balance = balance,
                price = price,
                side = side,
                orderType = orderType,
                leverage = leverage,
                quantity = quantity,
                limitPrice = limitPrice,
                position = position,
                onSideChange = vm::setSide,
                onOrderTypeChange = vm::setOrderType,
                onLeverageClick = {
                    showLeverage = true
                },
                onQuantityChange = vm::setQuantity,
                onPercent = vm::setQuantityPercent,
                onLimitPriceChange = vm::setLimitPrice,
                onOpen = vm::openOrder
            )

            // RIGHT
            OrderBookPanel(
                modifier = Modifier.weight(1f),
                symbol = symbol,
                currentPrice = price,
                bids = bids,
                asks = asks
            )
        }

        // ====================================================
        // BOTTOM TABS
        // ====================================================

        BottomTradeTabs(
            selected = selectedBottomTab,
            onSelected = {
                selectedBottomTab = it
            }
        )

        // ====================================================
        // BOTTOM CONTENT
        // ====================================================

        BottomTradeContent(
            selected = selectedBottomTab,
            position = position,
            pendingOrders = pendingOrders,
            history = history,
            currentPrice = price,
            pnl = pnl,
            onClose = vm::closePosition,
            onCancelPending = vm::cancelPending
        )

        // ====================================================
        // MESSAGE
        // ====================================================

        if (message.isNotBlank()) {
            Text(
                text = message,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(
                        horizontal = 10.dp,
                        vertical = 4.dp
                    ),
                fontSize = 10.sp,
                color = PurpleMimosa
            )
        }
    }

    // ========================================================
    // PAIR SELECTOR
    // ========================================================

    if (showPairMenu) {

        AlertDialog(
            onDismissRequest = {
                showPairMenu = false
            },

            title = {
                Text("Select Futures Pair")
            },

            text = {

                Column {

                    OutlinedTextField(
                        value = pairSearch,
                        onValueChange = {
                            pairSearch = it.uppercase(Locale.US)
                        },
                        modifier = Modifier.fillMaxWidth(),
                        singleLine = true,
                        placeholder = {
                            Text("BTCUSDT")
                        }
                    )

                    Spacer(
                        modifier = Modifier.height(10.dp)
                    )

                    val pairs = listOf(
                        "BTCUSDT",
                        "ETHUSDT",
                        "BNBUSDT",
                        "SOLUSDT",
                        "XRPUSDT",
                        "DOGEUSDT",
                        "ADAUSDT",
                        "AVAXUSDT",
                        "LINKUSDT",
                        "LTCUSDT"
                    ).filter {
                        it.contains(
                            pairSearch,
                            ignoreCase = true
                        )
                    }

                    pairs.forEach { pair ->

                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable {

                                    vm.changeSymbol(pair)

                                    pairSearch = ""
                                    showPairMenu = false
                                }
                                .padding(vertical = 11.dp),

                            verticalAlignment =
                                Alignment.CenterVertically
                        ) {

                            Text(
                                text = pair,
                                fontWeight = FontWeight.Medium
                            )
                        }
                    }
                }
            },

            confirmButton = {

                TextButton(
                    onClick = {

                        if (pairSearch.isNotBlank()) {
                            vm.changeSymbol(pairSearch)
                        }

                        pairSearch = ""
                        showPairMenu = false
                    }
                ) {
                    Text(
                        text = "Apply",
                        color = PurpleMimosa
                    )
                }
            }
        )
    }

    // ========================================================
    // LEVERAGE DIALOG
    // ========================================================

    if (showLeverage) {

        AlertDialog(
            onDismissRequest = {
                showLeverage = false
            },

            title = {
                Text("Leverage")
            },

            text = {

                Column {

                    listOf(
                        1,
                        2,
                        3,
                        5,
                        10,
                        20,
                        25,
                        50
                    ).forEach { value ->

                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable {

                                    vm.setLeverage(value)
                                    showLeverage = false
                                }
                                .padding(12.dp),

                            horizontalArrangement =
                                Arrangement.SpaceBetween,

                            verticalAlignment =
                                Alignment.CenterVertically
                        ) {

                            Text("${value}x")

                            if (value == leverage) {

                                Icon(
                                    imageVector = Icons.Default.Check,
                                    contentDescription = null,
                                    tint = PurpleMimosa
                                )
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
private fun ModeBar(
    mode: TradeMode,
    onSelect: (TradeMode) -> Unit
) {

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .background(CardBackground)
            .padding(
                horizontal = 8.dp,
                vertical = 6.dp
            ),
        horizontalArrangement = Arrangement.spacedBy(6.dp)
    ) {

        ModeButton(
            title = "Spot",
            selected = mode == TradeMode.SPOT,
            onClick = {
                onSelect(TradeMode.SPOT)
            }
        )

        ModeButton(
            title = "Future",
            selected = mode == TradeMode.FUTURES,
            onClick = {
                onSelect(TradeMode.FUTURES)
            }
        )

        ModeButton(
            title = "Bot",
            selected = mode == TradeMode.BOT,
            onClick = {
                onSelect(TradeMode.BOT)
            }
        )
    }
}

@Composable
private fun RowScope.ModeButton(
    title: String,
    selected: Boolean,
    onClick: () -> Unit
) {

    Button(
        onClick = onClick,
        modifier = Modifier.weight(1f),
        shape = RoundedCornerShape(7.dp),
        colors = ButtonDefaults.buttonColors(
            containerColor =
                if (selected) {
                    PurpleMimosa
                } else {
                    Color(0xFFE9E9ED)
                },

            contentColor =
                if (selected) {
                    Color.White
                } else {
                    Color.DarkGray
                }
        )
    ) {

        Text(
            text = title,
            fontSize = 11.sp
        )
    }
}

// ============================================================
// MARKET HEADER
// ============================================================

@Composable
private fun MarketHeader(
    symbol: String,
    price: Double,
    change24h: Double,
    high24h: Double,
    low24h: Double,
    fundingRate: Double,
    fundingCountdown: String,
    onPairClick: () -> Unit
) {

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(
                horizontal = 7.dp,
                vertical = 4.dp
            ),
        shape = RoundedCornerShape(10.dp),
        colors = CardDefaults.cardColors(
            containerColor = CardBackground
        )
    ) {

        Column {

            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(9.dp),
                verticalAlignment =
                    Alignment.CenterVertically
            ) {

                // LEFT
                Column(
                    modifier = Modifier.weight(1.2f)
                ) {

                    TextButton(
                        onClick = onPairClick,
                        contentPadding =
                            PaddingValues(0.dp)
                    ) {

                        Row(
                            verticalAlignment =
                                Alignment.CenterVertically
                        ) {

                            Text(
                                text = symbol,
                                fontSize = 15.sp,
                                fontWeight = FontWeight.Bold,
                                color = Color.Black
                            )

                            Icon(
                                imageVector =
                                    Icons.Default.KeyboardArrowDown,
                                contentDescription = null,
                                modifier = Modifier.size(18.dp)
                            )
                        }
                    }

                    Text(
                        text = "24h",
                        fontSize = 8.sp,
                        color = Color.Gray
                    )

                    Text(
                        text = formatSignedPct(change24h),
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Bold,
                        color =
                            if (change24h >= 0) {
                                Green
                            } else {
                                Red
                            }
                    )
                }

                // CENTER
                Column(
                    modifier = Modifier.weight(1f)
                ) {

                    SmallStat(
                        label = "24h Low",
                        value =
                            if (low24h > 0) {
                                formatPrice(low24h)
                            } else {
                                "—"
                            }
                    )

                    Spacer(
                        modifier = Modifier.height(5.dp)
                    )

                    SmallStat(
                        label = "24h High",
                        value =
                            if (high24h > 0) {
                                formatPrice(high24h)
                            } else {
                                "—"
                            }
                    )
                }

                // RIGHT
                Column(
                    modifier = Modifier.weight(1f),
                    horizontalAlignment =
                        Alignment.End
                ) {

                    Text(
                        text = "Funding Rate",
                        fontSize = 8.sp,
                        color = Color.Gray
                    )

                    Text(
                        text = formatFunding(fundingRate),
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Bold,
                        color =
                            if (fundingRate >= 0) {
                                Green
                            } else {
                                Red
                            }
                    )

                    Spacer(
                        modifier = Modifier.height(2.dp)
                    )

                    Text(
                        text = "Next",
                        fontSize = 8.sp,
                        color = Color.Gray
                    )

                    Text(
                        text = fundingCountdown,
                        fontSize = 10.sp,
                        fontWeight = FontWeight.Bold,
                        color = PurpleMimosa
                    )
                }
            }

            HorizontalDivider(
                modifier = Modifier.padding(horizontal = 9.dp)
            )

            Text(
                text =
                    if (price > 0) {
                        formatPrice(price)
                    } else {
                        "Loading..."
                    },

                modifier = Modifier
                    .fillMaxWidth()
                    .padding(
                        horizontal = 9.dp,
                        vertical = 5.dp
                    ),

                fontSize = 11.sp,
                color = Color.Gray
            )
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
        colors = CardDefaults.cardColors(
            containerColor = CardBackground
        )
    ) {

        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(9.dp)
        ) {

            Text(
                text = "Place Order",
                fontSize = 12.sp,
                fontWeight = FontWeight.Bold
            )

            Spacer(
                modifier = Modifier.height(7.dp)
            )

            // LONG / SHORT
            Row(
                modifier = Modifier.fillMaxWidth()
            ) {

                SideButton(
                    text = "Long",
                    selected = side == PositionSide.LONG,
                    color = Green,
                    onClick = {
                        onSideChange(PositionSide.LONG)
                    }
                )

                Spacer(
                    modifier = Modifier.width(5.dp)
                )

                SideButton(
                    text = "Short",
                    selected = side == PositionSide.SHORT,
                    color = Red,
                    onClick = {
                        onSideChange(PositionSide.SHORT)
                    }
                )
            }

            Spacer(
                modifier = Modifier.height(7.dp)
            )

            // MARKET / LIMIT
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement =
                    Arrangement.SpaceBetween
            ) {

                SmallOrderType(
                    text = "Market",
                    selected =
                        orderType == TradeOrderType.MARKET,
                    onClick = {
                        onOrderTypeChange(
                            TradeOrderType.MARKET
                        )
                    }
                )

                SmallOrderType(
                    text = "Limit",
                    selected =
                        orderType == TradeOrderType.LIMIT,
                    onClick = {
                        onOrderTypeChange(
                            TradeOrderType.LIMIT
                        )
                    }
                )
            }

            Spacer(
                modifier = Modifier.height(7.dp)
            )

            // LEVERAGE
            OutlinedButton(
                onClick = onLeverageClick,
                modifier = Modifier.fillMaxWidth(),
                contentPadding =
                    PaddingValues(vertical = 6.dp)
            ) {

                Text(
                    text = "${leverage}x",
                    fontSize = 10.sp
                )
            }

            Spacer(
                modifier = Modifier.height(7.dp)
            )

            // AVAILABLE
            SmallStat(
                label = "Available",
                value =
                    "${String.format(
                        Locale.US,
                        "%,.2f",
                        balance
                    )} USDT"
            )

            Spacer(
                modifier = Modifier.height(6.dp)
            )

            // LIMIT PRICE
            if (orderType == TradeOrderType.LIMIT) {

                OutlinedTextField(
                    value = limitPrice,
                    onValueChange = onLimitPriceChange,
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true,
                    label = {
                        Text(
                            text = "Price",
                            fontSize = 10.sp
                        )
                    },
                    textStyle =
                        LocalTextStyle.current.copy(
                            fontSize = 11.sp
                        )
                )

                Spacer(
                    modifier = Modifier.height(6.dp)
                )
            }

            // =================================================
            // AMOUNT
            // =================================================

            var amountText by rememberSaveable {
                mutableStateOf(
                    quantity.toString()
                )
            }

            LaunchedEffect(quantity) {

                val currentValue =
                    amountText.toDoubleOrNull()

                if (
                    currentValue == null ||
                    kotlin.math.abs(
                        currentValue - quantity
                    ) > 0.000000001
                ) {
                    amountText = quantity.toString()
                }
            }

            OutlinedTextField(
                value = amountText,

                onValueChange = { input ->

                    if (
                        input.isEmpty() ||
                        input.matches(
                            Regex("^\\d*(\\.\\d*)?$")
                        )
                    ) {

                        amountText = input

                        input
                            .toDoubleOrNull()
                            ?.let { value ->

                                if (value > 0.0) {
                                    onQuantityChange(value)
                                }
                            }
                    }
                },

                modifier = Modifier.fillMaxWidth(),

                singleLine = true,

                label = {
                    Text(
                        text = "Amount",
                        fontSize = 10.sp
                    )
                },

                trailingIcon = {
                    Text(
                        text = "BTC",
                        fontSize = 9.sp
                    )
                },

                textStyle =
                    LocalTextStyle.current.copy(
                        fontSize = 11.sp
                    )
            )

            Spacer(
                modifier = Modifier.height(5.dp)
            )

            // PERCENTAGES
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement =
                    Arrangement.SpaceBetween
            ) {

                listOf(
                    25,
                    50,
                    75,
                    100
                ).forEach { percent ->

                    TextButton(
                        onClick = {
                            onPercent(percent)
                        },
                        contentPadding =
                            PaddingValues(
                                horizontal = 4.dp
                            )
                    ) {

                        Text(
                            text = "$percent%",
                            fontSize = 9.sp,
                            color = PurpleMimosa
                        )
                    }
                }
            }

            // SLIDER
            Slider(
                value = quantity
                    .toFloat()
                    .coerceIn(
                        0.000001f,
                        1.0f
                    ),

                onValueChange = {
                    onQuantityChange(
                        it.toDouble()
                    )
                },

                valueRange =
                    0.000001f..1.0f,

                colors = SliderDefaults.colors(
                    thumbColor = PurpleMimosa,
                    activeTrackColor = PurpleMimosa
                )
            )

            Spacer(
                modifier = Modifier.height(4.dp)
            )

            // CALCULATIONS
            val executionPrice =
                if (
                    orderType == TradeOrderType.LIMIT
                ) {
                    limitPrice
                        .toDoubleOrNull()
                        ?: price
                } else {
                    price
                }

            val notional =
                executionPrice * quantity

            val margin =
                if (leverage > 0) {
                    notional / leverage
                } else {
                    notional
                }

            SmallStat(
                label = "Size",
                value =
                    "${String.format(
                        Locale.US,
                        "%,.2f",
                        notional
                    )} USDT"
            )

            Spacer(
                modifier = Modifier.height(3.dp)
            )

            SmallStat(
                label = "Margin",
                value =
                    "${String.format(
                        Locale.US,
                        "%,.2f",
                        margin
                    )} USDT"
            )

            Spacer(
                modifier = Modifier.height(8.dp)
            )

            // OPEN BUTTON
            Button(
                onClick = onOpen,
                modifier = Modifier.fillMaxWidth(),

                enabled =
                    price > 0.0 &&
                        quantity > 0.0 &&
                        position == null,

                colors =
                    ButtonDefaults.buttonColors(
                        containerColor =
                            if (side == PositionSide.LONG) {
                                Green
                            } else {
                                Red
                            }
                    ),

                contentPadding =
                    PaddingValues(
                        vertical = 10.dp
                    )
            ) {

                Text(
                    text =
                        if (side == PositionSide.LONG) {
                            "OPEN LONG"
                        } else {
                            "OPEN SHORT"
                        },
                    fontSize = 11.sp
                )
            }
        }
    }
}

// ============================================================
// ORDER BOOK
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
        colors = CardDefaults.cardColors(
            containerColor = CardBackground
        )
    ) {

        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(8.dp)
        ) {

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement =
                    Arrangement.SpaceBetween
            ) {

                Text(
                    text = "Order Book",
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Bold
                )

                Text(
                    text = symbol,
                    fontSize = 9.sp,
                    color = Color.Gray
                )
            }

            Spacer(
                modifier = Modifier.height(5.dp)
            )

            // HEADERS
            Row(
                modifier = Modifier.fillMaxWidth()
            ) {

                Text(
                    text = "USDT",
                    modifier = Modifier.weight(1f),
                    fontSize = 8.sp,
                    color = Color.Gray
                )

                Text(
                    text = "Price",
                    modifier = Modifier.weight(1f),
                    textAlign = TextAlign.Center,
                    fontSize = 8.sp,
                    color = Color.Gray
                )

                Text(
                    text = "Amount",
                    modifier = Modifier.weight(1f),
                    textAlign = TextAlign.End,
                    fontSize = 8.sp,
                    color = Color.Gray
                )
            }

            Spacer(
                modifier = Modifier.height(3.dp)
            )

            // ASKS
            asks
                .take(6)
                .asReversed()
                .forEach { level ->

                    OrderBookLine(
                        level = level,
                        color = Red
                    )
                }

            // CURRENT PRICE
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(
                        Color(0xFFF3EDF7),
                        RoundedCornerShape(5.dp)
                    )
                    .padding(vertical = 5.dp)
            ) {

                Text(
                    text =
                        if (currentPrice > 0) {
                            formatPrice(currentPrice)
                        } else {
                            "—"
                        },

                    modifier = Modifier.fillMaxWidth(),

                    textAlign = TextAlign.Center,

                    fontSize = 11.sp,

                    fontWeight = FontWeight.Bold,

                    color = PurpleMimosa
                )
            }

            Spacer(
                modifier = Modifier.height(3.dp)
            )

            // BIDS
            bids
                .take(6)
                .forEach { level ->

                    OrderBookLine(
                        level = level,
                        color = Green
                    )
                }

            Spacer(
                modifier = Modifier.height(6.dp)
            )

            // RATIO
            val buyTotal =
                bids
                    .take(10)
                    .sumOf { it.quantity }

            val sellTotal =
                asks
                    .take(10)
                    .sumOf { it.quantity }

            val total =
                (buyTotal + sellTotal)
                    .takeIf { it > 0.0 }
                    ?: 1.0

            val buyPct =
                (buyTotal / total) * 100.0

            val sellPct =
                100.0 - buyPct

            Text(
                text = "Order Ratio",
                fontSize = 9.sp,
                fontWeight = FontWeight.Bold
            )

            Spacer(
                modifier = Modifier.height(4.dp)
            )

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement =
                    Arrangement.SpaceBetween
            ) {

                Text(
                    text =
                        "Buy ${
                            String.format(
                                Locale.US,
                                "%.1f",
                                buyPct
                            )
                        }%",

                    fontSize = 9.sp,
                    color = Green
                )

                Text(
                    text =
                        "Sell ${
                            String.format(
                                Locale.US,
                                "%.1f",
                                sellPct
                            )
                        }%",

                    fontSize = 9.sp,
                    color = Red
                )
            }

            Spacer(
                modifier = Modifier.height(4.dp)
            )

            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(6.dp)
            ) {

                if (buyPct > 0f) {

                    Box(
                        modifier = Modifier
                            .weight(
                                buyPct
                                    .toFloat()
                                    .coerceAtLeast(0.01f)
                            )
                            .fillMaxHeight()
                            .background(
                                Green,
                                RoundedCornerShape(4.dp)
                            )
                    )
                }

                Spacer(
                    modifier = Modifier.width(2.dp)
                )

                if (sellPct > 0f) {

                    Box(
                        modifier = Modifier
                            .weight(
                                sellPct
                                    .toFloat()
                                    .coerceAtLeast(0.01f)
                            )
                            .fillMaxHeight()
                            .background(
                                Red,
                                RoundedCornerShape(4.dp)
                            )
                    )
                }
            }
        }
    }
}

// ============================================================
// ORDER BOOK LINE
// ============================================================

@Composable
private fun OrderBookLine(
    level: OrderBookLevel,
    color: Color
) {

    val usdtValue =
        level.price * level.quantity

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 2.dp)
    ) {

        Text(
            text =
                String.format(
                    Locale.US,
                    "%.2f",
                    usdtValue
                ),

            modifier = Modifier.weight(1f),

            fontSize = 8.sp,

            color = Color.Gray
        )

        Text(
            text = formatPrice(level.price),

            modifier = Modifier.weight(1f),

            textAlign = TextAlign.Center,

            fontSize = 8.sp,

            color = color
        )

        Text(
            text =
                String.format(
                    Locale.US,
                    "%.4f",
                    level.quantity
                ),

            modifier = Modifier.weight(1f),

            textAlign = TextAlign.End,

            fontSize = 8.sp,

            color = Color.Gray
        )
    }
}

// ============================================================
// BOTTOM TABS
// ============================================================

private enum class TradeBottomTab {
    POSITION,
    PENDING,
    HISTORY
}

@Composable
private fun BottomTradeTabs(
    selected: TradeBottomTab,
    onSelected: (TradeBottomTab) -> Unit
) {

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .background(CardBackground)
            .padding(
                horizontal = 7.dp,
                vertical = 5.dp
            ),
        horizontalArrangement =
            Arrangement.spacedBy(5.dp)
    ) {

        BottomTabButton(
            title = "Active Position",
            selected =
                selected == TradeBottomTab.POSITION,
            onClick = {
                onSelected(TradeBottomTab.POSITION)
            }
        )

        BottomTabButton(
            title = "Pending",
            selected =
                selected == TradeBottomTab.PENDING,
            onClick = {
                onSelected(TradeBottomTab.PENDING)
            }
        )

        BottomTabButton(
            title = "History",
            selected =
                selected == TradeBottomTab.HISTORY,
            onClick = {
                onSelected(TradeBottomTab.HISTORY)
            }
        )
    }
}

@Composable
private fun RowScope.BottomTabButton(
    title: String,
    selected: Boolean,
    onClick: () -> Unit
) {

    TextButton(
        onClick = onClick,
        modifier = Modifier.weight(1f)
    ) {

        Text(
            text = title,
            fontSize = 9.sp,
            fontWeight =
                if (selected) {
                    FontWeight.Bold
                } else {
                    FontWeight.Normal
                },
            color =
                if (selected) {
                    PurpleMimosa
                } else {
                    Color.Gray
                }
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

        TradeBottomTab.POSITION -> {

            ActivePositionContent(
                position = position,
                currentPrice = currentPrice,
                pnl = pnl,
                onClose = onClose
            )
        }

        TradeBottomTab.PENDING -> {

            PendingContent(
                orders = pendingOrders,
                onCancel = onCancelPending
            )
        }

        TradeBottomTab.HISTORY -> {

            HistoryContent(
                history = history
            )
        }
    }
}

// ============================================================
// ACTIVE POSITION
// ============================================================

@Composable
private fun ActivePositionContent(
    position: DemoPosition?,
    currentPrice: Double,
    pnl: Double,
    onClose: () -> Unit
) {

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(
                horizontal = 7.dp,
                vertical = 4.dp
            ),

        shape = RoundedCornerShape(9.dp),

        colors = CardDefaults.cardColors(
            containerColor = CardBackground
        )
    ) {

        if (position == null) {

            Text(
                text = "No active position",

                modifier = Modifier
                    .fillMaxWidth()
                    .padding(14.dp),

                textAlign = TextAlign.Center,

                fontSize = 10.sp,

                color = Color.Gray
            )

        } else {

            Column(
                modifier = Modifier.padding(10.dp)
            ) {

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement =
                        Arrangement.SpaceBetween
                ) {

                    Text(
                        text =
                            if (
                                position.side ==
                                PositionSide.LONG
                            ) {
                                "LONG"
                            } else {
                                "SHORT"
                            },

                        fontWeight = FontWeight.Bold,

                        color =
                            if (
                                position.side ==
                                PositionSide.LONG
                            ) {
                                Green
                            } else {
                                Red
                            }
                    )

                    Text(
                        text =
                            "${position.symbol} ${position.leverage}x",

                        fontSize = 10.sp
                    )
                }

                Spacer(
                    modifier = Modifier.height(6.dp)
                )

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement =
                        Arrangement.SpaceBetween
                ) {

                    BottomValue(
                        label = "Entry",
                        value =
                            formatPrice(
                                position.entryPrice
                            )
                    )

                    BottomValue(
                        label = "Mark",
                        value =
                            formatPrice(
                                currentPrice
                            )
                    )

                    BottomValue(
                        label = "Qty",
                        value =
                            String.format(
                                Locale.US,
                                "%.6f",
                                position.quantity
                            )
                    )

                    BottomValue(
                        label = "PnL",
                        value =
                            TradeViewModel.formatMoney(
                                pnl
                            ),
                        color =
                            if (pnl >= 0) {
                                Green
                            } else {
                                Red
                            }
                    )
                }

                Spacer(
                    modifier = Modifier.height(7.dp)
                )

                Button(
                    onClick = onClose,
                    modifier = Modifier.fillMaxWidth(),
                    colors =
                        ButtonDefaults.buttonColors(
                            containerColor = Red
                        )
                ) {

                    Text(
                        text = "CLOSE POSITION",
                        fontSize = 10.sp
                    )
                }
            }
        }
    }
}

// ============================================================
// PENDING
// ============================================================

@Composable
private fun PendingContent(
    orders: List<PendingOrder>,
    onCancel: (Long) -> Unit
) {

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(
                horizontal = 7.dp,
                vertical = 4.dp
            ),

        shape = RoundedCornerShape(9.dp),

        colors = CardDefaults.cardColors(
            containerColor = CardBackground
        )
    ) {

        if (orders.isEmpty()) {

            Text(
                text = "No pending orders",

                modifier = Modifier
                    .fillMaxWidth()
                    .padding(14.dp),

                textAlign = TextAlign.Center,

                fontSize = 10.sp,

                color = Color.Gray
            )

        } else {

            Column(
                modifier = Modifier.padding(8.dp)
            ) {

                orders.forEach { order ->

                    PendingRow(
                        order = order,
                        onCancel = {
                            onCancel(order.id)
                        }
                    )

                    HorizontalDivider()
                }
            }
        }
    }
}

// ============================================================
// PENDING ROW
// ============================================================

@Composable
private fun PendingRow(
    order: PendingOrder,
    onCancel: () -> Unit
) {

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 6.dp),

        verticalAlignment =
            Alignment.CenterVertically
    ) {

        Column(
            modifier = Modifier.weight(1f)
        ) {

            Text(
                text =
                    "${order.symbol} ${
                        if (
                            order.side ==
                            PositionSide.LONG
                        ) {
                            "LONG"
                        } else {
                            "SHORT"
                        }
                    }",

                fontSize = 9.sp,

                fontWeight = FontWeight.Bold
            )

            Text(
                text =
                    "Limit ${
                        formatPrice(order.price)
                    } • ${
                        String.format(
                            Locale.US,
                            "%.6f",
                            order.quantity
                        )
                    }",

                fontSize = 8.sp,

                color = Color.Gray
            )
        }

        Text(
            text = "${order.leverage}x",
            fontSize = 9.sp
        )

        TextButton(
            onClick = onCancel
        ) {

            Text(
                text = "Cancel",
                fontSize = 9.sp,
                color = Red
            )
        }
    }
}

// ============================================================
// HISTORY
// ============================================================

@Composable
private fun HistoryContent(
    history: List<TradeHistory>
) {

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(
                horizontal = 7.dp,
                vertical = 4.dp
            ),

        shape = RoundedCornerShape(9.dp),

        colors = CardDefaults.cardColors(
            containerColor = CardBackground
        )
    ) {

        if (history.isEmpty()) {

            Text(
                text = "No trading history",

                modifier = Modifier
                    .fillMaxWidth()
                    .padding(14.dp),

                textAlign = TextAlign.Center,

                fontSize = 10.sp,

                color = Color.Gray
            )

        } else {

            LazyColumn(
                modifier = Modifier.heightIn(
                    max = 160.dp
                )
            ) {

                items(
                    items = history,
                    key = { it.id }
                ) { item ->

                    HistoryRow(item)

                    HorizontalDivider()
                }
            }
        }
    }
}

// ============================================================
// HISTORY ROW
// ============================================================

@Composable
private fun HistoryRow(
    item: TradeHistory
) {

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(
                horizontal = 9.dp,
                vertical = 7.dp
            ),

        verticalAlignment =
            Alignment.CenterVertically
    ) {

        Column(
            modifier = Modifier.weight(1f)
        ) {

            Text(
                text =
                    "${item.symbol} ${
                        if (
                            item.side ==
                            PositionSide.LONG
                        ) {
                            "LONG"
                        } else {
                            "SHORT"
                        }
                    }",

                fontSize = 9.sp,

                fontWeight = FontWeight.Bold
            )

            Text(
                text =
                    "${formatPrice(item.entryPrice)} → ${
                        formatPrice(item.exitPrice)
                    }",

                fontSize = 8.sp,

                color = Color.Gray
            )
        }

        Text(
            text =
                TradeViewModel.formatMoney(
                    item.pnl
                ),

            fontSize = 10.sp,

            fontWeight = FontWeight.Bold,

            color =
                if (item.pnl >= 0) {
                    Green
                } else {
                    Red
                }
        )
    }
}

// ============================================================
// SMALL COMPONENTS
// ============================================================

@Composable
private fun RowScope.SideButton(
    text: String,
    selected: Boolean,
    color: Color,
    onClick: () -> Unit
) {

    Button(
        onClick = onClick,
        modifier = Modifier.weight(1f),

        colors =
            ButtonDefaults.buttonColors(
                containerColor =
                    if (selected) {
                        color
                    } else {
                        Color(0xFFE7E7EA)
                    },

                contentColor =
                    if (selected) {
                        Color.White
                    } else {
                        Color.DarkGray
                    }
            ),

        shape = RoundedCornerShape(7.dp),

        contentPadding =
            PaddingValues(vertical = 7.dp)
    ) {

        Text(
            text = text,
            fontSize = 10.sp
        )
    }
}

@Composable
private fun SmallOrderType(
    text: String,
    selected: Boolean,
    onClick: () -> Unit
) {

    TextButton(
        onClick = onClick,

        contentPadding =
            PaddingValues(
                horizontal = 4.dp,
                vertical = 0.dp
            )
    ) {

        Text(
            text = text,

            fontSize = 9.sp,

            fontWeight =
                if (selected) {
                    FontWeight.Bold
                } else {
                    FontWeight.Normal
                },

            color =
                if (selected) {
                    PurpleMimosa
                } else {
                    Color.Gray
                }
        )
    }
}

@Composable
private fun SmallStat(
    label: String,
    value: String
) {

    Column {

        Text(
            text = label,
            fontSize = 8.sp,
            color = Color.Gray
        )

        Text(
            text = value,
            fontSize = 10.sp,
            fontWeight = FontWeight.Bold
        )
    }
}

@Composable
private fun BottomValue(
    label: String,
    value: String,
    color: Color = Color.Black
) {

    Column {

        Text(
            text = label,
            fontSize = 8.sp,
            color = Color.Gray
        )

        Text(
            text = value,
            fontSize = 9.sp,
            fontWeight = FontWeight.Bold,
            color = color
        )
    }
}

// ============================================================
// FORMATTING
// ============================================================

private fun formatPrice(
    value: Double
): String {

    if (
        value.isNaN() ||
        value.isInfinite()
    ) {
        return "0"
    }

    return if (value >= 1.0) {

        String.format(
            Locale.US,
            "%,.2f",
            value
        )

    } else {

        String.format(
            Locale.US,
            "%.8f",
            value
        )
            .trimEnd('0')
            .trimEnd('.')
    }
}

private fun formatSignedPct(
    value: Double
): String {

    return String.format(
        Locale.US,
        "%+.2f%%",
        value
    )
}

private fun formatFunding(
    value: Double
): String {

    return String.format(
        Locale.US,
        "%+.4f%%",
        value * 100.0
    )
}

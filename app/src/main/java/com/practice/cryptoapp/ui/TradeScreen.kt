package com.practice.cryptoapp.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.practice.cryptoapp.DemoAccountStore
import com.practice.cryptoapp.DemoPosition
import com.practice.cryptoapp.OrderBookLevel
import com.practice.cryptoapp.PositionSide
import com.practice.cryptoapp.PurpleMimosa
import com.practice.cryptoapp.TradeOrderType
import com.practice.cryptoapp.TradeViewModel
import kotlinx.coroutines.delay
import java.util.Locale

private val Green =
    Color(0xFF16A34A)

private val Red =
    Color(0xFFDC2626)

private val DarkBackground =
    Color(0xFFF7F7F9)

// ============================================================
// TRADE SCREEN
// ============================================================

@Composable
fun TradeScreen(
    vm: TradeViewModel =
        viewModel()
) {

    val symbol by
        vm.symbol.collectAsState()

    val price by
        vm.price.collectAsState()

    val bids by
        vm.bids.collectAsState()

    val asks by
        vm.asks.collectAsState()

    val orderType by
        vm.orderType.collectAsState()

    val side by
        vm.side.collectAsState()

    val leverage by
        vm.leverage.collectAsState()

    val quantity by
        vm.quantity.collectAsState()

    val limitPrice by
        vm.limitPrice.collectAsState()

    val balance by
        DemoAccountStore.balance.collectAsState()

    val position by
        DemoAccountStore.position.collectAsState()

    val message by
        vm.message.collectAsState()

    val unrealizedPnl =
        vm.unrealizedPnl()

    var coinSearch by
        rememberSaveable {
            mutableStateOf("")
        }

    var showCoinSearch by
        remember {
            mutableStateOf(false)
        }

    var showLeverage by
        remember {
            mutableStateOf(false)
        }

    var showTpSl by
        remember {
            mutableStateOf(false)
        }

    Column(
        modifier =
            Modifier
                .fillMaxSize()
                .background(
                    DarkBackground
                )
    ) {

        // ====================================================
        // TOP BAR
        // ====================================================

        TradeTopBar(
            symbol = symbol,
            price = price,
            onCoinClick = {
                showCoinSearch = true
            }
        )

        // ====================================================
        // CHART
        // ====================================================

        PriceChart(
            prices = vm.priceHistory.collectAsState().value,
            price = price
        )

        // ====================================================
        // TIMEFRAME
        // ====================================================

        TimeframeBar()

        // ====================================================
        // ORDER BOOK
        // ====================================================

        OrderBookSection(
            bids = bids,
            asks = asks,
            currentPrice = price
        )

        // ====================================================
        // ORDER PANEL
        // ====================================================

        TradeOrderPanel(
            balance = balance,
            price = price,
            orderType = orderType,
            side = side,
            leverage = leverage,
            quantity = quantity,
            limitPrice = limitPrice,
            position = position,
            unrealizedPnl = unrealizedPnl,
            onOrderTypeChange = {
                vm.setOrderType(it)
            },
            onSideChange = {
                vm.setSide(it)
            },
            onLeverageClick = {
                showLeverage = true
            },
            onQuantityChange = {
                vm.setQuantity(it)
            },
            onLimitPriceChange = {
                vm.setLimitPrice(it)
            },
            onOpen = {
                vm.openPosition()
            },
            onClose = {
                vm.closePosition()
            },
            onTpSl = {
                showTpSl = true
            },
            onReset = {
                vm.resetDemo()
            }
        )

        if (message.isNotBlank()) {

            Text(
                message,
                modifier =
                    Modifier
                        .fillMaxWidth()
                        .padding(
                            horizontal = 14.dp,
                            vertical = 5.dp
                        ),
                fontSize = 10.sp,
                color = PurpleMimosa
            )
        }
    }

    // ========================================================
    // COIN SEARCH DIALOG
    // ========================================================

    if (showCoinSearch) {

        AlertDialog(
            onDismissRequest = {
                showCoinSearch = false
            },
            title = {
                Text("Trading Pair")
            },
            text = {

                Column {

                    OutlinedTextField(
                        value =
                            coinSearch,
                        onValueChange = {
                            coinSearch = it.uppercase()
                        },
                        singleLine = true,
                        modifier =
                            Modifier.fillMaxWidth(),
                        placeholder = {
                            Text("BTCUSDT")
                        }
                    )

                    Spacer(
                        Modifier.height(8.dp)
                    )

                    Text(
                        "Example: BTCUSDT, ETHUSDT, SOLUSDT",
                        fontSize = 10.sp,
                        color = Color.Gray
                    )
                }
            },
            confirmButton = {

                TextButton(
                    onClick = {

                        if (
                            coinSearch.isNotBlank()
                        ) {

                            vm.changeSymbol(
                                coinSearch
                            )
                        }

                        showCoinSearch = false
                    }
                ) {

                    Text(
                        "Apply",
                        color =
                            PurpleMimosa
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
                            modifier =
                                Modifier
                                    .fillMaxWidth()
                                    .clickable {

                                        vm.setLeverage(
                                            value
                                        )

                                        showLeverage =
                                            false
                                    }
                                    .padding(
                                        12.dp
                                    ),
                            horizontalArrangement =
                                Arrangement.SpaceBetween
                        ) {

                            Text(
                                "${value}x"
                            )

                            if (
                                value ==
                                leverage
                            ) {

                                Icon(
                                    Icons.Default.Check,
                                    contentDescription =
                                        null,
                                    tint =
                                        PurpleMimosa
                                )
                            }
                        }
                    }
                }
            },
            confirmButton = {}
        )
    }

    // ========================================================
    // TP / SL DEMO DIALOG
    // ========================================================

    if (showTpSl) {

        AlertDialog(
            onDismissRequest = {
                showTpSl = false
            },
            title = {
                Text(
                    "Take Profit / Stop Loss"
                )
            },
            text = {
                Text(
                    "TP/SL controls are prepared for the demo trading engine. No real order is sent to Binance.",
                    fontSize = 12.sp
                )
            },
            confirmButton = {

                TextButton(
                    onClick = {
                        showTpSl = false
                    }
                ) {

                    Text(
                        "OK",
                        color =
                            PurpleMimosa
                    )
                }
            }
        )
    }
}

// ============================================================
// TOP BAR
// ============================================================

@Composable
private fun TradeTopBar(
    symbol: String,
    price: Double,
    onCoinClick: () -> Unit
) {

    Row(
        modifier =
            Modifier
                .fillMaxWidth()
                .background(
                    Color.White
                )
                .padding(
                    horizontal = 12.dp,
                    vertical = 10.dp
                ),
        verticalAlignment =
            Alignment.CenterVertically
    ) {

        Icon(
            Icons.Default.ArrowBack,
            contentDescription =
                "Back",
            modifier =
                Modifier.size(22.dp)
        )

        Spacer(
            Modifier.width(10.dp)
        )

        Column(
            modifier =
                Modifier
                    .weight(1f)
                    .clickable {
                        onCoinClick()
                    }
        ) {

            Row(
                verticalAlignment =
                    Alignment.CenterVertically
            ) {

                Text(
                    symbol,
                    fontSize = 16.sp,
                    fontWeight =
                        FontWeight.Bold
                )

                Icon(
                    Icons.Default.KeyboardArrowDown,
                    contentDescription =
                        null,
                    modifier =
                        Modifier.size(18.dp)
                )
            }

            Text(
                "USDT Perpetual • Demo",
                fontSize = 9.sp,
                color = Color.Gray
            )
        }

        Column(
            horizontalAlignment =
                Alignment.End
        ) {

            Text(
                if (price > 0.0)
                    formatPrice(price)
                else
                    "—",
                fontSize = 15.sp,
                fontWeight =
                    FontWeight.Bold
            )

            Text(
                "LIVE",
                fontSize = 8.sp,
                color = Green
            )
        }
    }
}

// ============================================================
// PRICE CHART
// ============================================================

@Composable
private fun PriceChart(
    prices: List<Double>,
    price: Double
) {

    Card(
        modifier =
            Modifier
                .fillMaxWidth()
                .height(205.dp)
                .padding(
                    horizontal = 8.dp,
                    vertical = 5.dp
                ),
        shape =
            RoundedCornerShape(
                10.dp
            )
    ) {

        Column(
            Modifier.padding(10.dp)
        ) {

            Row(
                Modifier.fillMaxWidth(),
                horizontalArrangement =
                    Arrangement.SpaceBetween
            ) {

                Text(
                    "BTC/USDT",
                    fontSize = 11.sp,
                    fontWeight =
                        FontWeight.Bold
                )

                Text(
                    "Candles",
                    fontSize = 9.sp,
                    color = PurpleMimosa
                )
            }

            Spacer(
                Modifier.height(8.dp)
            )

            Box(
                modifier =
                    Modifier
                        .fillMaxSize()
            ) {

                if (prices.size < 2) {

                    Box(
                        Modifier.fillMaxSize(),
                        contentAlignment =
                            Alignment.Center
                    ) {

                        Text(
                            "Loading live chart...",
                            fontSize = 11.sp,
                            color = Color.Gray
                        )
                    }

                } else {

                    androidx.compose.foundation.Canvas(
                        modifier =
                            Modifier.fillMaxSize()
                    ) {

                        val min =
                            prices.minOrNull()
                                ?: price

                        val max =
                            prices.maxOrNull()
                                ?: price

                        val range =
                            (max - min)
                                .takeIf {
                                    it > 0
                                }
                                ?: 1.0

                        val stepX =
                            size.width /
                                (prices.size - 1)

                        val points =
                            prices.mapIndexed {
                                    index,
                                    value ->

                                val x =
                                    index *
                                        stepX

                                val y =
                                    size.height -
                                        (
                                            (
                                                value -
                                                    min
                                            ) /
                                                range
                                        ).toFloat() *
                                            size.height

                                androidx.compose.ui.geometry
                                    .Offset(
                                        x.toFloat(),
                                        y
                                    )
                            }

                        for (
                            i in 0 until
                                    points.size - 1
                        ) {

                            drawLine(
                                color =
                                    PurpleMimosa,
                                start =
                                    points[i],
                                end =
                                    points[i + 1],
                                strokeWidth =
                                    3f
                            )
                        }
                    }
                }
            }
        }
    }
}

// ============================================================
// TIMEFRAME BAR
// ============================================================

@Composable
private fun TimeframeBar() {

    Row(
        modifier =
            Modifier
                .fillMaxWidth()
                .horizontalScroll(
                    rememberScrollState()
                )
                .padding(
                    horizontal = 8.dp
                ),
        horizontalArrangement =
            Arrangement.spacedBy(5.dp)
    ) {

        listOf(
            "1m",
            "5m",
            "15m",
            "30m",
            "1H",
            "4H",
            "1D"
        ).forEach { timeframe ->

            AssistChip(
                onClick = {},
                label = {
                    Text(
                        timeframe,
                        fontSize = 9.sp
                    )
                }
            )
        }
    }
}

// ============================================================
// ORDER BOOK
// ============================================================

@Composable
private fun OrderBookSection(
    bids: List<OrderBookLevel>,
    asks: List<OrderBookLevel>,
    currentPrice: Double
) {

    Card(
        modifier =
            Modifier
                .fillMaxWidth()
                .padding(
                    horizontal = 8.dp,
                    vertical = 6.dp
                ),
        shape =
            RoundedCornerShape(
                10.dp
            )
    ) {

        Column(
            Modifier.padding(10.dp)
        ) {

            Row(
                Modifier.fillMaxWidth(),
                horizontalArrangement =
                    Arrangement.SpaceBetween
            ) {

                Text(
                    "Order Book",
                    fontWeight =
                        FontWeight.Bold,
                    fontSize = 12.sp
                )

                Text(
                    "Price          Size",
                    fontSize = 9.sp,
                    color = Color.Gray
                )
            }

            Spacer(
                Modifier.height(5.dp)
            )

            asks
                .take(4)
                .asReversed()
                .forEach { level ->

                    OrderBookRow(
                        level = level,
                        positive = false
                    )
                }

            HorizontalDivider()

            Text(
                if (currentPrice > 0.0)
                    formatPrice(currentPrice)
                else
                    "—",
                modifier =
                    Modifier
                        .fillMaxWidth()
                        .padding(
                            vertical = 5.dp
                        ),
                fontSize = 12.sp,
                fontWeight =
                    FontWeight.Bold,
                color =
                    PurpleMimosa
            )

            bids
                .take(4)
                .forEach { level ->

                    OrderBookRow(
                        level = level,
                        positive = true
                    )
                }
        }
    }
}

@Composable
private fun OrderBookRow(
    level: OrderBookLevel,
    positive: Boolean
) {

    Row(
        modifier =
            Modifier
                .fillMaxWidth()
                .padding(
                    vertical = 2.dp
                ),
        horizontalArrangement =
            Arrangement.SpaceBetween
    ) {

        Text(
            formatPrice(level.price),
            fontSize = 9.sp,
            color =
                if (positive)
                    Green
                else
                    Red
        )

        Text(
            String.format(
                Locale.US,
                "%.4f",
                level.quantity
            ),
            fontSize = 9.sp,
            color = Color.Gray
        )
    }
}

// ============================================================
// ORDER PANEL
// ============================================================

@Composable
private fun TradeOrderPanel(
    balance: Double,
    price: Double,
    orderType: TradeOrderType,
    side: PositionSide,
    leverage: Int,
    quantity: Double,
    limitPrice: String,
    position: DemoPosition?,
    unrealizedPnl: Double,
    onOrderTypeChange:
        (TradeOrderType) -> Unit,
    onSideChange:
        (PositionSide) -> Unit,
    onLeverageClick: () -> Unit,
    onQuantityChange:
        (Double) -> Unit,
    onLimitPriceChange:
        (String) -> Unit,
    onOpen: () -> Unit,
    onClose: () -> Unit,
    onTpSl: () -> Unit,
    onReset: () -> Unit
) {

    Card(
        modifier =
            Modifier
                .fillMaxWidth()
                .padding(
                    horizontal = 8.dp,
                    vertical = 5.dp
                ),
        shape =
            RoundedCornerShape(
                12.dp
            )
    ) {

        Column(
            Modifier.padding(12.dp)
        ) {

            // ------------------------------------------------
            // BALANCE
            // ------------------------------------------------

            Row(
                Modifier.fillMaxWidth(),
                horizontalArrangement =
                    Arrangement.SpaceBetween
            ) {

                Column {

                    Text(
                        "Available",
                        fontSize = 8.sp,
                        color = Color.Gray
                    )

                    Text(
                        "$" +
                            String.format(
                                Locale.US,
                                "%,.2f",
                                balance
                            ),
                        fontSize = 13.sp,
                        fontWeight =
                            FontWeight.Bold
                    )
                }

                TextButton(
                    onClick = onReset
                ) {

                    Text(
                        "Reset Demo",
                        color =
                            PurpleMimosa
                    )
                }
            }

            Spacer(
                Modifier.height(8.dp)
            )

            // ------------------------------------------------
            // LONG / SHORT
            // ------------------------------------------------

            Row(
                Modifier.fillMaxWidth()
            ) {

                SideButton(
                    title = "Long",
                    selected =
                        side ==
                            PositionSide.LONG,
                    color =
                        Green,
                    onClick = {
                        onSideChange(
                            PositionSide.LONG
                        )
                    }
                )

                Spacer(
                    Modifier.width(6.dp)
                )

                SideButton(
                    title = "Short",
                    selected =
                        side ==
                            PositionSide.SHORT,
                    color =
                        Red,
                    onClick = {
                        onSideChange(
                            PositionSide.SHORT
                        )
                    }
                )
            }

            Spacer(
                Modifier.height(8.dp)
            )

            // ------------------------------------------------
            // ORDER TYPE
            // ------------------------------------------------

            Row(
                Modifier.fillMaxWidth(),
                horizontalArrangement =
                    Arrangement.SpaceBetween
            ) {

                Text(
                    "Order",
                    fontSize = 10.sp,
                    fontWeight =
                        FontWeight.Bold
                )

                Row {

                    OrderTypeButton(
                        "Market",
                        orderType ==
                            TradeOrderType.MARKET
                    ) {
                        onOrderTypeChange(
                            TradeOrderType.MARKET
                        )
                    }

                    Spacer(
                        Modifier.width(4.dp)
                    )

                    OrderTypeButton(
                        "Limit",
                        orderType ==
                            TradeOrderType.LIMIT
                    ) {
                        onOrderTypeChange(
                            TradeOrderType.LIMIT
                        )
                    }
                }
            }

            Spacer(
                Modifier.height(8.dp)
            )

            // ------------------------------------------------
            // LEVERAGE
            // ------------------------------------------------

            OutlinedButton(
                onClick =
                    onLeverageClick,
                modifier =
                    Modifier.fillMaxWidth()
            ) {

                Text(
                    "${leverage}x Leverage",
                    fontSize = 11.sp
                )
            }

            Spacer(
                Modifier.height(8.dp)
            )

            // ------------------------------------------------
            // LIMIT PRICE
            // ------------------------------------------------

            if (
                orderType ==
                TradeOrderType.LIMIT
            ) {

                OutlinedTextField(
                    value =
                        limitPrice,
                    onValueChange =
                        onLimitPriceChange,
                    modifier =
                        Modifier.fillMaxWidth(),
                    singleLine = true,
                    label = {
                        Text(
                            "Limit Price"
                        )
                    }
                )

                Spacer(
                    Modifier.height(8.dp)
                )
            }

            // ------------------------------------------------
            // QUANTITY
            // ------------------------------------------------

            Text(
                "Quantity",
                fontSize = 10.sp,
                fontWeight =
                    FontWeight.Bold
            )

            Slider(

                value =
                    quantity.toFloat(),

                onValueChange = {
                    onQuantityChange(
                        it.toDouble()
                    )
                },

                valueRange =
                    0.000001f..1.0f,

                colors =
                    SliderDefaults.colors(
                        thumbColor =
                            PurpleMimosa,
                        activeTrackColor =
                            PurpleMimosa
                    )
            )

            Text(
                String.format(
                    Locale.US,
                    "%.6f BTC",
                    quantity
                ),
                fontSize = 10.sp,
                color = Color.Gray
            )

            Spacer(
                Modifier.height(8.dp)
            )

            // ------------------------------------------------
            // ESTIMATE
            // ------------------------------------------------

            val executionPrice =
                if (
                    orderType ==
                    TradeOrderType.LIMIT
                ) {

                    limitPrice
                        .toDoubleOrNull()
                        ?: price

                } else {

                    price
                }

            val notional =
                executionPrice *
                    quantity

            val margin =
                if (
                    leverage > 0
                ) {
                    notional /
                        leverage
                } else {
                    notional
                }

            TradeInfoRow(
                "Price",
                if (executionPrice > 0)
                    formatPrice(executionPrice)
                else
                    "—"
            )

            TradeInfoRow(
                "Size",
                "$" +
                    String.format(
                        Locale.US,
                        "%,.2f",
                        notional
                    )
            )

            TradeInfoRow(
                "Margin",
                "$" +
                    String.format(
                        Locale.US,
                        "%,.2f",
                        margin
                    )
            )

            Spacer(
                Modifier.height(10.dp)
            )

            // ------------------------------------------------
            // OPEN BUTTON
            // ------------------------------------------------

            Button(

                onClick =
                    onOpen,

                modifier =
                    Modifier.fillMaxWidth(),

                enabled =
                    position == null &&
                        price > 0.0,

                colors =
                    ButtonDefaults
                        .buttonColors(
                            containerColor =
                                if (
                                    side ==
                                    PositionSide.LONG
                                ) {
                                    Green
                                } else {
                                    Red
                                }
                        )
            ) {

                Text(
                    if (
                        side ==
                        PositionSide.LONG
                    ) {
                        "OPEN LONG"
                    } else {
                        "OPEN SHORT"
                    }
                )
            }

            // ------------------------------------------------
            // POSITION
            // ------------------------------------------------

            if (
                position != null
            ) {

                Spacer(
                    Modifier.height(10.dp)
                )

                PositionCard(
                    position =
                        position,

                    currentPrice =
                        price,

                    pnl =
                        unrealizedPnl,

                    onClose =
                        onClose,

                    onTpSl =
                        onTpSl
                )
            }
        }
    }
}

// ============================================================
// SIDE BUTTON
// ============================================================

@Composable
private fun RowScope.SideButton(
    title: String,
    selected: Boolean,
    color: Color,
    onClick: () -> Unit
) {

    Button(

        onClick = onClick,

        modifier =
            Modifier.weight(1f),

        colors =
            ButtonDefaults.buttonColors(

                containerColor =
                    if (selected)
                        color
                    else
                        Color.LightGray,

                contentColor =
                    if (selected)
                        Color.White
                    else
                        Color.DarkGray
            ),

        shape =
            RoundedCornerShape(
                8.dp
            )
    ) {

        Text(
            title
        )
    }
}

// ============================================================
// ORDER TYPE BUTTON
// ============================================================

@Composable
private fun OrderTypeButton(
    title: String,
    selected: Boolean,
    onClick: () -> Unit
) {

    TextButton(
        onClick = onClick
    ) {

        Text(
            title,
            color =
                if (selected)
                    PurpleMimosa
                else
                    Color.Gray,

            fontWeight =
                if (selected)
                    FontWeight.Bold
                else
                    FontWeight.Normal
        )
    }
}

// ============================================================
// TRADE INFO
// ============================================================

@Composable
private fun TradeInfoRow(
    label: String,
    value: String
) {

    Row(
        Modifier.fillMaxWidth(),
        horizontalArrangement =
            Arrangement.SpaceBetween
    ) {

        Text(
            label,
            fontSize = 9.sp,
            color = Color.Gray
        )

        Text(
            value,
            fontSize = 9.sp,
            fontWeight =
                FontWeight.Medium
        )
    }

    Spacer(
        Modifier.height(3.dp)
    )
}

private fun formatPrice(value: Double): String {

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

// ============================================================
// POSITION CARD
// ============================================================

@Composable
private fun PositionCard(
    position: DemoPosition,
    currentPrice: Double,
    pnl: Double,
    onClose: () -> Unit,
    onTpSl: () -> Unit
) {

    Card(

        Modifier.fillMaxWidth(),

        colors =
            CardDefaults.cardColors(
                containerColor =
                    Color(0xFFF4F0F8)
            )
    ) {

        Column(
            Modifier.padding(
                10.dp
            )
        ) {

            Row(
                Modifier.fillMaxWidth(),
                horizontalArrangement =
                    Arrangement.SpaceBetween
            ) {

                Text(
                    if (
                        position.side ==
                        PositionSide.LONG
                    ) {
                        "LONG"
                    } else {
                        "SHORT"
                    },
                    fontWeight =
                        FontWeight.Bold,

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
                    "${position.leverage}x",
                    fontWeight =
                        FontWeight.Bold
                )
            }

            Spacer(
                Modifier.height(6.dp)
            )

            TradeInfoRow(
                "Entry",
                formatPrice(
                    position.entryPrice
                )
            )

            TradeInfoRow(
                "Mark",
                formatPrice(
                    currentPrice
                )
            )

            TradeInfoRow(
                "Quantity",
                String.format(
                    Locale.US,
                    "%.6f",
                    position.quantity
                )
            )

            TradeInfoRow(
                "Unrealized PnL",
                TradeViewModel.formatMoney(
                    pnl
                )
            )

            Spacer(
                Modifier.height(8.dp)
            )

            Row(
                Modifier.fillMaxWidth(),
                horizontalArrangement =
                    Arrangement.spacedBy(
                        6.dp
                    )
            ) {

                OutlinedButton(
                    onClick =
                        onTpSl,
                    modifier =
                        Modifier.weight(1f)
                ) {

                    Text(
                        "TP / SL",
                        fontSize = 10.sp
                    )
                }

                Button(
                    onClick =
                        onClose,
                    modifier =
                        Modifier.weight(1f),
                    colors =
                        ButtonDefaults
                            .buttonColors(
                                containerColor =
                                    Red
                            )
                ) {

                    Text(
                        "Close",
                        fontSize = 10.sp
                    )
                }
            }
        }
    }
}

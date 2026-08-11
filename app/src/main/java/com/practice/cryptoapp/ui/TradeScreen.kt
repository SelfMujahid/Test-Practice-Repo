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
import com.practice.cryptoapp.PendingOrder
import com.practice.cryptoapp.PositionSide
import com.practice.cryptoapp.PurpleMimosa
import com.practice.cryptoapp.TradeHistory
import com.practice.cryptoapp.TradeMode
import com.practice.cryptoapp.TradeOrderType
import com.practice.cryptoapp.TradeViewModel
import kotlinx.coroutines.delay
import java.util.Locale
import kotlin.math.abs

private val Green =
    Color(0xFF16A34A)

private val Red =
    Color(0xFFDC2626)

private val ScreenBackground =
    Color(0xFFF5F5F7)

private val CardBackground =
    Color.White

// ============================================================
// TRADE SCREEN
// ============================================================

@Composable
fun TradeScreen(
    vm: TradeViewModel =
        viewModel()
) {

    val mode by
        vm.mode.collectAsState()

    val symbol by
        vm.symbol.collectAsState()

    val price by
        vm.price.collectAsState()

    val high24h by
        vm.high24h.collectAsState()

    val low24h by
        vm.low24h.collectAsState()

    val change24h by
        vm.change24h.collectAsState()

    val fundingRate by
        vm.fundingRate.collectAsState()

    val fundingCountdown by
        vm.fundingCountdown.collectAsState()

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

    val pendingOrders by
        DemoAccountStore.pendingOrders.collectAsState()

    val history by
        DemoAccountStore.history.collectAsState()

    val message by
        vm.message.collectAsState()

    val pnl =
        vm.unrealizedPnl()

    var showPairMenu by
        remember {
            mutableStateOf(false)
        }

    var showLeverage by
        remember {
            mutableStateOf(false)
        }

    var selectedBottomTab by
        rememberSaveable {
            mutableStateOf(
                TradeBottomTab.POSITION
            )
        }

    var pairSearch by
        rememberSaveable {
            mutableStateOf("")
        }

    Column(
        modifier =
            Modifier
                .fillMaxSize()
                .background(
                    ScreenBackground
                )
    ) {

        // ====================================================
        // TOP MODE BAR
        // ====================================================

        ModeBar(
            mode = mode,
            onSelect = {
                vm.setMode(it)
            }
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
        // MAIN SPLIT SECTION
        // ====================================================

        Row(
            modifier =
                Modifier
                    .fillMaxWidth()
                    .weight(
                        1f,
                        fill = false
                    )
                    .padding(
                        horizontal = 7.dp
                    ),
            horizontalArrangement =
                Arrangement.spacedBy(
                    7.dp
                )
        ) {

            // ------------------------------------------------
            // LEFT ORDER PANEL
            // ------------------------------------------------

            OrderPanel(
                modifier =
                    Modifier
                        .weight(1f),
                balance = balance,
                price = price,
                side = side,
                orderType = orderType,
                leverage = leverage,
                quantity = quantity,
                limitPrice = limitPrice,
                position = position,
                onSideChange = {
                    vm.setSide(it)
                },
                onOrderTypeChange = {
                    vm.setOrderType(it)
                },
                onLeverageClick = {
                    showLeverage = true
                },
                onQuantityChange = {
                    vm.setQuantity(it)
                },
                onPercent = {
                    vm.setQuantityPercent(it)
                },
                onLimitPriceChange = {
                    vm.setLimitPrice(it)
                },
                onOpen = {
                    vm.openOrder()
                }
            )

            // ------------------------------------------------
            // RIGHT ORDER BOOK
            // ------------------------------------------------

            OrderBookPanel(
                modifier =
                    Modifier
                        .weight(1f),
                symbol =
                    symbol,
                currentPrice =
                    price,
                bids =
                    bids,
                asks =
                    asks
            )
        }

        // ====================================================
        // BOTTOM TABS
        // ====================================================

        BottomTradeTabs(
            selected =
                selectedBottomTab,
            onSelected = {
                selectedBottomTab =
                    it
            }
        )

        // ====================================================
        // BOTTOM DATA
        // ====================================================

        BottomTradeContent(

            selected =
                selectedBottomTab,

            position =
                position,

            pendingOrders =
                pendingOrders,

            history =
                history,

            currentPrice =
                price,

            pnl =
                pnl,

            onClose =
                {
                    vm.closePosition()
                },

            onCancelPending =
                {
                    vm.cancelPending(it)
                }
        )

        // ====================================================
        // MESSAGE
        // ====================================================

        if (
            message.isNotBlank()
        ) {

            Text(
                message,
                modifier =
                    Modifier
                        .fillMaxWidth()
                        .padding(
                            horizontal = 10.dp,
                            vertical = 4.dp
                        ),
                fontSize =
                    10.sp,
                color =
                    PurpleMimosa
            )
        }
    }

    // ========================================================
    // PAIR SELECTOR
    // ========================================================

    if (
        showPairMenu
    ) {

        AlertDialog(

            onDismissRequest = {
                showPairMenu =
                    false
            },

            title = {
                Text(
                    "Select Futures Pair"
                )
            },

            text = {

                Column {

                    OutlinedTextField(

                        value =
                            pairSearch,

                        onValueChange = {
                            pairSearch =
                                it.uppercase()
                        },

                        modifier =
                            Modifier.fillMaxWidth(),

                        singleLine = true,

                        placeholder = {
                            Text(
                                "BTCUSDT"
                            )
                        }
                    )

                    Spacer(
                        Modifier.height(
                            10.dp
                        )
                    )

                    val pairs =
                        listOf(
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
                        )
                            .filter {
                                it.contains(
                                    pairSearch,
                                    true
                                )
                            }

                    pairs.forEach { pair ->

                        Row(
                            modifier =
                                Modifier
                                    .fillMaxWidth()
                                    .clickable {

                                        vm.changeSymbol(
                                            pair
                                        )

                                        pairSearch =
                                            ""

                                        showPairMenu =
                                            false
                                    }
                                    .padding(
                                        vertical = 11.dp
                                    ),
                            verticalAlignment =
                                Alignment.CenterVertically
                        ) {

                            Text(
                                pair,
                                fontWeight =
                                    FontWeight.Medium
                            )
                        }
                    }
                }
            },

            confirmButton = {

                TextButton(
                    onClick = {

                        if (
                            pairSearch.isNotBlank()
                        ) {

                            vm.changeSymbol(
                                pairSearch
                            )
                        }

                        pairSearch =
                            ""

                        showPairMenu =
                            false
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

    if (
        showLeverage
    ) {

        AlertDialog(

            onDismissRequest = {
                showLeverage =
                    false
            },

            title = {
                Text(
                    "Leverage"
                )
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
                                Arrangement
                                    .SpaceBetween
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
}

// ============================================================
// MODE BAR
// ============================================================

@Composable
private fun ModeBar(
    mode: TradeMode,
    onSelect:
        (TradeMode) -> Unit
) {

    Row(
        modifier =
            Modifier
                .fillMaxWidth()
                .background(
                    CardBackground
                )
                .padding(
                    horizontal = 8.dp,
                    vertical = 6.dp
                ),
        horizontalArrangement =
            Arrangement.spacedBy(
                6.dp
            )
    ) {

        ModeButton(
            title = "Spot",
            selected =
                mode ==
                    TradeMode.SPOT,
            onClick = {
                onSelect(
                    TradeMode.SPOT
                )
            }
        )

        ModeButton(
            title = "Future",
            selected =
                mode ==
                    TradeMode.FUTURES,
            onClick = {
                onSelect(
                    TradeMode.FUTURES
                )
            }
        )

        ModeButton(
            title = "Bot",
            selected =
                mode ==
                    TradeMode.BOT,
            onClick = {
                onSelect(
                    TradeMode.BOT
                )
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

        onClick =
            onClick,

        modifier =
            Modifier.weight(
                1f
            ),

        shape =
            RoundedCornerShape(
                7.dp
            ),

        colors =
            ButtonDefaults
                .buttonColors(

                    containerColor =
                        if (selected)
                            PurpleMimosa
                        else
                            Color(
                                0xFFE9E9ED
                            ),

                    contentColor =
                        if (selected)
                            Color.White
                        else
                            Color.DarkGray
                )
    ) {

        Text(
            title,
            fontSize =
                11.sp
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
        modifier =
            Modifier
                .fillMaxWidth()
                .padding(
                    horizontal = 7.dp,
                    vertical = 4.dp
                ),
        shape =
            RoundedCornerShape(
                10.dp
            ),
        colors =
            CardDefaults.cardColors(
                containerColor =
                    CardBackground
            )
    ) {

        Row(
            modifier =
                Modifier
                    .fillMaxWidth()
                    .padding(
                        9.dp
                    ),
            verticalAlignment =
                Alignment.CenterVertically
        ) {

            // ------------------------------------------------
            // LEFT: PAIR + 24H CHANGE
            // ------------------------------------------------

            Column(
                modifier =
                    Modifier
                        .weight(
                            1.2f
                        )
            ) {

                TextButton(
                    onClick =
                        onPairClick,

                    contentPadding =
                        PaddingValues(
                            0.dp
                        )
                ) {

                    Row(
                        verticalAlignment =
                            Alignment.CenterVertically
                    ) {

                        Text(
                            symbol,
                            fontSize =
                                15.sp,
                            fontWeight =
                                FontWeight.Bold,
                            color =
                                Color.Black
                        )

                        Icon(
                            Icons.Default
                                .KeyboardArrowDown,
                            contentDescription =
                                null,
                            modifier =
                                Modifier.size(
                                    18.dp
                                )
                        )
                    }
                }

                Text(
                    "24h",
                    fontSize =
                        8.sp,
                    color =
                        Color.Gray
                )

                Text(
                    formatSignedPct(
                        change24h
                    ),
                    fontSize =
                        11.sp,
                    fontWeight =
                        FontWeight.Bold,
                    color =
                        if (
                            change24h >=
                            0
                        ) {
                            Green
                        } else {
                            Red
                        }
                )
            }

            // ------------------------------------------------
            // CENTER: 24H LOW/HIGH
            // ------------------------------------------------

            Column(
                modifier =
                    Modifier.weight(
                        1f
                    )
            ) {

                SmallStat(
                    "24h Low",
                    if (
                        low24h > 0
                    )
                        formatPrice(
                            low24h
                        )
                    else
                        "—"
                )

                Spacer(
                    Modifier.height(
                        5.dp
                    )
                )

                SmallStat(
                    "24h High",
                    if (
                        high24h > 0
                    )
                        formatPrice(
                            high24h
                        )
                    else
                        "—"
                )
            }

            // ------------------------------------------------
            // RIGHT: FUNDING
            // ------------------------------------------------

            Column(
                modifier =
                    Modifier.weight(
                        1f
                    ),
                horizontalAlignment =
                    Alignment.End
            ) {

                Text(
                    "Funding Rate",
                    fontSize =
                        8.sp,
                    color =
                        Color.Gray
                )

                Text(
                    formatFunding(
                        fundingRate
                    ),
                    fontSize =
                        11.sp,
                    fontWeight =
                        FontWeight.Bold,
                    color =
                        if (
                            fundingRate >=
                            0
                        ) {
                            Green
                        } else {
                            Red
                        }
                )

                Spacer(
                    Modifier.height(
                        2.dp
                    )
                )

                Text(
                    "Next",
                    fontSize =
                        8.sp,
                    color =
                        Color.Gray
                )

                Text(
                    fundingCountdown,
                    fontSize =
                        10.sp,
                    fontWeight =
                        FontWeight.Bold,
                    color =
                        PurpleMimosa
                )
            }
        }

        Divider(
            modifier =
                Modifier.padding(
                    horizontal = 9.dp
                )
        )

        Text(
            if (price > 0)
                formatPrice(price)
            else
                "Loading...",
            modifier =
                Modifier
                    .fillMaxWidth()
                    .padding(
                        horizontal = 9.dp,
                        vertical = 5.dp
                    ),
            fontSize =
                11.sp,
            color =
                Color.Gray
        )
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
    onSideChange:
        (PositionSide) -> Unit,
    onOrderTypeChange:
        (TradeOrderType) -> Unit,
    onLeverageClick: () -> Unit,
    onQuantityChange:
        (Double) -> Unit,
    onPercent:
        (Int) -> Unit,
    onLimitPriceChange:
        (String) -> Unit,
    onOpen: () -> Unit
) {

    Card(
        modifier =
            modifier
                .fillMaxHeight(),
        shape =
            RoundedCornerShape(
                10.dp
            ),
        colors =
            CardDefaults.cardColors(
                containerColor =
                    CardBackground
            )
    ) {

        Column(
            modifier =
                Modifier
                    .fillMaxSize()
                    .padding(
                        9.dp
                    )
        ) {

            Text(
                "Place Order",
                fontSize =
                    12.sp,
                fontWeight =
                    FontWeight.Bold
            )

            Spacer(
                Modifier.height(
                    7.dp
                )
            )

            // ------------------------------------------------
            // LONG / SHORT
            // ------------------------------------------------

            Row(
                Modifier.fillMaxWidth()
            ) {

                SideButton(
                    text = "Long",
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
                    Modifier.width(
                        5.dp
                    )
                )

                SideButton(
                    text = "Short",
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
                Modifier.height(
                    7.dp
                )
            )

            // ------------------------------------------------
            // MARKET / LIMIT
            // ------------------------------------------------

            Row(
                Modifier.fillMaxWidth(),
                horizontalArrangement =
                    Arrangement.SpaceBetween
            ) {

                SmallOrderType(
                    "Market",
                    orderType ==
                        TradeOrderType.MARKET
                ) {
                    onOrderTypeChange(
                        TradeOrderType.MARKET
                    )
                }

                SmallOrderType(
                    "Limit",
                    orderType ==
                        TradeOrderType.LIMIT
                ) {
                    onOrderTypeChange(
                        TradeOrderType.LIMIT
                    )
                }
            }

            Spacer(
                Modifier.height(
                    7.dp
                )
            )

            // ------------------------------------------------
            // LEVERAGE
            // ------------------------------------------------

            OutlinedButton(
                onClick =
                    onLeverageClick,
                modifier =
                    Modifier.fillMaxWidth(),
                contentPadding =
                    PaddingValues(
                        vertical = 6.dp
                    )
            ) {

                Text(
                    "${leverage}x",
                    fontSize =
                        10.sp
                )
            }

            Spacer(
                Modifier.height(
                    7.dp
                )
            )

            // ------------------------------------------------
            // AVAILABLE
            // ------------------------------------------------

            SmallStat(
                "Available",
                "${
                    String.format(
                        Locale.US,
                        "%,.2f",
                        balance
                    )
               } USDT"
            )

            Spacer(
                Modifier.height(
                    6.dp
                )
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
                            "Price",
                            fontSize =
                                10.sp
                        )
                    },
                    textStyle =
                        LocalTextStyle.current.copy(
                            fontSize =
                                11.sp
                        )
                )

                Spacer(
                    Modifier.height(
                        6.dp
                    )
                )
            }

            // ------------------------------------------------
            // AMOUNT
            // ------------------------------------------------

            OutlinedTextField(
                value =
                    String.format(
                        Locale.US,
                        "%.6f",
                        quantity
                    ),
                onValueChange = {
                    it.toDoubleOrNull()
                        ?.let(
                            onQuantityChange
                        )
                },
                modifier =
                    Modifier.fillMaxWidth(),
                singleLine = true,
                label = {
                    Text(
                        "Amount",
                        fontSize =
                            10.sp
                    )
                },
                trailingIcon = {
                    Text(
                        "BTC",
                        fontSize =
                            9.sp
                    )
                },
                textStyle =
                    LocalTextStyle.current.copy(
                        fontSize =
                            11.sp
                    )
            )

            Spacer(
                Modifier.height(
                    5.dp
                )
            )

            // ------------------------------------------------
            // PERCENTAGE
            // ------------------------------------------------

            Row(
                Modifier.fillMaxWidth(),
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
                            onPercent(
                                percent
                            )
                        },
                        contentPadding =
                            PaddingValues(
                                horizontal = 4.dp
                            )
                    ) {

                        Text(
                            "$percent%",
                            fontSize =
                                9.sp,
                            color =
                                PurpleMimosa
                        )
                    }
                }
            }

            Slider(
                value =
                    quantity
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
                colors =
                    SliderDefaults.colors(
                        thumbColor =
                            PurpleMimosa,
                        activeTrackColor =
                            PurpleMimosa
                    )
            )

            Spacer(
                Modifier.height(
                    4.dp
                )
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

            SmallStat(
                "Size",
                "${
                    String.format(
                        Locale.US,
                        "%,.2f",
                        notional
                    )
               } USDT"
            )

            Spacer(
                Modifier.height(
                    3.dp
                )
            )

            SmallStat(
                "Margin",
                "${
                    String.format(
                        Locale.US,
                        "%,.2f",
                        margin
                    )
               } USDT"
            )

            Spacer(
                Modifier.height(
                    8.dp
                )
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
                    price > 0.0 &&
                        position == null,
                colors =
                    ButtonDefaults.buttonColors(
                        containerColor =
                            if (
                                side ==
                                PositionSide.LONG
                            ) {
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
                    if (
                        side ==
                        PositionSide.LONG
                    ) {
                        "OPEN LONG"
                    } else {
                        "OPEN SHORT"
                    },
                    fontSize =
                        11.sp
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
        modifier =
            modifier
                .fillMaxHeight(),
        shape =
            RoundedCornerShape(
                10.dp
            ),
        colors =
            CardDefaults.cardColors(
                containerColor =
                    CardBackground
            )
    ) {

        Column(
            Modifier
                .fillMaxSize()
                .padding(
                    8.dp
                )
        ) {

            Row(
                Modifier.fillMaxWidth(),
                horizontalArrangement =
                    Arrangement.SpaceBetween
            ) {

                Text(
                    "Order Book",
                    fontSize =
                        12.sp,
                    fontWeight =
                        FontWeight.Bold
                )

                Text(
                    symbol,
                    fontSize =
                        9.sp,
                    color =
                        Color.Gray
                )
            }

            Spacer(
                Modifier.height(
                    5.dp
                )
            )

            // ------------------------------------------------
            // HEADERS
            // ------------------------------------------------

            Row(
                Modifier.fillMaxWidth()
            ) {

                Text(
                    "USDT",
                    modifier =
                        Modifier.weight(
                            1f
                        ),
                    fontSize =
                        8.sp,
                    color =
                        Color.Gray
                )

                Text(
                    "Price",
                    modifier =
                        Modifier.weight(
                            1f
                        ),
                    textAlign =
                        androidx.compose.ui.text.style
                            .TextAlign.Center,
                    fontSize =
                        8.sp,
                    color =
                        Color.Gray
                )

                Text(
                    "Amount",
                    modifier =
                        Modifier.weight(
                            1f
                        ),
                    textAlign =
                        androidx.compose.ui.text.style
                            .TextAlign.End,
                    fontSize =
                        8.sp,
                    color =
                        Color.Gray
                )
            }

            Spacer(
                Modifier.height(
                    3.dp
                )
            )

            // ------------------------------------------------
            // SELL ORDERS
            // ------------------------------------------------

            asks
                .take(6)
                .asReversed()
                .forEach { level ->

                    OrderBookLine(
                        level =
                            level,
                        color =
                            Red
                    )
                }

            // ------------------------------------------------
            // CURRENT PRICE
            // ------------------------------------------------

            Box(
                Modifier
                    .fillMaxWidth()
                    .background(
                        Color(
                            0xFFF3EDF7
                        ),
                        RoundedCornerShape(
                            5.dp
                        )
                    )
                    .padding(
                        vertical = 5.dp
                    )
            ) {

                Text(
                    if (
                        currentPrice > 0
                    ) {
                        formatPrice(
                            currentPrice
                        )
                    } else {
                        "—"
                    },
                    modifier =
                        Modifier.fillMaxWidth(),
                    textAlign =
                        androidx.compose.ui.text.style
                            .TextAlign.Center,
                    fontSize =
                        11.sp,
                    fontWeight =
                        FontWeight.Bold,
                    color =
                        PurpleMimosa
                )
            }

            Spacer(
                Modifier.height(
                    3.dp
                )
            )

            // ------------------------------------------------
            // BUY ORDERS
            // ------------------------------------------------

            bids
                .take(6)
                .forEach { level ->

                    OrderBookLine(
                        level =
                            level,
                        color =
                            Green
                    )
                }

            Spacer(
                Modifier.height(
                    6.dp
                )
            )

            // ------------------------------------------------
            // BUY / SELL PERCENTAGE
            // ------------------------------------------------

            val buyTotal =
                bids
                    .take(10)
                    .sumOf {
                        it.quantity
                    }

            val sellTotal =
                asks
                    .take(10)
                    .sumOf {
                        it.quantity
                    }

            val total =
                (
                    buyTotal +
                        sellTotal
                )
                    .takeIf {
                        it > 0.0
                    }
                    ?: 1.0

            val buyPct =
                (
                    buyTotal /
                        total
                    ) * 100.0

            val sellPct =
                100.0 -
                    buyPct

            Text(
                "Order Ratio",
                fontSize =
                    9.sp,
                fontWeight =
                    FontWeight.Bold
            )

            Spacer(
                Modifier.height(
                    4.dp
                )
            )

            Row(
                Modifier.fillMaxWidth(),
                horizontalArrangement =
                    Arrangement.SpaceBetween
            ) {

                Text(
                    "Buy ${
                        String.format(
                            Locale.US,
                            "%.1f",
                            buyPct
                        )
                    }%",
                    fontSize =
                        9.sp,
                    color =
                        Green
                )

                Text(
                    "Sell ${
                        String.format(
                            Locale.US,
                            "%.1f",
                            sellPct
                        )
                    }%",
                    fontSize =
                        9.sp,
                    color =
                        Red
                )
            }

            Spacer(
                Modifier.height(
                    4.dp
                )
            )

            Row(
                Modifier
                    .fillMaxWidth()
                    .height(6.dp)
            ) {

                Box(
                    Modifier
                        .weight(
                            buyPct
                                .toFloat()
                                .coerceIn(
                                    0.0f,
                                    100.0f
                                )
                    )
                    .fillMaxHeight()
                    .background(
                        Green,
                        RoundedCornerShape(
                            4.dp
                        )
                    )
                )

                Spacer(
                    Modifier.width(
                        2.dp
                    )
                )

                Box(
                    Modifier
                        .weight(
                            sellPct
                                .toFloat()
                                .coerceIn(
                                    0.0f,
                                    100.0f
                                )
                    )
                    .fillMaxHeight()
                    .background(
                        Red,
                        RoundedCornerShape(
                            4.dp
                        )
                    )
                )
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
        level.price *
            level.quantity

    Row(
        Modifier
            .fillMaxWidth()
            .padding(
                vertical = 2.dp
            )
    ) {

        Text(
            String.format(
                Locale.US,
                "%.2f",
                usdtValue
            ),
            modifier =
                Modifier.weight(
                    1f
                ),
            fontSize =
                8.sp,
            color =
                Color.Gray
        )

        Text(
            formatPrice(
                level.price
            ),
            modifier =
                Modifier.weight(
                    1f
                ),
            textAlign =
                androidx.compose.ui.text.style
                    .TextAlign.Center,
            fontSize =
                8.sp,
            color =
                color
        )

        Text(
            String.format(
                Locale.US,
                "%.4f",
                level.quantity
            ),
            modifier =
                Modifier.weight(
                    1f
                ),
            textAlign =
                androidx.compose.ui.text.style
                    .TextAlign.End,
            fontSize =
                8.sp,
            color =
                Color.Gray
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
    onSelected:
        (TradeBottomTab) -> Unit
) {

    Row(
        Modifier
            .fillMaxWidth()
            .background(
                CardBackground
            )
            .padding(
                horizontal = 7.dp,
                vertical = 5.dp
            ),
        horizontalArrangement =
            Arrangement.spacedBy(
                5.dp
            )
    ) {

        BottomTabButton(
            "Active Position",
            selected ==
                TradeBottomTab.POSITION
        ) {
            onSelected(
                TradeBottomTab.POSITION
            )
        }

        BottomTabButton(
            "Pending",
            selected ==
                TradeBottomTab.PENDING
        ) {
            onSelected(
                TradeBottomTab.PENDING
            )
        }

        BottomTabButton(
            "History",
            selected ==
                TradeBottomTab.HISTORY
        ) {
            onSelected(
                TradeBottomTab.HISTORY
            )
        }
    }
}

@Composable
private fun RowScope.BottomTabButton(
    title: String,
    selected: Boolean,
    onClick: () -> Unit
) {

    TextButton(
        onClick =
            onClick,
        modifier =
            Modifier.weight(
                1f
            )
    ) {

        Text(
            title,
            fontSize =
                9.sp,
            fontWeight =
                if (selected)
                    FontWeight.Bold
                else
                    FontWeight.Normal,
            color =
                if (selected)
                    PurpleMimosa
                else
                    Color.Gray
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
    onCancelPending:
        (Long) -> Unit
) {

    when (
        selected
    ) {

        TradeBottomTab.POSITION -> {

            ActivePositionContent(
                position =
                    position,
                currentPrice =
                    currentPrice,
                pnl =
                    pnl,
                onClose =
                    onClose
            )
        }

        TradeBottomTab.PENDING -> {

            PendingContent(
                orders =
                    pendingOrders,
                onCancel =
                    onCancelPending
            )
        }

        TradeBottomTab.HISTORY -> {

            HistoryContent(
                history =
                    history
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
        modifier =
            Modifier
                .fillMaxWidth()
                .padding(
                    horizontal = 7.dp,
                    vertical = 4.dp
                ),
        shape =
            RoundedCornerShape(
                9.dp
            ),
        colors =
            CardDefaults.cardColors(
                containerColor =
                    CardBackground
            )
    ) {

        if (
            position == null
        ) {

            Text(
                "No active position",
                modifier =
                    Modifier
                        .fillMaxWidth()
                        .padding(
                            14.dp
                        ),
                textAlign =
                    androidx.compose.ui.text.style
                        .TextAlign.Center,
                fontSize =
                    10.sp,
                color =
                    Color.Gray
            )

        } else {

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
                        "${position.symbol} ${position.leverage}x",
                        fontSize =
                            10.sp
                    )
                }

                Spacer(
                    Modifier.height(
                        6.dp
                    )
                )

                Row(
                    Modifier.fillMaxWidth(),
                    horizontalArrangement =
                        Arrangement.SpaceBetween
                ) {

                    BottomValue(
                        "Entry",
                        formatPrice(
                            position.entryPrice
                        )
                    )

                    BottomValue(
                        "Mark",
                        formatPrice(
                            currentPrice
                        )
                    )

                    BottomValue(
                        "Qty",
                        String.format(
                            Locale.US,
                            "%.6f",
                            position.quantity
                        )
                    )

                    BottomValue(
                        "PnL",
                        TradeViewModel
                            .formatMoney(
                                pnl
                            ),
                        if (
                            pnl >= 0
                        ) {
                            Green
                        } else {
                            Red
                        }
                    )
                }

                Spacer(
                    Modifier.height(
                        7.dp
                    )
                )

                Button(
                    onClick =
                        onClose,
                    modifier =
                        Modifier.fillMaxWidth(),
                    colors =
                        ButtonDefaults
                            .buttonColors(
                                containerColor =
                                    Red
                            )
                ) {

                    Text(
                        "CLOSE POSITION",
                        fontSize =
                            10.sp
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
    onCancel:
        (Long) -> Unit
) {

    Card(
        modifier =
            Modifier
                .fillMaxWidth()
                .padding(
                    horizontal = 7.dp,
                    vertical = 4.dp
                ),
        shape =
            RoundedCornerShape(
                9.dp
            )
    ) {

        if (
            orders.isEmpty()
        ) {

            Text(
                "No pending orders",
                modifier =
                    Modifier
                        .fillMaxWidth()
                        .padding(
                            14.dp
                        ),
                textAlign =
                    androidx.compose.ui.text.style
                        .TextAlign.Center,
                fontSize =
                    10.sp,
                color =
                    Color.Gray
            )

        } else {

            Column(
                Modifier.padding(
                    8.dp
                )
            ) {

                orders.forEach { order ->

                    PendingRow(
                        order =
                            order,
                        onCancel =
                            {
                                onCancel(
                                    order.id
                                )
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
        modifier =
            Modifier
                .fillMaxWidth()
                .padding(
                    vertical = 6.dp
                ),
        verticalAlignment =
            Alignment.CenterVertically
    ) {

        Column(
            Modifier.weight(
                1f
            )
        ) {

            Text(
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
                fontSize =
                    9.sp,
                fontWeight =
                    FontWeight.Bold
            )

            Text(
                "Limit ${
                    formatPrice(
                        order.price
                    )
                } • ${
                    String.format(
                        Locale.US,
                        "%.6f",
                        order.quantity
                    )
                }",
                fontSize =
                    8.sp,
                color =
                    Color.Gray
            )
        }

        Text(
            "${order.leverage}x",
            fontSize =
                9.sp
        )

        TextButton(
            onClick =
                onCancel
        ) {

            Text(
                "Cancel",
                fontSize =
                    9.sp,
                color =
                    Red
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
        modifier =
            Modifier
                .fillMaxWidth()
                .padding(
                    horizontal = 7.dp,
                    vertical = 4.dp
                ),
        shape =
            RoundedCornerShape(
                9.dp
            )
    ) {

        if (
            history.isEmpty()
        ) {

            Text(
                "No trading history",
                modifier =
                    Modifier
                        .fillMaxWidth()
                        .padding(
                            14.dp
                        ),
                textAlign =
                    androidx.compose.ui.text.style
                        .TextAlign.Center,
                fontSize =
                    10.sp,
                color =
                    Color.Gray
            )

        } else {

            LazyColumn(
                modifier =
                    Modifier
                        .heightIn(
                            max = 160.dp
                        )
            ) {

                items(
                    history,
                    key = {
                        it.id
                    }
                ) { item ->

                    HistoryRow(
                        item
                    )

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
        Modifier
            .fillMaxWidth()
            .padding(
                horizontal = 9.dp,
                vertical = 7.dp
            ),
        verticalAlignment =
            Alignment.CenterVertically
    ) {

        Column(
            Modifier.weight(
                1f
            )
        ) {

            Text(
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
                fontSize =
                    9.sp,
                fontWeight =
                    FontWeight.Bold
            )

            Text(
                "${formatPrice(item.entryPrice)} → ${
                    formatPrice(item.exitPrice)
                }",
                fontSize =
                    8.sp,
                color =
                    Color.Gray
            )
        }

        Text(
            TradeViewModel.formatMoney(
                item.pnl
            ),
            fontSize =
                10.sp,
            fontWeight =
                FontWeight.Bold,
            color =
                if (
                    item.pnl >=
                    0
                ) {
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
        onClick =
            onClick,
        modifier =
            Modifier.weight(
                1f
            ),
        colors =
            ButtonDefaults
                .buttonColors(
                    containerColor =
                        if (selected)
                            color
                        else
                            Color(
                                0xFFE7E7EA
                            ),
                    contentColor =
                        if (selected)
                            Color.White
                        else
                            Color.DarkGray
                ),
        shape =
            RoundedCornerShape(
                7.dp
            ),
        contentPadding =
            PaddingValues(
                vertical = 7.dp
            )
    ) {

        Text(
            text,
            fontSize =
                10.sp
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
        onClick =
            onClick,
        contentPadding =
            PaddingValues(
                horizontal = 4.dp,
                vertical = 0.dp
            )
    ) {

        Text(
            text,
            fontSize =
                9.sp,
            fontWeight =
                if (selected)
                    FontWeight.Bold
                else
                    FontWeight.Normal,
            color =
                if (selected)
                    PurpleMimosa
                else
                    Color.Gray
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
            label,
            fontSize =
                8.sp,
            color =
                Color.Gray
        )

        Text(
            value,
            fontSize =
                10.sp,
            fontWeight =
                FontWeight.Bold
        )
    }
}

@Composable
private fun BottomValue(
    label: String,
    value: String,
    color: Color =
        Color.Black
) {

    Column {

        Text(
            label,
            fontSize =
                8.sp,
            color =
                Color.Gray
        )

        Text(
            value,
            fontSize =
                9.sp,
            fontWeight =
                FontWeight.Bold,
            color =
                color
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

    return if (
        value >= 1.0
    ) {

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

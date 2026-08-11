package com.practice.cryptoapp

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.Response
import okhttp3.WebSocket
import okhttp3.WebSocketListener
import org.json.JSONObject
import java.util.Locale
import java.util.concurrent.TimeUnit
import kotlin.math.abs
import kotlin.math.max

// ============================================================
// DEMO ACCOUNT
// ============================================================

object DemoAccountStore {

    private val _balance =
        MutableStateFlow(10_000.0)

    val balance: StateFlow<Double> =
        _balance.asStateFlow()

    private val _usedMargin =
        MutableStateFlow(0.0)

    val usedMargin: StateFlow<Double> =
        _usedMargin.asStateFlow()

    private val _position =
        MutableStateFlow<DemoPosition?>(null)

    val position: StateFlow<DemoPosition?> =
        _position.asStateFlow()

    private val _pendingOrders =
        MutableStateFlow<List<PendingOrder>>(
            emptyList()
        )

    val pendingOrders:
        StateFlow<List<PendingOrder>> =
        _pendingOrders.asStateFlow()

    private val _history =
        MutableStateFlow<List<TradeHistory>>(
            emptyList()
        )

    val history:
        StateFlow<List<TradeHistory>> =
        _history.asStateFlow()

    fun reset() {

        _balance.value =
            10_000.0

        _usedMargin.value =
            0.0

        _position.value =
            null

        _pendingOrders.value =
            emptyList()

        _history.value =
            emptyList()
    }

    fun openMarketPosition(
        symbol: String,
        side: PositionSide,
        entryPrice: Double,
        quantity: Double,
        leverage: Int
    ): Boolean {

        if (
            entryPrice <= 0.0 ||
            quantity <= 0.0
        ) {
            return false
        }

        if (
            _position.value != null
        ) {
            return false
        }

        val actualLeverage =
            leverage.coerceIn(
                1,
                125
            )

        val notional =
            entryPrice *
                quantity

        val margin =
            notional /
                actualLeverage

        if (
            margin >
            _balance.value
        ) {
            return false
        }

        _balance.value -=
            margin

        _usedMargin.value =
            margin

        _position.value =
            DemoPosition(

                symbol =
                    symbol,

                side =
                    side,

                entryPrice =
                    entryPrice,

                quantity =
                    quantity,

                leverage =
                    actualLeverage
            )

        return true
    }

    fun addPendingOrder(
        symbol: String,
        side: PositionSide,
        price: Double,
        quantity: Double,
        leverage: Int
    ): Boolean {

        if (
            price <= 0.0 ||
            quantity <= 0.0
        ) {
            return false
        }

        val actualLeverage =
            leverage.coerceIn(
                1,
                125
            )

        val margin =
            (
                price *
                    quantity
                ) /
                    actualLeverage

        if (
            margin >
            _balance.value
        ) {
            return false
        }

        val order =
            PendingOrder(

                id =
                    System.currentTimeMillis(),

                symbol =
                    symbol,

                side =
                    side,

                price =
                    price,

                quantity =
                    quantity,

                leverage =
                    actualLeverage
            )

        _pendingOrders.value =
            _pendingOrders.value +
                order

        return true
    }

    fun cancelPendingOrder(
        id: Long
    ) {

        _pendingOrders.value =
            _pendingOrders.value
                .filterNot {
                    it.id == id
                }
    }

    fun closePosition(
        currentPrice: Double
    ): Double {

        val currentPosition =
            _position.value
                ?: return 0.0

        val pnl =
            calculatePnl(
                currentPosition,
                currentPrice
            )

        _balance.value +=
            _usedMargin.value +
                pnl

        _history.value =
            listOf(

                TradeHistory(

                    id =
                        System.currentTimeMillis(),

                    symbol =
                        currentPosition.symbol,

                    side =
                        currentPosition.side,

                    entryPrice =
                        currentPosition.entryPrice,

                    exitPrice =
                        currentPrice,

                    quantity =
                        currentPosition.quantity,

                    pnl =
                        pnl
                )

            ) +
                _history.value

        _usedMargin.value =
            0.0

        _position.value =
            null

        return pnl
    }

    fun calculatePnl(
        currentPrice: Double
    ): Double {

        val currentPosition =
            _position.value
                ?: return 0.0

        return calculatePnl(
            currentPosition,
            currentPrice
        )
    }

    private fun calculatePnl(
        position: DemoPosition,
        currentPrice: Double
    ): Double {

        return if (
            position.side ==
            PositionSide.LONG
        ) {

            (
                currentPrice -
                    position.entryPrice
            ) *
                position.quantity

        } else {

            (
                position.entryPrice -
                    currentPrice
            ) *
                position.quantity
        }
    }
}

// ============================================================
// MODELS
// ============================================================

data class DemoPosition(

    val symbol: String,

    val side: PositionSide,

    val entryPrice: Double,

    val quantity: Double,

    val leverage: Int
)

data class PendingOrder(

    val id: Long,

    val symbol: String,

    val side: PositionSide,

    val price: Double,

    val quantity: Double,

    val leverage: Int
)

data class TradeHistory(

    val id: Long,

    val symbol: String,

    val side: PositionSide,

    val entryPrice: Double,

    val exitPrice: Double,

    val quantity: Double,

    val pnl: Double
)

data class OrderBookLevel(

    val price: Double,

    val quantity: Double
)

enum class PositionSide {

    LONG,
    SHORT
}

enum class TradeOrderType {

    MARKET,
    LIMIT
}

enum class TradeMode {

    SPOT,
    FUTURES,
    BOT
}

// ============================================================
// TRADE VIEW MODEL
// ============================================================

class TradeViewModel :
    ViewModel() {

    private val client =
        OkHttpClient.Builder()

            .readTimeout(
                0,
                TimeUnit.MILLISECONDS
            )

            .pingInterval(
                20,
                TimeUnit.SECONDS
            )

            .retryOnConnectionFailure(
                true
            )

            .build()

    private var socket:
        WebSocket? =
        null

    private var reconnectJob:
        Job? =
        null

    private var fundingJob:
        Job? =
        null

    // ========================================================
    // MODE
    // ========================================================

    private val _mode =
        MutableStateFlow(
            TradeMode.FUTURES
        )

    val mode:
        StateFlow<TradeMode> =
        _mode.asStateFlow()

    // ========================================================
    // SYMBOL
    // ========================================================

    private val _symbol =
        MutableStateFlow(
            "BTCUSDT"
        )

    val symbol:
        StateFlow<String> =
        _symbol.asStateFlow()

    // ========================================================
    // LIVE MARKET
    // ========================================================

    private val _price =
        MutableStateFlow(
            0.0
        )

    val price:
        StateFlow<Double> =
        _price.asStateFlow()

    private val _high24h =
        MutableStateFlow(
            0.0
        )

    val high24h:
        StateFlow<Double> =
        _high24h.asStateFlow()

    private val _low24h =
        MutableStateFlow(
            0.0
        )

    val low24h:
        StateFlow<Double> =
        _low24h.asStateFlow()

    private val _change24h =
        MutableStateFlow(
            0.0
        )

    val change24h:
        StateFlow<Double> =
        _change24h.asStateFlow()

    // ========================================================
    // FUNDING
    // ========================================================

    private val _fundingRate =
        MutableStateFlow(
            0.0
        )

    val fundingRate:
        StateFlow<Double> =
        _fundingRate.asStateFlow()

    private val _fundingCountdown =
        MutableStateFlow(
            "--:--:--"
        )

    val fundingCountdown:
        StateFlow<String> =
        _fundingCountdown.asStateFlow()

    private var nextFundingTime =
        0L

    // ========================================================
    // ORDER BOOK
    // ========================================================

    private val _bids =
        MutableStateFlow<
            List<OrderBookLevel>
            >(
            emptyList()
        )

    val bids:
        StateFlow<List<OrderBookLevel>> =
        _bids.asStateFlow()

    private val _asks =
        MutableStateFlow<
            List<OrderBookLevel>
            >(
            emptyList()
        )

    val asks:
        StateFlow<List<OrderBookLevel>> =
        _asks.asStateFlow()

    // ========================================================
    // CHART
    // ========================================================

    private val _priceHistory =
        MutableStateFlow<
            List<Double>
            >(
            emptyList()
        )

    val priceHistory:
        StateFlow<List<Double>> =
        _priceHistory.asStateFlow()

    // ========================================================
    // ORDER FORM
    // ========================================================

    private val _orderType =
        MutableStateFlow(
            TradeOrderType.MARKET
        )

    val orderType:
        StateFlow<TradeOrderType> =
        _orderType.asStateFlow()

    private val _side =
        MutableStateFlow(
            PositionSide.LONG
        )

    val side:
        StateFlow<PositionSide> =
        _side.asStateFlow()

    private val _leverage =
        MutableStateFlow(
            10
        )

    val leverage:
        StateFlow<Int> =
        _leverage.asStateFlow()

    private val _quantity =
        MutableStateFlow(
            0.001
        )

    val quantity:
        StateFlow<Double> =
        _quantity.asStateFlow()

    private val _limitPrice =
        MutableStateFlow(
            ""
        )

    val limitPrice:
        StateFlow<String> =
        _limitPrice.asStateFlow()

    // ========================================================
    // MESSAGE
    // ========================================================

    private val _message =
        MutableStateFlow(
            ""
        )

    val message:
        StateFlow<String> =
        _message.asStateFlow()

    // ========================================================
    // SHARED DEMO DATA
    // ========================================================

    val demoBalance:
        StateFlow<Double> =
        DemoAccountStore.balance

    val usedMargin:
        StateFlow<Double> =
        DemoAccountStore.usedMargin

    val position:
        StateFlow<DemoPosition?> =
        DemoAccountStore.position

    val pendingOrders:
        StateFlow<List<PendingOrder>> =
        DemoAccountStore.pendingOrders

    val history:
        StateFlow<List<TradeHistory>> =
        DemoAccountStore.history

    // ========================================================
    // INIT
    // ========================================================

    init {

        connect()
    }

    // ========================================================
    // MODE
    // ========================================================

    fun setMode(
        newMode: TradeMode
    ) {

        _mode.value =
            newMode

        _message.value =
            when (
                newMode
            ) {

                TradeMode.FUTURES ->
                    ""

                TradeMode.SPOT ->
                    "Spot mode is UI-only for now"

                TradeMode.BOT ->
                    "Bot mode is UI-only for now"
            }
    }

    // ========================================================
    // CONNECT BINANCE FUTURES
    // ========================================================

    fun connect() {

        reconnectJob?.cancel()

        socket?.close(
            1000,
            "Reconnect"
        )

        val lower =
            _symbol.value
                .lowercase()

        val streams =
            "$lower@ticker/" +
                "$lower@depth10@100ms/" +
                "$lower@markPrice@1s"

        val url =
            "wss://fstream.binance.com/stream?streams=$streams"

        val request =
            Request.Builder()
                .url(url)
                .build()

        socket =
            client.newWebSocket(

                request,

                object :
                    WebSocketListener() {

                    override fun onOpen(
                        webSocket:
                            WebSocket,
                        response:
                            Response
                    ) {

                        _message.value =
                            ""
                    }

                    override fun onMessage(
                        webSocket:
                            WebSocket,
                        text:
                            String
                    ) {

                        parseMessage(
                            text
                        )
                    }

                    override fun onFailure(
                        webSocket:
                            WebSocket,
                        t:
                            Throwable,
                        response:
                            Response?
                    ) {

                        _message.value =
                            "Reconnecting..."

                        scheduleReconnect()
                    }

                    override fun onClosed(
                        webSocket:
                            WebSocket,
                        code:
                            Int,
                        reason:
                            String
                    ) {

                        scheduleReconnect()
                    }
                }
            )

        startFundingCountdown()
    }

    private fun scheduleReconnect() {

        if (
            reconnectJob?.isActive ==
            true
        ) {
            return
        }

        reconnectJob =
            viewModelScope.launch {

                delay(
                    2500
                )

                connect()
            }
    }

    // ========================================================
    // FUNDING COUNTDOWN
    // ========================================================

    private fun startFundingCountdown() {

        fundingJob?.cancel()

        fundingJob =
            viewModelScope.launch {

                while (true) {

                    delay(
                        1000
                    )

                    val target =
                        nextFundingTime

                    if (
                        target <= 0L
                    ) {

                        _fundingCountdown.value =
                            "--:--:--"

                        continue
                    }

                    val remaining =
                        (
                            target -
                                System.currentTimeMillis()
                            )
                            .coerceAtLeast(
                                0L
                            )

                    val totalSeconds =
                        remaining /
                            1000L

                    val hours =
                        totalSeconds /
                            3600L

                    val minutes =
                        (
                            totalSeconds %
                                3600L
                            ) /
                            60L

                    val seconds =
                        totalSeconds %
                            60L

                    _fundingCountdown.value =
                        String.format(
                            Locale.US,
                            "%02d:%02d:%02d",
                            hours,
                            minutes,
                            seconds
                        )
                }
            }
    }

    // ========================================================
    // PARSE WEBSOCKET
    // ========================================================

    private fun parseMessage(
        text: String
    ) {

        try {

            val root =
                JSONObject(
                    text
                )

            val stream =
                root.optString(
                    "stream"
                )

            val data =
                root.optJSONObject(
                    "data"
                )
                    ?: return

            when {

                stream.contains(
                    "@ticker"
                ) -> {

                    val currentPrice =
                        data
                            .optString(
                                "c"
                            )
                            .toDoubleOrNull()

                    val high =
                        data
                            .optString(
                                "h"
                            )
                            .toDoubleOrNull()

                    val low =
                        data
                            .optString(
                                "l"
                            )
                            .toDoubleOrNull()

                    val change =
                        data
                            .optString(
                                "P"
                            )
                            .toDoubleOrNull()

                    if (
                        currentPrice != null
                    ) {

                        _price.value =
                            currentPrice

                        _priceHistory.value =
                            (
                                _priceHistory.value +
                                    currentPrice
                                )
                                .takeLast(
                                    100
                                )

                        executeTriggeredPendingOrders(
                            currentPrice
                        )
                    }

                    if (
                        high != null
                    ) {

                        _high24h.value =
                            high
                    }

                    if (
                        low != null
                    ) {

                        _low24h.value =
                            low
                    }

                    if (
                        change != null
                    ) {

                        _change24h.value =
                            change
                    }
                }

                stream.contains(
                    "@depth10"
                ) -> {

                    parseDepth(
                        data
                    )
                }

                stream.contains(
                    "@markPrice"
                ) -> {

                    val rate =
                        data
                            .optString(
                                "r"
                            )
                            .toDoubleOrNull()

                    if (
                        rate != null
                    ) {

                        _fundingRate.value =
                            rate
                    }

                    nextFundingTime =
                        data.optLong(
                            "T",
                            0L
                        )
                }
            }

        } catch (
            _: Exception
        ) {
            // Ignore malformed websocket messages.
        }
    }

    // ========================================================
    // ORDER BOOK
    // ========================================================

    private fun parseDepth(
        data: JSONObject
    ) {

        val bidArray =
            data.optJSONArray(
                "b"
            )

        val askArray =
            data.optJSONArray(
                "a"
            )

        if (
            bidArray != null
        ) {

            val list =
                mutableListOf<
                    OrderBookLevel
                    >()

            for (
                i in
                    0 until
                        bidArray.length()
            ) {

                val row =
                    bidArray.optJSONArray(
                        i
                    )
                        ?: continue

                val price =
                    row
                        .optString(
                            0
                        )
                        .toDoubleOrNull()
                        ?: continue

                val quantity =
                    row
                        .optString(
                            1
                        )
                        .toDoubleOrNull()
                        ?: continue

                list.add(
                    OrderBookLevel(

                        price =
                            price,

                        quantity =
                            quantity
                    )
                )
            }

            _bids.value =
                list
        }

        if (
            askArray != null
        ) {

            val list =
                mutableListOf<
                    OrderBookLevel
                    >()

            for (
                i in
                    0 until
                        askArray.length()
            ) {

                val row =
                    askArray.optJSONArray(
                        i
                    )
                        ?: continue

                val price =
                    row
                        .optString(
                            0
                        )
                        .toDoubleOrNull()
                        ?: continue

                val quantity =
                    row
                        .optString(
                            1
                        )
                        .toDoubleOrNull()
                        ?: continue

                list.add(
                    OrderBookLevel(

                        price =
                            price,

                        quantity =
                            quantity
                    )
                )
            }

            _asks.value =
                list
        }
    }

    // ========================================================
    // SYMBOL
    // ========================================================

    fun changeSymbol(
        newSymbol: String
    ) {

        var clean =
            newSymbol
                .trim()
                .uppercase()

        if (
            clean.isBlank()
        ) {
            return
        }

        if (
            !clean.endsWith(
                "USDT"
            )
        ) {

            clean +=
                "USDT"
        }

        if (
            clean ==
            _symbol.value
        ) {
            return
        }

        _symbol.value =
            clean

        _price.value =
            0.0

        _high24h.value =
            0.0

        _low24h.value =
            0.0

        _change24h.value =
            0.0

        _fundingRate.value =
            0.0

        _fundingCountdown.value =
            "--:--:--"

        nextFundingTime =
            0L

        _bids.value =
            emptyList()

        _asks.value =
            emptyList()

        _priceHistory.value =
            emptyList()

        connect()
    }

    // ========================================================
    // ORDER CONTROLS
    // ========================================================

    fun setOrderType(
        type: TradeOrderType
    ) {

        _orderType.value =
            type
    }

    fun setSide(
        newSide: PositionSide
    ) {

        _side.value =
            newSide
    }

    fun setLeverage(
        value: Int
    ) {

        _leverage.value =
            value.coerceIn(
                1,
                50
            )
    }

    fun setQuantity(
        value: Double
    ) {

        _quantity.value =
            max(
                0.000001,
                value
            )
    }

    fun setLimitPrice(
        value: String
    ) {

        if (
            value.isEmpty() ||
            value.matches(
                Regex(
                    "^\\d*(\\.\\d*)?$"
                )
            )
        ) {

            _limitPrice.value =
                value
        }
    }

    // ========================================================
    // OPEN ORDER
    // ========================================================

    fun openOrder() {

        if (
            _mode.value !=
            TradeMode.FUTURES
        ) {

            _message.value =
                "Select Futures first"

            return
        }

        if (
            _quantity.value <=
            0.0
        ) {

            _message.value =
                "Enter quantity"

            return
        }

        when (
            _orderType.value
        ) {

            TradeOrderType.MARKET -> {

                val executionPrice =
                    _price.value

                if (
                    executionPrice <=
                    0.0
                ) {

                    _message.value =
                        "Waiting for live price"

                    return
                }

                val success =
                    DemoAccountStore
                        .openMarketPosition(

                            symbol =
                                _symbol.value,

                            side =
                                _side.value,

                            entryPrice =
                                executionPrice,

                            quantity =
                                _quantity.value,

                            leverage =
                                _leverage.value
                        )

                _message.value =
                    if (
                        success
                    ) {
                        "Demo position opened"
                    } else {
                        "Cannot open position"
                    }
            }

            TradeOrderType.LIMIT -> {

                val limit =
                    _limitPrice.value
                        .toDoubleOrNull()
                        ?: 0.0

                if (
                    limit <= 0.0
                ) {

                    _message.value =
                        "Enter limit price"

                    return
                }

                val success =
                    DemoAccountStore
                        .addPendingOrder(

                            symbol =
                                _symbol.value,

                            side =
                                _side.value,

                            price =
                                limit,

                            quantity =
                                _quantity.value,

                            leverage =
                                _leverage.value
                        )

                _message.value =
                    if (
                        success
                    ) {
                        "Pending limit order added"
                    } else {
                        "Cannot add order"
                    }
            }
        }
    }

    // ========================================================
    // EXECUTE PENDING ORDERS
    // ========================================================

    private fun executeTriggeredPendingOrders(
        currentPrice: Double
    ) {

        val orders =
            DemoAccountStore
                .pendingOrders
                .value

        if (
            orders.isEmpty()
        ) {
            return
        }

        if (
            DemoAccountStore
                .position
                .value !=
            null
        ) {
            return
        }

        val currentSymbol =
            _symbol.value

        val triggered =
            orders.firstOrNull { order ->

                if (
                    order.symbol !=
                    currentSymbol
                ) {

                    false

                } else {

                    when (
                        order.side
                    ) {

                        PositionSide.LONG ->
                            currentPrice <=
                                order.price

                        PositionSide.SHORT ->
                            currentPrice >=
                                order.price
                    }
                }
            }

        if (
            triggered == null
        ) {
            return
        }

        DemoAccountStore
            .cancelPendingOrder(
                triggered.id
            )

        DemoAccountStore
            .openMarketPosition(

                symbol =
                    triggered.symbol,

                side =
                    triggered.side,

                entryPrice =
                    currentPrice,

                quantity =
                    triggered.quantity,

                leverage =
                    triggered.leverage
            )
    }

    // ========================================================
    // CLOSE POSITION
    // ========================================================

    fun closePosition() {

        if (
            _price.value <=
            0.0
        ) {
            return
        }

        val pnl =
            DemoAccountStore
                .closePosition(
                    _price.value
                )

        _message.value =
            "Position closed: ${
                formatMoney(
                    pnl
                )
            }"
    }

    // ========================================================
    // CANCEL PENDING
    // ========================================================

    fun cancelPending(
        id: Long
    ) {

        DemoAccountStore
            .cancelPendingOrder(
                id
            )

        _message.value =
            "Pending order cancelled"
    }

    // ========================================================
    // QUANTITY PERCENTAGE
    // ========================================================

    fun setQuantityPercent(
        percent: Int
    ) {

        val safePercent =
            percent.coerceIn(
                0,
                100
            )

        val balance =
            DemoAccountStore
                .balance
                .value

        val leverageValue =
            _leverage.value
                .coerceAtLeast(
                    1
                )

        val currentPrice =
            _price.value

        if (
            currentPrice <=
            0.0
        ) {
            return
        }

        val marginToUse =
            balance *
                safePercent /
                100.0

        val notional =
            marginToUse *
                leverageValue

        val calculatedQuantity =
            notional /
                currentPrice

        _quantity.value =
            calculatedQuantity
                .coerceAtLeast(
                    0.000001
                )
    }

    // ========================================================
    // CALCULATIONS
    // ========================================================

    fun unrealizedPnl():
        Double {

        return DemoAccountStore
            .calculatePnl(
                _price.value
            )
    }

    fun notional():
        Double {

        val executionPrice =
            if (
                _orderType.value ==
                TradeOrderType.LIMIT
            ) {

                _limitPrice.value
                    .toDoubleOrNull()
                    ?: _price.value

            } else {

                _price.value
            }

        return executionPrice *
            _quantity.value
    }

    fun estimatedMargin():
        Double {

        return notional() /
            _leverage.value
                .coerceAtLeast(
                    1
                )
    }

    // ========================================================
    // RESET DEMO
    // ========================================================

    fun resetDemo() {

        DemoAccountStore.reset()

        _message.value =
            "Demo balance reset to $10,000"
    }

    // ========================================================
    // CLEANUP
    // ========================================================

    override fun onCleared() {

        reconnectJob?.cancel()

        fundingJob?.cancel()

        socket?.close(
            1000,
            "ViewModel cleared"
        )

        client
            .dispatcher
            .executorService
            .shutdown()

        super.onCleared()
    }

    // ========================================================
    // FORMAT MONEY
    // ========================================================

    companion object {

        fun formatMoney(
            value: Double
        ): String {

            val sign =
                if (
                    value >= 0.0
                ) {
                    "+"
                } else {
                    "-"
                }

            return "$sign${
                String.format(
                    Locale.US,
                    "%,.2f",
                    abs(value)
                )
            }"
        }
    }
}

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
// SHARED DEMO ACCOUNT
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

    fun reset() {
        _balance.value = 10_000.0
        _usedMargin.value = 0.0
        _position.value = null
    }

    fun openPosition(
        side: PositionSide,
        entryPrice: Double,
        quantity: Double,
        leverage: Int
    ): Boolean {

        if (entryPrice <= 0.0 || quantity <= 0.0) {
            return false
        }

        if (_position.value != null) {
            return false
        }

        val notional =
            entryPrice * quantity

        val margin =
            notional / leverage.coerceIn(1, 125)

        if (margin > _balance.value) {
            return false
        }

        _balance.value -= margin
        _usedMargin.value = margin

        _position.value =
            DemoPosition(
                side = side,
                entryPrice = entryPrice,
                quantity = quantity,
                leverage = leverage
            )

        return true
    }

    fun closePosition(
        currentPrice: Double
    ): Double {

        val position =
            _position.value
                ?: return 0.0

        val pnl =
            calculatePnl(
                position,
                currentPrice
            )

        _balance.value +=
            _usedMargin.value + pnl

        _usedMargin.value = 0.0
        _position.value = null

        return pnl
    }

    fun calculatePnl(
        currentPrice: Double
    ): Double {

        val position =
            _position.value
                ?: return 0.0

        return calculatePnl(
            position,
            currentPrice
        )
    }

    private fun calculatePnl(
        position: DemoPosition,
        currentPrice: Double
    ): Double {

        val raw =
            if (position.side == PositionSide.LONG) {
                (currentPrice - position.entryPrice) *
                    position.quantity
            } else {
                (position.entryPrice - currentPrice) *
                    position.quantity
            }

        return raw
    }
}

data class DemoPosition(
    val side: PositionSide,
    val entryPrice: Double,
    val quantity: Double,
    val leverage: Int
)

enum class PositionSide {
    LONG,
    SHORT
}

// ============================================================
// ORDER TYPES
// ============================================================

enum class TradeOrderType {
    MARKET,
    LIMIT
}

// ============================================================
// MARKET DEPTH
// ============================================================

data class OrderBookLevel(
    val price: Double,
    val quantity: Double
)

// ============================================================
// TRADE VIEW MODEL
// ============================================================

class TradeViewModel : ViewModel() {

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
            .retryOnConnectionFailure(true)
            .build()

    private var socket: WebSocket? = null
    private var reconnectJob: Job? = null

    private val _symbol =
        MutableStateFlow("BTCUSDT")

    val symbol: StateFlow<String> =
        _symbol.asStateFlow()

    private val _price =
        MutableStateFlow(0.0)

    val price: StateFlow<Double> =
        _price.asStateFlow()

    private val _priceHistory =
        MutableStateFlow<List<Double>>(
            emptyList()
        )

    val priceHistory:
        StateFlow<List<Double>> =
        _priceHistory.asStateFlow()

    private val _bids =
        MutableStateFlow<List<OrderBookLevel>>(
            emptyList()
        )

    val bids:
        StateFlow<List<OrderBookLevel>> =
        _bids.asStateFlow()

    private val _asks =
        MutableStateFlow<List<OrderBookLevel>>(
            emptyList()
        )

    val asks:
        StateFlow<List<OrderBookLevel>> =
        _asks.asStateFlow()

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
        MutableStateFlow(10)

    val leverage:
        StateFlow<Int> =
        _leverage.asStateFlow()

    private val _quantity =
        MutableStateFlow(0.001)

    val quantity:
        StateFlow<Double> =
        _quantity.asStateFlow()

    private val _limitPrice =
        MutableStateFlow("")

    val limitPrice:
        StateFlow<String> =
        _limitPrice.asStateFlow()

    private val _message =
        MutableStateFlow("")

    val message:
        StateFlow<String> =
        _message.asStateFlow()

    val demoBalance =
        DemoAccountStore.balance

    val usedMargin =
        DemoAccountStore.usedMargin

    val position =
        DemoAccountStore.position

    init {
        connect()
    }

    // ========================================================
    // CONNECTION
    // ========================================================

    fun connect() {

        reconnectJob?.cancel()

        socket?.close(
            1000,
            "Reconnect"
        )

        val stream =
            "${_symbol.value.lowercase()}@ticker/" +
                "${_symbol.value.lowercase()}@depth10@100ms"

        val url =
            "wss://stream.binance.com:9443/stream?streams=$stream"

        val request =
            Request.Builder()
                .url(url)
                .build()

        socket =
            client.newWebSocket(
                request,
                object : WebSocketListener() {

                    override fun onOpen(
                        webSocket: WebSocket,
                        response: Response
                    ) {
                        _message.value = "LIVE"
                    }

                    override fun onMessage(
                        webSocket: WebSocket,
                        text: String
                    ) {
                        parseMessage(text)
                    }

                    override fun onFailure(
                        webSocket: WebSocket,
                        t: Throwable,
                        response: Response?
                    ) {

                        _message.value =
                            "Reconnecting..."

                        scheduleReconnect()
                    }

                    override fun onClosed(
                        webSocket: WebSocket,
                        code: Int,
                        reason: String
                    ) {

                        scheduleReconnect()
                    }
                }
            )
    }

    private fun scheduleReconnect() {

        if (
            reconnectJob?.isActive == true
        ) {
            return
        }

        reconnectJob =
            viewModelScope.launch {

                delay(2500)

                connect()
            }
    }

    // ========================================================
    // PARSE WSS
    // ========================================================

    private fun parseMessage(
        text: String
    ) {

        try {

            val root =
                JSONObject(text)

            val stream =
                root.optString("stream")

            val data =
                root.optJSONObject("data")
                    ?: return

            when {

                stream.endsWith(
                    "@ticker"
                ) -> {

                    val price =
                        data.optString("c")
                            .toDoubleOrNull()
                            ?: return

                    _price.value =
                        price

                    val old =
                        _priceHistory.value

                    _priceHistory.value =
                        (
                            old + price
                        ).takeLast(80)

                    /*
                     * Update position PnL.
                     */
                }

                stream.contains(
                    "@depth10"
                ) -> {

                    parseDepth(
                        data
                    )
                }
            }

        } catch (_: Exception) {
        }
    }

    private fun parseDepth(
        data: JSONObject
    ) {

        val bidsArray =
            data.optJSONArray(
                "bids"
            )

        val asksArray =
            data.optJSONArray(
                "asks"
            )

        if (bidsArray != null) {

            val result =
                mutableListOf<OrderBookLevel>()

            for (
                i in 0 until bidsArray.length()
            ) {

                val row =
                    bidsArray
                        .optJSONArray(i)
                        ?: continue

                val price =
                    row.optString(0)
                        .toDoubleOrNull()
                        ?: continue

                val qty =
                    row.optString(1)
                        .toDoubleOrNull()
                        ?: continue

                result.add(
                    OrderBookLevel(
                        price = price,
                        quantity = qty
                    )
                )
            }

            _bids.value =
                result
        }

        if (asksArray != null) {

            val result =
                mutableListOf<OrderBookLevel>()

            for (
                i in 0 until asksArray.length()
            ) {

                val row =
                    asksArray
                        .optJSONArray(i)
                        ?: continue

                val price =
                    row.optString(0)
                        .toDoubleOrNull()
                        ?: continue

                val qty =
                    row.optString(1)
                        .toDoubleOrNull()
                        ?: continue

                result.add(
                    OrderBookLevel(
                        price = price,
                        quantity = qty
                    )
                )
            }

            _asks.value =
                result
        }
    }

    // ========================================================
    // CONTROLS
    // ========================================================

    fun setOrderType(
        type: TradeOrderType
    ) {
        _orderType.value = type
    }

    fun setSide(
        newSide: PositionSide
    ) {
        _side.value = newSide
    }

    fun setLeverage(
        value: Int
    ) {
        _leverage.value =
            value.coerceIn(1, 50)
    }

    fun setQuantity(
        value: Double
    ) {
        _quantity.value =
            max(0.000001, value)
    }

    fun setLimitPrice(
        value: String
    ) {
        if (
            value.isEmpty() ||
            value.matches(
                Regex("^\\d*(\\.\\d*)?$")
            )
        ) {
            _limitPrice.value =
                value
        }
    }

    // ========================================================
    // OPEN POSITION
    // ========================================================

    fun openPosition() {

        if (
            DemoAccountStore.position.value != null
        ) {
            _message.value =
                "Position already open"

            return
        }

        val executionPrice =
            when (
                _orderType.value
            ) {

                TradeOrderType.MARKET ->
                    _price.value

                TradeOrderType.LIMIT ->
                    _limitPrice.value
                        .toDoubleOrNull()
                        ?: 0.0
            }

        if (
            executionPrice <= 0.0
        ) {

            _message.value =
                "Enter a valid price"

            return
        }

        val success =
            DemoAccountStore.openPosition(
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
            if (success) {
                "Demo position opened"
            } else {
                "Insufficient demo balance"
            }
    }

    // ========================================================
    // CLOSE POSITION
    // ========================================================

    fun closePosition() {

        val pnl =
            DemoAccountStore.closePosition(
                _price.value
            )

        _message.value =
            "Position closed: ${
                formatMoney(pnl)
            }"
    }

    // ========================================================
    // CALCULATIONS
    // ========================================================

    fun notional(): Double {

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

    fun estimatedMargin(): Double {

        return notional() /
            _leverage.value.coerceAtLeast(1)
    }

    fun unrealizedPnl(): Double {

        return DemoAccountStore
            .calculatePnl(
                _price.value
            )
    }

    fun resetDemo() {

        DemoAccountStore.reset()

        _message.value =
            "Demo balance reset to $10,000"
    }

    fun changeSymbol(
        newSymbol: String
    ) {

        val clean =
            newSymbol
                .trim()
                .uppercase()

        if (
            clean.isBlank()
        ) {
            return
        }

        if (
            clean == _symbol.value
        ) {
            return
        }

        _symbol.value =
            clean

        _price.value =
            0.0

        _priceHistory.value =
            emptyList()

        _bids.value =
            emptyList()

        _asks.value =
            emptyList()

        connect()
    }

    override fun onCleared() {

        reconnectJob?.cancel()

        socket?.close(
            1000,
            "ViewModel cleared"
        )

        client.dispatcher.executorService.shutdown()

        super.onCleared()
    }

    companion object {

        fun formatMoney(
            value: Double
        ): String {

            val sign =
                if (value >= 0)
                    "+"
                else
                    "-"

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

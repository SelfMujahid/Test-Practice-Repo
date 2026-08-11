package com.practice.cryptoapp

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import okhttp3.*
import org.json.JSONObject
import java.util.concurrent.TimeUnit
import kotlin.math.abs
import kotlin.math.max

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
// DEMO ACCOUNT STORE
// ============================================================

object DemoAccountStore {

    private val _balance = MutableStateFlow(10_000.0)
    val balance: StateFlow<Double> = _balance.asStateFlow()

    private val _usedMargin = MutableStateFlow(0.0)
    val usedMargin: StateFlow<Double> = _usedMargin.asStateFlow()

    private val _position = MutableStateFlow<DemoPosition?>(null)
    val position: StateFlow<DemoPosition?> = _position.asStateFlow()

    private val _pendingOrders = MutableStateFlow<List<PendingOrder>>(emptyList())
    val pendingOrders: StateFlow<List<PendingOrder>> = _pendingOrders.asStateFlow()

    private val _history = MutableStateFlow<List<TradeHistory>>(emptyList())
    val history: StateFlow<List<TradeHistory>> = _history.asStateFlow()

    fun reset() {
        _balance.value = 10_000.0
        _usedMargin.value = 0.0
        _position.value = null
        _pendingOrders.value = emptyList()
        _history.value = emptyList()
    }

    fun openMarketPosition(
        symbol: String,
        side: PositionSide,
        entryPrice: Double,
        quantity: Double,
        leverage: Int
    ): Boolean {
        if (entryPrice <= 0.0 || quantity <= 0.0) return false
        if (_position.value != null) return false

        val actualLeverage = leverage.coerceIn(1, 125)
        val notional = entryPrice * quantity
        val margin = notional / actualLeverage

        if (margin > _balance.value) return false

        _balance.value -= margin
        _usedMargin.value = margin
        _position.value = DemoPosition(
            symbol = symbol,
            side = side,
            entryPrice = entryPrice,
            quantity = quantity,
            leverage = actualLeverage
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
        if (price <= 0.0 || quantity <= 0.0) return false

        val actualLeverage = leverage.coerceIn(1, 125)
        val margin = (price * quantity) / actualLeverage

        if (margin > _balance.value) return false

        val order = PendingOrder(
            id = System.currentTimeMillis(),
            symbol = symbol,
            side = side,
            price = price,
            quantity = quantity,
            leverage = actualLeverage
        )
        _pendingOrders.value = _pendingOrders.value + order
        return true
    }

    fun cancelPendingOrder(id: Long) {
        _pendingOrders.value = _pendingOrders.value.filterNot { it.id == id }
    }

    fun closePosition(currentPrice: Double): Double {
        val currentPosition = _position.value ?: return 0.0
        val pnl = calculatePnl(currentPosition, currentPrice)

        _balance.value += _usedMargin.value + pnl
        _history.value = listOf(
            TradeHistory(
                id = System.currentTimeMillis(),
                symbol = currentPosition.symbol,
                side = currentPosition.side,
                entryPrice = currentPosition.entryPrice,
                exitPrice = currentPrice,
                quantity = currentPosition.quantity,
                pnl = pnl
            )
        ) + _history.value

        _usedMargin.value = 0.0
        _position.value = null
        return pnl
    }

    fun calculatePnl(currentPrice: Double): Double {
        val currentPosition = _position.value ?: return 0.0
        return calculatePnl(currentPosition, currentPrice)
    }

    private fun calculatePnl(position: DemoPosition, currentPrice: Double): Double {
        return if (position.side == PositionSide.LONG) {
            (currentPrice - position.entryPrice) * position.quantity
        } else {
            (position.entryPrice - currentPrice) * position.quantity
        }
    }
}

// ============================================================
// TRADE VIEW MODEL (with live order book via dedicated WebSocket)
// ============================================================

class TradeViewModel : ViewModel() {

    // --- Mode ---
    private val _mode = MutableStateFlow(TradeMode.FUTURES)
    val mode: StateFlow<TradeMode> = _mode.asStateFlow()

    // --- Order form ---
    private val _symbol = MutableStateFlow("BTCUSDT")
    val symbol: StateFlow<String> = _symbol.asStateFlow()

    private val _orderType = MutableStateFlow(TradeOrderType.MARKET)
    val orderType: StateFlow<TradeOrderType> = _orderType.asStateFlow()

    private val _side = MutableStateFlow(PositionSide.LONG)
    val side: StateFlow<PositionSide> = _side.asStateFlow()

    private val _leverage = MutableStateFlow(10)
    val leverage: StateFlow<Int> = _leverage.asStateFlow()

    private val _quantity = MutableStateFlow(0.001)
    val quantity: StateFlow<Double> = _quantity.asStateFlow()

    private val _limitPrice = MutableStateFlow("")
    val limitPrice: StateFlow<String> = _limitPrice.asStateFlow()

    private val _message = MutableStateFlow("")
    val message: StateFlow<String> = _message.asStateFlow()

    // --- Shared demo data ---
    val balance: StateFlow<Double> = DemoAccountStore.balance
    val position: StateFlow<DemoPosition?> = DemoAccountStore.position
    val pendingOrders: StateFlow<List<PendingOrder>> = DemoAccountStore.pendingOrders
    val history: StateFlow<List<TradeHistory>> = DemoAccountStore.history

    // --- Order Book State ---
    private val _bids = MutableStateFlow<List<OrderBookLevel>>(emptyList())
    val bids: StateFlow<List<OrderBookLevel>> = _bids.asStateFlow()

    private val _asks = MutableStateFlow<List<OrderBookLevel>>(emptyList())
    val asks: StateFlow<List<OrderBookLevel>> = _asks.asStateFlow()

    // --- Depth WebSocket (dedicated, ek coin ke liye) ---
    private var depthSocket: WebSocket? = null
    private val depthClient = OkHttpClient.Builder()
        .readTimeout(0, TimeUnit.MILLISECONDS)
        .pingInterval(20, TimeUnit.SECONDS)
        .retryOnConnectionFailure(true)
        .build()
    private var depthJob: Job? = null
    private var currentDepthSymbol: String? = null

    init {
        // Start depth stream for default symbol
        startDepthStream(_symbol.value)
    }

    // --- Symbol change handler ---
    fun setSymbol(sym: String) {
        val clean = sym.trim().uppercase()
        if (clean == _symbol.value) return
        _symbol.value = clean
        // Restart depth stream for new symbol
        startDepthStream(clean)
    }

    private fun startDepthStream(symbol: String) {
        depthJob?.cancel()
        depthSocket?.close(1000, "Symbol changed")
        currentDepthSymbol = symbol

        val stream = "${symbol.lowercase()}@depth10@100ms"
        val url = "wss://stream.binance.com:9443/ws/$stream"
        val request = Request.Builder().url(url).build()

        depthSocket = depthClient.newWebSocket(request, object : WebSocketListener() {
            override fun onOpen(webSocket: WebSocket, response: Response) {
                // connected
            }

            override fun onMessage(webSocket: WebSocket, text: String) {
                parseDepthMessage(text)
            }

            override fun onFailure(webSocket: WebSocket, t: Throwable, response: Response?) {
                depthJob = viewModelScope.launch {
                    delay(2000)
                    currentDepthSymbol?.let { startDepthStream(it) }
                }
            }

            override fun onClosed(webSocket: WebSocket, code: Int, reason: String) {
                if (reason != "Symbol changed" && reason != "ViewModel cleared") {
                    depthJob = viewModelScope.launch {
                        delay(2000)
                        currentDepthSymbol?.let { startDepthStream(it) }
                    }
                }
            }
        })
    }

    private fun parseDepthMessage(text: String) {
        try {
            val json = JSONObject(text)
            val bidsArr = json.optJSONArray("b")
            val asksArr = json.optJSONArray("a")
            val bidList = mutableListOf<OrderBookLevel>()
            val askList = mutableListOf<OrderBookLevel>()
            for (i in 0 until (bidsArr?.length() ?: 0)) {
                val row = bidsArr?.optJSONArray(i) ?: continue
                val price = row.optString(0).toDoubleOrNull() ?: continue
                val qty = row.optString(1).toDoubleOrNull() ?: continue
                bidList.add(OrderBookLevel(price, qty))
            }
            for (i in 0 until (asksArr?.length() ?: 0)) {
                val row = asksArr?.optJSONArray(i) ?: continue
                val price = row.optString(0).toDoubleOrNull() ?: continue
                val qty = row.optString(1).toDoubleOrNull() ?: continue
                askList.add(OrderBookLevel(price, qty))
            }
            _bids.value = bidList
            _asks.value = askList
        } catch (_: Exception) { }
    }

    // --- Existing functions (unchanged) ---
    fun setMode(mode: TradeMode) {
        _mode.value = mode
        _message.value = when (mode) {
            TradeMode.FUTURES -> ""
            TradeMode.SPOT -> "Spot mode is UI-only for now"
            TradeMode.BOT -> "Bot mode is UI-only for now"
        }
    }

    fun setOrderType(type: TradeOrderType) { _orderType.value = type }
    fun setSide(side: PositionSide) { _side.value = side }
    fun setLeverage(lev: Int) { _leverage.value = lev.coerceIn(1, 50) }
    fun setQuantity(qty: Double) { _quantity.value = max(0.000001, qty) }
    fun setLimitPrice(price: String) {
        if (price.isEmpty() || price.matches(Regex("^\\d*(\\.\\d*)?$"))) _limitPrice.value = price
    }

    fun setQuantityPercent(percent: Int, currentPrice: Double) {
        val safePercent = percent.coerceIn(0, 100)
        val bal = DemoAccountStore.balance.value
        val lev = _leverage.value.coerceAtLeast(1)
        if (currentPrice <= 0.0) return
        val marginToUse = bal * safePercent / 100.0
        val notional = marginToUse * lev
        val calculatedQuantity = notional / currentPrice
        _quantity.value = calculatedQuantity.coerceAtLeast(0.000001)
    }

    fun openOrder(executionPrice: Double) {
        if (_mode.value != TradeMode.FUTURES) { _message.value = "Select Futures first"; return }
        if (_quantity.value <= 0.0) { _message.value = "Enter quantity"; return }
        when (_orderType.value) {
            TradeOrderType.MARKET -> {
                if (executionPrice <= 0.0) { _message.value = "Waiting for live price"; return }
                val success = DemoAccountStore.openMarketPosition(
                    symbol = _symbol.value, side = _side.value,
                    entryPrice = executionPrice, quantity = _quantity.value,
                    leverage = _leverage.value
                )
                _message.value = if (success) "Demo position opened" else "Cannot open position"
            }
            TradeOrderType.LIMIT -> {
                val limit = _limitPrice.value.toDoubleOrNull() ?: 0.0
                if (limit <= 0.0) { _message.value = "Enter limit price"; return }
                val success = DemoAccountStore.addPendingOrder(
                    symbol = _symbol.value, side = _side.value,
                    price = limit, quantity = _quantity.value,
                    leverage = _leverage.value
                )
                _message.value = if (success) "Pending limit order added" else "Cannot add order"
            }
        }
    }

    fun closePosition(currentPrice: Double) {
        if (currentPrice <= 0.0) return
        val pnl = DemoAccountStore.closePosition(currentPrice)
        _message.value = "Position closed: ${formatMoney(pnl)}"
    }

    fun cancelPending(id: Long) {
        DemoAccountStore.cancelPendingOrder(id)
        _message.value = "Pending order cancelled"
    }

    fun unrealizedPnl(currentPrice: Double): Double {
        return DemoAccountStore.calculatePnl(currentPrice)
    }

    override fun onCleared() {
        depthJob?.cancel()
        depthSocket?.close(1000, "ViewModel cleared")
        super.onCleared()
    }

    companion object {
        fun formatMoney(value: Double): String {
            val sign = if (value >= 0.0) "+" else "-"
            return "$sign${"%,.2f".format(abs(value))}"
        }
    }
}

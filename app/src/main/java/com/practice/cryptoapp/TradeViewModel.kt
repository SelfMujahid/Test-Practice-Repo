package com.practice.cryptoapp

import androidx.lifecycle.ViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlin.math.max

class TradeViewModel : ViewModel() {

    // --- Order form state ---
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

    // --- Shared demo data (from DemoAccountStore) ---
    val balance: StateFlow<Double> = DemoAccountStore.balance
    val position: StateFlow<DemoPosition?> = DemoAccountStore.position
    val pendingOrders: StateFlow<List<PendingOrder>> = DemoAccountStore.pendingOrders
    val history: StateFlow<List<TradeHistory>> = DemoAccountStore.history

    // --- Actions ---
    fun setSymbol(sym: String) {
        _symbol.value = sym.trim().uppercase()
    }

    fun setOrderType(type: TradeOrderType) {
        _orderType.value = type
    }

    fun setSide(side: PositionSide) {
        _side.value = side
    }

    fun setLeverage(lev: Int) {
        _leverage.value = lev.coerceIn(1, 50)
    }

    fun setQuantity(qty: Double) {
        _quantity.value = max(0.000001, qty)
    }

    fun setLimitPrice(price: String) {
        if (price.isEmpty() || price.matches(Regex("^\\d*(\\.\\d*)?$"))) {
            _limitPrice.value = price
        }
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
        if (_quantity.value <= 0.0) {
            _message.value = "Enter quantity"
            return
        }
        when (_orderType.value) {
            TradeOrderType.MARKET -> {
                if (executionPrice <= 0.0) {
                    _message.value = "Waiting for price"
                    return
                }
                val success = DemoAccountStore.openMarketPosition(
                    symbol = _symbol.value,
                    side = _side.value,
                    entryPrice = executionPrice,
                    quantity = _quantity.value,
                    leverage = _leverage.value
                )
                _message.value = if (success) "Demo position opened" else "Cannot open position"
            }
            TradeOrderType.LIMIT -> {
                val limit = _limitPrice.value.toDoubleOrNull() ?: 0.0
                if (limit <= 0.0) {
                    _message.value = "Enter limit price"
                    return
                }
                val success = DemoAccountStore.addPendingOrder(
                    symbol = _symbol.value,
                    side = _side.value,
                    price = limit,
                    quantity = _quantity.value,
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

    companion object {
        fun formatMoney(value: Double): String {
            val sign = if (value >= 0.0) "+" else "-"
            val absVal = kotlin.math.abs(value)
            val formatted = "%,.2f".format(absVal)
            return "$sign$formatted"
        }
    }
}

package com.practice.cryptoapp

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import okhttp3.*
import org.json.JSONObject

data class TradeUiState(
    val symbol: String = "BTCUSDT",
    val price: Double = 65420.50,
    val priceChangePercent: Double = 2.45,
    val high24h: Double = 66200.00,
    val low24h: Double = 64100.00,
    val volume24h: Double = 1.2,
    val isCrossMargin: Boolean = true,
    val leverage: Int = 20,
    val availableBalance: Double = 10000.00,
    val selectedOrderType: String = "Limit Order",
    val inputPrice: String = "65420.50",
    val inputQuantity: String = "1000",
    val selectedTab: String = "Open Positions"
)

class TradeViewModel : ViewModel() {

    private val _uiState = MutableStateFlow(TradeUiState())
    val uiState: StateFlow<TradeUiState> = _uiState.asStateFlow()

    private val client = OkHttpClient()
    private var webSocket: WebSocket? = null

    init {
        connectBinanceWebSocket(_uiState.value.symbol)
    }

    fun onSymbolChanged(newSymbol: String) {
        _uiState.value = _uiState.value.copy(symbol = newSymbol)
        connectBinanceWebSocket(newSymbol)
    }

    fun onLeverageChanged(newLeverage: Int) {
        _uiState.value = _uiState.value.copy(leverage = newLeverage)
    }

    fun onMarginTypeChanged(isCross: Boolean) {
        _uiState.value = _uiState.value.copy(isCrossMargin = isCross)
    }

    fun onPriceInputChanged(price: String) {
        _uiState.value = _uiState.value.copy(inputPrice = price)
    }

    fun onQuantityInputChanged(quantity: String) {
        _uiState.value = _uiState.value.copy(inputQuantity = quantity)
    }

    fun adjustPrice(delta: Double) {
        val current = _uiState.value.inputPrice.toDoubleOrNull() ?: _uiState.value.price
        val updated = (current + delta).coerceAtLeast(0.0)
        _uiState.value = _uiState.value.copy(inputPrice = "%.2f".format(updated))
    }

    fun setPercentageQuantity(percentage: Double) {
        val maxQty = _uiState.value.availableBalance * _uiState.value.leverage
        val calculated = maxQty * (percentage / 100.0)
        _uiState.value = _uiState.value.copy(inputQuantity = "%.2f".format(calculated))
    }

    private fun connectBinanceWebSocket(symbol: String) {
        webSocket?.close(1000, "Switching symbol")
        val streamSymbol = symbol.lowercase()
        val request = Request.Builder()
            .url("wss://stream.binance.com:9443/ws/${streamSymbol}@ticker")
            .build()

        webSocket = client.newWebSocket(request, object : WebSocketListener() {
            override fun onMessage(webSocket: WebSocket, text: String) {
                viewModelScope.launch {
                    try {
                        val json = JSONObject(text)
                        if (json.has("c")) {
                            val cPrice = json.getDouble("c")
                            val change = json.getDouble("P")
                            val high = json.getDouble("h")
                            val low = json.getDouble("l")

                            _uiState.value = _uiState.value.copy(
                                price = cPrice,
                                priceChangePercent = change,
                                high24h = high,
                                low24h = low
                            )
                        }
                    } catch (e: Exception) {
                        e.printStackTrace()
                    }
                }
            }
        })
    }

    override fun onCleared() {
        super.onCleared()
        webSocket?.close(1000, "ViewModel Cleared")
    }
}

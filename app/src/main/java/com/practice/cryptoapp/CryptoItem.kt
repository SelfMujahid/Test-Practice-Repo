package com.practice.cryptoapp

enum class PriceState {
    UP, DOWN, NEUTRAL
}

data class CryptoItem(
    var rank: Int,
    val name: String,
    val symbol: String,
    var price: String,
    val logoUrl: String = "",
    val change1h: Double = 0.0,
    val change8h: Double = 0.0,
    val change24h: Double = 0.0,
    val change7d: Double = 0.0,
    var rawPrice: Double = 0.0,
    var priceState: PriceState = PriceState.NEUTRAL,
    var isFlashed: Boolean = false
)

data class TradeItem(
    val symbol: String,
    val type: String, // "LONG" or "SHORT"
    val pnl: Double,
    val pnlPercentage: Double
)

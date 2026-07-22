package com.practice.cryptoapp

enum class PriceState {
    UP, DOWN, NEUTRAL
}

data class CryptoItem(
    var rank: Int,
    val symbol: String,
    var price: String,
    val marketCap: Double,
    var rawPrice: Double = 0.0,
    var priceState: PriceState = PriceState.NEUTRAL
)

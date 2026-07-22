package com.practice.cryptoapp

data class CryptoItem(
    val rank: Int,
    val symbol: String,      // e.g. "BTC"
    val name: String,        // e.g. "Bitcoin"
    var price: String,       // Formatted Price String
    var rawPrice: Double,    // Raw Price for Comparison
    var change24h: Double    // Percentage Change e.g. 2.45
)

package com.goldfvg.alarm

data class Candle(
    val time: Long,
    val open: Double,
    val high: Double,
    val low: Double,
    val close: Double
)

data class Fvg(
    val id: String,
    val type: String,          // "bullish" | "bearish"
    val bottom: Double,
    val top: Double,
    val createdAt: Long,       // open-time of the 3rd candle
    var status: String = "active" // "active" | "filled" | "cancelled"
)

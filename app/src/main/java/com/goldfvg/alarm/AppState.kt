package com.goldfvg.alarm

import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf

object AppState {
    val candles = mutableStateListOf<Candle>()
    val fvgs = mutableStateListOf<Fvg>()
    var running = mutableStateOf(false)
    var ringing = mutableStateOf(false)
    var lastPrice = mutableStateOf(0.0)
    var statusText = mutableStateOf("غیرفعال")
    var lastUpdate = mutableStateOf("")
}

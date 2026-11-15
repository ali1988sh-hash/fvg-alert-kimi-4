package com.goldfvg.alarm

object FvgDetector {

    /** Bullish FVG: low of candle3 > high of candle1 -> zone [c1.high, c3.low]
     *  Bearish FVG: high of candle3 < low of candle1 -> zone [c3.high, c1.low] */
    fun detect(candles: List<Candle>): List<Fvg> {
        val result = mutableListOf<Fvg>()
        for (i in 2 until candles.size) {
            val c1 = candles[i - 2]
            val c3 = candles[i]
            if (c3.low > c1.high) {
                val f = Fvg(
                    id = "bull_${c3.time}_${c1.high}_${c3.low}",
                    type = "bullish",
                    bottom = c1.high,
                    top = c3.low,
                    createdAt = c3.time
                )
                result.add(f)
            } else if (c3.high < c1.low) {
                val f = Fvg(
                    id = "bear_${c3.time}_${c3.high}_${c1.low}",
                    type = "bearish",
                    bottom = c3.high,
                    top = c1.low,
                    createdAt = c3.time
                )
                result.add(f)
            }
        }
        return result
    }
}

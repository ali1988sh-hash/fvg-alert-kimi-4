package com.goldfvg.alarm

import androidx.compose.foundation.Canvas
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color

private val UpColor = Color(0xFF22C55E)
private val DownColor = Color(0xFFEF4444)
private val BullZone = Color(0x3322C55E)
private val BearZone = Color(0x33EF4444)
private val LastPriceColor = Color(0xFFD4AF37)

@Composable
fun PriceChart(candles: List<Candle>, fvgs: List<Fvg>, modifier: Modifier = Modifier) {
    Canvas(modifier = modifier) {
        if (candles.isEmpty()) return@Canvas
        val visible = candles.takeLast(90)
        val activeZones = fvgs.filter { it.status == "active" }
        val levels = mutableListOf<Double>()
        visible.forEach { levels.add(it.high); levels.add(it.low) }
        activeZones.forEach { levels.add(it.top); levels.add(it.bottom) }
        val minP = levels.min()
        val maxP = levels.max()
        val pad = (maxP - minP) * 0.06 + 1e-9
        val range = maxP - minP + 2 * pad

        fun y(p: Double): Float = (((maxP + pad - p) / range) * size.height).toFloat()

        // FVG zones (drawn behind candles)
        activeZones.forEach { f ->
            val topY = y(f.top)
            val botY = y(f.bottom)
            drawRect(
                color = if (f.type == "bullish") BullZone else BearZone,
                topLeft = Offset(0f, topY),
                size = Size(size.width, (botY - topY).coerceAtLeast(2f))
            )
        }

        val cw = size.width / visible.size
        visible.forEachIndexed { i, c ->
            val x = i * cw + cw / 2f
            val up = c.close >= c.open
            val color = if (up) UpColor else DownColor
            drawLine(color, Offset(x, y(c.high)), Offset(x, y(c.low)), strokeWidth = 2f)
            val bodyTop = y(maxOf(c.open, c.close))
            val bodyBot = y(minOf(c.open, c.close))
            drawRect(
                color = color,
                topLeft = Offset(x - cw * 0.3f, bodyTop),
                size = Size(cw * 0.6f, (bodyBot - bodyTop).coerceAtLeast(2f))
            )
        }

        // last price line
        val last = visible.last().close
        drawLine(LastPriceColor, Offset(0f, y(last)), Offset(size.width, y(last)), strokeWidth = 2f)
    }
}

package com.goldfvg.alarm

import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL
import java.time.Instant

/**
 * OANDA v20 REST API — gold spot (XAU_USD), 15-minute candles.
 * Token comes from app settings (OANDA practice account API key).
 * Docs: https://developer.oanda.com/rest-live-v20/introduction/
 */
object PriceFetcher {

    // Practice (free) endpoint. For a live account use https://api-fxtrade.oanda.com
    private const val BASE = "https://api-fxpractice.oanda.com"
    private const val INSTRUMENT = "XAU_USD"

    fun fetch15m(token: String): List<Candle> {
        if (token.isBlank()) throw Exception("ابتدا توکن OANDA را در تنظیمات اپ وارد کنید")

        val url = "$BASE/v3/instruments/$INSTRUMENT/candles?count=300&granularity=M15&price=M"
        val conn = URL(url).openConnection() as HttpURLConnection
        conn.connectTimeout = 15000
        conn.readTimeout = 15000
        conn.setRequestProperty("Authorization", "Bearer $token")
        val code = conn.responseCode
        val text = if (code == 200) {
            conn.inputStream.bufferedReader().readText()
        } else {
            val err = conn.errorStream?.bufferedReader()?.readText() ?: ""
            conn.disconnect()
            throw Exception("OANDA خطا $code: $err")
        }
        conn.disconnect()

        val candles = JSONObject(text).getJSONArray("candles")
        val list = ArrayList<Candle>(candles.length())
        for (i in 0 until candles.length()) {
            val c = candles.getJSONObject(i)
            if (c.getString("complete") != "true") continue
            val mid = c.getJSONObject("mid")
            list.add(
                Candle(
                    time = Instant.parse(c.getString("time")).toEpochMilli(),
                    open = mid.getDouble("o"),
                    high = mid.getDouble("h"),
                    low = mid.getDouble("l"),
                    close = mid.getDouble("c")
                )
            )
        }
        return list.sortedBy { it.time }
    }
}

package com.goldfvg.alarm

import android.content.Context
import org.json.JSONArray
import org.json.JSONObject

class FvgStore(context: Context) {

    private val prefs = context.getSharedPreferences("fvg_store", Context.MODE_PRIVATE)

    var lastProcessedTime: Long
        get() = prefs.getLong("last_time", 0L)
        set(value) { prefs.edit().putLong("last_time", value).apply() }

    var apiToken: String
        get() = prefs.getString("api_token", "") ?: ""
        set(value) { prefs.edit().putString("api_token", value.trim()).apply() }

    fun loadActive(): List<Fvg> {
        val out = mutableListOf<Fvg>()
        val arr = JSONArray(prefs.getString("active", "[]"))
        for (i in 0 until arr.length()) {
            val o = arr.getJSONObject(i)
            out.add(
                Fvg(
                    id = o.getString("id"),
                    type = o.getString("type"),
                    bottom = o.getDouble("bottom"),
                    top = o.getDouble("top"),
                    createdAt = o.getLong("createdAt"),
                    status = o.getString("status")
                )
            )
        }
        return out
    }

    fun saveActive(list: List<Fvg>) {
        val arr = JSONArray()
        list.forEach { f ->
            arr.put(
                JSONObject()
                    .put("id", f.id)
                    .put("type", f.type)
                    .put("bottom", f.bottom)
                    .put("top", f.top)
                    .put("createdAt", f.createdAt)
                    .put("status", f.status)
            )
        }
        prefs.edit().putString("active", arr.toString()).apply()
    }
}

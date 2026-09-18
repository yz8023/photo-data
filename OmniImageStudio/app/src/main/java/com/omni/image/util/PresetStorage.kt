package com.omni.image.util

import android.content.Context
import com.omni.image.model.ExifPreset
import com.omni.image.model.WatermarkPreset
import org.json.JSONArray
import org.json.JSONObject

object PresetStorage {
    private const val PREF_EXIF = "exif_presets"
    private const val PREF_WM = "watermark_presets"
    private const val PREF_FILTER = "filter_presets"

    private fun prefs(c: Context) = c.getSharedPreferences("omni_presets", Context.MODE_PRIVATE)

    fun saveExifPreset(context: Context, preset: ExifPreset) {
        val arr = loadExifJson(context).put(presetToJson(preset))
        prefs(context).edit().putString(PREF_EXIF, arr.toString()).apply()
    }

    fun listExifPresets(context: Context): List<ExifPreset> {
        return runCatching {
            val arr = loadExifJson(context)
            (0 until arr.length()).map { jsonToExif(arr.getJSONObject(it)) }
        }.getOrDefault(emptyList())
    }

    private fun loadExifJson(context: Context): JSONArray {
        return runCatching {
            JSONArray(prefs(context).getString(PREF_EXIF, "[]"))
        }.getOrDefault(JSONArray())
    }

    private fun presetToJson(p: ExifPreset): JSONObject = JSONObject().apply {
        put("name", p.name)
        put("fields", JSONObject(p.fields))
    }

    private fun jsonToExif(o: JSONObject): ExifPreset {
        val fields = mutableMapOf<String, String>()
        val fo = o.optJSONObject("fields")
        if (fo != null) {
            val names = fo.names()
            for (i in 0 until names.length()) {
                val k = names.getString(i)
                fields[k] = fo.optString(k)
            }
        }
        return ExifPreset(name = o.optString("name"), fields = fields)
    }

    fun saveWatermarkPreset(context: Context, p: WatermarkPreset) {
        val arr = loadWmJson(context).put(JSONObject().apply {
            put("name", p.name)
            put("text", p.text)
            put("color", p.color)
            put("sizePx", p.sizePx.toDouble())
            put("rotation", p.rotation.toDouble())
            put("opacity", p.opacity.toDouble())
            put("shadow", p.shadow)
            put("position", p.position)
            put("tileMode", p.tileMode)
            put("tileGap", p.tileGap)
            put("imageUri", p.imageUri)
        })
        prefs(context).edit().putString(PREF_WM, arr.toString()).apply()
    }

    fun listWatermarkPresets(context: Context): List<WatermarkPreset> {
        return runCatching {
            val arr = loadWmJson(context)
            (0 until arr.length()).map { i ->
                val o = arr.getJSONObject(i)
                WatermarkPreset(
                    name = o.optString("name"),
                    text = o.optString("text"),
                    color = o.optLong("color", 0xFFFFFFFF),
                    sizePx = o.optDouble("sizePx", 48.0).toFloat(),
                    rotation = o.optDouble("rotation", 0.0).toFloat(),
                    opacity = o.optDouble("opacity", 0.5).toFloat(),
                    shadow = o.optBoolean("shadow", true),
                    position = o.optString("position", "center"),
                    tileMode = o.optBoolean("tileMode", false),
                    tileGap = o.optInt("tileGap", 200),
                    imageUri = o.optString("imageUri")
                )
            }
        }.getOrDefault(emptyList())
    }

    private fun loadWmJson(context: Context): JSONArray {
        return runCatching {
            JSONArray(prefs(context).getString(PREF_WM, "[]"))
        }.getOrDefault(JSONArray())
    }

    fun saveFilterPreset(context: Context, name: String, params: Map<String, Float>) {
        val prefs = prefs(context)
        val current = runCatching {
            JSONObject(prefs.getString(PREF_FILTER, "{}"))
        }.getOrDefault(JSONObject())
        current.put(name, JSONObject(params))
        prefs.edit().putString(PREF_FILTER, current.toString()).apply()
    }

    fun listFilterPresets(context: Context): List<Pair<String, Map<String, Float>>> {
        return runCatching {
            val o = JSONObject(prefs(context).getString(PREF_FILTER, "{}"))
            val names = o.names()
            if (names == null) return@runCatching emptyList()
            val out = mutableListOf<Pair<String, Map<String, Float>>>()
            for (i in 0 until names.length()) {
                val n = names.getString(i)
                val po = o.optJSONObject(n) ?: continue
                val params = mutableMapOf<String, Float>()
                val keys = po.names() ?: continue
                for (j in 0 until keys.length()) {
                    params[keys.getString(j)] = po.optDouble(keys.getString(j), 0.0).toFloat()
                }
                out.add(n to params)
            }
            out
        }.getOrDefault(emptyList())
    }
}

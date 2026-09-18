package com.omni.image.util

import android.content.Context

object RecentFiles {
    private const val PREF = "recent_files"
    private const val MAX = 12

    private fun prefs(c: Context) = c.getSharedPreferences(PREF, Context.MODE_PRIVATE)

    fun list(context: Context): List<Pair<String, String>> {
        val raw = prefs(context).getString("items", "") ?: return emptyList()
        if (raw.isEmpty()) return emptyList()
        return raw.split("\u0001").mapNotNull { seg ->
            val parts = seg.split("\u0002", limit = 2)
            if (parts.size == 2) parts[0] to parts[1] else null
        }
    }

    fun add(context: Context, uri: String, name: String) {
        val current = list(context).filter { it.first != uri }.toMutableList()
        current.add(0, uri to name)
        val capped = current.take(MAX)
        val raw = capped.joinToString("\u0001") { "${it.first}\u0002${it.second}" }
        prefs(context).edit().putString("items", raw).apply()
    }
}

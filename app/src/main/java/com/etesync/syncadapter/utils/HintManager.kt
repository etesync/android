package com.etesync.syncadapter.utils


import android.content.Context
import android.content.SharedPreferences

object HintManager {
    private val PREF_NAME = "hints"

    private fun getPrefs(context: Context): SharedPreferences {
        return context.getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE)
    }

    fun setHintSeen(context: Context, hint: String, seen: Boolean) {
        val prefs = getPrefs(context)

        prefs.edit().putBoolean(hint, seen).apply()
    }

    fun getHintSeen(context: Context, hint: String): Boolean {
        val prefs = getPrefs(context)

        return prefs.getBoolean(hint, false)
    }

    fun resetHints(context: Context) {
        val prefs = getPrefs(context)

        prefs.edit().clear().apply()
    }
}

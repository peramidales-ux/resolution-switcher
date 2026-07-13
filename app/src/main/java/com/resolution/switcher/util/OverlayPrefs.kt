package com.resolution.switcher.util

import android.content.Context

object OverlayPrefs {

    private const val PREFS_NAME = "overlay_prefs"

    private fun prefs(context: Context) =
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    fun saveOverlayPosition(context: Context, x: Int, y: Int) {
        prefs(context).edit().putInt("overlay_x", x).putInt("overlay_y", y).apply()
    }

    fun getOverlayPosition(context: Context): Pair<Int, Int> {
        val p = prefs(context)
        return Pair(p.getInt("overlay_x", 20), p.getInt("overlay_y", 200))
    }

    fun saveCollapsedPosition(context: Context, x: Int, y: Int) {
        prefs(context).edit().putInt("collapsed_x", x).putInt("collapsed_y", y).apply()
    }

    fun getCollapsedPosition(context: Context): Pair<Int, Int> {
        val p = prefs(context)
        return Pair(p.getInt("collapsed_x", 20), p.getInt("collapsed_y", 200))
    }

    fun saveAlpha(context: Context, alpha: Float) {
        prefs(context).edit().putFloat("overlay_alpha", alpha).apply()
    }

    fun getAlpha(context: Context): Float {
        return prefs(context).getFloat("overlay_alpha", 0.75f)
    }

    fun saveResolution(context: Context, width: Int, height: Int, density: Int) {
        prefs(context).edit()
            .putInt("saved_width", width)
            .putInt("saved_height", height)
            .putInt("saved_density", density)
            .putBoolean("has_saved_resolution", true)
            .apply()
    }

    fun getSavedResolution(context: Context): Triple<Int, Int, Int>? {
        val p = prefs(context)
        if (!p.getBoolean("has_saved_resolution", false)) return null
        return Triple(
            p.getInt("saved_width", 1080),
            p.getInt("saved_height", 2400),
            p.getInt("saved_density", 420)
        )
    }

    fun clearSavedResolution(context: Context) {
        prefs(context).edit().putBoolean("has_saved_resolution", false).apply()
    }
}

package com.resolution.switcher.util

import android.app.Activity
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.provider.Settings
import rikka.shizuku.Shizuku

object PermissionHelper {

    fun hasOverlayPermission(context: Context): Boolean {
        return Settings.canDrawOverlays(context)
    }

    fun requestOverlayPermission(activity: Activity) {
        val intent = Intent(
            Settings.ACTION_MANAGE_OVERLAY_PERMISSION,
            Uri.parse("package:${activity.packageName}")
        )
        activity.startActivityForResult(intent, 1001)
    }

    fun isRootAvailable(): Boolean {
        return try {
            val process = Runtime.getRuntime().exec(arrayOf("su", "-c", "id"))
            val output = process.inputStream.bufferedReader().readText()
            process.waitFor()
            output.contains("uid=0")
        } catch (_: Exception) {
            false
        }
    }

    fun isShizukuAvailable(): Boolean {
        return try {
            Shizuku.pingBinder()
        } catch (_: Exception) {
            false
        }
    }

    fun isShizukuPermissionGranted(): Boolean {
        return try {
            Shizuku.checkSelfPermission() == android.content.pm.PackageManager.PERMISSION_GRANTED
        } catch (_: Exception) {
            false
        }
    }

    fun requestShizukuPermission() {
        try {
            Shizuku.requestPermission(1002)
        } catch (_: Exception) {
        }
    }

    fun getAccessMethod(context: Context): String {
        val prefs = context.getSharedPreferences("settings", Context.MODE_PRIVATE)
        return prefs.getString("access_method", "none") ?: "none"
    }

    fun setAccessMethod(context: Context, method: String) {
        context.getSharedPreferences("settings", Context.MODE_PRIVATE)
            .edit().putString("access_method", method).apply()
    }

    fun hasAnyAccessMethod(context: Context): Boolean {
        val saved = getAccessMethod(context)
        if (saved == "root" && isRootAvailable()) return true
        if (saved == "shizuku" && isShizukuAvailable() && isShizukuPermissionGranted()) return true
        if (isRootAvailable()) {
            setAccessMethod(context, "root")
            return true
        }
        if (isShizukuAvailable() && isShizukuPermissionGranted()) {
            setAccessMethod(context, "shizuku")
            return true
        }
        return false
    }
}

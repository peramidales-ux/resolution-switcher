package com.resolution.switcher

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent

class BootReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent?) {
        if (intent?.action != Intent.ACTION_BOOT_COMPLETED) return
        Thread {
            try {
                Runtime.getRuntime().exec(arrayOf("su", "-c", "wm size reset")).waitFor()
                Runtime.getRuntime().exec(arrayOf("su", "-c", "wm density reset")).waitFor()
                Runtime.getRuntime().exec(arrayOf("su", "-c", "settings delete system display_size_forced")).waitFor()
            } catch (_: Exception) {}
        }.start()
    }
}

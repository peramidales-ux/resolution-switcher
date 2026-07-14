package com.resolution.switcher

import android.app.usage.UsageStatsManager
import android.content.Context
import android.content.pm.PackageManager
import android.os.Handler
import android.os.Looper
import com.resolution.switcher.resolution.ResolutionController
import com.resolution.switcher.util.OverlayPrefs
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch

class ForegroundAppMonitor(
    private val context: Context,
    private val resolutionController: ResolutionController?,
    private val onResolutionApplied: (() -> Unit)? = null,
    private val onResolutionReset: (() -> Unit)? = null
) {

    private val handler = Handler(Looper.getMainLooper())
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val checkInterval = 1500L
    private var running = false
    private var lastWasApp = false
    private var launcherPackages: Set<String> = emptySet()

    private val checkRunnable = object : Runnable {
        override fun run() {
            if (!running) return
            checkForeground()
            handler.postDelayed(this, checkInterval)
        }
    }

    fun start() {
        if (running) return
        running = true
        launcherPackages = findLauncherPackages()
        handler.post(checkRunnable)
    }

    fun stop() {
        running = false
        handler.removeCallbacks(checkRunnable)
        scope.cancel()
    }

    private fun checkForeground() {
        val topPackage = getTopPackage() ?: return
        val isLauncher = topPackage in launcherPackages || topPackage == context.packageName

        if (!isLauncher && !lastWasApp) {
            applySavedResolution()
            lastWasApp = true
        } else if (isLauncher && lastWasApp) {
            resetResolution()
            lastWasApp = false
        }
    }

    private fun applySavedResolution() {
        val saved = OverlayPrefs.getSavedResolution(context) ?: return
        val (w, h, dpi) = saved
        val ctrl = resolutionController ?: return
        scope.launch {
            ctrl.setResolution(w, h)
            ctrl.setDensity(dpi)
            handler.post { onResolutionApplied?.invoke() }
        }
    }

    private fun resetResolution() {
        val ctrl = resolutionController ?: return
        scope.launch {
            ctrl.resetResolution()
            ctrl.resetDensity()
            handler.post { onResolutionReset?.invoke() }
        }
    }

    private fun getTopPackage(): String? {
        val usageStatsManager = context.getSystemService(Context.USAGE_STATS_SERVICE) as? UsageStatsManager
            ?: return null

        val endTime = System.currentTimeMillis()
        val beginTime = endTime - 5000

        val stats = usageStatsManager.queryEvents(beginTime, endTime)
        var lastForegroundPackage: String? = null

        val event = android.app.usage.UsageEvents.Event()
        while (stats.hasNextEvent()) {
            stats.getNextEvent(event)
            if (event.eventType == android.app.usage.UsageEvents.Event.MOVE_TO_FOREGROUND) {
                lastForegroundPackage = event.packageName
            }
        }
        return lastForegroundPackage
    }

    private fun findLauncherPackages(): Set<String> {
        val intent = android.content.Intent(android.content.Intent.ACTION_MAIN).apply {
            addCategory(android.content.Intent.CATEGORY_HOME)
        }
        val resolveInfos = context.packageManager.queryIntentActivities(intent, PackageManager.MATCH_DEFAULT_ONLY)
        return resolveInfos.map { it.activityInfo.packageName }.toSet()
    }
}

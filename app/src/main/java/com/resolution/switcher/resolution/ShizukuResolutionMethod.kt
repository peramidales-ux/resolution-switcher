package com.resolution.switcher.resolution

import android.content.Context
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import rikka.shizuku.Shizuku
import java.lang.reflect.Method

class ShizukuResolutionMethod(private val context: Context) : ResolutionController {

    private var newProcessMethod: Method? = null

    init {
        try {
            newProcessMethod = Shizuku::class.java.getDeclaredMethod(
                "newProcess",
                Array<String>::class.java,
                Array<String>::class.java,
                String::class.java
            )
            newProcessMethod?.isAccessible = true
        } catch (_: Exception) {
        }
    }

    override suspend fun setResolution(width: Int, height: Int): Boolean = withContext(Dispatchers.IO) {
        executeCommand("wm overscan 0,0,0,0")
        executeCommand("wm size ${width}x${height}")
        executeCommand("settings put system display_size_forced ${width}x${height}")
        true
    }

    override suspend fun resetResolution(): Boolean = withContext(Dispatchers.IO) {
        executeCommand("wm overscan reset")
        executeCommand("wm size reset")
        executeCommand("settings delete system display_size_forced")
        true
    }

    override suspend fun getNativeResolution(): Pair<Int, Int>? = withContext(Dispatchers.IO) {
        val output = executeCommandWithOutput("wm size") ?: return@withContext null
        RootResolutionMethod.parseNativeResolution(output)
    }

    override suspend fun getCurrentResolution(): Pair<Int, Int>? = withContext(Dispatchers.IO) {
        val output = executeCommandWithOutput("wm size") ?: return@withContext null
        RootResolutionMethod.parseCurrentResolution(output)
    }

    override suspend fun setDensity(dpi: Int): Boolean = withContext(Dispatchers.IO) {
        executeCommand("wm density $dpi")
    }

    override suspend fun getNativeDensity(): Int? = withContext(Dispatchers.IO) {
        val output = executeCommandWithOutput("wm density") ?: return@withContext null
        RootResolutionMethod.parseNativeDensity(output)
    }

    override suspend fun resetDensity(): Boolean = withContext(Dispatchers.IO) {
        executeCommand("wm density reset")
    }

    private fun executeCommand(command: String): Boolean {
        return try {
            val process = newProcessMethod?.invoke(
                null,
                arrayOf("sh", "-c", command),
                null,
                null
            ) as? Process
            process?.waitFor() == 0
        } catch (_: Exception) {
            false
        }
    }

    private fun executeCommandWithOutput(command: String): String? {
        return try {
            val process = newProcessMethod?.invoke(
                null,
                arrayOf("sh", "-c", command),
                null,
                null
            ) as? Process
            val output = process?.inputStream?.bufferedReader()?.readText()
            process?.waitFor()
            output
        } catch (_: Exception) {
            null
        }
    }
}

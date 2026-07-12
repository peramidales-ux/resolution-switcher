package com.resolution.switcher.resolution

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

class RootResolutionMethod : ResolutionController {

    override suspend fun setResolution(width: Int, height: Int): Boolean = withContext(Dispatchers.IO) {
        // Try multiple approaches to force screen stretch
        executeCommand("wm overscan 0,0,0,0")
        executeCommand("wm size ${width}x${height}")
        // Force display manager to apply new config
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
        parseNativeResolution(output)
    }

    override suspend fun getCurrentResolution(): Pair<Int, Int>? = withContext(Dispatchers.IO) {
        val output = executeCommandWithOutput("wm size") ?: return@withContext null
        parseCurrentResolution(output)
    }

    override suspend fun setDensity(dpi: Int): Boolean = withContext(Dispatchers.IO) {
        executeCommand("wm density $dpi")
    }

    override suspend fun getNativeDensity(): Int? = withContext(Dispatchers.IO) {
        val output = executeCommandWithOutput("wm density") ?: return@withContext null
        parseNativeDensity(output)
    }

    override suspend fun resetDensity(): Boolean = withContext(Dispatchers.IO) {
        executeCommand("wm density reset")
    }

    private fun executeCommand(command: String): Boolean {
        return try {
            val process = Runtime.getRuntime().exec(arrayOf("su", "-c", command))
            val exitCode = process.waitFor()
            exitCode == 0
        } catch (_: Exception) {
            false
        }
    }

    private fun executeCommandWithOutput(command: String): String? {
        return try {
            val process = Runtime.getRuntime().exec(arrayOf("su", "-c", command))
            val output = process.inputStream.bufferedReader().readText()
            process.waitFor()
            output
        } catch (_: Exception) {
            null
        }
    }

    companion object {
        fun parseNativeResolution(output: String): Pair<Int, Int>? {
            val match = Regex("Physical size:\\s*(\\d+)x(\\d+)").find(output)
                ?: Regex("Override size:\\s*(\\d+)x(\\d+)").find(output)
            return match?.let {
                Pair(it.groupValues[1].toInt(), it.groupValues[2].toInt())
            }
        }

        fun parseCurrentResolution(output: String): Pair<Int, Int>? {
            val overrideMatch = Regex("Override size:\\s*(\\d+)x(\\d+)").find(output)
            if (overrideMatch != null) {
                return Pair(overrideMatch.groupValues[1].toInt(), overrideMatch.groupValues[2].toInt())
            }
            return parseNativeResolution(output)
        }

        fun parseNativeDensity(output: String): Int? {
            val match = Regex("Physical density:\\s*(\\d+)").find(output)
                ?: Regex("Override density:\\s*(\\d+)").find(output)
            return match?.groupValues?.get(1)?.toInt()
        }
    }
}

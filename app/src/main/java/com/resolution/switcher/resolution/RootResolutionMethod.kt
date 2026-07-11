package com.resolution.switcher.resolution

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

class RootResolutionMethod : ResolutionController {

    override suspend fun setResolution(width: Int, height: Int): Boolean = withContext(Dispatchers.IO) {
        executeCommand("wm size ${width}x${height}")
    }

    override suspend fun resetResolution(): Boolean = withContext(Dispatchers.IO) {
        executeCommand("wm size reset")
    }

    override suspend fun getNativeResolution(): Pair<Int, Int>? = withContext(Dispatchers.IO) {
        val output = executeCommandWithOutput("wm size") ?: return@withContext null
        parseNativeResolution(output)
    }

    override suspend fun getCurrentResolution(): Pair<Int, Int>? = withContext(Dispatchers.IO) {
        val output = executeCommandWithOutput("wm size") ?: return@withContext null
        parseCurrentResolution(output)
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
    }
}

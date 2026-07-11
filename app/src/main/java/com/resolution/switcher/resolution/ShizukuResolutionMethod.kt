package com.resolution.switcher.resolution

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import rikka.shizuku.Shizuku

class ShizukuResolutionMethod : ResolutionController {

    override suspend fun setResolution(width: Int, height: Int): Boolean = withContext(Dispatchers.IO) {
        executeCommand("wm size ${width}x${height}")
    }

    override suspend fun resetResolution(): Boolean = withContext(Dispatchers.IO) {
        executeCommand("wm size reset")
    }

    override suspend fun getNativeResolution(): Pair<Int, Int>? = withContext(Dispatchers.IO) {
        val output = executeCommandWithOutput("wm size") ?: return@withContext null
        RootResolutionMethod.parseNativeResolution(output)
    }

    override suspend fun getCurrentResolution(): Pair<Int, Int>? = withContext(Dispatchers.IO) {
        val output = executeCommandWithOutput("wm size") ?: return@withContext null
        RootResolutionMethod.parseCurrentResolution(output)
    }

    private fun executeCommand(command: String): Boolean {
        return try {
            val process = Shizuku.newProcess(arrayOf("sh", "-c", command), null, null)
            process.waitFor() == 0
        } catch (_: Exception) {
            false
        }
    }

    private fun executeCommandWithOutput(command: String): String? {
        return try {
            val process = Shizuku.newProcess(arrayOf("sh", "-c", command), null, null)
            val output = process.inputStream.bufferedReader().readText()
            process.waitFor()
            output
        } catch (_: Exception) {
            null
        }
    }
}

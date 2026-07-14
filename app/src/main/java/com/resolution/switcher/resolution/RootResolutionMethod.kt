package com.resolution.switcher.resolution

import android.content.Context
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

class RootResolutionMethod(private val context: Context) : ResolutionController {

    override suspend fun setResolution(width: Int, height: Int): Boolean = withContext(Dispatchers.IO) {
        // 1) SurfaceFlinger — реальное разрешение дисплея (влияет на OpenGL/Vulkan игры)
        setDisplayModeSF(width, height)
        // 2) wm size — оконный менеджер (для обычных приложений)
        executeCommand("wm size ${width}x${height}")
        executeCommand("settings put system display_size_forced ${width}x${height}")
        true
    }

    override suspend fun resetResolution(): Boolean = withContext(Dispatchers.IO) {
        // Сброс SurfaceFlinger
        resetDisplayModeSF()
        // Сброс wm size
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

    /**
     * SurfaceFlinger transaction 1013 — setActiveDisplayModeWithConstraints.
     * Меняет реальное разрешение framebuffer. Влияет на ВСЁ, включая OpenGL/Vulkan игры.
     * Формат: service call SurfaceFlinger 1013 i32 {width} i32 {height} i32 {fpsNumerator} i32 {fpsDenominator}
     */
    private fun setDisplayModeSF(width: Int, height: Int): Boolean {
        val native = getNativeResolution() ?: return false
        val fps = getRefreshRate() ?: 60
        // Пробуем разные коды транзакций (зависят от устройства/версии Android)
        for (txCode in listOf(1013, 1024, 1035)) {
            val cmd = "service call SurfaceFlinger $txCode " +
                    "i32 $width i32 $height i32 $fps i32 1"
            if (executeCommand(cmd)) return true
        }
        return false
    }

    private fun resetDisplayModeSF(): Boolean {
        val native = getNativeResolution() ?: return false
        val fps = getRefreshRate() ?: 60
        for (txCode in listOf(1013, 1024, 1035)) {
            val cmd = "service call SurfaceFlinger $txCode " +
                    "i32 ${native.first} i32 ${native.second} i32 $fps i32 1"
            if (executeCommand(cmd)) return true
        }
        return false
    }

    private fun getRefreshRate(): Int? {
        val output = executeCommandWithOutput("dumpsys SurfaceFlinger --display-id") ?: return null
        // Ищем текущий refresh rate
        val match = Regex("refreshRate:\\s*(\\d+)").find(output)
        return match?.groupValues?.get(1)?.toIntOrNull()
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

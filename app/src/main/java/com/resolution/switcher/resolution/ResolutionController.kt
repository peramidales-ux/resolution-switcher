package com.resolution.switcher.resolution

import android.content.Context
import com.resolution.switcher.util.PermissionHelper

interface ResolutionController {
    suspend fun setResolution(width: Int, height: Int): Boolean
    suspend fun resetResolution(): Boolean
    suspend fun getNativeResolution(): Pair<Int, Int>?
    suspend fun getCurrentResolution(): Pair<Int, Int>?
    suspend fun setDensity(dpi: Int): Boolean
    suspend fun getNativeDensity(): Int?
    suspend fun resetDensity(): Boolean

    companion object {
        fun create(context: Context): ResolutionController? {
            val method = PermissionHelper.getAccessMethod(context)
            return when (method) {
                "root" -> RootResolutionMethod(context)
                "shizuku" -> ShizukuResolutionMethod(context)
                else -> null
            }
        }
    }
}

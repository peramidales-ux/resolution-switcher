package com.resolution.switcher.presets

import android.content.Context
import org.json.JSONArray
import org.json.JSONObject

data class Preset(
    val id: Long = System.currentTimeMillis(),
    val name: String,
    val width: Int,
    val height: Int
)

class PresetStorage(context: Context) {

    private val prefs = context.getSharedPreferences("presets", Context.MODE_PRIVATE)

    fun getAll(): List<Preset> {
        val json = prefs.getString("presets", "[]") ?: "[]"
        return try {
            val array = JSONArray(json)
            (0 until array.length()).map { i ->
                val obj = array.getJSONObject(i)
                Preset(
                    id = obj.getLong("id"),
                    name = obj.getString("name"),
                    width = obj.getInt("width"),
                    height = obj.getInt("height")
                )
            }
        } catch (_: Exception) {
            emptyList()
        }
    }

    fun save(preset: Preset) {
        val list = getAll().toMutableList()
        val existingIndex = list.indexOfFirst { it.id == preset.id }
        if (existingIndex >= 0) {
            list[existingIndex] = preset
        } else {
            list.add(preset)
        }
        persist(list)
    }

    fun delete(id: Long) {
        val list = getAll().filter { it.id != id }
        persist(list)
    }

    private fun persist(list: List<Preset>) {
        val array = JSONArray()
        list.forEach { preset ->
            val obj = JSONObject().apply {
                put("id", preset.id)
                put("name", preset.name)
                put("width", preset.width)
                put("height", preset.height)
            }
            array.put(obj)
        }
        prefs.edit().putString("presets", array.toString()).apply()
    }
}

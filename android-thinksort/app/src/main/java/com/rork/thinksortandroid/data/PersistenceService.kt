package com.rork.thinksortandroid.data

import android.content.Context
import android.util.Log
import kotlinx.serialization.json.Json
import java.io.File

/** Stores everything as a single JSON file in app-private storage. Nothing leaves the device. */
class PersistenceService(context: Context) {
    private val file = File(context.filesDir, "thinksort-data.json")

    fun load(): AppData? {
        if (!file.exists()) return null
        return try {
            json.decodeFromString(AppData.serializer(), file.readText())
        } catch (e: Exception) {
            Log.e(TAG, "Could not read saved data: ${e.javaClass.simpleName}")
            null
        }
    }

    fun save(data: AppData) {
        try {
            val temp = File(file.parentFile, "${file.name}.tmp")
            temp.writeText(json.encodeToString(AppData.serializer(), data))
            if (!temp.renameTo(file)) {
                file.writeText(temp.readText())
                temp.delete()
            }
        } catch (e: Exception) {
            Log.e(TAG, "Could not save data: ${e.javaClass.simpleName}")
        }
    }

    fun exportJSON(data: AppData): String = prettyJson.encodeToString(AppData.serializer(), data)

    fun deleteAll() {
        file.delete()
    }

    companion object {
        private const val TAG = "Persistence"
        val json = Json {
            ignoreUnknownKeys = true
            encodeDefaults = true
            explicitNulls = false
        }
        private val prettyJson = Json {
            prettyPrint = true
            encodeDefaults = true
            explicitNulls = false
        }
    }
}

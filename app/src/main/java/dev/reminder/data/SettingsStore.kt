package dev.reminder.data

import java.io.File
import java.time.LocalDateTime
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import org.json.JSONObject

data class AppSettings(
    val defaultSnoozeMinutes: Int = 10,
    val quietEnabled: Boolean = false,
    val quietStartMinute: Int = 12 * 60,
    val quietEndMinute: Int = 14 * 60,
    val oemGuideShown: Boolean = false
)

class SettingsStore(private val file: File) {
    private val lock = Any()

    private val _state = MutableStateFlow(read())
    val state: StateFlow<AppSettings> = _state

    fun update(transform: (AppSettings) -> AppSettings) {
        synchronized(lock) {
            val next = transform(_state.value)
            _state.value = next
            write(next)
        }
    }

    /** 当前是否处于免打扰时段（要求 start < end，不支持跨零点） */
    fun inQuietWindow(at: LocalDateTime): Boolean {
        val s = _state.value
        if (!s.quietEnabled) return false
        if (s.quietStartMinute >= s.quietEndMinute) return false
        val minute = at.hour * 60 + at.minute
        return minute >= s.quietStartMinute && minute < s.quietEndMinute
    }

    private fun read(): AppSettings = try {
        if (!file.exists()) AppSettings() else {
            val obj = JSONObject(file.readText())
            AppSettings(
                defaultSnoozeMinutes = obj.optInt("defaultSnoozeMinutes", 10).coerceIn(1, 120),
                quietEnabled = obj.optBoolean("quietEnabled", false),
                quietStartMinute = obj.optInt("quietStartMinute", 12 * 60).coerceIn(0, 1439),
                quietEndMinute = obj.optInt("quietEndMinute", 14 * 60).coerceIn(0, 1439),
                oemGuideShown = obj.optBoolean("oemGuideShown", false)
            )
        }
    } catch (e: Exception) {
        AppSettings()
    }

    private fun write(settings: AppSettings) {
        try {
            val obj = JSONObject()
            obj.put("defaultSnoozeMinutes", settings.defaultSnoozeMinutes)
            obj.put("quietEnabled", settings.quietEnabled)
            obj.put("quietStartMinute", settings.quietStartMinute)
            obj.put("quietEndMinute", settings.quietEndMinute)
            obj.put("oemGuideShown", settings.oemGuideShown)
            file.parentFile?.mkdirs()
            file.writeText(obj.toString())
        } catch (e: Exception) {
            // 忽略落盘失败
        }
    }
}

package dev.reminder.data

import java.io.File
import java.util.UUID
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import org.json.JSONArray
import org.json.JSONObject

/**
 * 任务与日志的本地仓库。
 *
 * v1 用 JSON 文件持久化（无 SQLite / 无注解处理器），对外暴露 StateFlow。
 * 写操作同步落盘，调用方负责在 IO 线程调用（见 AppGraph.ioScope）。
 */
class TaskRepository(
    private val taskFile: File,
    private val logFile: File
) {
    private val lock = Any()

    private val _tasks = MutableStateFlow(readTasks())
    val tasks: StateFlow<List<ReminderTask>> = _tasks

    private val _logs = MutableStateFlow(readLogs())
    val logs: StateFlow<List<ReminderLog>> = _logs

    fun get(id: String): ReminderTask? = _tasks.value.firstOrNull { it.id == id }

    fun all(): List<ReminderTask> = _tasks.value

    fun newId(): String = UUID.randomUUID().toString()

    fun save(task: ReminderTask) {
        synchronized(lock) {
            val list = _tasks.value.filter { it.id != task.id } + task
            _tasks.value = list
            writeTasks(list)
        }
    }

    fun replaceAll(list: List<ReminderTask>) {
        synchronized(lock) {
            _tasks.value = list
            writeTasks(list)
        }
    }

    fun delete(id: String) {
        synchronized(lock) {
            val list = _tasks.value.filter { it.id != id }
            _tasks.value = list
            writeTasks(list)
        }
    }

    fun log(taskId: String, action: String) {
        synchronized(lock) {
            val list = (_logs.value + ReminderLog(taskId, System.currentTimeMillis(), action)).takeLast(MAX_LOGS)
            _logs.value = list
            writeLogs(list)
        }
    }

    // ---------- 备份 ----------

    fun exportJson(): String {
        val arr = JSONArray()
        _tasks.value.forEach { arr.put(taskToJson(it)) }
        val root = JSONObject()
        root.put("format", FORMAT)
        root.put("version", 1)
        root.put("exportedAt", System.currentTimeMillis())
        root.put("tasks", arr)
        return root.toString(2)
    }

    /** 返回导入的任务数；解析失败抛 IllegalArgumentException */
    fun importJson(text: String, replace: Boolean): Int {
        val root = JSONObject(text)
        if (root.optString("format") != FORMAT) {
            throw IllegalArgumentException("不是本应用的备份文件")
        }
        val arr = root.optJSONArray("tasks") ?: JSONArray()
        val imported = ArrayList<ReminderTask>()
        for (i in 0 until arr.length()) {
            val obj = arr.optJSONObject(i) ?: continue
            imported += taskFromJson(obj)
        }
        if (imported.isEmpty()) return 0
        synchronized(lock) {
            val list = if (replace) {
                imported
            } else {
                // 同 id 覆盖，其余追加
                val ids = imported.map { it.id }.toSet()
                _tasks.value.filter { it.id !in ids } + imported
            }
            _tasks.value = list
            writeTasks(list)
        }
        return imported.size
    }

    // ---------- 读写 ----------

    private fun readTasks(): List<ReminderTask> = try {
        if (!taskFile.exists()) emptyList() else {
            val arr = JSONArray(taskFile.readText())
            val out = ArrayList<ReminderTask>()
            for (i in 0 until arr.length()) {
                val obj = arr.optJSONObject(i) ?: continue
                out += taskFromJson(obj)
            }
            out
        }
    } catch (e: Exception) {
        emptyList()
    }

    private fun writeTasks(list: List<ReminderTask>) {
        try {
            val arr = JSONArray()
            list.forEach { arr.put(taskToJson(it)) }
            taskFile.parentFile?.mkdirs()
            taskFile.writeText(arr.toString())
        } catch (e: Exception) {
            // 落盘失败不影响内存态
        }
    }

    private fun readLogs(): List<ReminderLog> = try {
        if (!logFile.exists()) emptyList() else {
            val arr = JSONArray(logFile.readText())
            val out = ArrayList<ReminderLog>()
            for (i in 0 until arr.length()) {
                val obj = arr.optJSONObject(i) ?: continue
                out += ReminderLog(
                    taskId = obj.optString("taskId"),
                    atEpochMs = obj.optLong("atEpochMs"),
                    action = obj.optString("action")
                )
            }
            out
        }
    } catch (e: Exception) {
        emptyList()
    }

    private fun writeLogs(list: List<ReminderLog>) {
        try {
            val arr = JSONArray()
            list.forEach { log ->
                val obj = JSONObject()
                obj.put("taskId", log.taskId)
                obj.put("atEpochMs", log.atEpochMs)
                obj.put("action", log.action)
                arr.put(obj)
            }
            logFile.parentFile?.mkdirs()
            logFile.writeText(arr.toString())
        } catch (e: Exception) {
            // 忽略
        }
    }

    private companion object {
        const val FORMAT = "reminder-app-backup"
        const val MAX_LOGS = 5000
    }
}

fun taskToJson(task: ReminderTask): JSONObject {
    val obj = JSONObject()
    obj.put("id", task.id)
    obj.put("name", task.name)
    obj.put("emoji", task.emoji)
    obj.put("prompt", task.prompt)
    obj.put("scheduleType", task.scheduleType.name)
    obj.put("intervalMinutes", task.intervalMinutes)
    obj.put("dailyTimes", JSONArray(task.dailyTimes))
    obj.put("windowStartMinute", task.windowStartMinute)
    obj.put("windowEndMinute", task.windowEndMinute)
    obj.put("daysOfWeek", JSONArray(task.daysOfWeek))
    obj.put("snoozeMinutes", task.snoozeMinutes)
    obj.put("quiet", task.quiet)
    obj.put("enabled", task.enabled)
    obj.put("nextFireEpochMs", task.nextFireEpochMs ?: JSONObject.NULL)
    obj.put("lastFiredEpochMs", task.lastFiredEpochMs ?: JSONObject.NULL)
    obj.put("createdAtEpochMs", task.createdAtEpochMs)
    return obj
}

fun taskFromJson(obj: JSONObject): ReminderTask {
    val times = ArrayList<String>()
    obj.optJSONArray("dailyTimes")?.let { arr ->
        for (i in 0 until arr.length()) {
            val s = arr.optString(i)
            if (s.length >= 4) times += s
        }
    }
    val days = ArrayList<Int>()
    obj.optJSONArray("daysOfWeek")?.let { arr ->
        for (i in 0 until arr.length()) days += arr.optInt(i)
    }
    val type = try {
        ScheduleType.valueOf(obj.optString("scheduleType", ScheduleType.INTERVAL.name))
    } catch (e: Exception) {
        ScheduleType.INTERVAL
    }
    return ReminderTask(
        id = obj.optString("id").ifEmpty { java.util.UUID.randomUUID().toString() },
        name = obj.optString("name", "提醒"),
        emoji = obj.optString("emoji", "⏰"),
        prompt = obj.optString("prompt", "该提醒你了"),
        scheduleType = type,
        intervalMinutes = obj.optInt("intervalMinutes", 30).coerceIn(1, 1440),
        dailyTimes = times,
        windowStartMinute = obj.optInt("windowStartMinute", 9 * 60).coerceIn(0, 1439),
        windowEndMinute = obj.optInt("windowEndMinute", 22 * 60).coerceIn(0, 1439),
        daysOfWeek = if (days.isEmpty()) listOf(1, 2, 3, 4, 5, 6, 7) else days,
        snoozeMinutes = obj.optInt("snoozeMinutes", 10).coerceIn(1, 120),
        quiet = obj.optBoolean("quiet", false),
        enabled = obj.optBoolean("enabled", true),
        nextFireEpochMs = if (obj.has("nextFireEpochMs") && !obj.isNull("nextFireEpochMs")) obj.optLong("nextFireEpochMs") else null,
        lastFiredEpochMs = if (obj.has("lastFiredEpochMs") && !obj.isNull("lastFiredEpochMs")) obj.optLong("lastFiredEpochMs") else null,
        createdAtEpochMs = obj.optLong("createdAtEpochMs", System.currentTimeMillis())
    )
}

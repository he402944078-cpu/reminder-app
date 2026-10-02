package dev.reminder.domain

import dev.reminder.data.ReminderTask
import dev.reminder.data.ScheduleType
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime

/**
 * 纯函数调度器：不依赖 Android，可直接单元测试。
 *
 * 约定：
 * - 时间窗为一天内的 [windowStartMinute, windowEndMinute]，要求 start < end（不支持跨零点窗口）。
 * - INTERVAL 以「时间窗起点」为锚点按间隔对齐，跨窗不提醒，新的一天重新对齐。
 * - 未选中星期不产生槽位。
 */
object Scheduler {

    /** 向后搜索的最大天数，防止整周未勾选时无限循环 */
    const val MAX_SEARCH_DAYS = 400

    /**
     * 计算严格晚于 [after] 的下一次触发时间。
     * 任务被禁用时返回 null。
     */
    fun nextFire(task: ReminderTask, after: LocalDateTime, maxDays: Int = MAX_SEARCH_DAYS): LocalDateTime? {
        if (!task.enabled) return null
        var date = after.toLocalDate()
        var guard = 0
        while (guard <= maxDays) {
            if (task.daysOfWeek.contains(date.dayOfWeek.value)) {
                for (slot in slotsOf(task, date)) {
                    val dt = LocalDateTime.of(date, slot)
                    if (dt.isAfter(after)) return dt
                }
            }
            date = date.plusDays(1)
            guard++
        }
        return null
    }

    /** 某天内的全部触发槽位（升序） */
    fun slotsOf(task: ReminderTask, date: LocalDate): List<LocalTime> = when (task.scheduleType) {
        ScheduleType.INTERVAL -> intervalSlots(task)
        ScheduleType.DAILY_TIMES -> task.dailyTimes.mapNotNull { parseTime(it) }.sorted()
    }

    /** 由间隔与时间窗生成槽位 */
    fun intervalSlots(task: ReminderTask): List<LocalTime> {
        val step = task.intervalMinutes.coerceAtLeast(1)
        val start = task.windowStartMinute.coerceIn(0, 1439)
        val end = task.windowEndMinute.coerceIn(0, 1439)
        if (end < start) return emptyList()
        val out = ArrayList<LocalTime>()
        var m = start
        while (m <= end) {
            out += LocalTime.of(m / 60, m % 60)
            m += step
        }
        return out
    }

    /** 某天内严格晚于 [after] 的首个槽位；当天没有则返回 null */
    fun firstSlotAfter(task: ReminderTask, after: LocalDateTime): LocalDateTime? {
        if (!task.daysOfWeek.contains(after.toLocalDate().dayOfWeek.value)) return null
        for (slot in slotsOf(task, after.toLocalDate())) {
            val dt = LocalDateTime.of(after.toLocalDate(), slot)
            if (dt.isAfter(after)) return dt
        }
        return null
    }

    fun parseTime(text: String): LocalTime? = try {
        val parts = text.split(":")
        if (parts.size < 2) null else LocalTime.of(parts[0].toInt(), parts[1].toInt())
    } catch (e: Exception) {
        null
    }

    fun formatTime(time: LocalTime): String = "%02d:%02d".format(time.hour, time.minute)

    fun formatMinuteOfDay(minute: Int): String = "%02d:%02d".format(minute / 60, minute % 60)

    fun toEpochMs(dt: LocalDateTime): Long =
        dt.atZone(java.time.ZoneId.systemDefault()).toInstant().toEpochMilli()

    fun fromEpochMs(ms: Long): LocalDateTime =
        LocalDateTime.ofInstant(java.time.Instant.ofEpochMilli(ms), java.time.ZoneId.systemDefault())
}

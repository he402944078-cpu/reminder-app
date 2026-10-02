package dev.reminder.data

/** 调度类型 */
enum class ScheduleType {
    /** 每隔 intervalMinutes 分钟，在时间窗内对齐 */
    INTERVAL,

    /** 每日固定时刻列表 */
    DAILY_TIMES
}

/**
 * 一条提醒任务。
 *
 * 时间语义：
 * - [windowStartMinute] / [windowEndMinute]：一天内的时间窗（分钟数 0..1439），要求 start < end。
 * - [daysOfWeek]：java.time.DayOfWeek.value，1=周一 … 7=周日。
 * - [nextFireEpochMs] / [lastFiredEpochMs]：调度器写入的运行态字段。
 */
data class ReminderTask(
    val id: String,
    val name: String,
    val emoji: String,
    val prompt: String,
    val scheduleType: ScheduleType,
    val intervalMinutes: Int,
    val dailyTimes: List<String>,
    val windowStartMinute: Int,
    val windowEndMinute: Int,
    val daysOfWeek: List<Int>,
    val snoozeMinutes: Int,
    val quiet: Boolean,
    val enabled: Boolean,
    val nextFireEpochMs: Long?,
    val lastFiredEpochMs: Long?,
    val createdAtEpochMs: Long
)

/** 提醒动作日志 */
data class ReminderLog(
    val taskId: String,
    val atEpochMs: Long,
    val action: String
) {
    companion object {
        const val FIRED = "FIRED"
        const val DONE = "DONE"
        const val SNOOZED = "SNOOZED"
        const val SKIPPED = "SKIPPED"
    }
}

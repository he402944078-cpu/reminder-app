package dev.reminder.domain

import dev.reminder.data.ReminderTask
import java.time.LocalDateTime

/**
 * 错过合并策略：设备关机/省电导致连续错过 K 次时，只补发 1 条并说明次数，不刷屏。
 */
object MergePolicy {

    /** 统计 (lastFired, now] 区间内应触发但未触发的次数 */
    fun countMissed(task: ReminderTask, lastFired: LocalDateTime?, now: LocalDateTime): Int {
        if (lastFired == null) return 0
        if (!now.isAfter(lastFired)) return 0
        var count = 0
        var date = lastFired.toLocalDate()
        var guard = 0
        val endDate = now.toLocalDate()
        while (guard <= Scheduler.MAX_SEARCH_DAYS) {
            if (task.daysOfWeek.contains(date.dayOfWeek.value)) {
                for (slot in Scheduler.slotsOf(task, date)) {
                    val dt = LocalDateTime.of(date, slot)
                    if (dt.isAfter(lastFired) && !dt.isAfter(now)) count++
                }
            }
            if (date.isEqual(endDate)) break
            date = date.plusDays(1)
            guard++
        }
        return count
    }

    /** 补发文案：错过 0/1 次不额外说明，>=2 次提示合并 */
    fun missedSuffix(missed: Int): String =
        if (missed >= 2) "已合并 ${missed} 次未提醒" else ""
}

package dev.reminder.domain

import dev.reminder.data.ReminderTask
import dev.reminder.data.ScheduleType
import java.time.LocalDateTime
import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * 错过合并策略单测：省电模式 / 关机导致连续错过时，只补发 1 条并说明次数。
 */
class MergePolicyTest {

    private fun intervalTask(
        interval: Int = 30,
        startMinute: Int = 9 * 60,
        endMinute: Int = 22 * 60,
        days: List<Int> = listOf(1, 2, 3, 4, 5, 6, 7)
    ) = ReminderTask(
        id = "t1",
        name = "喝水",
        emoji = "💧",
        prompt = "喝口水吧",
        scheduleType = ScheduleType.INTERVAL,
        intervalMinutes = interval,
        dailyTimes = emptyList(),
        windowStartMinute = startMinute,
        windowEndMinute = endMinute,
        daysOfWeek = days,
        snoozeMinutes = 10,
        quiet = false,
        enabled = true,
        nextFireEpochMs = null,
        lastFiredEpochMs = null,
        createdAtEpochMs = 0L
    )

    @Test
    fun countsSlotsInsideSameDay() {
        val last = LocalDateTime.of(2025, 1, 6, 9, 0)
        val now = LocalDateTime.of(2025, 1, 6, 11, 5)
        assertEquals(4, MergePolicy.countMissed(intervalTask(), last, now))
    }

    @Test
    fun zeroWhenLastFiredMissing() {
        val now = LocalDateTime.of(2025, 1, 6, 11, 5)
        assertEquals(0, MergePolicy.countMissed(intervalTask(), null, now))
    }

    @Test
    fun zeroWhenNowNotAfterLastFired() {
        val last = LocalDateTime.of(2025, 1, 6, 11, 0)
        assertEquals(0, MergePolicy.countMissed(intervalTask(), last, last))
        assertEquals(
            0,
            MergePolicy.countMissed(
                intervalTask(),
                last,
                LocalDateTime.of(2025, 1, 6, 10, 0)
            )
        )
    }

    @Test
    fun countsAcrossMidnightIncludingWindowEnd() {
        val last = LocalDateTime.of(2025, 1, 6, 21, 45)
        val now = LocalDateTime.of(2025, 1, 7, 9, 30)
        // 1/6 22:00 + 1/7 09:00, 09:30
        assertEquals(3, MergePolicy.countMissed(intervalTask(), last, now))
    }

    @Test
    fun skipsUnselectedWeekdays() {
        // 2025-01-05 周日，2025-01-06 周一
        val last = LocalDateTime.of(2025, 1, 5, 21, 0)
        val now = LocalDateTime.of(2025, 1, 6, 9, 30)
        assertEquals(2, MergePolicy.countMissed(intervalTask(days = listOf(1)), last, now))
    }

    @Test
    fun dailyTimesCountsOnlyListedSlots() {
        val task = ReminderTask(
            id = "t2",
            name = "三餐",
            emoji = "🍎",
            prompt = "吃饭",
            scheduleType = ScheduleType.DAILY_TIMES,
            intervalMinutes = 30,
            dailyTimes = listOf("07:30", "12:00", "18:30"),
            windowStartMinute = 0,
            windowEndMinute = 24 * 60 - 1,
            daysOfWeek = listOf(1, 2, 3, 4, 5, 6, 7),
            snoozeMinutes = 10,
            quiet = false,
            enabled = true,
            nextFireEpochMs = null,
            lastFiredEpochMs = null,
            createdAtEpochMs = 0L
        )
        val last = LocalDateTime.of(2025, 1, 6, 7, 30)
        val now = LocalDateTime.of(2025, 1, 6, 19, 0)
        assertEquals(2, MergePolicy.countMissed(task, last, now))
    }

    @Test
    fun suffixOnlyForMultipleMisses() {
        assertEquals("", MergePolicy.missedSuffix(0))
        assertEquals("", MergePolicy.missedSuffix(1))
        assertEquals("已合并 2 次未提醒", MergePolicy.missedSuffix(2))
        assertEquals("已合并 7 次未提醒", MergePolicy.missedSuffix(7))
    }
}

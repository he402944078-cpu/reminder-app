package dev.reminder.domain

import dev.reminder.data.ReminderTask
import dev.reminder.data.ScheduleType
import java.time.LocalDateTime
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * 调度内核单测：CI 的 test job 会跑这些用例。
 * 覆盖「30 分钟间隔 + 时间窗 + 星期 + 跨天 + 错过合并」这些验收语义。
 */
class SchedulerTest {

    private fun intervalTask(
        interval: Int = 30,
        startMinute: Int = 9 * 60,
        endMinute: Int = 22 * 60,
        days: List<Int> = listOf(1, 2, 3, 4, 5, 6, 7),
        enabled: Boolean = true,
        type: ScheduleType = ScheduleType.INTERVAL,
        times: List<String> = emptyList()
    ) = ReminderTask(
        id = "t1",
        name = "喝水",
        emoji = "💧",
        prompt = "喝口水吧",
        scheduleType = type,
        intervalMinutes = interval,
        dailyTimes = times,
        windowStartMinute = startMinute,
        windowEndMinute = endMinute,
        daysOfWeek = days,
        snoozeMinutes = 10,
        quiet = false,
        enabled = enabled,
        nextFireEpochMs = null,
        lastFiredEpochMs = null,
        createdAtEpochMs = 0L
    )

    private fun at(day: Int, hour: Int, minute: Int) =
        LocalDateTime.of(2025, 1, day, hour, minute)

    @Test
    fun intervalSlotsCount() {
        // 09:00..22:00 每 30 分钟 -> (1320-540)/30 + 1 = 27 个槽位
        val slots = Scheduler.intervalSlots(intervalTask())
        assertEquals(27, slots.size)
        assertEquals("09:00", Scheduler.formatTime(slots.first()))
        assertEquals("22:00", Scheduler.formatTime(slots.last()))
    }

    @Test
    fun nextFireInsideWindow() {
        val task = intervalTask()
        val next = Scheduler.nextFire(task, at(6, 9, 0)) // 2025-01-06 是周一
        assertNotNull(next)
        assertEquals(at(6, 9, 30), next)
    }

    @Test
    fun nextFireCrossesWindowEnd() {
        val task = intervalTask()
        // 22:00 是当天最后一个槽位，下一次应落到次日 09:00
        val next = Scheduler.nextFire(task, at(6, 22, 0))
        assertEquals(at(7, 9, 0), next)
    }

    @Test
    fun nextFireSkipsUnselectedWeekday() {
        val task = intervalTask(days = listOf(1, 2, 3, 4, 5))
        // 2025-01-05 是周日 -> 应跳到周一 09:00
        val next = Scheduler.nextFire(task, at(5, 12, 0))
        assertEquals(at(6, 9, 0), next)
    }

    @Test
    fun disabledTaskHasNoNextFire() {
        assertNull(Scheduler.nextFire(intervalTask(enabled = false), at(6, 9, 0)))
    }

    @Test
    fun dailyTimesPickNearestFutureSlot() {
        val task = intervalTask(
            type = ScheduleType.DAILY_TIMES,
            times = listOf("20:00", "09:00", "14:30")
        )
        assertEquals(at(6, 14, 30), Scheduler.nextFire(task, at(6, 10, 0)))
        assertEquals(at(7, 9, 0), Scheduler.nextFire(task, at(6, 21, 0)))
    }

    @Test
    fun dailyTimesSlotsAreSorted() {
        val task = intervalTask(type = ScheduleType.DAILY_TIMES, times = listOf("20:00", "09:00", "14:30"))
        val slots = Scheduler.slotsOf(task, at(6, 0, 0).toLocalDate())
        assertEquals(listOf("09:00", "14:30", "20:00"), slots.map { Scheduler.formatTime(it) })
    }

    @Test
    fun countMissedMergesMultipleFires() {
        val task = intervalTask()
        // 上次 09:00，现在 11:05 -> 09:30/10:00/10:30/11:00 共 4 次
        val missed = MergePolicy.countMissed(task, at(6, 9, 0), at(6, 11, 5))
        assertEquals(4, missed)
        assertEquals("已合并 4 次未提醒", MergePolicy.missedSuffix(missed))
        assertEquals("", MergePolicy.missedSuffix(1))
    }

    @Test
    fun countMissedZeroWhenNoLastFired() {
        assertEquals(0, MergePolicy.countMissed(intervalTask(), null, at(6, 11, 0)))
    }

    @Test
    fun intervalAnchoredToWindowStart() {
        // 锚点是窗口起点：10:05 保存任务，下一次应是 10:30 而不是 10:05+30
        val task = intervalTask(interval = 30, startMinute = 9 * 60, endMinute = 22 * 60)
        val next = Scheduler.nextFire(task, at(6, 10, 5))
        assertEquals(at(6, 10, 30), next)
    }

    @Test
    fun invalidWindowProducesNoSlots() {
        val task = intervalTask(startMinute = 22 * 60, endMinute = 9 * 60)
        assertTrue(Scheduler.intervalSlots(task).isEmpty())
        assertNull(Scheduler.nextFire(task, at(6, 10, 0)))
    }

    @Test
    fun epochRoundTrip() {
        val dt = at(6, 13, 45)
        assertEquals(dt, Scheduler.fromEpochMs(Scheduler.toEpochMs(dt)))
    }
}

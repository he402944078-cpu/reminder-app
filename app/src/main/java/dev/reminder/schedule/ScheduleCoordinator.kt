package dev.reminder.schedule

import android.content.Context
import dev.reminder.AppGraph
import dev.reminder.data.ReminderLog
import dev.reminder.data.ReminderTask
import dev.reminder.domain.MergePolicy
import dev.reminder.domain.Scheduler
import dev.reminder.notify.Notifier
import java.time.LocalDateTime

/**
 * 调度编排：所有「改任务 → 重排闹钟」的动作都收敛到这里，UI 只调用它。
 */
class ScheduleCoordinator(private val context: Context) {

    private val repo get() = AppGraph.repo
    private val alarms = AlarmScheduler(context)

    /** 任务保存后重排（以当前时间为基准） */
    fun reschedule(task: ReminderTask) {
        val now = LocalDateTime.now()
        val next = Scheduler.nextFire(task, now)
        repo.save(task.copy(nextFireEpochMs = next?.let { Scheduler.toEpochMs(it) }))
        if (next != null) {
            alarms.schedule(task.id, next)
        } else {
            alarms.cancel(task.id)
        }
    }

    /** 开机 / 改时 / 自愈：全量重建 */
    fun rescheduleAll() {
        val now = LocalDateTime.now()
        repo.all().forEach { task ->
            if (!task.enabled) {
                alarms.cancel(task.id)
                return@forEach
            }
            val next = Scheduler.nextFire(task, now)
            repo.save(task.copy(nextFireEpochMs = next?.let { Scheduler.toEpochMs(it) }))
            if (next != null) {
                alarms.schedule(task.id, next)
            } else {
                alarms.cancel(task.id)
            }
        }
    }

    /** 校验并修复：Watchdog 用，缺注册/过期就重排 */
    fun verifyAndRepair(): Int {
        val now = LocalDateTime.now()
        var repaired = 0
        repo.all().forEach { task ->
            val next = task.nextFireEpochMs
            val stale = next == null || next < Scheduler.toEpochMs(now)
            if (task.enabled && stale) {
                reschedule(task)
                repaired++
            }
        }
        return repaired
    }

    /** 闹钟到点：发通知 + 记日志 + 计算并注册下一次 */
    fun handleFired(taskId: String, snoozed: Boolean) {
        val task = repo.get(taskId) ?: return
        val now = LocalDateTime.now()
        val lastFired = task.lastFiredEpochMs?.let { Scheduler.fromEpochMs(it) }
        val missed = if (snoozed) 0 else MergePolicy.countMissed(task, lastFired, now)

        repo.log(taskId, ReminderLog.FIRED)
        val updated = task.copy(lastFiredEpochMs = Scheduler.toEpochMs(now))
        repo.save(updated)

        val next = Scheduler.nextFire(updated, now)
        val withNext = updated.copy(nextFireEpochMs = next?.let { Scheduler.toEpochMs(it) })
        repo.save(withNext)
        if (next != null) {
            alarms.schedule(taskId, next)
        } else {
            alarms.cancel(taskId)
        }

        Notifier.show(context, withNext, missed, snoozed, next)
    }

    /** 贪睡：只推迟本次，不改变整体节拍（触发后由 handleFired 按原节拍续排） */
    fun snooze(taskId: String, minutes: Int) {
        val task = repo.get(taskId) ?: return
        val at = LocalDateTime.now().plusMinutes(minutes.coerceAtLeast(1).toLong())
        alarms.schedule(taskId, at, snoozed = true)
        repo.save(task.copy(nextFireEpochMs = Scheduler.toEpochMs(at)))
        repo.log(taskId, ReminderLog.SNOOZED)
    }

    fun complete(taskId: String) {
        repo.log(taskId, ReminderLog.DONE)
        val task = repo.get(taskId) ?: return
        reschedule(task)
    }

    fun skip(taskId: String) {
        repo.log(taskId, ReminderLog.SKIPPED)
        val task = repo.get(taskId) ?: return
        reschedule(task)
    }

    fun setEnabled(taskId: String, enabled: Boolean) {
        val task = repo.get(taskId) ?: return
        val updated = task.copy(enabled = enabled)
        repo.save(updated)
        if (enabled) {
            reschedule(updated)
        } else {
            alarms.cancel(taskId)
            repo.save(updated.copy(nextFireEpochMs = null))
            Notifier.cancelTask(context, taskId)
        }
    }

    fun cancelAlarm(taskId: String) {
        alarms.cancel(taskId)
    }
}

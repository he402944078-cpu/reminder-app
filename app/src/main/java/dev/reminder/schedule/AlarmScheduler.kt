package dev.reminder.schedule

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build
import java.time.LocalDateTime
import java.time.ZoneId

/**
 * 精确闹钟封装：自链式调度（每次触发后由接收器重新注册下一次）。
 *
 * - 有精确闹钟权限：setExactAndAllowWhileIdle（Doze 下每 9 分钟窗口允许 1 次，30 分钟间隔完全够用）
 * - 无权限 / SecurityException：降级为 setWindow，保证「会响但不精确」，UI 侧提示用户授权
 */
class AlarmScheduler(private val context: Context) {

    private val manager: AlarmManager?
        get() = context.getSystemService(Context.ALARM_SERVICE) as? AlarmManager

    fun canScheduleExact(): Boolean {
        val am = manager ?: return false
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            am.canScheduleExactAlarms()
        } else {
            true
        }
    }

    fun schedule(taskId: String, at: LocalDateTime, snoozed: Boolean = false) {
        val am = manager ?: return
        val pi = pendingIntent(taskId, snoozed)
        val millis = at.atZone(ZoneId.systemDefault()).toInstant().toEpochMilli()
        try {
            if (canScheduleExact()) {
                am.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, millis, pi)
            } else {
                am.setWindow(AlarmManager.RTC_WAKEUP, millis, FLEX_MS, pi)
            }
        } catch (e: SecurityException) {
            am.setWindow(AlarmManager.RTC_WAKEUP, millis, FLEX_MS, pi)
        }
    }

    fun cancel(taskId: String) {
        val am = manager ?: return
        am.cancel(pendingIntent(taskId, false))
        am.cancel(pendingIntent(taskId, true))
    }

    private fun pendingIntent(taskId: String, snoozed: Boolean): PendingIntent {
        val intent = Intent(context, AlarmReceiver::class.java)
            .setAction(ACTION_FIRE)
            .putExtra(EXTRA_TASK, taskId)
            .putExtra(EXTRA_SNOOZED, snoozed)
        val code = (taskId.hashCode() and 0x3fffff) + if (snoozed) 1 else 0
        return PendingIntent.getBroadcast(
            context,
            code,
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
    }

    companion object {
        const val ACTION_FIRE = "dev.reminder.action.FIRE"
        const val EXTRA_TASK = "task_id"
        const val EXTRA_SNOOZED = "snoozed"
        private const val FLEX_MS = 10L * 60 * 1000
    }
}

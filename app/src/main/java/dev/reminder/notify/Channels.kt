package dev.reminder.notify

import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Context
import android.os.Build

/** 通知渠道：高优先（响铃+震动）与静默（免打扰时段/低优先任务） */
object Channels {
    const val HIGH = "reminder_high"
    const val QUIET = "reminder_quiet"
    const val GROUP = "dev.reminder.GROUP"

    fun ensure(context: Context) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val manager = context.getSystemService(NotificationManager::class.java) ?: return

            val high = NotificationChannel(
                HIGH,
                "提醒",
                NotificationManager.IMPORTANCE_HIGH
            ).apply {
                description = "按时响铃提醒"
                enableLights(true)
                enableVibration(true)
                vibrationPattern = longArrayOf(0, 300, 200, 300)
                setBypassDnd(false)
                lockscreenVisibility = android.app.Notification.VISIBILITY_PUBLIC
            }

            val quiet = NotificationChannel(
                QUIET,
                "静默提醒",
                NotificationManager.IMPORTANCE_LOW
            ).apply {
                description = "免打扰时段内使用，不响铃不震动"
                enableVibration(false)
                setBypassDnd(false)
                lockscreenVisibility = android.app.Notification.VISIBILITY_PUBLIC
            }

            manager.createNotificationChannel(high)
            manager.createNotificationChannel(quiet)
        }
    }
}

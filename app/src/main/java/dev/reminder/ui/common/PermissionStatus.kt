package dev.reminder.ui.common

import android.content.Context
import android.os.PowerManager
import androidx.core.app.NotificationManagerCompat
import dev.reminder.schedule.AlarmScheduler

data class PermissionStatus(
    val notifications: Boolean,
    val exactAlarm: Boolean,
    val batteryIgnored: Boolean
) {
    val healthy: Boolean
        get() = notifications && exactAlarm

    companion object {
        fun snapshot(context: Context): PermissionStatus {
            val notifications = try {
                NotificationManagerCompat.from(context).areNotificationsEnabled()
            } catch (e: Exception) {
                false
            }
            val exact = AlarmScheduler(context).canScheduleExact()
            val ignored = try {
                val pm = context.getSystemService(Context.POWER_SERVICE) as PowerManager
                pm.isIgnoringBatteryOptimizations(context.packageName)
            } catch (e: Exception) {
                false
            }
            return PermissionStatus(notifications, exact, ignored)
        }
    }
}

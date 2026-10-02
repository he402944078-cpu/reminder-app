package dev.reminder.ui.common

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.provider.Settings
import androidx.core.app.ActivityCompat
import androidx.core.app.NotificationManagerCompat
import android.Manifest

/**
 * 权限跳转集中处：所有 intent 都 try/catch，厂商 ROM 缺失入口时退回「应用详情页」。
 */
object PermissionActions {

    private fun packageUri(context: Context) = Uri.parse("package:${context.packageName}")

    private fun start(context: Context, intent: Intent) {
        try {
            context.startActivity(intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
        } catch (e: Exception) {
            openAppDetails(context)
        }
    }

    fun openAppDetails(context: Context) {
        try {
            context.startActivity(
                Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, packageUri(context))
                    .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            )
        } catch (e: Exception) {
            // 无入口
        }
    }

    fun requestNotifications(context: Context) {
        val activity = context as? android.app.Activity
        if (activity != null && Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            try {
                ActivityCompat.requestPermissions(
                    activity,
                    arrayOf(Manifest.permission.POST_NOTIFICATIONS),
                    REQUEST_NOTIFICATIONS_CODE
                )
                return
            } catch (e: Exception) {
                // 落到系统通知设置
            }
        }
        try {
            start(
                context,
                Intent(Settings.ACTION_APP_NOTIFICATION_SETTINGS)
                    .putExtra(Settings.EXTRA_APP_PACKAGE, context.packageName)
            )
        } catch (e: Exception) {
            openAppDetails(context)
        }
    }

    fun requestExactAlarm(context: Context) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            start(context, Intent(Settings.ACTION_REQUEST_SCHEDULE_EXACT_ALARM, packageUri(context)))
        } else {
            openAppDetails(context)
        }
    }

    fun requestBatteryExemption(context: Context) {
        start(
            context,
            Intent(Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS, packageUri(context))
        )
    }

    fun openBatteryOptimizationList(context: Context) {
        start(context, Intent(Settings.ACTION_IGNORE_BATTERY_OPTIMIZATION_SETTINGS))
    }

    fun notificationsEnabled(context: Context): Boolean = try {
        NotificationManagerCompat.from(context).areNotificationsEnabled()
    } catch (e: Exception) {
        false
    }

    private const val REQUEST_NOTIFICATIONS_CODE = 101
}

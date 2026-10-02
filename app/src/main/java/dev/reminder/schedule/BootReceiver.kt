package dev.reminder.schedule

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import dev.reminder.AppGraph
import kotlinx.coroutines.launch

/**
 * 系统事件自愈入口：开机、改系统时间、改时区、改语言、应用升级后全量重建闹钟。
 * 这是「重启手机后闹钟自动恢复」这条验收项的实现位置。
 */
class BootReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent) {
        AppGraph.init(context)
        val action = intent.action ?: return
        if (!isRelevant(action)) return
        val pending = goAsync()
        AppGraph.ioScope.launch {
            try {
                AppGraph.coordinator.rescheduleAll()
            } catch (e: Exception) {
                // 忽略
            } finally {
                pending.finish()
            }
        }
    }

    private fun isRelevant(action: String): Boolean = when (action) {
        Intent.ACTION_BOOT_COMPLETED,
        "android.intent.action.QUICKBOOT_POWERON",
        "com.htc.intent.action.QUICKBOOT_POWERON",
        Intent.ACTION_TIME_CHANGED,
        Intent.ACTION_TIMEZONE_CHANGED,
        Intent.ACTION_LOCALE_CHANGED,
        Intent.ACTION_MY_PACKAGE_REPLACED,
        Intent.ACTION_USER_PRESENT -> true
        else -> false
    }

    companion object {
        val ACTIONS = listOf(
            Intent.ACTION_BOOT_COMPLETED,
            "android.intent.action.QUICKBOOT_POWERON",
            "com.htc.intent.action.QUICKBOOT_POWERON",
            Intent.ACTION_TIME_CHANGED,
            Intent.ACTION_TIMEZONE_CHANGED,
            Intent.ACTION_LOCALE_CHANGED,
            Intent.ACTION_MY_PACKAGE_REPLACED
        )
    }
}

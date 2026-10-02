package dev.reminder.schedule

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import dev.reminder.AppGraph
import kotlinx.coroutines.launch

/** 闹钟到点接收器：goAsync + IO 协程，避免主线程阻塞 */
class AlarmReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        AppGraph.init(context)
        val taskId = intent.getStringExtra(AlarmScheduler.EXTRA_TASK) ?: return
        val snoozed = intent.getBooleanExtra(AlarmScheduler.EXTRA_SNOOZED, false)
        val pending = goAsync()
        AppGraph.ioScope.launch {
            try {
                AppGraph.coordinator.handleFired(taskId, snoozed)
            } catch (e: Exception) {
                // 单次失败不影响后续调度
            } finally {
                pending.finish()
            }
        }
    }
}

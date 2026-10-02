package dev.reminder.schedule

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import dev.reminder.AppGraph
import dev.reminder.notify.Notifier
import kotlinx.coroutines.launch

/** 通知上的「完成 / 贪睡 / 跳过」动作 */
class NotificationActionReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent) {
        AppGraph.init(context)
        val taskId = intent.getStringExtra(EXTRA_TASK) ?: return
        val action = intent.action ?: return
        Notifier.cancelTask(context, taskId)

        val pending = goAsync()
        AppGraph.ioScope.launch {
            try {
                when (action) {
                    ACTION_DONE -> AppGraph.coordinator.complete(taskId)
                    ACTION_SNOOZE -> {
                        val minutes = AppGraph.repo.get(taskId)?.snoozeMinutes
                            ?: AppGraph.settings.state.value.defaultSnoozeMinutes
                        AppGraph.coordinator.snooze(taskId, minutes)
                    }
                    ACTION_SKIP -> AppGraph.coordinator.skip(taskId)
                }
            } catch (e: Exception) {
                // 忽略
            } finally {
                pending.finish()
            }
        }
    }

    companion object {
        const val ACTION_DONE = "dev.reminder.action.DONE"
        const val ACTION_SNOOZE = "dev.reminder.action.SNOOZE"
        const val ACTION_SKIP = "dev.reminder.action.SKIP"
        const val EXTRA_TASK = "task_id"
    }
}

package dev.reminder.notify

import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import dev.reminder.AppGraph
import dev.reminder.MainActivity
import dev.reminder.R
import dev.reminder.data.ReminderTask
import dev.reminder.domain.MergePolicy
import dev.reminder.domain.Scheduler
import java.time.LocalDateTime

/** 通知构建与发送 */
object Notifier {

    fun show(
        context: Context,
        task: ReminderTask,
        missed: Int,
        snoozed: Boolean,
        next: LocalDateTime?
    ) {
        Channels.ensure(context)
        val now = LocalDateTime.now()
        val quiet = task.quiet || AppGraph.settings.inQuietWindow(now)
        val channelId = if (quiet) Channels.QUIET else Channels.HIGH

        val title = buildString {
            append(task.emoji)
            append(' ')
            append(task.name)
            if (snoozed) append(" · 贪睡提醒")
        }

        val text = buildString {
            append(task.prompt)
            val suffix = MergePolicy.missedSuffix(missed)
            if (suffix.isNotEmpty()) {
                append(" · ")
                append(suffix)
            }
            if (next != null) {
                append(" · 下次 ")
                append(Scheduler.formatTime(next.toLocalTime()))
            }
        }

        val builder = NotificationCompat.Builder(context, channelId)
            .setSmallIcon(R.drawable.ic_stat_reminder)
            .setContentTitle(title)
            .setContentText(text)
            .setStyle(
                NotificationCompat.BigTextStyle()
                    .setBigContentTitle(title)
                    .setBigText(text)
            )
            .setPriority(
                if (quiet) NotificationCompat.PRIORITY_LOW else NotificationCompat.PRIORITY_HIGH
            )
            .setCategory(NotificationCompat.CATEGORY_REMINDER)
            .setAutoCancel(true)
            .setOnlyAlertOnce(false)
            .setGroup(Channels.GROUP)
            .setContentIntent(openAppIntent(context))
            .addAction(
                R.drawable.ic_stat_reminder,
                "完成",
                actionIntent(context, task.id, NotificationActionReceiver.ACTION_DONE)
            )
            .addAction(
                R.drawable.ic_stat_reminder,
                "贪睡${task.snoozeMinutes}分",
                actionIntent(context, task.id, NotificationActionReceiver.ACTION_SNOOZE)
            )
            .addAction(
                R.drawable.ic_stat_reminder,
                "跳过",
                actionIntent(context, task.id, NotificationActionReceiver.ACTION_SKIP)
            )

        try {
            NotificationManagerCompat.from(context).notify(task.id.hashCode(), builder.build())
        } catch (e: SecurityException) {
            // 缺少 POST_NOTIFICATIONS 权限，UI 侧已提示
        }
    }

    fun cancelTask(context: Context, taskId: String) {
        try {
            NotificationManagerCompat.from(context).cancel(taskId.hashCode())
        } catch (e: Exception) {
            // 忽略
        }
    }

    private fun openAppIntent(context: Context): PendingIntent {
        val intent = Intent(context, MainActivity::class.java)
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP)
        return PendingIntent.getActivity(
            context,
            900_000,
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
    }

    private fun actionIntent(context: Context, taskId: String, action: String): PendingIntent {
        val intent = Intent(context, NotificationActionReceiver::class.java)
            .setAction(action)
            .putExtra(NotificationActionReceiver.EXTRA_TASK, taskId)
        val offset = when (action) {
            NotificationActionReceiver.ACTION_DONE -> 1_000
            NotificationActionReceiver.ACTION_SNOOZE -> 2_000
            else -> 3_000
        }
        val code = (taskId.hashCode() and 0x3fffff) + offset
        return PendingIntent.getBroadcast(
            context,
            code,
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
    }
}

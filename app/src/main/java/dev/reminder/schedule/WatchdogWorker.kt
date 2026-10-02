package dev.reminder.schedule

import android.content.Context
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import dev.reminder.AppGraph

/**
 * 自愈看门狗：每 12 小时校验一次闹钟注册状态。
 * 用于厂商 ROM 杀进程 / 系统清理 AlarmManager 后的兜底恢复。
 */
class WatchdogWorker(
    context: Context,
    params: WorkerParameters
) : CoroutineWorker(context, params) {

    override suspend fun doWork(): Result {
        AppGraph.init(applicationContext)
        return try {
            AppGraph.coordinator.verifyAndRepair()
            Result.success()
        } catch (e: Exception) {
            Result.retry()
        }
    }

    companion object {
        const val NAME = "reminder-watchdog"
    }
}

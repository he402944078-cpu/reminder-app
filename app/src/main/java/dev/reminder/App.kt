package dev.reminder

import android.app.Application
import android.content.Context
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import dev.reminder.data.SettingsStore
import dev.reminder.data.TaskRepository
import dev.reminder.schedule.ScheduleCoordinator
import dev.reminder.schedule.WatchdogWorker
import java.io.File
import java.util.concurrent.TimeUnit
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch

class App : Application() {
    override fun onCreate() {
        super.onCreate()
        AppGraph.init(this)
        // 冷启动即重建全部闹钟：覆盖开机、升级、被杀后重启等场景
        AppGraph.ioScope.launch { AppGraph.coordinator.rescheduleAll() }
        WorkManager.getInstance(this).enqueueUniquePeriodicWork(
            WatchdogWorker.NAME,
            ExistingPeriodicWorkPolicy.KEEP,
            PeriodicWorkRequestBuilder<WatchdogWorker>(12, TimeUnit.HOURS)
                .setInitialDelay(5, TimeUnit.MINUTES)
                .build()
        )
    }
}

/** 极简依赖图（无 Hilt，降低首构建风险） */
object AppGraph {
    private var appContext: Context? = null

    val app: Context
        get() = appContext ?: error("AppGraph 未初始化")

    val ioScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    val repo: TaskRepository by lazy {
        TaskRepository(
            taskFile = File(app.filesDir, "tasks.json"),
            logFile = File(app.filesDir, "logs.json")
        )
    }

    val settings: SettingsStore by lazy { SettingsStore(File(app.filesDir, "settings.json")) }

    val coordinator: ScheduleCoordinator by lazy { ScheduleCoordinator(app) }

    fun init(context: Context) {
        appContext = context.applicationContext
    }

    /** UI 侧的「写盘 + 重排」统一丢到 IO 线程执行 */
    fun io(block: () -> Unit) {
        ioScope.launch { block() }
    }
}

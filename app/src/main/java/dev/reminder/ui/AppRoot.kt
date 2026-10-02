package dev.reminder.ui

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.DateRange
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import dev.reminder.AppGraph
import dev.reminder.data.ReminderTask
import dev.reminder.data.ScheduleType
import dev.reminder.ui.stats.StatsScreen
import dev.reminder.ui.task.TaskEditScreen
import dev.reminder.ui.task.TemplatesScreen
import dev.reminder.ui.settings.SettingsScreen
import dev.reminder.ui.settings.PermissionScreen
import dev.reminder.ui.timeline.TimelineScreen
import kotlinx.coroutines.delay

sealed interface Screen {
    data object Timeline : Screen
    data class Edit(val taskId: String?) : Screen
    data object Templates : Screen
    data object Stats : Screen
    data object Settings : Screen
    data object Permission : Screen
}

private fun titleOf(screen: Screen): String = when (screen) {
    Screen.Timeline -> "提醒时间线"
    is Screen.Edit -> if (screen.taskId == null) "新建提醒" else "编辑提醒"
    Screen.Templates -> "快捷模板"
    Screen.Stats -> "统计"
    Screen.Settings -> "设置"
    Screen.Permission -> "权限与自启"
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AppRoot(openTaskId: String?, onOpenConsumed: () -> Unit) {
    val stack = remember { mutableStateListOf<Screen>(Screen.Timeline) }
    var nowMs by remember { mutableStateOf(System.currentTimeMillis()) }

    // 1 秒节拍：驱动倒计时与权限状态刷新
    LaunchedEffect(Unit) {
        while (true) {
            nowMs = System.currentTimeMillis()
            delay(1_000L)
        }
    }

    LaunchedEffect(openTaskId) {
        if (openTaskId != null) {
            stack.clear()
            stack.add(Screen.Timeline)
            stack.add(Screen.Edit(openTaskId))
            onOpenConsumed()
        }
    }

    val current = stack.last()
    val canGoBack = stack.size > 1
    BackHandler(enabled = canGoBack) {
        if (stack.size > 1) stack.removeAt(stack.lastIndex)
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(titleOf(current), style = MaterialTheme.typography.titleMedium) },
                navigationIcon = {
                    if (canGoBack) {
                        IconButton(onClick = { if (stack.size > 1) stack.removeAt(stack.lastIndex) }) {
                            Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "返回")
                        }
                    }
                },
                actions = {
                    if (current is Screen.Timeline) {
                        IconButton(onClick = { stack.add(Screen.Stats) }) {
                            Icon(Icons.Default.DateRange, contentDescription = "统计")
                        }
                        IconButton(onClick = { stack.add(Screen.Settings) }) {
                            Icon(Icons.Default.Settings, contentDescription = "设置")
                        }
                    }
                }
            )
        },
        floatingActionButton = {
            if (current is Screen.Timeline) {
                FloatingActionButton(onClick = { stack.add(Screen.Edit(null)) }) {
                    Icon(Icons.Default.Add, contentDescription = "新建提醒")
                }
            }
        }
    ) { padding ->
        Box(modifier = Modifier.padding(padding)) {
            when (val screen = current) {
                Screen.Timeline -> TimelineScreen(
                    nowMs = nowMs,
                    onEdit = { stack.add(Screen.Edit(it)) },
                    onTemplates = { stack.add(Screen.Templates) },
                    onPermission = { stack.add(Screen.Permission) }
                )

                is Screen.Edit -> TaskEditScreen(
                    taskId = screen.taskId,
                    onDone = { if (stack.size > 1) stack.removeAt(stack.lastIndex) }
                )

                Screen.Templates -> TemplatesScreen(
                    onCreate = { template ->
                        val task = template.copy(id = AppGraph.repo.newId())
                        AppGraph.io { AppGraph.coordinator.reschedule(task) }
                        stack.add(Screen.Edit(task.id))
                    }
                )

                Screen.Stats -> StatsScreen(nowMs = nowMs)

                Screen.Settings -> SettingsScreen(
                    onPermission = { stack.add(Screen.Permission) }
                )

                Screen.Permission -> PermissionScreen(nowMs = nowMs)
            }
        }
    }
}

/** 模板落库时的默认节拍保护：不低于 15 分钟 */
fun sanitizeInterval(minutes: Int): Int = if (minutes < 15) 15.coerceAtLeast(minutes) else minutes

/** 供模板页构造任务 */
fun templateTask(
    name: String,
    emoji: String,
    prompt: String,
    intervalMinutes: Int,
    windowStartMinute: Int = 9 * 60,
    windowEndMinute: Int = 22 * 60,
    times: List<String> = emptyList()
): ReminderTask = ReminderTask(
    id = AppGraph.repo.newId(),
    name = name,
    emoji = emoji,
    prompt = prompt,
    scheduleType = if (times.isEmpty()) ScheduleType.INTERVAL else ScheduleType.DAILY_TIMES,
    intervalMinutes = intervalMinutes.coerceIn(5, 1440),
    dailyTimes = times,
    windowStartMinute = windowStartMinute,
    windowEndMinute = windowEndMinute,
    daysOfWeek = listOf(1, 2, 3, 4, 5, 6, 7),
    snoozeMinutes = AppGraph.settings.state.value.defaultSnoozeMinutes,
    quiet = false,
    enabled = true,
    nextFireEpochMs = null,
    lastFiredEpochMs = null,
    createdAtEpochMs = System.currentTimeMillis()
)

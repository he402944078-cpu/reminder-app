package dev.reminder.ui.timeline

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsStateWithLifecycle
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import dev.reminder.AppGraph
import dev.reminder.data.ReminderTask
import dev.reminder.data.ScheduleType
import dev.reminder.domain.Scheduler
import dev.reminder.ui.common.CountdownText
import dev.reminder.ui.common.EmptyHint
import dev.reminder.ui.common.InfoBanner
import dev.reminder.ui.common.PermissionActions
import dev.reminder.ui.common.PermissionStatus
import java.time.LocalDateTime

@Composable
fun TimelineScreen(
    nowMs: Long,
    onEdit: (String) -> Unit,
    onTemplates: () -> Unit,
    onPermission: () -> Unit
) {
    val context = LocalContext.current
    val tasks by AppGraph.repo.tasks.collectAsStateWithLifecycle()
    val settings by AppGraph.settings.state.collectAsStateWithLifecycle()
    val permission = remember(nowMs / 30_000L) { PermissionStatus.snapshot(context) }

    val now = remember(nowMs) { LocalDateTime.ofInstant(java.time.Instant.ofEpochMilli(nowMs), java.time.ZoneId.systemDefault()) }
    val inQuiet = settings.quietEnabled && settings.quietStartMinute < settings.quietEndMinute &&
        (now.hour * 60 + now.minute) in settings.quietStartMinute until settings.quietEndMinute

    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = androidx.compose.foundation.layout.Padding(12.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        if (!permission.notifications) {
            item {
                InfoBanner(
                    text = "通知权限未开启，提醒不会弹出。",
                    actionLabel = "去开启",
                    onAction = { PermissionActions.requestNotifications(context) }
                )
            }
        }
        if (!permission.exactAlarm) {
            item {
                InfoBanner(
                    text = "未授予「精确闹钟」，提醒可能被系统延后。",
                    actionLabel = "去授权",
                    onAction = { PermissionActions.requestExactAlarm(context) }
                )
            }
        }
        if (permission.notifications && permission.exactAlarm && !permission.batteryIgnored) {
            item {
                InfoBanner(
                    text = "建议关闭电池优化，避免后台被系统冻结。",
                    actionLabel = "去设置",
                    onAction = { PermissionActions.requestBatteryExemption(context) }
                )
            }
        }
        if (!settings.oemGuideShown) {
            item {
                InfoBanner(
                    text = "小米/华为/OPPO/vivo 需允许本应用「自启动」，否则后台清理后提醒会失效。",
                    actionLabel = "查看指引",
                    onAction = { AppGraph.io { AppGraph.settings.update { it.copy(oemGuideShown = true) } } }
                )
            }
        }
        if (inQuiet) {
            item {
                InfoBanner(
                    text = "当前处于免打扰时段 ${Scheduler.formatMinuteOfDay(settings.quietStartMinute)}-${Scheduler.formatMinuteOfDay(settings.quietEndMinute)}，提醒静默送达。"
                )
            }
        }

        if (tasks.isEmpty()) {
            item {
                EmptyHint("还没有提醒任务。点右下角 + 新建，或从快捷模板一键添加。")
            }
        } else {
            items(tasks, key = { it.id }) { task ->
                TaskCard(
                    task = task,
                    now = now,
                    nowMs = nowMs,
                    onEdit = { onEdit(task.id) },
                    onToggle = { enabled -> AppGraph.io { AppGraph.coordinator.setEnabled(task.id, enabled) } }
                )
            }
        }

        item {
            OutlinedButton(
                onClick = onTemplates,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 4.dp)
            ) {
                Text("从快捷模板新建（喝水 / 起身 / 护眼 / 用药…）")
            }
        }

        item {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 4.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    "权限与自启指引",
                    style = MaterialTheme.typography.bodyMedium,
                    modifier = Modifier.weight(1f)
                )
                OutlinedButton(onClick = onPermission) { Text("检查") }
            }
        }

        item { Spacer(Modifier.height(72.dp)) }
    }
}

@Composable
private fun TaskCard(
    task: ReminderTask,
    now: LocalDateTime,
    nowMs: Long,
    onEdit: () -> Unit,
    onToggle: (Boolean) -> Unit
) {
    val today = now.toLocalDate()
    val slotsToday = if (task.enabled && task.daysOfWeek.contains(today.dayOfWeek.value)) {
        Scheduler.slotsOf(task, today)
    } else {
        emptyList()
    }
    val remaining = slotsToday.count { !it.isBefore(now.toLocalTime()) }

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onEdit),
        shape = RoundedCornerShape(18.dp),
        colors = CardDefaults.cardColors(
            containerColor = if (task.enabled) {
                MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.55f)
            } else {
                MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.25f)
            }
        )
    ) {
        Row(
            modifier = Modifier.padding(14.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(task.emoji, style = MaterialTheme.typography.headlineSmall)
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f)) {
                Text(
                    task.name,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold
                )
                Text(
                    task.prompt,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Spacer(Modifier.height(6.dp))
                Text(
                    scheduleSummary(task),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Spacer(Modifier.height(4.dp))
                CountdownText(targetMs = task.nextFireEpochMs, nowMs = nowMs)
                if (task.enabled) {
                    Text(
                        "今日剩余 $remaining 次",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
            Switch(checked = task.enabled, onCheckedChange = onToggle)
        }
    }
}

fun scheduleSummary(task: ReminderTask): String {
    val base = when (task.scheduleType) {
        ScheduleType.INTERVAL ->
            "每 ${task.intervalMinutes} 分钟 · ${Scheduler.formatMinuteOfDay(task.windowStartMinute)}-${Scheduler.formatMinuteOfDay(task.windowEndMinute)}"

        ScheduleType.DAILY_TIMES ->
            "定点 " + task.dailyTimes.sorted().joinToString(" ")
    }
    return "$base · ${daysLabel(task.daysOfWeek)}"
}

private val WeekNames = listOf("周一", "周二", "周三", "周四", "周五", "周六", "周日")

fun daysLabel(days: List<Int>): String = when {
    days.size >= 7 -> "每天"
    days == listOf(1, 2, 3, 4, 5) -> "工作日"
    days == listOf(6, 7) -> "周末"
    days.isEmpty() -> "未选星期"
    else -> days.sorted().joinToString("、") { WeekNames[it - 1] }
}

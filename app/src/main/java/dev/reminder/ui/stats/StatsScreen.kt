package dev.reminder.ui.stats

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsStateWithLifecycle
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import dev.reminder.AppGraph
import dev.reminder.data.ReminderLog
import dev.reminder.domain.Scheduler
import dev.reminder.ui.common.EmptyHint
import java.time.Instant
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.ZoneId
import java.time.format.DateTimeFormatter

private val TimeFormat = DateTimeFormatter.ofPattern("MM-dd HH:mm")

@Composable
fun StatsScreen(nowMs: Long) {
    val logs by AppGraph.repo.logs.collectAsStateWithLifecycle()
    val tasks by AppGraph.repo.tasks.collectAsStateWithLifecycle()

    val names = remember(tasks) { tasks.associate { it.id to "${it.emoji} ${it.name}" } }
    val todayStart = remember(nowMs) { Scheduler.toEpochMs(LocalDate.now().atStartOfDay()) }

    val today = remember(logs, todayStart) { logs.filter { it.atEpochMs >= todayStart } }
    val fired = today.count { it.action == ReminderLog.FIRED }
    val done = today.count { it.action == ReminderLog.DONE }
    val snoozed = today.count { it.action == ReminderLog.SNOOZED }
    val skipped = today.count { it.action == ReminderLog.SKIPPED }
    val handled = done + snoozed + skipped
    val ratio = if (handled == 0) 0f else done.toFloat() / handled.toFloat()

    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = androidx.compose.foundation.layout.Padding(12.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        item {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                StatCell("触发", "$fired", Modifier.weight(1f))
                StatCell("完成", "$done", Modifier.weight(1f))
                StatCell("贪睡", "$snoozed", Modifier.weight(1f))
                StatCell("跳过", "$skipped", Modifier.weight(1f))
            }
        }

        item {
            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(16.dp),
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.45f)
                )
            ) {
                Column(Modifier.padding(14.dp)) {
                    Text("今日完成率", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold)
                    Spacer(Modifier.height(8.dp))
                    LinearProgressIndicator(
                        progress = ratio,
                        modifier = Modifier.fillMaxWidth()
                    )
                    Spacer(Modifier.height(6.dp))
                    Text(
                        text = if (handled == 0) "今天还没有处理过提醒" else "完成 ${done} / 处理 ${handled}（${(ratio * 100).toInt()}%）",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        }

        item {
            Text(
                "最近记录",
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.SemiBold
            )
        }

        val recent = logs.takeLast(40).asReversed()
        if (recent.isEmpty()) {
            item { EmptyHint("还没有提醒记录。任务触发后这里会记录触发、完成、贪睡、跳过。") }
        } else {
            items(recent) { log ->
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 2.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        actionLabel(log.action),
                        style = MaterialTheme.typography.bodyMedium,
                        modifier = Modifier.padding(end = 8.dp)
                    )
                    Text(
                        names[log.taskId] ?: "已删除任务",
                        style = MaterialTheme.typography.bodyMedium,
                        modifier = Modifier.weight(1f)
                    )
                    Text(
                        formatMs(log.atEpochMs),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        textAlign = TextAlign.End
                    )
                }
            }
        }

        item { Spacer(Modifier.height(24.dp)) }
    }
}

@Composable
private fun StatCell(label: String, value: String, modifier: Modifier = Modifier) {
    Card(
        modifier = modifier,
        shape = RoundedCornerShape(14.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.45f)
        )
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(vertical = 12.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text(value, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.SemiBold)
            Text(
                label,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

private fun actionLabel(action: String): String = when (action) {
    ReminderLog.FIRED -> "🔔 触发"
    ReminderLog.DONE -> "✅ 完成"
    ReminderLog.SNOOZED -> "😴 贪睡"
    ReminderLog.SKIPPED -> "⏭ 跳过"
    else -> action
}

private fun formatMs(ms: Long): String {
    val dt = LocalDateTime.ofInstant(Instant.ofEpochMilli(ms), ZoneId.systemDefault())
    return dt.format(TimeFormat)
}

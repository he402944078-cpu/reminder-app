package dev.reminder.ui.task

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
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import dev.reminder.data.ReminderTask
import dev.reminder.ui.templateTask
import dev.reminder.ui.timeline.scheduleSummary

private data class Template(
    val name: String,
    val emoji: String,
    val prompt: String,
    val intervalMinutes: Int = 30,
    val windowStartMinute: Int = 9 * 60,
    val windowEndMinute: Int = 22 * 60,
    val times: List<String> = emptyList()
)

private val Templates = listOf(
    Template("喝水", "💧", "喝口水吧", intervalMinutes = 30),
    Template("起身活动", "🚶", "站起来活动一下，走两步", intervalMinutes = 45, windowEndMinute = 21 * 60),
    Template("护眼 20-20-20", "🪟", "远眺 20 秒，放松眼睛", intervalMinutes = 20),
    Template("站立办公", "🏋️", "换成站立姿势工作一会儿", intervalMinutes = 60, windowStartMinute = 9 * 60, windowEndMinute = 19 * 60),
    Template("少刷手机", "📱", "放下手机，做点别的事", intervalMinutes = 90, windowStartMinute = 10 * 60, windowEndMinute = 23 * 60),
    Template("开窗通风", "🌬", "开窗通风几分钟", intervalMinutes = 120),
    Template("三餐定点", "🍎", "该吃饭了", times = listOf("07:30", "12:00", "18:30")),
    Template("喝水（工作日）", "💧", "喝口水吧", intervalMinutes = 30, windowStartMinute = 9 * 60, windowEndMinute = 18 * 60),
    Template("睡前洗漱", "🛏", "洗漱一下，准备睡觉", times = listOf("22:30")),
    Template("用药提醒", "💊", "按医嘱服药", times = listOf("08:00", "20:00"))
)

@Composable
fun TemplatesScreen(onCreate: (ReminderTask) -> Unit) {
    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = androidx.compose.foundation.layout.Padding(12.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        item {
            Text(
                "点一下即创建，创建后可继续微调。",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(bottom = 4.dp)
            )
        }
        Templates.forEach { template ->
            item {
                Card(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable { onCreate(build(template)) },
                    shape = RoundedCornerShape(16.dp),
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.45f)
                    )
                ) {
                    Row(
                        modifier = Modifier.padding(14.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(template.emoji, style = MaterialTheme.typography.headlineSmall)
                        Spacer(Modifier.width(12.dp))
                        Column(Modifier.weight(1f)) {
                            Text(
                                template.name,
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.SemiBold
                            )
                            Text(
                                template.prompt,
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                            Spacer(Modifier.height(4.dp))
                            Text(
                                scheduleSummary(build(template)),
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                }
            }
        }
        item { Spacer(Modifier.height(24.dp)) }
    }
}

private fun build(template: Template): ReminderTask = templateTask(
    name = template.name,
    emoji = template.emoji,
    prompt = template.prompt,
    intervalMinutes = template.intervalMinutes,
    windowStartMinute = template.windowStartMinute,
    windowEndMinute = template.windowEndMinute,
    times = template.times
)

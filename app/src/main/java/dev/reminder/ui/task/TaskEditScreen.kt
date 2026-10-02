package dev.reminder.ui.task

import android.app.TimePickerDialog
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material3.Button
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Slider
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import dev.reminder.AppGraph
import dev.reminder.data.ReminderTask
import dev.reminder.data.ScheduleType
import dev.reminder.domain.Scheduler
import dev.reminder.ui.common.DayPickerRow
import dev.reminder.ui.common.EmojiGrid
import dev.reminder.ui.common.LabeledTextField
import dev.reminder.ui.common.KeyValueRow
import dev.reminder.ui.common.SectionCard
import dev.reminder.ui.common.SwitchRow
import dev.reminder.ui.common.TimeField
import java.time.LocalDateTime

private const val MIN_INTERVAL = 15
private const val MAX_INTERVAL = 180
private const val RECOMMENDED_MIN = 15

@Composable
fun TaskEditScreen(taskId: String?, onDone: () -> Unit) {
    val context = LocalContext.current
    val repo = AppGraph.repo
    val existing = remember(taskId) { taskId?.let { id -> repo.get(id) } }
    var draft by remember {
        mutableStateOf(
            existing ?: ReminderTask(
                id = repo.newId(),
                name = "",
                emoji = "💧",
                prompt = "喝口水吧",
                scheduleType = ScheduleType.INTERVAL,
                intervalMinutes = 30,
                dailyTimes = emptyList(),
                windowStartMinute = 9 * 60,
                windowEndMinute = 22 * 60,
                daysOfWeek = listOf(1, 2, 3, 4, 5, 6, 7),
                snoozeMinutes = AppGraph.settings.state.value.defaultSnoozeMinutes,
                quiet = false,
                enabled = true,
                nextFireEpochMs = null,
                lastFiredEpochMs = null,
                createdAtEpochMs = System.currentTimeMillis()
            )
        )
    }

    val windowInvalid = draft.scheduleType == ScheduleType.INTERVAL &&
        draft.windowStartMinute >= draft.windowEndMinute
    val daysEmpty = draft.daysOfWeek.isEmpty()
    val timesEmpty = draft.scheduleType == ScheduleType.DAILY_TIMES && draft.dailyTimes.isEmpty()
    val canSave = !windowInvalid && !daysEmpty && !timesEmpty && draft.name.isNotBlank()

    val preview = remember(draft) {
        Scheduler.nextFire(draft.copy(enabled = true), LocalDateTime.now())
    }

    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(12.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        item {
            SectionCard(title = "基本信息") {
                LabeledTextField(
                    label = "名称",
                    value = draft.name,
                    onValueChange = { draft = draft.copy(name = it) },
                    placeholder = "喝水 / 起身 / 护眼"
                )
                LabeledTextField(
                    label = "提示语",
                    value = draft.prompt,
                    onValueChange = { draft = draft.copy(prompt = it) },
                    placeholder = "喝口水吧",
                    singleLine = false
                )
                Text("图标", style = MaterialTheme.typography.bodyMedium)
                Spacer(Modifier.height(4.dp))
                EmojiGrid(selected = draft.emoji, onSelect = { draft = draft.copy(emoji = it) })
            }
        }

        item {
            SectionCard(title = "提醒方式") {
                Row {
                    FilterChip(
                        selected = draft.scheduleType == ScheduleType.INTERVAL,
                        onClick = { draft = draft.copy(scheduleType = ScheduleType.INTERVAL) },
                        label = { Text("按间隔") },
                        modifier = Modifier.padding(end = 6.dp)
                    )
                    FilterChip(
                        selected = draft.scheduleType == ScheduleType.DAILY_TIMES,
                        onClick = { draft = draft.copy(scheduleType = ScheduleType.DAILY_TIMES) },
                        label = { Text("定点时刻") }
                    )
                }
                Spacer(Modifier.height(8.dp))

                if (draft.scheduleType == ScheduleType.INTERVAL) {
                    Text("间隔 ${draft.intervalMinutes} 分钟")
                    Slider(
                        value = draft.intervalMinutes.toFloat(),
                        onValueChange = { draft = draft.copy(intervalMinutes = it.toInt()) },
                        valueRange = MIN_INTERVAL.toFloat()..MAX_INTERVAL.toFloat(),
                        steps = (MAX_INTERVAL - MIN_INTERVAL) / 5 - 1
                    )
                    if (draft.intervalMinutes < RECOMMENDED_MIN) {
                        Text(
                            "低于 ${RECOMMENDED_MIN} 分钟系统可能合并触发，建议不低于 ${RECOMMENDED_MIN} 分钟。",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.error
                        )
                    }
                    Spacer(Modifier.height(6.dp))
                    TimeField(
                        label = "时间窗开始",
                        minuteOfDay = draft.windowStartMinute,
                        onChange = { draft = draft.copy(windowStartMinute = it) }
                    )
                    TimeField(
                        label = "时间窗结束",
                        minuteOfDay = draft.windowEndMinute,
                        onChange = { draft = draft.copy(windowEndMinute = it) }
                    )
                    Text(
                        "提醒只在时间窗内触发，每天从窗口起点重新对齐；跨窗不提醒。",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    if (windowInvalid) {
                        Text(
                            "开始时间必须早于结束时间（不支持跨零点窗口）。",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.error
                        )
                    }
                } else {
                    DailyTimesEditor(
                        times = draft.dailyTimes,
                        onChange = { draft = draft.copy(dailyTimes = it) }
                    )
                    Text(
                        "每个时刻每天触发一次，适合固定作息。",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }

                Spacer(Modifier.height(8.dp))
                Text("重复星期", style = MaterialTheme.typography.bodyMedium)
                Spacer(Modifier.height(4.dp))
                DayPickerRow(
                    selected = draft.daysOfWeek,
                    onChange = { draft = draft.copy(daysOfWeek = it) }
                )
                if (daysEmpty) {
                    Text(
                        "至少选择一个星期。",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.error
                    )
                }
            }
        }

        item {
            SectionCard(title = "提醒行为") {
                Text("贪睡时长 ${draft.snoozeMinutes} 分钟")
                Slider(
                    value = draft.snoozeMinutes.toFloat(),
                    onValueChange = { draft = draft.copy(snoozeMinutes = it.toInt()) },
                    valueRange = 5f..60f,
                    steps = 10
                )
                SwitchRow(
                    title = "静默送达",
                    subtitle = "只发通知，不响铃不振动",
                    checked = draft.quiet,
                    onCheckedChange = { draft = draft.copy(quiet = it) }
                )
                SwitchRow(
                    title = "启用",
                    subtitle = "关闭后取消闹钟并撤回通知",
                    checked = draft.enabled,
                    onCheckedChange = { draft = draft.copy(enabled = it) }
                )
            }
        }

        item {
            SectionCard(title = "预览") {
                KeyValueRow("下一次", preview?.let { "${it.toLocalDate()} ${Scheduler.formatTime(it.toLocalTime())}" } ?: "无（任务停用）")
                val slots = preview?.let { Scheduler.slotsOf(draft, it.toLocalDate()) } ?: emptyList()
                KeyValueRow("当天次数", "${slots.size}")
                KeyValueRow("贪睡", "${draft.snoozeMinutes} 分钟")
            }
        }

        item {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Button(
                    onClick = {
                        val toSave = draft.copy(name = draft.name.trim())
                        AppGraph.io { AppGraph.coordinator.reschedule(toSave) }
                        onDone()
                    },
                    enabled = canSave,
                    modifier = Modifier.weight(1f)
                ) { Text("保存") }
                if (existing != null) {
                    TextButton(
                        onClick = {
                            val id = existing.id
                            AppGraph.io {
                                AppGraph.coordinator.cancelAlarm(id)
                                AppGraph.repo.delete(id)
                            }
                            onDone()
                        },
                        modifier = Modifier.padding(start = 8.dp)
                    ) { Text("删除", color = MaterialTheme.colorScheme.error) }
                }
            }
        }

        item { Spacer(Modifier.height(24.dp)) }
    }
}

@Composable
private fun DailyTimesEditor(times: List<String>, onChange: (List<String>) -> Unit) {
    val context = LocalContext.current
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(6.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text("时刻", style = MaterialTheme.typography.bodyMedium, modifier = Modifier.weight(1f))
        OutlinedButton(onClick = {
            TimePickerDialog(
                context,
                { _, hour, minute ->
                    val label = "%02d:%02d".format(hour, minute)
                    if (!times.contains(label)) onChange((times + label).sorted())
                },
                9,
                0,
                true
            ).show()
        }) { Text("添加") }
    }
    Spacer(Modifier.height(4.dp))
    if (times.isEmpty()) {
        Text(
            "还没有时刻，点「添加」。",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.error
        )
    } else {
        Row(modifier = Modifier.fillMaxWidth()) {
            times.sorted().forEach { label ->
                FilterChip(
                    selected = true,
                    onClick = { onChange(times.filter { it != label }) },
                    label = { Text(label) },
                    modifier = Modifier.padding(end = 4.dp)
                )
            }
        }
        Text(
            "点击时刻可删除。",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}

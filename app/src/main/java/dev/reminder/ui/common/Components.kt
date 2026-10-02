package dev.reminder.ui.common

import android.app.TimePickerDialog
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import dev.reminder.domain.Scheduler

/** 通用卡片分区 */
@Composable
fun SectionCard(
    title: String? = null,
    modifier: Modifier = Modifier,
    content: @Composable () -> Unit
) {
    Card(
        modifier = modifier.fillMaxWidth(),
        shape = RoundedCornerShape(18.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.45f)
        ),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant)
    ) {
        Column(modifier = Modifier.padding(14.dp)) {
            if (title != null) {
                Text(
                    text = title,
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.SemiBold,
                    color = MaterialTheme.colorScheme.primary
                )
                Spacer(Modifier.height(8.dp))
            }
            content()
        }
    }
}

@Composable
fun SwitchRow(
    title: String,
    subtitle: String? = null,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(Modifier.weight(1f)) {
            Text(title, style = MaterialTheme.typography.bodyLarge)
            if (subtitle != null) {
                Text(
                    subtitle,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
        Switch(checked = checked, onCheckedChange = onCheckedChange)
    }
}

@Composable
fun InfoBanner(
    text: String,
    actionLabel: String? = null,
    onAction: (() -> Unit)? = null
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp),
        shape = RoundedCornerShape(14.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.secondaryContainer
        )
    ) {
        Row(
            modifier = Modifier.padding(start = 14.dp, end = 6.dp, top = 8.dp, bottom = 8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = text,
                modifier = Modifier.weight(1f),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSecondaryContainer
            )
            if (actionLabel != null && onAction != null) {
                TextButton(onClick = onAction) { Text(actionLabel) }
            }
        }
    }
}

/** 点击弹出系统时间选择器 */
@Composable
fun TimeField(
    label: String,
    minuteOfDay: Int,
    onChange: (Int) -> Unit
) {
    val context = LocalContext.current
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(label, Modifier.weight(1f), style = MaterialTheme.typography.bodyLarge)
        OutlinedButton(onClick = {
            TimePickerDialog(
                context,
                { _, hour, minute -> onChange(hour * 60 + minute) },
                minuteOfDay / 60,
                minuteOfDay % 60,
                true
            ).show()
        }) {
            Text(Scheduler.formatMinuteOfDay(minuteOfDay))
        }
    }
}

private val DayLabels = listOf("一", "二", "三", "四", "五", "六", "日")

@Composable
fun DayPickerRow(selected: List<Int>, onChange: (List<Int>) -> Unit) {
    Row(modifier = Modifier.fillMaxWidth()) {
        DayLabels.forEachIndexed { index, label ->
            val day = index + 1
            FilterChip(
                selected = selected.contains(day),
                onClick = {
                    val next = if (selected.contains(day)) {
                        selected.filter { it != day }
                    } else {
                        (selected + day).sorted()
                    }
                    onChange(next)
                },
                label = { Text(label, textAlign = TextAlign.Center) },
                modifier = Modifier
                    .weight(1f)
                    .padding(end = 2.dp)
            )
        }
    }
}

@Composable
fun CountdownText(targetMs: Long?, nowMs: Long) {
    val text = when {
        targetMs == null -> "已暂停"
        targetMs <= nowMs -> "提醒中"
        else -> formatCountdown(targetMs - nowMs)
    }
    Text(
        text = if (targetMs == null) text else "下次 $text",
        style = MaterialTheme.typography.titleSmall,
        color = MaterialTheme.colorScheme.primary,
        fontWeight = FontWeight.SemiBold
    )
}

fun formatCountdown(deltaMs: Long): String {
    val totalSec = deltaMs / 1000
    val days = totalSec / 86_400
    val hours = (totalSec % 86_400) / 3_600
    val minutes = (totalSec % 3_600) / 60
    val seconds = totalSec % 60
    return when {
        days > 0 -> "${days}天 ${hours}时${minutes}分"
        hours > 0 -> "${hours}:${"%02d".format(minutes)}:${"%02d".format(seconds)}"
        else -> "%02d:%02d".format(minutes, seconds)
    }
}

@Composable
fun EmojiGrid(selected: String, onSelect: (String) -> Unit) {
    val emojis = listOf(
        "💧", "🍎", "🚶", "🧘", "💊", "☕", "🛏", "🌙",
        "📚", "💻", "🪟", "🧴", "🐾", "📝", "🏋️", "🥗",
        "🍵", "🧦", "🌡", "💉", "📱", "🧹", "🎯", "⏰"
    )
    LazyVerticalGrid(
        columns = GridCells.Fixed(8),
        modifier = Modifier
            .fillMaxWidth()
            .height(150.dp)
    ) {
        items(emojis) { emoji ->
            FilterChip(
                selected = emoji == selected,
                onClick = { onSelect(emoji) },
                label = { Text(emoji, style = MaterialTheme.typography.bodyLarge) },
                modifier = Modifier.padding(2.dp)
            )
        }
    }
}

@Composable
fun LabeledTextField(
    label: String,
    value: String,
    onValueChange: (String) -> Unit,
    placeholder: String = "",
    singleLine: Boolean = true
) {
    OutlinedTextField(
        value = value,
        onValueChange = onValueChange,
        label = { Text(label) },
        placeholder = { Text(placeholder) },
        singleLine = singleLine,
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp)
    )
}

@Composable
fun KeyValueRow(key: String, value: String) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 3.dp)
    ) {
        Text(
            key,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Spacer(Modifier.width(8.dp))
        Text(
            value,
            style = MaterialTheme.typography.bodyMedium,
            modifier = Modifier.weight(1f),
            textAlign = TextAlign.End
        )
    }
}

@Composable
fun EmptyHint(text: String) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 28.dp),
        contentAlignment = Alignment.Center
    ) {
        Text(
            text = text,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.widthIn(max = 280.dp),
            textAlign = TextAlign.Center
        )
    }
}

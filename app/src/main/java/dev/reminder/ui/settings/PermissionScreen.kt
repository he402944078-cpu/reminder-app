package dev.reminder.ui.settings

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import dev.reminder.AppGraph
import dev.reminder.ui.common.InfoBanner
import dev.reminder.ui.common.KeyValueRow
import dev.reminder.ui.common.PermissionActions
import dev.reminder.ui.common.PermissionStatus
import dev.reminder.ui.common.SectionCard

@Composable
fun PermissionScreen(nowMs: Long) {
    val context = LocalContext.current
    val permission = remember(nowMs / 5_000L) { PermissionStatus.snapshot(context) }
    val settings by AppGraph.settings.state.collectAsStateWithLifecycle()

    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(12.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        item {
            if (permission.healthy) {
                InfoBanner(text = "核心权限齐了，提醒可以正常触发。建议再关闭电池优化并允许自启动。")
            } else {
                InfoBanner(text = "缺少关键权限，提醒可能不会弹出或被延后。")
            }
        }

        item {
            SectionCard(title = "关键权限") {
                StatusRow(
                    title = "通知",
                    ok = permission.notifications,
                    hint = "Android 13+ 需显式授权，否则提醒不可见",
                    actionLabel = "去授权",
                    onAction = { PermissionActions.requestNotifications(context) }
                )
                StatusRow(
                    title = "精确闹钟",
                    ok = permission.exactAlarm,
                    hint = "决定 30 分钟这类节拍是否准点",
                    actionLabel = "去授权",
                    onAction = { PermissionActions.requestExactAlarm(context) }
                )
                StatusRow(
                    title = "忽略电池优化",
                    ok = permission.batteryIgnored,
                    hint = "省电模式下系统会冻结后台",
                    actionLabel = "去设置",
                    onAction = { PermissionActions.requestBatteryExemption(context) }
                )
            }
        }

        item {
            SectionCard(title = "厂商自启动（必须手动）") {
                Text(
                    "小米 / 华为 / OPPO / vivo 的清理策略不受本应用控制，需在系统设置里允许自启动并无限制省电。",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Spacer(Modifier.height(8.dp))
                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedButton(
                        onClick = { PermissionActions.openAppDetails(context) },
                        modifier = Modifier.weight(1f)
                    ) { Text("应用详情") }
                    OutlinedButton(
                        onClick = { PermissionActions.openBatteryOptimizationList(context) },
                        modifier = Modifier.weight(1f)
                    ) { Text("电池优化") }
                }
                Spacer(Modifier.height(8.dp))
                KeyValueRow("自启指引已读", if (settings.oemGuideShown) "是" else "否")
                OutlinedButton(
                    onClick = { AppGraph.settings.update { it.copy(oemGuideShown = false) } },
                    modifier = Modifier.fillMaxWidth()
                ) { Text("重新显示时间线里的指引") }
            }
        }

        item {
            SectionCard(title = "自愈机制") {
                Text(
                    "开机 / 改时 / 升级 / 被杀后重启：重建全部闹钟。",
                    style = MaterialTheme.typography.bodySmall
                )
                Text(
                    "每 12 小时看门狗校验一次，缺注册或已过期就补排。",
                    style = MaterialTheme.typography.bodySmall
                )
                Spacer(Modifier.height(8.dp))
                OutlinedButton(
                    onClick = { AppGraph.io { AppGraph.coordinator.rescheduleAll() } },
                    modifier = Modifier.fillMaxWidth()
                ) { Text("立即重建全部闹钟") }
            }
        }

        item { Spacer(Modifier.height(24.dp)) }
    }
}

@Composable
private fun StatusRow(
    title: String,
    ok: Boolean,
    hint: String,
    actionLabel: String,
    onAction: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(Modifier.weight(1f)) {
            Text(
                text = (if (ok) "✅ " else "⚠️ ") + title,
                style = MaterialTheme.typography.bodyLarge,
                fontWeight = FontWeight.SemiBold
            )
            Text(
                hint,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
        OutlinedButton(onClick = onAction) { Text(actionLabel) }
    }
}

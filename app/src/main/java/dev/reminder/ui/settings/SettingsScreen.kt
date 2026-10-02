package dev.reminder.ui.settings

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.provider.Settings
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Slider
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsStateWithLifecycle
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import dev.reminder.AppGraph
import dev.reminder.ui.common.PermissionActions
import dev.reminder.ui.common.SectionCard
import dev.reminder.ui.common.SwitchRow
import dev.reminder.ui.common.TimeField
import java.io.File

@Composable
fun SettingsScreen(onPermission: () -> Unit) {
    val context = LocalContext.current
    val settings by AppGraph.settings.state.collectAsStateWithLifecycle()
    val tasks by AppGraph.repo.tasks.collectAsStateWithLifecycle()

    var exportPath by remember { mutableStateOf<String?>(null) }
    var importText by remember { mutableStateOf("") }
    var importMessage by remember { mutableStateOf<String?>(null) }
    var showGuide by remember { mutableStateOf(false) }

    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = androidx.compose.foundation.layout.Padding(12.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        item {
            SectionCard(title = "提醒行为") {
                Text("默认贪睡时长 ${settings.defaultSnoozeMinutes} 分钟")
                Slider(
                    value = settings.defaultSnoozeMinutes.toFloat(),
                    onValueChange = { value ->
                        AppGraph.settings.update { it.copy(defaultSnoozeMinutes = value.toInt()) }
                    },
                    valueRange = 5f..60f,
                    steps = 10
                )
                Text(
                    "新建任务时的默认值，已存在的任务在编辑页单独设置。",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }

        item {
            SectionCard(title = "免打扰时段") {
                SwitchRow(
                    title = "启用免打扰",
                    subtitle = "时段内的提醒只静默送达",
                    checked = settings.quietEnabled,
                    onCheckedChange = { value -> AppGraph.settings.update { it.copy(quietEnabled = value) } }
                )
                TimeField(
                    label = "开始",
                    minuteOfDay = settings.quietStartMinute,
                    onChange = { value -> AppGraph.settings.update { it.copy(quietStartMinute = value) } }
                )
                TimeField(
                    label = "结束",
                    minuteOfDay = settings.quietEndMinute,
                    onChange = { value -> AppGraph.settings.update { it.copy(quietEndMinute = value) } }
                )
                if (settings.quietStartMinute >= settings.quietEndMinute) {
                    Text(
                        "开始需早于结束（不支持跨零点）。",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.error
                    )
                }
            }
        }

        item {
            SectionCard(title = "后台存活") {
                Text(
                    "本应用不使用常驻前台服务，靠精确闹钟 + 开机重建 + 12 小时看门狗自愈。国产 ROM 需允许自启动。",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Spacer(Modifier.height(8.dp))
                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedButton(onClick = onPermission, modifier = Modifier.weight(1f)) { Text("权限检查") }
                    OutlinedButton(onClick = { showGuide = !showGuide }, modifier = Modifier.weight(1f)) { Text("自启指引") }
                }
                if (showGuide) {
                    Spacer(Modifier.height(8.dp))
                    Text(OemGuide, style = MaterialTheme.typography.bodySmall)
                    Spacer(Modifier.height(6.dp))
                    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        OutlinedButton(
                            onClick = { PermissionActions.openAppDetails(context) },
                            modifier = Modifier.weight(1f)
                        ) { Text("应用详情") }
                        OutlinedButton(
                            onClick = { copyToClipboard(context, OemGuide) },
                            modifier = Modifier.weight(1f)
                        ) { Text("复制指引") }
                    }
                }
            }
        }

        item {
            SectionCard(title = "备份（共 ${tasks.size} 个任务）") {
                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Button(
                        onClick = {
                            val json = AppGraph.repo.exportJson()
                            val file = backupFile(context)
                            var path = "剪贴板"
                            try {
                                file.parentFile?.mkdirs()
                                file.writeText(json)
                                path = file.absolutePath
                            } catch (e: Exception) {
                                // 写文件失败时仍走剪贴板
                            }
                            copyToClipboard(context, json)
                            exportPath = "$path（已复制到剪贴板）"
                        },
                        modifier = Modifier.weight(1f)
                    ) { Text("导出") }
                    OutlinedButton(
                        onClick = {
                            val json = AppGraph.repo.exportJson()
                            copyToClipboard(context, json)
                            exportPath = "已复制到剪贴板"
                        },
                        modifier = Modifier.weight(1f)
                    ) { Text("复制备份") }
                }
                if (exportPath != null) {
                    Spacer(Modifier.height(6.dp))
                    Text(
                        "备份位置：$exportPath",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                Spacer(Modifier.height(8.dp))
                OutlinedTextField(
                    value = importText,
                    onValueChange = { importText = it },
                    label = { Text("粘贴备份 JSON") },
                    minLines = 3,
                    modifier = Modifier.fillMaxWidth()
                )
                Spacer(Modifier.height(6.dp))
                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedButton(
                        onClick = { importMessage = runImport(context, importText, replace = false) },
                        enabled = importText.isNotBlank(),
                        modifier = Modifier.weight(1f)
                    ) { Text("合并导入") }
                    OutlinedButton(
                        onClick = { importMessage = runImport(context, importText, replace = true) },
                        enabled = importText.isNotBlank(),
                        modifier = Modifier.weight(1f)
                    ) { Text("覆盖导入") }
                }
                if (importMessage != null) {
                    Spacer(Modifier.height(6.dp))
                    Text(
                        importMessage!!,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.primary
                    )
                }
            }
        }

        item {
            SectionCard(title = "关于") {
                Text(
                    "自用版 v0.1 · 仅中文 · 侧载安装",
                    style = MaterialTheme.typography.bodyMedium
                )
                Spacer(Modifier.height(4.dp))
                Text(
                    "提醒依赖系统精确闹钟：请保持通知权限、精确闹钟授权，并在厂商设置里允许自启动。",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Spacer(Modifier.height(8.dp))
                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedButton(
                        onClick = { PermissionActions.openBatteryOptimizationList(context) },
                        modifier = Modifier.weight(1f)
                    ) { Text("电池优化") }
                    OutlinedButton(
                        onClick = {
                            try {
                                context.startActivity(
                                    Intent(Settings.ACTION_APPLICATION_SETTINGS)
                                        .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                                )
                            } catch (e: Exception) {
                                PermissionActions.openAppDetails(context)
                            }
                        },
                        modifier = Modifier.weight(1f)
                    ) { Text("系统应用设置") }
                }
            }
        }

        item { Spacer(Modifier.height(24.dp)) }
    }
}

private const val OemGuide = """自启动 / 后台白名单指引（按品牌）：

【小米 MIUI / HyperOS】
设置 → 应用设置 → 自启动管理 → 打开「提醒」
安全中心 → 权限管理 → 通知管理 → 允许
设置 → 电池 → 应用省电策略 → 提醒 → 无限制

【华为 / 荣耀】
手机管家 → 应用启动管理 → 提醒 → 关闭「自动管理」，三项全部允许
设置 → 电池 → 更多电池设置 → 允许后台活动

【OPPO / 一加 ColorOS】
设置 → 应用 → 自启动管理 → 允许
设置 → 电池 → 更多 → 后台智能运行 → 允许

【vivo OriginOS】
i 管家 → 权限管理 → 自启动 → 允许
设置 → 电池 → 后台高耗电 → 允许

【通用】
- 通知权限：允许
- 精确闹钟（闹钟和日历 → 允许设置精确闹钟）：允许
- 电池优化：不优化 / 无限制
- 最近任务里下拉锁定本应用，避免被一键清理"""

private fun backupFile(context: Context): File {
    val dir = context.getExternalFilesDir(null) ?: context.filesDir
    return File(dir, "reminder-backup.json")
}

private fun copyToClipboard(context: Context, text: String) {
    try {
        val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
        clipboard.setPrimaryClip(ClipData.newPlainText("提醒备份", text))
    } catch (e: Exception) {
        // 忽略
    }
}

private fun runImport(context: Context, text: String, replace: Boolean): String {
    return try {
        val count = AppGraph.repo.importJson(text.trim(), replace)
        AppGraph.io { AppGraph.coordinator.rescheduleAll() }
        "已导入 $count 个任务"
    } catch (e: Exception) {
        "导入失败：${e.message}"
    }
}

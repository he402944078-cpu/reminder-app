# 提醒管家（Reminder）· 自用版

后台常驻的「定时提醒」Android 应用：**不使用常驻前台服务**，靠精确闹钟 + 开机重建 + 12 小时看门狗自愈。
支持「每隔 N 分钟（最小 15 分钟）」与「每日定点时刻」两类任务，带时间窗、星期、贪睡、静默时段、错过合并。

- 语言：仅中文
- 分发：GitHub Actions 出 debug APK，自行侧载安装
- 平台：Android 原生（Kotlin + Jetpack Compose），minSdk 26 / targetSdk 34

---

## 1. 用 GitHub Actions 出包（推荐，本机无需 Android SDK）

1. 新建一个 GitHub 仓库（建议 Private），把本目录全部内容推上去：

```bash
cd reminder-app
git init
git add .
git commit -m "M0-M2: 提醒管家自用版"
git branch -M main
git remote add origin https://github.com/<你的用户名>/<仓库名>.git
git push -u origin main
```

2. 打开仓库 → **Actions**，工作流 `build.yml` 会自动跑（push / 手动 `workflow_dispatch` 都可触发）：
   - `test`：跑 `:app:testDebugUnitTest`（调度内核与合并策略单测）
   - `apk`：`test` 通过后跑 `:app:assembleDebug`，上传产物

3. 进最新的 run → Artifacts → 下载 **`reminder-debug-apk`** → 解压得到 `app-debug.apk`。

   > 当前绿包：run [`36961792934`](https://github.com/he402944078-cpu/reminder-app/actions/runs/36961792934)（`test` 19 例通过 + `apk`），产物 `reminder-debug-apk` 约 11.2 MB，解压后 `app-debug.apk` 约 12.5 MB。私有仓库需登录 GitHub 才能下载；本机已下载一份在 `release/app-debug.apk`（已 gitignore，不入库）。

4. 传到手机安装（需允许「安装未知来源应用」）：

```bash
adb install -r app-debug.apk
# 或者直接用手机文件管理器点击安装
```

> 工作流不依赖 `gradle-wrapper.jar`：由 `gradle/actions/setup-gradle@v4` 安装 Gradle 8.10 后直接调用 `gradle`。

## 2. 本机编译（可选）

需要 JDK 17 + Android SDK（platform 35、build-tools 35）。

```bash
gradle --no-daemon :app:assembleDebug     # 产物 app/build/outputs/apk/debug/app-debug.apk
gradle --no-daemon :app:testDebugUnitTest # 单测报告 app/build/reports/tests/testDebugUnitTest/index.html
```

---

## 3. 首次使用必做的 3 件事（决定提醒能不能准点）

| 项 | 位置 | 说明 |
| --- | --- | --- |
| 通知权限 | 应用内「权限检查」→ 去授权 | Android 13+ 必须显式授权，否则提醒不可见 |
| 精确闹钟 | 「权限检查」→ 去授权（闹钟和日历 → 允许设置精确闹钟） | 决定 30 分钟这类节拍是否准点 |
| 忽略电池优化 | 「权限检查」→ 去设置 | 省电模式下系统会冻结后台 |

**国产 ROM 额外必需**（应用无法代劳）：允许**自启动** + 省电策略设为**无限制**，并在最近任务里下拉锁定本应用。
应用内「设置 → 后台存活 → 自启指引」有小米 / 华为 / OPPO / vivo 的分品牌步骤，可一键复制。

## 4. 使用流程

1. 时间线右下角 **+** 新建任务，或先「从模板创建」（喝水 30 分钟、起身 45 分钟、护眼 20 分钟、三餐、用药…）。
2. 提醒方式二选一：
   - **按间隔**：5–180 分钟（步长 5，**低于 15 分钟会提示不推荐**），在时间窗内按窗起对齐，每天重新对齐。
   - **定点时刻**：任意多个 `HH:mm`，按当天排序取最近未来时刻。
3. 时间窗（开始需早于结束，不支持跨零点）、星期、贪睡时长、静默送达均可调。
4. 提醒弹出后：`完成` / `贪睡 N 分钟` / `跳过`；连续错过 K≥2 次只补发 1 条并写明「已合并 K 次未提醒」。
5. 「统计」看今日触发/完成/贪睡/跳过与完成率；「设置」里可导出/导入 JSON 备份（写入应用外部私有目录并复制到剪贴板）。

## 5. 目录结构

```
app/src/main/java/dev/reminder/
  App.kt                     全局装配 + 开机/看门狗注册
  MainActivity.kt            Compose 容器（单 Activity + 自管导航栈）
  data/                      ReminderTask / ReminderLog / TaskRepository(JSON) / SettingsStore
  domain/                    Scheduler（时间语义内核）/ MergePolicy（错过合并）
  schedule/                  AlarmScheduler / ScheduleCoordinator / AlarmReceiver / BootReceiver / WatchdogWorker / NotificationActionReceiver
  notify/                    Channels / Notifier
  ui/                        AppRoot + timeline / task / templates / stats / settings
app/src/test/java/dev/reminder/domain/   SchedulerTest / MergePolicyTest
.github/workflows/build.yml              CI：test → assembleDebug → 上传 APK
PLAN.md                                  规划方案（含决策与验收清单）
```

## 6. 设计要点（为什么这样实现）

- **主调度 = `AlarmManager.setExactAndAllowWhileIdle` 自串联**：每次触发后由接收器登记下一次，Doze 下最省、最准点。
- **WorkManager 只做 12 小时看门狗**：校验「已注册/已过期」，缺就补排。WorkManager 不能当主调度（周期下限 15 分钟 + 20% 弹性 + Doze → 30 分钟会漂到 ~36 分钟）。
- **无注解处理器**：持久化用 `org.json` 文件 + `StateFlow`，避免 Room/KSP/Hilt 带来的构建复杂度。
- **无前台服务**：不声明 `FOREGROUND_SERVICE`（manifest 里显式 `tools:node="remove"`），因此不受「前台服务类型」新约束影响，也更省电。
- **权限降级可见**：缺通知/精确闹钟/电池优化时，时间线顶部横幅直接说明当前影响（例如「提醒会延后到 10 分钟窗口」）。

## 7. 已知限制

- 时间窗不支持跨零点（要求开始 < 结束）。
- 厂商一键清理 / 强制停止后无法自愈，需要用户侧白名单（应用内已给出指引与自检入口）。
- 间隔小于 15 分钟不推荐（UI 会警告，不禁止）。
- 备份文件在应用外部私有目录，卸载即清除；建议导出后自行保存。

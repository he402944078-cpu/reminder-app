# 后台定时提醒 App —— 规划方案 v0.1

> 目标：一个挂在手机后台的「定时任务提醒」App。用户自定义若干提醒任务（例：每 30 分钟喝水、每 45 分钟起身、20-20-20 护眼、吃药打卡），App 在后台按间隔/时间点推送通知，可贪睡、可标记完成、可暂停，并在重启/省电模式下自愈。

---

## 1. 需求拆解

### 1.1 功能需求
| 编号 | 需求 | 说明 |
|---|---|---|
| F1 | 多任务 | 任意数量提醒任务，各自独立开关 |
| F2 | 间隔型调度 | 「每隔 N 分钟」，N 支持 5–720，含预设 15/30/45/60/90/120 |
| F3 | 时间点型调度 | 每日固定时刻列表（如 09:00 / 14:30 / 20:00） |
| F4 | 时间窗 | 只在 09:00–22:00 之类窗口内提醒（喝水/久坐场景必需） |
| F5 | 星期维度 | 仅工作日 / 仅周末 / 自定义星期 |
| F6 | 通知交互 | 完成 / 贪睡(5/10/15min) / 跳过本次，直接在通知上点 |
| F7 | 常驻可见（可选） | 前台服务通知栏显示「距下次提醒 12:05」，用户可开关 |
| F8 | 免打扰 | 全局静默时段（会议/午休），静默期只记低优先级通知 |
| F9 | 统计 | 完成率、连续天数、7 日热力图 |
| F10 | 模板 | 内置喝水/起身/护眼/番茄钟/喂药/拉伸，一键添加 |
| F11 | 权限自检 | 页面内显示精确闹钟/通知/电池优化/自启动状态并一键跳转 |
| F12 | 备份 | 导出/导入 JSON（本地文件，无云、无账号） |

### 1.2 非功能需求
- **离线**：零网络、零账号、零埋点，数据全在本地。
- **省电**：默认走精确闹钟自链式调度，不用轮询、不用 WakeLock。
- **自愈**：重启、改系统时间、被厂商 ROM 杀进程后能恢复调度。
- **体积**：APK < 15 MB；冷启动 < 1.5 s。
- **隐私**：无广告、无 SDK、可 F-Droid 化。

### 1.3 明确不做（v1 边界）
账号同步、云端推送、Web 端、手表端、语音助手、第三方 API 集成、iOS 实时后台常驻。

---

## 2. 平台决策（关键）

| 方案 | 后台可靠性 | 成本 | 结论 |
|---|---|---|---|
| **Android 原生**（Kotlin + Compose） | ★★★★★ 完全掌控 AlarmManager / 前台服务 / 厂商适配 | 只覆盖 Android | **v1 首选** |
| **iOS 原生**（UNNotification） | ★★★ 只能预生成通知，无后台常驻 | 需 Mac + Xcode 出包 | v2 |
| **Flutter 跨平台** | ★★★★（Android 侧走原生插件） | 需 Flutter SDK；iOS 侧受系统限制 | 若要「同时上 iOS」则选它 |
| PWA / H5 | ★ 后台不可靠 | — | 否决 |

**核心判断**：这个产品的价值全在「后台真的能按时响」。Android 是唯一能做到「任意间隔 + 精确 + 自愈」的平台；iOS 受系统限制，只能做到「预设间隔 + 每日预生成通知」。所以 **v1 = Android 原生**，iOS 作为 v2 的独立工程（或 Flutter 路线一次性覆盖）。

---

## 3. 技术架构（Android 原生）

```
┌────────────────────────────────────────────────────────┐
│ UI 层  Jetpack Compose + Material 3                     │
│  TimelineScreen · TaskEditScreen · StatsScreen · Settings│
├────────────────────────────────────────────────────────┤
│ 领域层  Scheduler（纯函数，可单测）                      │
│  computeNextFire(task, now) -> Instant?                 │
│  合并策略：错过 K 次 → 只补发 1 条「已错过 K 次」        │
├────────────────────────────────────────────────────────┤
│ 调度层                                                  │
│  AlarmScheduler  : setExactAndAllowWhileIdle 自链式      │
│  AlarmReceiver   : 发通知 → 算 next → 重注册            │
│  BootReceiver    : BOOT / TIME_CHANGED / TZ_CHANGED /    │
│                    LOCALE / MY_PACKAGE_REPLACED → 全量重建│
│  WatchdogWorker  : WorkManager 每日 1 次校验+补注册      │
│  ReminderService : 可选前台服务(specialAccess) 倒计时    │
├────────────────────────────────────────────────────────┤
│ 通知层  NotificationChannel × 3（高优先/静默/常驻）      │
│  Action: DONE · SNOOZE · SKIP  → NotificationReceiver    │
├────────────────────────────────────────────────────────┤
│ 数据层  Room(SQLite) + DataStore(Preferences)            │
└────────────────────────────────────────────────────────┘
```

### 3.1 为什么用「自链式精确闹钟」而不是 WorkManager 轮询
- `WorkManager` 周期任务最小 15 分钟，且带 20% 弹性窗口 + Doze 批量延迟 → 30 分钟间隔可能漂到 36 分钟，「喝水提醒」会明显不准。
- `AlarmManager.setExactAndAllowWhileIdle` 精确，Doze 下每 9 分钟窗口允许 1 次 → 30 分钟间隔完全够用。
- 方案：**主用精确闹钟（精确性）+ WorkManager 每日 1 次自愈（可靠性）**，两者互补。

### 3.2 权限与厂商适配（本项目最大风险点，必须前置设计）
| 项 | 版本 | 处理 |
|---|---|---|
| `POST_NOTIFICATIONS` | Android 13+ | 首次进入引导页运行时申请 |
| `SCHEDULE_EXACT_ALARM` | Android 12+ | 运行时检测 `canScheduleExactAlarms()`，未授予则跳系统设置并降级为 inexact |
| `USE_EXACT_ALARM` | Android 14+ | 提醒/闹钟类属允许用途，清单声明 + 上架说明用途 |
| `USE_FULL_SCREEN_INTENT` | Android 14+ | 仅「闹钟级」任务使用，默认可能不授予 |
| `REQUEST_IGNORE_BATTERY_OPTIMIZATIONS` | — | 引导页一键申请「不优化」 |
| `RECEIVE_BOOT_COMPLETED` | — | 开机重建闹钟 |
| 厂商自启动白名单 | 小米/华为/OPPO/vivo/三星 | 内置分厂商图文指引页（无法自动化，必须做） |
| 前台服务类型 | Android 14+ | `android:foregroundServiceType="specialAccess"`，需精确闹钟权限 |

### 3.3 数据模型（Room）
```kotlin
// reminder_task
id: UUID                 name: String        icon: String(emoji)
scheduleType: INTERVAL | DAILY_TIMES | WEEKLY
intervalMinutes: Int     windowStart: Int(min-of-day)  windowEnd: Int
dailyTimes: List<String> ("HH:mm")
daysOfWeek: Int (bitmask 0b1111100 = 工作日)
promptText: String       channel: HIGH | QUIET
snoozeMinutes: Int       autoAdvance: Boolean
enabled: Boolean         pausedUntil: Long?
nextFireAt: Long         lastFiredAt: Long?
graceMinutes: Int        // 超过该时长的过期提醒合并补发
createdAt / updatedAt

// reminder_log
id, taskId, firedAt, action: FIRED|DONE|SNOOZED|SKIPPED|EXPIRED, note

// settings (DataStore)
quietStart/quietEnd, defaultSnooze, theme, soundUri,
oemGuideSeenFlags, exactAlarmGrantedCache
```

### 3.4 调度语义（写清边界，避免实现歧义）
1. **INTERVAL**：以「时间窗起点」为锚，按 N 分钟对齐；跨窗不提醒；新窗口起点重新对齐。
2. **DAILY_TIMES**：命中列表内最近时刻；跳过非选中星期。
3. **错过合并**：关机/省电导致错过 K 次 → 恢复后只发 1 条「今日已错过 K 次喝水提醒」，不刷屏。
4. **贪睡**：贪睡只推迟本次，不改变整体节拍。
5. **免打扰**：静默时段内以低优先级渠道发出，不响铃不震动。
6. **系统时间变更**：`ACTION_TIME_CHANGED` / `TIMEZONE_CHANGED` 广播 → 全量重建。

---

## 4. iOS 版可行性（诚实评估）
- iOS 无后台常驻服务。可行做法：`UNCalendarNotificationTrigger(repeats: true)` 预生成通知。
- 整除小时的间隔很省额度：每 30 分钟 = 每小时第 0/30 分 = **2 条**；每 15 分钟 = 4 条；每 45 分钟 = 3 小时周期 3 条。
- 硬限制：**每个 App 最多 64 条待推送通知** → 任意间隔（如 25 分钟）无法长期表达。
- 结论：iOS 版**限制为预设间隔集合 {15,20,30,45,60,90,120,180}** + `BGAppRefreshTask` 每日重建，且需说明「iOS 无法保证任意间隔精确提醒」。

---

## 5. 构建与交付（本机现状：无 JDK / 无 Android SDK）

本机已确认缺 `java` / `javac` / `gradle` / `flutter` / `adb`。三条路：

| 路线 | 说明 | 依赖 |
|---|---|---|
| **A. GitHub Actions 出包（推荐）** | 我写全代码 + `.github/workflows/build.yml`，push 后 CI 自动构建签名 APK 并上传 artifact | 只需 git + GitHub 账号，本机零安装 |
| B. 本机装工具链 | `winget install Microsoft.OpenJDK.17` + Android `cmdline-tools` + `sdkmanager "platform-tools" "platforms;android-35" "build-tools;35.0.0"` | 下载约 2–3 GB，需网络与磁盘 |
| C. 只交付源码 | 用户自己在 Android Studio 里打开构建 | 用户侧有 Studio |

推荐 **A 为主 + B 为备**：CI 路线最快拿到可安装 APK，本机装 SDK 用于本地调试。

---

## 6. 里程碑

| 阶段 | 内容 | 产出 | 工作量(单人) |
|---|---|---|---|
| **M0 决策与骨架** | 确认平台/栈/构建路线；Gradle 多模块骨架 + Compose 空壳 + CI 出包跑通 | 能安装的空壳 APK | 0.5 天 |
| **M1 调度内核** | Room 数据层 + Scheduler 纯函数 + AlarmReceiver + BootReceiver + 通知渠道 | 命令行注入任务→按时弹通知（无 UI）+ 单测通过 | 2 天 |
| **M2 UI 与权限** | 任务列表/编辑/模板/权限引导页/免打扰 | 可完整使用的 MVP APK | 2 天 |
| **M3 可靠性** | 贪睡/完成/跳过动作、错过合并、Watchdog 自愈、厂商指引、重启与改时测试 | 通过「可靠性验收清单」 | 1.5 天 |
| **M4 增强** | 统计页、导入导出、深色模式、图标与动效、可选前台服务 | v1.0 发布包 | 2 天 |
| **M5 iOS（可选）** | Flutter 重写 或 iOS 原生，预设间隔方案 | iOS 测试包 | 3–4 天 |

**MVP（M0–M3）≈ 6 个工作日**；v1.0 ≈ 8 个工作日。

### 验收清单（M3 硬指标）
- [ ] 设 30 分钟间隔，连续 24 h 漂移 ≤ 2 分钟
- [ ] 重启手机后闹钟自动恢复（无需打开 App）
- [ ] 修改系统时间/时区后调度正确
- [ ] 开启省电模式 8 h 后仍能提醒（允许合并补发）
- [ ] 通知上「完成/贪睡」动作生效，贪睡后节拍不变
- [ ] 关闭精确闹钟权限时 App 明确提示并降级，不静默失效
- [ ] 小米/华为 ROM 上按指引设置后可稳定提醒

---

## 7. 风险清单

| 风险 | 影响 | 缓解 |
|---|---|---|
| 厂商 ROM 杀后台（小米/华为/OPPO） | 提醒失效，差评主因 | 引导页 + 自检页 + Watchdog 自愈 + 文档 |
| Android 14 收紧 `USE_EXACT_ALARM` | 上架审核 | 用途声明 + 降级为 inexact + WorkManager 兜底 |
| 前台服务被系统停止（Android 15 限制后台启动 FGS） | 倒计时消失 | FGS 仅由用户显式开关触发，UI 不依赖它 |
| iOS 64 条通知额度 | iOS 功能缩水 | 预设间隔集合 + 每日重建 + 明确文案 |
| 通知被用户长期屏蔽 | 产品失效 | 提供声音/震动/全屏 intent 分级 + 自检提示 |
| 无本机工具链 | 无法本地出包 | CI 出包为主路线 |

---

## 8. 目录结构（v1）
```
reminder-app/
├─ PLAN.md
├─ settings.gradle.kts  build.gradle.kts  gradle/libs.versions.toml
├─ .github/workflows/build.yml
└─ app/
   ├─ src/main/AndroidManifest.xml
   ├─ src/main/java/dev/reminder/
   │  ├─ data/       (AppDatabase, TaskDao, LogDao, SettingsStore, Repo)
   │  ├─ domain/     (Scheduler, NextFireCalculator, MergePolicy)
   │  ├─ schedule/   (AlarmScheduler, AlarmReceiver, BootReceiver, WatchdogWorker)
   │  ├─ notify/     (Channels, Notifier, NotificationActionReceiver, ReminderService)
   │  ├─ ui/         (theme, timeline, taskedit, templates, stats, settings, permission)
   │  └─ App.kt  MainActivity.kt
   └─ src/test/java/ (SchedulerTest, MergePolicyTest)
```

---

## 9. 已确认决策（v0.2 锁定）
| 决策点 | 结论 |
|---|---|
| 平台 | **只做 Android 原生**（Kotlin + Jetpack Compose），iOS 留 v2 |
| 构建 | **GitHub Actions CI 出包**（本机零安装，push 后产出签名 debug APK artifact） |
| 最小间隔 | **15 分钟**（滑杆下限 5 分钟，但推荐 ≥15）；**不做常驻前台服务** |
| 语言/分发 | **仅中文 + 自用侧载 APK**（无隐私政策/上架材料） |
| 移除项 | F7 常驻倒计时、`FOREGROUND_SERVICE` 权限、`specialAccess` 类型 → 全部砍掉，降低 Android 14/15 权限风险 |

### 9.1 v1 持久化选型调整（重要）
原计划用 Room + KSP。因**本机无 JDK/Android SDK，无法本地编译验证**，注解处理器（KSP）是首构建失败的最大来源，故 v1 改为：

- **零注解处理器方案**：任务与日志用 `org.json` 序列化到 `filesDir/tasks.json`、`logs.json`，内存 `StateFlow` 对外暴露，写操作走单线程 IO。
- 数据量级（几十个任务 + 数千条日志）完全不需要 SQLite；导出/导入即文件读写，天然满足 F12。
- 依赖从 `Room + KSP` 降为 `core-ktx + compose + work`，首构建成功率显著提升。
- **v1.1 升级路径**：若日志量增长或需要复杂查询，再迁移 Room（Repository 接口不变，调用方零改动）。

## 10. 实现顺序（对应里程碑）
1. **M0** Gradle 骨架 + CI workflow → 目标是「push 即出 APK」
2. **M1** `Scheduler` 纯函数 + 单测 → `AlarmScheduler`/`AlarmReceiver`/`BootReceiver`/`WatchdogWorker` → `Notifier`
3. **M2** Compose：时间轴 / 任务编辑 / 模板 / 统计 / 设置 / 权限自检
4. **M3** 贪睡·完成·跳过、错过合并、自愈、厂商指引、验收清单
5. **M4** 导入导出、深色模式、图标、v1.0 发布包

---

## 11. 实现进度（v0.4 · CI 已出绿包，待真机验收）

### 已完成（M0 + M1 + M2 + M3 主体）

| 层 | 文件 | 说明 |
|---|---|---|
| 构建 | `settings.gradle.kts` / `build.gradle.kts` / `gradle/libs.versions.toml` / `app/build.gradle.kts` | AGP 8.5.2 + Kotlin 2.0.21 + compose-bom 2024.11.00，compileSdk 35 / minSdk 26 / targetSdk 34，JVM 17 |
| CI | `.github/workflows/build.yml` | `test` → `apk`（`setup-gradle@v4` 装 Gradle 8.10，不依赖 wrapper jar），artifact `reminder-debug-apk` |
| 数据 | `data/ReminderTask.kt` `TaskRepository.kt` `SettingsStore.kt` | 零注解处理器：`filesDir/tasks.json`、`logs.json`、`settings.json` + `StateFlow`；日志上限 5000，导出格式 `reminder-app-backup` |
| 领域 | `domain/Scheduler.kt` `domain/MergePolicy.kt` | 纯函数时间内核（可单测）：间隔对齐、时间窗、星期、跨天、定点时刻、错过合并 |
| 调度 | `schedule/AlarmScheduler.kt` `ScheduleCoordinator.kt` `AlarmReceiver.kt` `BootReceiver.kt` `WatchdogWorker.kt` `NotificationActionReceiver.kt` | 精确闹钟自串联（触发后登记下一次）；无精确闹钟权限降级为 10 分钟窗口；开机/改时/改时区/改语言/升级/解锁 → 全量重建；12 h 看门狗校验补排 |
| 通知 | `notify/Channels.kt` `Notifier.kt` | 高优先/静默双渠道 + 分组；动作「完成 / 贪睡 N 分 / 跳过」；静默时段自动走低优先 |
| UI | `ui/AppRoot.kt` `timeline/` `task/` `templates/` `stats/` `settings/` `common/` | 单 Activity + 自管导航栈；时间线（倒计时、今日剩余次数、权限横幅）、任务编辑（间隔/定点 + 时间窗 + 星期 + 贪睡 + 静默 + 预览）、10 条模板、统计与完成率、设置（默认贪睡/免打扰/自启指引/导入导出）、权限自检 |
| 资源 | `AndroidManifest.xml` + `res/` | 权限清单（含 `SCHEDULE_EXACT_ALARM`/`USE_EXACT_ALARM`，显式 `tools:node="remove"` 掉 `FOREGROUND_SERVICE`）、自适应图标、通知小图标 |
| 单测 | `SchedulerTest`（12 例）+ `MergePolicyTest`（7 例） | 覆盖 30 分钟节拍、时间窗边界、跨天、星期过滤、停用任务、定点时刻排序、错过合并计数与文案 |

### 时间语义（实现口径）
- 间隔任务在时间窗内**以窗起对齐**，每天重新对齐；`windowStart < windowEnd`（不支持跨零点）。
- 贪睡只推迟本次，不改变节拍。
- 连续错过 K≥2 次 → 只补发 1 条并写「已合并 K 次未提醒」。
- 停用任务 `nextFire` 返回 `null`，闹钟取消。

### 待办（需真机 / CI 反馈）
1. ~~CI 首构建日志校对（依赖解析、lint 规则、资源引用）。~~ ✅ 已完成，见 §11.2：3 轮出绿，APK 产物 `reminder-debug-apk` 已下载。
2. 真机验收清单（§6 的 7 项硬指标），尤其小米/华为自启与省电模式 8 h。
3. 可选增强：桌面倒计时小组件、深色模式细节、签名 release 包与自更新。
4. iOS v2（预设间隔 {15,20,30,45,60,90,120,180} + 64 条额度约束）。

### 11.1 静态编译审计（本机无 JDK/SDK，逐文件人工核对）

因本机无 Java/Android SDK，无法本地编译。已按「每个被引用的符号必须存在」的口径逐文件核对：

| 核对项 | 结论 |
|---|---|
| 跨包引用 | 修复 1 处 **BLOCKER**：`TimelineScreen.kt` 使用 `PermissionActions` 缺 `import dev.reminder.ui.common.PermissionActions` |
| 未使用 import | 清理 9 处（TimelineScreen 4、TemplatesScreen 1、SettingsScreen 4 含 `Column`、TaskEditScreen 2 含 `Column`、Components 2 含 `Arrangement`/`size`）；`getValue`/`setValue` 为 `by` 委托所需，**保留** |
| 调用签名 | `AppGraph.io{}`、`settings.state.value`、`repo.importJson(text, replace): Int`、`coordinator.*`、`Notifier.show(context, task, missed, snoozed, next)`、`AlarmScheduler.schedule(id, at, snoozed=false)` 全部与定义一致 |
| Compose API | `LinearProgressIndicator(progress = Float)`、`Slider(valueRange, steps)`、`Modifier.weight`（Row/Column 作用域内）、`LazyVerticalGrid + items`、`FilterChip(label = { })` 为 material3 1.3.1 / compose 1.7.5 合法签名；~~`Padding(all: Dp)`~~ 见 §11.2（1.7.5 已移除） |
| 系统 API 等级 | `canScheduleExactAlarms`/`ACTION_REQUEST_SCHEDULE_EXACT_ALARM` 有 `SDK_INT >= S` 守卫；`ACTION_APP_NOTIFICATION_SETTINGS`(26)、`ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS`(23)、`isIgnoringBatteryOptimizations`(23) 均 ≥ minSdk 26 |
| 资源引用 | 代码仅引用 `R.drawable.ic_stat_reminder`；`res/` 内 vector 图标、`@color/ic_launcher_background`、`Theme.Material3.DayNight.NoActionBar` 均存在；minSdk 26 → 仅需 `mipmap-anydpi-v26`，无缺失 PNG |
| 单测可编译性 | `SchedulerTest`(12) / `MergePolicyTest`(7) 的 16 个具名构造参数与 `ReminderTask` 一致；期望值按调度语义逐条复算（27 槽位、跨零点 3 次、周过滤 2 次、定点 2 次） |
| 清单/构建 | manifest 无 `FOREGROUND_SERVICE`；`libs.versions.toml` 别名与 `app/build.gradle.kts` 引用一一对应；CI 用 `setup-gradle@v4` + `gradle-version: 8.10`，不依赖 wrapper jar |

结论：未发现剩余 BLOCKER 级问题；剩余风险集中在 CI 首构建的依赖解析与 lint 规则（待办 1）。

### 11.2 CI 首构建审计记录（v0.4 · 已出包）

| 轮次 | run | 结果 | 根因 | 修复 |
|---|---|---|---|---|
| #1 | `36955215338` | ❌ `test` | `android-actions/setup-android@v3` 安装已移除的 legacy `tools` 包 → `sdkmanager` 退出码 1 | 去掉该 action，直接调用 runner 预装的 `sdkmanager --install "platform-tools" "platforms;android-35" "build-tools;34.0.0"` |
| #2 | `36956883515` | ❌ `test` | 80 条 Kotlin 编译错误，收敛为 3 个根因（其余 70+ 条是级联） | 见下 |
| #3 | `36961792934` | ✅ `test` + `apk` | — | artifact `reminder-debug-apk`（11.2 MB，19 个单测通过） |

三个真实根因（已用 Google Maven 上 `foundation-layout-android-1.7.5-sources.jar` / `foundation-android-1.7.5-sources.jar` 逐条核对，非凭记忆）：

1. **`collectAsStateWithLifecycle` 属于 `androidx.lifecycle.compose`**，不是 `androidx.compose.runtime`（后者只有 `collectAsState`）→ 4 个界面文件改 import。
2. **`androidx.compose.foundation.layout.Padding` 自 Compose 1.7.0 起已删除**：1.7.5 的 `Padding.kt` 里只剩 `PaddingValues`，且 `LazyColumn/LazyRow` 的 `contentPadding` 形参类型是 `PaddingValues` → 6 个界面文件改为 `contentPadding = PaddingValues(12.dp)`。
3. `Notifier.kt` 用了 Java setter 形式 `.setBigText(text)`（Kotlin 应写属性 `.bigText(text)`），并缺 `NotificationActionReceiver` 的 import（连带 `setAction` 一起报未解析）→ 两处修正。

审计教训：**「凭记忆认定第三方库 API 存在」是静态审计的最大风险源**。涉及库签名时以 sources jar / API dump 为准；Compose 1.7 的 `Padding → PaddingValues` 属于跨版本删除，光看 IDE 提示不足以发现。

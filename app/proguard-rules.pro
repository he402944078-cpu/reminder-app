# 提醒类应用：保留广播接收器与 Worker（反射实例化）
-keep class dev.reminder.schedule.** { *; }
-keep class dev.reminder.notify.NotificationActionReceiver { *; }

# WorkManager 通过反射创建 Worker
-keep class androidx.work.** { *; }

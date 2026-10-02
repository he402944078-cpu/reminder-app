package dev.reminder

import android.content.Context
import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import dev.reminder.ui.AppRoot
import dev.reminder.ui.theme.ReminderTheme

class MainActivity : ComponentActivity() {

    private var openTaskId by mutableStateOf<String?>(null)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        openTaskId = intent?.getStringExtra(EXTRA_TASK_ID)
        setContent {
            ReminderTheme {
                AppRoot(
                    openTaskId = openTaskId,
                    onOpenConsumed = { openTaskId = null }
                )
            }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        openTaskId = intent.getStringExtra(EXTRA_TASK_ID)
    }

    companion object {
        const val EXTRA_TASK_ID = "dev.reminder.extra.TASK_ID"

        fun openTaskIntent(context: Context, taskId: String): Intent =
            Intent(context, MainActivity::class.java)
                .setAction(Intent.ACTION_MAIN)
                .addCategory(Intent.CATEGORY_LAUNCHER)
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP)
                .putExtra(EXTRA_TASK_ID, taskId)
    }
}

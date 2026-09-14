package dev.jamesnicholls.memoswidget

import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.runtime.getValue
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import dev.jamesnicholls.memoswidget.ui.MemosTheme
import dev.jamesnicholls.memoswidget.ui.QuickComposeScreen

class QuickComposeActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        val initialText = extractSharedText(intent)

        val container = (application as MemosApp).container
        setContent {
            MemosTheme {
                val viewModel: QuickComposeViewModel = viewModel(
                    factory = QuickComposeViewModel.factory(container),
                )
                val settings by viewModel.settings.collectAsStateWithLifecycle()
                val sendState by viewModel.sendState.collectAsStateWithLifecycle()
                QuickComposeScreen(
                    initialText = initialText,
                    settings = settings,
                    sendState = sendState,
                    onSend = viewModel::send,
                    onSent = ::finish,
                    onOpenSettings = ::openSettings,
                )
            }
        }
    }

    private fun extractSharedText(intent: Intent): String {
        if (intent.action != Intent.ACTION_SEND) return ""
        val text = intent.getStringExtra(Intent.EXTRA_TEXT)?.trim().orEmpty()
        val subject = intent.getStringExtra(Intent.EXTRA_SUBJECT)?.trim().orEmpty()
        return when {
            text.isEmpty() -> subject
            subject.isEmpty() || text.contains(subject) -> text
            else -> "$subject\n\n$text"
        }
    }

    private fun openSettings() {
        startActivity(
            Intent(this, MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
        )
        finish()
    }

    companion object {
        private const val REQUEST_CODE = 1001

        fun createLaunchIntent(context: Context): PendingIntent {
            val intent = Intent(context, QuickComposeActivity::class.java).apply {
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP)
            }
            return PendingIntent.getActivity(
                context,
                REQUEST_CODE,
                intent,
                PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
            )
        }
    }
}

package dev.jamesnicholls.memoswidget

import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.provider.OpenableColumns
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.runtime.getValue
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import dev.jamesnicholls.memoswidget.ui.MemosTheme
import dev.jamesnicholls.memoswidget.ui.QuickComposeScreen
import dev.jamesnicholls.memoswidget.widget.SendMemoWorker
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import dev.jamesnicholls.memoswidget.data.MemosSettings
import dev.jamesnicholls.memoswidget.net.MemosApiException
import dev.jamesnicholls.memoswidget.net.UrlUtil
import java.io.File
import java.util.UUID

class QuickComposeViewModel(private val container: AppContainer) : ViewModel() {

    val settings: StateFlow<MemosSettings?> = container.settingsRepository.settings
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)

    val pendingAttachments = MutableStateFlow<List<SendMemoWorker.PendingAttachment>>(emptyList())
    private var intakeDone = false

    sealed interface SendState {
        data object Idle : SendState
        data object Sending : SendState
        data object Done : SendState
        data object HandedToBackground : SendState
        data class Error(val message: String) : SendState
    }

    private val _sendState = MutableStateFlow<SendState>(SendState.Idle)
    val sendState: StateFlow<SendState> = _sendState

    fun send(content: String) {
        val current = settings.value ?: return
        _sendState.value = SendState.Sending
        viewModelScope.launch {
            _sendState.value = try {
                val baseUrl = UrlUtil.normaliseBaseUrl(current.serverUrl)
                val created = container.memosApi.createMemo(
                    baseUrl = baseUrl,
                    accessToken = current.accessToken,
                    content = content.trim(),
                    visibilityWireName = current.defaultVisibility.wireName,
                )
                // Show the new memo in the widget immediately, then reconcile with the server.
                container.widgetRefresher.onMemoSent(created.name, content.trim())
                container.widgetRefresher.requestRefresh()
                SendState.Done
            } catch (e: MemosApiException) {
                SendState.Error(e.message ?: "Sending failed")
            } catch (e: Exception) {
                SendState.Error("Sending failed: ${e.message}")
            }
        }
    }

    fun sendWithAttachments(content: String) {
        val current = settings.value ?: return
        val attachments = pendingAttachments.value
        if (attachments.isEmpty()) {
            send(content)
            return
        }
        SendMemoWorker.enqueue(
            context = container.appContext,
            content = content.trim(),
            visibilityWireName = current.defaultVisibility.wireName,
            attachments = attachments,
        )
        _sendState.value = SendState.HandedToBackground
    }

    fun attachSharedUris(uris: List<Uri>, resolver: android.content.ContentResolver) {
        if (intakeDone || uris.isEmpty()) return
        intakeDone = true
        viewModelScope.launch {
            val copied = withContext(Dispatchers.IO) {
                uris.mapNotNull { uri -> runCatching { copyToCache(uri, resolver) }.getOrNull() }
            }
            if (copied.size != uris.size) {
                Toast.makeText(
                    container.appContext,
                    container.appContext.getString(R.string.compose_attachment_copy_failed),
                    Toast.LENGTH_LONG,
                ).show()
            }
            pendingAttachments.value = copied
        }
    }

    private fun copyToCache(
        uri: Uri,
        resolver: android.content.ContentResolver,
    ): SendMemoWorker.PendingAttachment? {
        val name = queryDisplayName(uri, resolver) ?: "attachment-${UUID.randomUUID()}"
        val safeName = name.replace(Regex("[^A-Za-z0-9._ ()-]"), "_").ifEmpty { "attachment" }
        val mime = resolver.getType(uri) ?: guessMime(safeName) ?: "application/octet-stream"
        val dir = File(container.appContext.cacheDir, "attachments").apply { mkdirs() }
        val target = File(dir, "${UUID.randomUUID()}_$safeName")
        resolver.openInputStream(uri)?.use { input ->
            target.outputStream().use { output -> input.copyTo(output) }
        } ?: return null
        return SendMemoWorker.PendingAttachment(
            path = target.absolutePath,
            filename = safeName,
            mimeType = mime,
        )
    }

    private fun queryDisplayName(uri: Uri, resolver: android.content.ContentResolver): String? =
        resolver.query(uri, arrayOf(OpenableColumns.DISPLAY_NAME), null, null, null)?.use { cursor ->
            val index = cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME)
            if (index >= 0 && cursor.moveToFirst()) cursor.getString(index) else null
        }

    private fun guessMime(filename: String): String? =
        android.webkit.MimeTypeMap.getSingleton()
            .getMimeTypeFromExtension(filename.substringAfterLast('.', "").lowercase())

    companion object {
        fun factory(container: AppContainer): ViewModelProvider.Factory = viewModelFactory {
            initializer { QuickComposeViewModel(container) }
        }
    }
}

class QuickComposeActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        val initialText = extractSharedText(intent)
        val sharedStreams = extractSharedStreams(intent)

        val container = (application as MemosApp).container
        setContent {
            MemosTheme {
                val viewModel: QuickComposeViewModel = viewModel(
                    factory = QuickComposeViewModel.factory(container),
                )
                val settings by viewModel.settings.collectAsStateWithLifecycle()
                val sendState by viewModel.sendState.collectAsStateWithLifecycle()
                val attachments by viewModel.pendingAttachments.collectAsStateWithLifecycle()
                androidx.compose.runtime.LaunchedEffect(sharedStreams) {
                    if (sharedStreams.isNotEmpty()) {
                        viewModel.attachSharedUris(sharedStreams, contentResolver)
                    }
                }
                QuickComposeScreen(
                    initialText = initialText,
                    settings = settings,
                    sendState = sendState,
                    attachmentCount = attachments.size,
                    onSend = { text ->
                        if (attachments.isEmpty()) viewModel.send(text) else viewModel.sendWithAttachments(text)
                    },
                    onSent = ::finish,
                    onOpenSettings = ::openSettings,
                )
            }
        }
    }

    private fun extractSharedText(intent: Intent): String {
        if (intent.action != Intent.ACTION_SEND && intent.action != Intent.ACTION_SEND_MULTIPLE) return ""
        val text = intent.getStringExtra(Intent.EXTRA_TEXT)?.trim().orEmpty()
        val subject = intent.getStringExtra(Intent.EXTRA_SUBJECT)?.trim().orEmpty()
        return when {
            text.isEmpty() -> subject
            subject.isEmpty() || text.contains(subject) -> text
            else -> "$subject\n\n$text"
        }
    }

    private fun extractSharedStreams(intent: Intent): List<Uri> = when (intent.action) {
        Intent.ACTION_SEND -> listOfNotNull(
            androidx.core.content.IntentCompat.getParcelableExtra(
                intent,
                Intent.EXTRA_STREAM,
                Uri::class.java,
            ),
        )
        Intent.ACTION_SEND_MULTIPLE ->
            androidx.core.content.IntentCompat.getParcelableArrayListExtra(
                intent,
                Intent.EXTRA_STREAM,
                Uri::class.java,
            ).orEmpty()
        else -> emptyList()
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

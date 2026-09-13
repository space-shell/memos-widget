package dev.jamesnicholls.memoswidget

import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.speech.RecognitionListener
import android.speech.RecognizerIntent
import android.speech.SpeechRecognizer
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.runtime.getValue
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import dev.jamesnicholls.memoswidget.ui.MemosTheme
import dev.jamesnicholls.memoswidget.ui.QuickComposeScreen
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow

class QuickComposeActivity : ComponentActivity() {

    sealed interface VoiceState {
        data object Idle : VoiceState
        data object Listening : VoiceState
        data object Unavailable : VoiceState
    }

    private val _voiceState = MutableStateFlow<VoiceState>(VoiceState.Idle)
    val voiceState: StateFlow<VoiceState> = _voiceState

    private val _voiceResults = MutableSharedFlow<String>(extraBufferCapacity = 8)
    val voiceResults: Flow<String> = _voiceResults

    private var speechRecognizer: SpeechRecognizer? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        val initialText = extractSharedText(intent)
        val autoStartVoice = intent.getBooleanExtra(EXTRA_START_VOICE, false)

        val container = (application as MemosApp).container
        setContent {
            MemosTheme {
                val viewModel: QuickComposeViewModel = viewModel(
                    factory = QuickComposeViewModel.factory(container),
                )
                val settings by viewModel.settings.collectAsStateWithLifecycle()
                val sendState by viewModel.sendState.collectAsStateWithLifecycle()
                val voiceState by voiceState.collectAsStateWithLifecycle()
                QuickComposeScreen(
                    initialText = initialText,
                    autoStartVoice = autoStartVoice,
                    settings = settings,
                    sendState = sendState,
                    voiceState = voiceState,
                    onMicClick = ::startVoiceInput,
                    voiceResults = voiceResults,
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

    fun startVoiceInput() {
        if (!SpeechRecognizer.isRecognitionAvailable(this)) {
            _voiceState.value = VoiceState.Unavailable
            Toast.makeText(this, R.string.compose_speech_unavailable, Toast.LENGTH_SHORT).show()
            return
        }
        speechRecognizer?.destroy()
        speechRecognizer = SpeechRecognizer.createSpeechRecognizer(this).also { recognizer ->
            recognizer.setRecognitionListener(object : RecognitionListener {
                override fun onReadyForSpeech(params: Bundle?) {
                    _voiceState.value = VoiceState.Listening
                }

                override fun onBeginningOfSpeech() = Unit
                override fun onRmsChanged(rmsdB: Float) = Unit
                override fun onBufferReceived(buffer: ByteArray?) = Unit
                override fun onEndOfSpeech() = Unit

                override fun onError(error: Int) {
                    _voiceState.value = VoiceState.Idle
                    if (error != SpeechRecognizer.ERROR_NO_MATCH && error != SpeechRecognizer.ERROR_SPEECH_TIMEOUT) {
                        Toast.makeText(
                            this@QuickComposeActivity,
                            getString(R.string.compose_speech_error, error),
                            Toast.LENGTH_SHORT,
                        ).show()
                    }
                }

                override fun onResults(results: Bundle?) {
                    _voiceState.value = VoiceState.Idle
                    val recognized = results
                        ?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)
                        ?.firstOrNull()
                    if (!recognized.isNullOrBlank()) {
                        _voiceResults.tryEmit(recognized)
                    }
                }

                override fun onPartialResults(partialResults: Bundle?) = Unit
                override fun onEvent(eventType: Int, params: Bundle?) = Unit
            })
        }
        val listenIntent = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
            putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
            putExtra(RecognizerIntent.EXTRA_PARTIAL_RESULTS, true)
        }
        speechRecognizer?.startListening(listenIntent)
    }

    private fun openSettings() {
        startActivity(
            Intent(this, MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
        )
        finish()
    }

    override fun onDestroy() {
        speechRecognizer?.destroy()
        speechRecognizer = null
        super.onDestroy()
    }

    companion object {
        const val EXTRA_START_VOICE = "dev.jamesnicholls.memoswidget.extra.START_VOICE"

        fun createLaunchIntent(context: Context, startVoice: Boolean): PendingIntent {
            val intent = Intent(context, QuickComposeActivity::class.java).apply {
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP)
                putExtra(EXTRA_START_VOICE, startVoice)
            }
            return PendingIntent.getActivity(
                context,
                if (startVoice) REQUEST_VOICE else REQUEST_PLAIN,
                intent,
                PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
            )
        }

        private const val REQUEST_PLAIN = 1001
        private const val REQUEST_VOICE = 1002
    }
}

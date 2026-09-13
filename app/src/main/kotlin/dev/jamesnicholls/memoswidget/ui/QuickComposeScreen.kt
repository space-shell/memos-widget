package dev.jamesnicholls.memoswidget.ui

import android.widget.Toast
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material.icons.filled.Public
import androidx.compose.material.icons.filled.Shield
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import dev.jamesnicholls.memoswidget.QuickComposeViewModel
import dev.jamesnicholls.memoswidget.R
import dev.jamesnicholls.memoswidget.data.MemosSettings
import dev.jamesnicholls.memoswidget.data.MemoVisibility
import kotlinx.coroutines.flow.Flow

@Composable
fun QuickComposeScreen(
    initialText: String,
    autoStartVoice: Boolean,
    settings: MemosSettings?,
    sendState: QuickComposeViewModel.SendState,
    voiceState: dev.jamesnicholls.memoswidget.QuickComposeActivity.VoiceState,
    onMicClick: () -> Unit,
    voiceResults: Flow<String>,
    onSend: (String, MemoVisibility) -> Unit,
    onSent: () -> Unit,
    onOpenSettings: () -> Unit,
) {
    val context = LocalContext.current
    var text by rememberSaveable(initialText) { mutableStateOf(initialText) }
    var visibility by rememberSaveable {
        mutableStateOf(settings?.defaultVisibility ?: MemoVisibility.PRIVATE)
    }

    val focusRequester = remember { FocusRequester() }

    LaunchedEffect(Unit) {
        voiceResults.collect { recognized ->
            text = if (text.isBlank()) recognized else "$text ${recognized.trim()}"
        }
    }

    LaunchedEffect(autoStartVoice) {
        if (autoStartVoice) onMicClick()
    }

    LaunchedEffect(sendState) {
        if (sendState is QuickComposeViewModel.SendState.Done) {
            Toast.makeText(context, context.getString(R.string.compose_sent), Toast.LENGTH_SHORT).show()
            onSent()
        }
    }

    Column(
        modifier = Modifier.padding(horizontal = 20.dp, vertical = 20.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        OutlinedTextField(
            value = text,
            onValueChange = { text = it },
            modifier = Modifier
                .fillMaxWidth()
                .focusRequester(focusRequester),
            minLines = 3,
            maxLines = 10,
            placeholder = { Text(stringResource(R.string.compose_hint)) },
        )

        LaunchedEffect(Unit) {
            if (!autoStartVoice) runCatching { focusRequester.requestFocus() }
        }

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(4.dp),
            verticalAlignment = androidx.compose.ui.Alignment.CenterVertically,
        ) {
            IconButton(onClick = onMicClick) {
                Icon(
                    imageVector = Icons.Filled.Mic,
                    contentDescription = stringResource(R.string.compose_mic),
                    tint = if (voiceState == dev.jamesnicholls.memoswidget.QuickComposeActivity.VoiceState.Listening) {
                        MaterialTheme.colorScheme.error
                    } else {
                        MaterialTheme.colorScheme.onSurfaceVariant
                    },
                )
            }

            TextButton(onClick = {
                visibility = when (visibility) {
                    MemoVisibility.PRIVATE -> MemoVisibility.PROTECTED
                    MemoVisibility.PROTECTED -> MemoVisibility.PUBLIC
                    MemoVisibility.PUBLIC -> MemoVisibility.PRIVATE
                }
            }) {
                Icon(
                    imageVector = when (visibility) {
                        MemoVisibility.PRIVATE -> Icons.Filled.Lock
                        MemoVisibility.PROTECTED -> Icons.Filled.Shield
                        MemoVisibility.PUBLIC -> Icons.Filled.Public
                    },
                    contentDescription = null,
                )
                Spacer(Modifier.width(6.dp))
                Text(visibility.wireName)
            }

            Spacer(Modifier.weight(1f))

            when {
                settings == null -> CircularProgressIndicator(
                    modifier = Modifier.height(24.dp).width(24.dp),
                    strokeWidth = 2.dp,
                )
                !settings.isConfigured -> TextButton(onClick = onOpenSettings) {
                    Text(stringResource(R.string.compose_configure))
                }
                else -> Button(
                    onClick = { onSend(text, visibility) },
                    enabled = text.isNotBlank() && sendState != QuickComposeViewModel.SendState.Sending,
                ) {
                    if (sendState is QuickComposeViewModel.SendState.Sending) {
                        CircularProgressIndicator(
                            modifier = Modifier.height(18.dp).width(18.dp),
                            strokeWidth = 2.dp,
                            color = MaterialTheme.colorScheme.onPrimary,
                        )
                        Spacer(Modifier.width(8.dp))
                    } else {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.Send,
                            contentDescription = null,
                        )
                        Spacer(Modifier.width(8.dp))
                    }
                    Text(stringResource(R.string.compose_send))
                }
            }
        }

        val error = sendState as? QuickComposeViewModel.SendState.Error
        if (error != null) {
            Text(
                text = error.message,
                color = MaterialTheme.colorScheme.error,
                style = MaterialTheme.typography.bodySmall,
            )
        }

        if (voiceState == dev.jamesnicholls.memoswidget.QuickComposeActivity.VoiceState.Listening) {
            Text(
                text = stringResource(R.string.compose_listening),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

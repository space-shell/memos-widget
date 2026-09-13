package dev.jamesnicholls.memoswidget

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import dev.jamesnicholls.memoswidget.data.MemosSettings
import dev.jamesnicholls.memoswidget.data.MemoVisibility
import dev.jamesnicholls.memoswidget.net.MemosApiException
import dev.jamesnicholls.memoswidget.net.UrlUtil
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

class QuickComposeViewModel(private val container: AppContainer) : ViewModel() {

    val settings: StateFlow<MemosSettings?> = container.settingsRepository.settings
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)

    sealed interface SendState {
        data object Idle : SendState
        data object Sending : SendState
        data object Done : SendState
        data class Error(val message: String) : SendState
    }

    private val _sendState = kotlinx.coroutines.flow.MutableStateFlow<SendState>(SendState.Idle)
    val sendState: StateFlow<SendState> = _sendState

    fun send(content: String, visibility: MemoVisibility) {
        val current = settings.value ?: return
        _sendState.value = SendState.Sending
        viewModelScope.launch {
            _sendState.value = try {
                val baseUrl = UrlUtil.normaliseBaseUrl(current.serverUrl)
                container.memosApi.createMemo(
                    baseUrl = baseUrl,
                    accessToken = current.accessToken,
                    content = content.trim(),
                    visibilityWireName = visibility.wireName,
                )
                SendState.Done
            } catch (e: MemosApiException) {
                SendState.Error(e.message ?: "Sending failed")
            } catch (e: Exception) {
                SendState.Error("Sending failed: ${e.message}")
            }
        }
    }

    companion object {
        fun factory(container: AppContainer): ViewModelProvider.Factory = viewModelFactory {
            initializer { QuickComposeViewModel(container) }
        }
    }
}

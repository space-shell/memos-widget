package dev.jamesnicholls.memoswidget

import android.os.Bundle
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
import dev.jamesnicholls.memoswidget.data.MemosSettings
import dev.jamesnicholls.memoswidget.data.MemoVisibility
import dev.jamesnicholls.memoswidget.net.ConnectionInfo
import dev.jamesnicholls.memoswidget.net.MemosApiException
import dev.jamesnicholls.memoswidget.net.UrlUtil
import dev.jamesnicholls.memoswidget.ui.MemosTheme
import dev.jamesnicholls.memoswidget.ui.SettingsScreen
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

class SettingsViewModel(private val container: AppContainer) : ViewModel() {

    val settings: StateFlow<MemosSettings?> = container.settingsRepository.settings
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)

    sealed interface TestState {
        data object Idle : TestState
        data object Testing : TestState
        data class Success(val info: ConnectionInfo) : TestState
        data class Error(val message: String) : TestState
    }

    private val _testState = MutableStateFlow<TestState>(TestState.Idle)
    val testState: StateFlow<TestState> = _testState.asStateFlow()

    fun setServerUrl(value: String) {
        viewModelScope.launch { container.settingsRepository.updateServerUrl(value) }
    }

    fun setAccessToken(value: String) {
        viewModelScope.launch { container.settingsRepository.updateAccessToken(value) }
    }

    fun setVisibility(value: MemoVisibility) {
        viewModelScope.launch { container.settingsRepository.updateDefaultVisibility(value) }
    }

    fun testConnection() {
        val current = settings.value ?: return
        viewModelScope.launch {
            _testState.value = TestState.Testing
            _testState.value = try {
                val baseUrl = UrlUtil.normaliseBaseUrl(current.serverUrl)
                if (baseUrl.isEmpty()) {
                    TestState.Error("Enter a server address first")
                } else {
                    TestState.Success(container.memosApi.validateConnection(baseUrl, current.accessToken))
                }
            } catch (e: MemosApiException) {
                TestState.Error(e.message ?: "Connection failed")
            } catch (e: Exception) {
                TestState.Error("Connection failed: ${e.message}")
            }
        }
    }

    companion object {
        fun factory(container: AppContainer): ViewModelProvider.Factory = viewModelFactory {
            initializer { SettingsViewModel(container) }
        }
    }
}

class MainActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val container = (application as MemosApp).container
        setContent {
            MemosTheme {
                val viewModel: SettingsViewModel = viewModel(factory = SettingsViewModel.factory(container))
                val settings by viewModel.settings.collectAsStateWithLifecycle()
                val testState by viewModel.testState.collectAsStateWithLifecycle()
                SettingsScreen(
                    settings = settings,
                    testState = testState,
                    onServerUrlChange = viewModel::setServerUrl,
                    onAccessTokenChange = viewModel::setAccessToken,
                    onVisibilityChange = viewModel::setVisibility,
                    onTestConnection = viewModel::testConnection,
                )
            }
        }
    }
}

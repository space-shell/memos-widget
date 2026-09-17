package dev.jamesnicholls.memoswidget.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Public
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.Shield
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material.icons.filled.VisibilityOff
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExposedDropdownMenuBox
import androidx.compose.material3.ExposedDropdownMenuDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.MenuAnchorType
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.dp
import dev.jamesnicholls.memoswidget.R
import dev.jamesnicholls.memoswidget.SettingsViewModel
import dev.jamesnicholls.memoswidget.data.MemosSettings
import dev.jamesnicholls.memoswidget.data.MemoVisibility
import dev.jamesnicholls.memoswidget.net.UrlUtil

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(
    settings: MemosSettings?,
    testState: SettingsViewModel.TestState,
    onServerUrlChange: (String) -> Unit,
    onAccessTokenChange: (String) -> Unit,
    onVisibilityChange: (MemoVisibility) -> Unit,
    onTestConnection: () -> Unit,
) {
    Scaffold(
        topBar = { TopAppBar(title = { Text(stringResource(R.string.settings_screen_title)) }) },
    ) { padding ->
        if (settings == null) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(padding),
                verticalArrangement = Arrangement.Center,
                horizontalAlignment = androidx.compose.ui.Alignment.CenterHorizontally,
            ) {
                CircularProgressIndicator()
            }
            return@Scaffold
        }

        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            OutlinedTextField(
                value = settings.serverUrl,
                onValueChange = onServerUrlChange,
                modifier = Modifier.fillMaxWidth(),
                singleLine = true,
                label = { Text(stringResource(R.string.settings_server_url_label)) },
                supportingText = {
                    val normalised = UrlUtil.normaliseBaseUrl(settings.serverUrl)
                    Text(
                        if (settings.serverUrl.isBlank()) {
                            stringResource(R.string.settings_server_url_empty_support)
                        } else {
                            stringResource(R.string.settings_server_url_will_use, normalised)
                        }
                    )
                },
            )

            var tokenVisible by rememberSaveable { mutableStateOf(false) }
            OutlinedTextField(
                value = settings.accessToken,
                onValueChange = onAccessTokenChange,
                modifier = Modifier.fillMaxWidth(),
                singleLine = true,
                label = { Text(stringResource(R.string.settings_access_token_label)) },
                visualTransformation = if (tokenVisible) VisualTransformation.None else PasswordVisualTransformation(),
                trailingIcon = {
                    IconButton(onClick = { tokenVisible = !tokenVisible }) {
                        Icon(
                            imageVector = if (tokenVisible) Icons.Filled.VisibilityOff else Icons.Filled.Visibility,
                            contentDescription = if (tokenVisible) {
                                stringResource(R.string.settings_token_hide)
                            } else {
                                stringResource(R.string.settings_token_show)
                            },
                        )
                    }
                },
                supportingText = {
                    Text(stringResource(R.string.settings_access_token_support))
                },
            )

            VisibilityField(
                selected = settings.defaultVisibility,
                onSelected = onVisibilityChange,
            )

            Button(
                onClick = onTestConnection,
                enabled = settings.serverUrl.isNotBlank(),
                modifier = Modifier.fillMaxWidth(),
            ) {
                Text(
                    if (testState is SettingsViewModel.TestState.Testing) {
                        stringResource(R.string.settings_testing)
                    } else {
                        stringResource(R.string.settings_test_connection)
                    }
                )
            }

            when (val state = testState) {
                is SettingsViewModel.TestState.Success -> {
                    val version = state.info.serverVersion
                    val who = state.info.displayName ?: state.info.username
                    Text(
                        text = when {
                            version != null && who != null ->
                                stringResource(R.string.settings_connected_user, version, who)
                            version != null ->
                                stringResource(R.string.settings_connected_version, version)
                            else -> stringResource(R.string.settings_connected)
                        },
                        color = MaterialTheme.colorScheme.primary,
                    )
                }
                is SettingsViewModel.TestState.Error -> Text(
                    text = state.message,
                    color = MaterialTheme.colorScheme.error,
                )
                else -> Unit
            }

            Text(
                text = stringResource(R.string.settings_usage_hint),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )

            Spacer(Modifier.height(24.dp))

            val context = androidx.compose.ui.platform.LocalContext.current
            val version = remember {
                runCatching { context.packageManager.getPackageInfo(context.packageName, 0).versionName }
                    .getOrNull()
            }
            Text(
                text = stringResource(
                    R.string.settings_version,
                    version ?: "?",
                ),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.fillMaxWidth(),
                textAlign = androidx.compose.ui.text.style.TextAlign.Center,
            )
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun VisibilityField(
    selected: MemoVisibility,
    onSelected: (MemoVisibility) -> Unit,
) {
    var expanded by remember { mutableStateOf(false) }

    ExposedDropdownMenuBox(
        expanded = expanded,
        onExpandedChange = { expanded = it },
    ) {
        OutlinedTextField(
            value = selected.wireName,
            onValueChange = {},
            modifier = Modifier
                .fillMaxWidth()
                .menuAnchor(MenuAnchorType.PrimaryNotEditable),
            readOnly = true,
            singleLine = true,
            label = { Text(stringResource(R.string.settings_visibility_label)) },
            leadingIcon = {
                Icon(
                    imageVector = when (selected) {
                        MemoVisibility.PRIVATE -> Icons.Filled.Lock
                        MemoVisibility.PROTECTED -> Icons.Filled.Shield
                        MemoVisibility.PUBLIC -> Icons.Filled.Public
                    },
                    contentDescription = null,
                )
            },
            trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = expanded) },
        )
        ExposedDropdownMenu(
            expanded = expanded,
            onDismissRequest = { expanded = false },
        ) {
            MemoVisibility.entries.forEach { option ->
                DropdownMenuItem(
                    text = { Text(option.wireName) },
                    onClick = {
                        onSelected(option)
                        expanded = false
                    },
                )
            }
        }
    }
}

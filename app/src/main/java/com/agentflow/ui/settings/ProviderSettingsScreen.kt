package com.agentflow.ui.settings

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.dp
import com.agentflow.data.security.SecureApiKeyStore
import com.agentflow.domain.provider.ProviderManager
import com.agentflow.domain.provider.ProviderResult
import com.agentflow.domain.provider.ProviderType
import kotlinx.coroutines.launch

private fun mask(key: String): String {
    val trimmed = key.trim()
    if (trimmed.length < 4) return "••••"
    return "••••••••${trimmed.takeLast(4)}"
}

@Composable
fun ProviderSettingsScreen(
    keyStore: SecureApiKeyStore,
    manager: ProviderManager,
) {
    val scope = rememberCoroutineScope()
    Column(
        Modifier.padding(16.dp).verticalScroll(rememberScrollState()),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        Text("AI Providers")
        Text("One key serves every Agent. Keys stay in Android Keystore, never Room.")
        ProviderType.entries.forEach { type ->
            ProviderCard(type, keyStore, manager, scope)
        }
    }
}

@Composable
private fun ProviderCard(
    type: ProviderType,
    keyStore: SecureApiKeyStore,
    manager: ProviderManager,
    scope: kotlinx.coroutines.CoroutineScope,
) {
    var configured by remember { mutableStateOf(false) }
    var masked by remember { mutableStateOf<String?>(null) }
    var draft by remember { mutableStateOf("") }
    var visible by remember { mutableStateOf(false) }
    var status by remember { mutableStateOf("") }
    LaunchedEffect(type) {
        configured = keyStore.hasApiKey(type)
        masked = if (configured) {
            keyStore.getApiKey(type)?.let(::mask)
        } else null
        draft = ""
    }
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text(type.name)
        Text(if (configured) "Configured ${masked ?: "••••"}" else "Not configured")
        OutlinedTextField(
            value = draft,
            onValueChange = { draft = it },
            modifier = Modifier.fillMaxWidth(),
            label = { Text("API key") },
            visualTransformation = if (visible) VisualTransformation.None else PasswordVisualTransformation(),
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
        )
        Row {
            TextButton(onClick = { visible = !visible }) { Text(if (visible) "Hide" else "Show") }
            Button(onClick = {
                scope.launch {
                    runCatching { keyStore.saveApiKey(type, draft) }
                        .onSuccess {
                            configured = true
                            masked = mask(draft)
                            draft = ""
                            visible = false
                            status = "Saved"
                        }
                        .onFailure { status = it.message ?: "Save failed" }
                }
            }) { Text(if (configured) "Replace" else "Save") }
            TextButton(onClick = {
                scope.launch {
                    keyStore.deleteApiKey(type)
                    configured = false
                    masked = null
                    draft = ""
                    status = "Removed"
                }
            }) { Text("Remove") }
        }
        Button(onClick = {
            scope.launch {
                status = when (val result = manager.testConnection(type)) {
                    is ProviderResult.Success -> "OK ${result.value.model ?: type.name} ${result.value.latencyMs}ms"
                    is ProviderResult.Failure -> result.error.type.name
                }
            }
        }) { Text("Test connection") }
        if (status.isNotBlank()) Text(status)
    }
}

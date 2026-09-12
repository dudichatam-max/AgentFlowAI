package com.agentflow.ui.chat

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import com.agentflow.domain.chat.ChatMessage
import com.agentflow.domain.chat.ChatRole

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AgentChatScreen(
    viewModel: ChatViewModel,
    onBack: () -> Unit,
) {
    val state by viewModel.state.collectAsState()
    val listState = rememberLazyListState()
    LaunchedEffect(state.messages.size, state.isStreaming) {
        val last = state.messages.lastIndex
        if (last >= 0) {
            val nearBottom = snapshotFlow { listState.layoutInfo.visibleItemsInfo.lastOrNull()?.index }.let { last }
            if (nearBottom >= last - 2) {
                listState.animateScrollToItem(last)
            }
        }
    }
    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Column {
                        Text(state.agent?.name ?: "Chat")
                        Text(
                            state.agent?.let { "${it.providerId.name} · ${it.modelId}" } ?: "",
                            style = MaterialTheme.typography.bodySmall,
                        )
                    }
                },
                navigationIcon = { TextButton(onClick = onBack) { Text("Back") } },
            )
        },
    ) { padding ->
        Column(Modifier.fillMaxSize().padding(padding)) {
            if (state.error != null) {
                Text(state.error ?: "", color = MaterialTheme.colorScheme.error, modifier = Modifier.padding(12.dp).testTag("chatError"))
            }
            LazyColumn(
                modifier = Modifier.weight(1f).fillMaxWidth().testTag("chatMessages"),
                state = listState,
                verticalArrangement = Arrangement.spacedBy(8.dp),
                contentPadding = androidx.compose.foundation.layout.PaddingValues(12.dp),
            ) {
                items(state.messages, key = { it.id }) { message ->
                    ChatMessageBubble(
                        message = message,
                        onRegenerate = { viewModel.regenerate(message.id) },
                        onBookmark = { viewModel.toggleBookmark(message.id) },
                    )
                }
            }
            Row(Modifier.fillMaxWidth().padding(8.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedTextField(
                    value = state.inputText,
                    onValueChange = viewModel::onInput,
                    modifier = Modifier.weight(1f).testTag("chatInput"),
                    placeholder = { Text("Message…") },
                    maxLines = 5,
                )
                if (state.isStreaming) {
                    Button(onClick = viewModel::stop, modifier = Modifier.testTag("chatStop")) { Text("Stop") }
                } else {
                    Button(
                        onClick = viewModel::send,
                        enabled = state.canSend,
                        modifier = Modifier.testTag("chatSend"),
                    ) { Text("Send") }
                }
            }
        }
    }
}

@Composable
fun ChatMessageBubble(
    message: ChatMessage,
    onRegenerate: () -> Unit,
    onBookmark: () -> Unit,
) {
    val isUser = message.role == ChatRole.USER
    Column(Modifier.fillMaxWidth().padding(horizontal = 4.dp)) {
        Text(
            if (isUser) "You" else "Agent · ${message.status.name}",
            style = MaterialTheme.typography.labelSmall,
        )
        Text(message.content.ifBlank { if (message.status.name == "STREAMING") "…" else "" })
        if (!isUser) {
            Row {
                TextButton(onClick = onRegenerate) { Text("Regenerate") }
                TextButton(onClick = onBookmark) { Text(if (message.bookmarked) "Saved" else "Save") }
            }
        }
    }
}

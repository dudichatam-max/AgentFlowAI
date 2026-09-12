package com.agentflow.ui.chat

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import com.agentflow.domain.chat.ChatSession
import kotlinx.coroutines.flow.StateFlow

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AgentChatSessionsScreen(
    agentName: String,
    sessions: StateFlow<List<ChatSession>>,
    onOpen: (String) -> Unit,
    onNew: () -> Unit,
    onBack: () -> Unit,
    onArchive: (String) -> Unit,
    onDelete: (String) -> Unit,
) {
    val items by sessions.collectAsState()
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("$agentName chats") },
                navigationIcon = { TextButton(onClick = onBack) { Text("Back") } },
            )
        },
        floatingActionButton = {
            FloatingActionButton(onClick = onNew, modifier = Modifier.testTag("newChat")) { Text("+") }
        },
    ) { padding ->
        LazyColumn(Modifier.padding(padding).testTag("sessionList")) {
            items(items, key = { it.id }) { session ->
                Text(
                    "${session.title}  ·  ${session.status.name}",
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable { onOpen(session.id) }
                        .padding(16.dp),
                )
                TextButton(onClick = { onArchive(session.id) }) { Text("Archive") }
                TextButton(onClick = { onDelete(session.id) }) { Text("Delete") }
            }
        }
    }
}

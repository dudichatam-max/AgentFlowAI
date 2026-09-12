package com.agentflow.ui.settings

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.agentflow.data.repository.AgentRepository
import com.agentflow.data.repository.ReferenceRepository
import com.agentflow.domain.agent.AgentConnectionTester
import com.agentflow.domain.reference.ReferenceManager
import kotlinx.coroutines.launch

@Composable
fun AgentDetailScreen(
    agentId: String,
    repository: AgentRepository,
    tester: AgentConnectionTester,
    referenceRepository: ReferenceRepository,
    referenceManager: ReferenceManager,
    onBack: () -> Unit,
) {
    val scope = rememberCoroutineScope()
    val agent by repository.observeAgent(agentId).collectAsState(initial = null)

    Column(
        modifier = Modifier.padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        TextButton(onClick = onBack) {
            Text("Back")
        }

        val current = agent
        if (current == null) {
            Text("Agent not found")
            return@Column
        }

        Text(current.name)
        Text("${current.role} · ${current.providerId.name} · ${current.modelId}")

        AgentEditor(
            agent = current,
            repository = repository,
            tester = tester,
            referenceRepository = referenceRepository,
            referenceManager = referenceManager,
            scope = scope,
        )
    }
}

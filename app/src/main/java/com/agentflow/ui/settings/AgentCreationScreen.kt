package com.agentflow.ui.settings

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.agentflow.data.repository.AgentRepository
import com.agentflow.domain.agent.AgentLimits
import com.agentflow.domain.agent.DefaultAgentBlueprint
import com.agentflow.domain.agent.DefaultAgentFactory
import com.agentflow.domain.model.Agent
import com.agentflow.domain.model.AgentStatus
import com.agentflow.domain.model.Ids
import com.agentflow.domain.model.ProviderId
import kotlinx.coroutines.launch

@Composable
fun AgentCreationScreen(
    projectId: String,
    repository: AgentRepository,
    onCreated: (String) -> Unit,
    onCancel: () -> Unit,
) {
    val scope = rememberCoroutineScope()
    val defaultBlueprint = remember { DefaultAgentFactory.blueprints.first() }
    var blueprint by remember { mutableStateOf(defaultBlueprint) }
    var name by remember { mutableStateOf(defaultBlueprint.name) }
    var role by remember { mutableStateOf(defaultBlueprint.role) }
    var description by remember { mutableStateOf(defaultBlueprint.description) }
    var error by remember { mutableStateOf<String?>(null) }

    fun applyBlueprint(selected: DefaultAgentBlueprint) {
        blueprint = selected
        name = selected.name
        role = selected.role
        description = selected.description
    }

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .verticalScroll(rememberScrollState())
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Text("Create Agent")
        Text("Start from a specialist template, then configure the agent in its dedicated detail screen.")

        BlueprintPicker(
            selected = blueprint,
            options = DefaultAgentFactory.blueprints,
            onSelected = ::applyBlueprint,
        )

        OutlinedTextField(
            value = name,
            onValueChange = { name = it.take(AgentLimits.MAX_NAME_LENGTH) },
            modifier = Modifier.fillMaxWidth(),
            label = { Text("Name") },
            singleLine = true,
        )
        OutlinedTextField(
            value = role,
            onValueChange = { role = it.take(AgentLimits.MAX_ROLE_LENGTH) },
            modifier = Modifier.fillMaxWidth(),
            label = { Text("Role") },
            singleLine = true,
        )
        OutlinedTextField(
            value = description,
            onValueChange = { description = it.take(AgentLimits.MAX_DESCRIPTION_LENGTH) },
            modifier = Modifier.fillMaxWidth(),
            label = { Text("Description") },
            minLines = 4,
        )

        if (error != null) {
            Text(error!!)
        }

        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            OutlinedButton(onClick = onCancel) {
                Text("Cancel")
            }
            Button(
                onClick = {
                    val trimmedName = name.trim()
                    val trimmedRole = role.trim()
                    val trimmedDescription = description.trim()
                    if (trimmedName.isBlank() || trimmedRole.isBlank()) {
                        error = "Name and role are required"
                        return@Button
                    }

                    scope.launch {
                        try {
                            val seeded = DefaultAgentFactory.create(
                                projectId = projectId,
                                blueprint = blueprint,
                            )
                            val agent = seeded.agent.copy(
                                id = Ids.new(),
                                name = trimmedName,
                                role = trimmedRole,
                                description = trimmedDescription,
                                providerId = ProviderId.GROQ,
                                modelId = "openai/gpt-oss-20b",
                                status = AgentStatus.READY,
                                createdAt = System.currentTimeMillis(),
                                updatedAt = System.currentTimeMillis(),
                            )
                            val rules = seeded.rules.map {
                                it.copy(
                                    id = Ids.new(),
                                    agentId = agent.id,
                                )
                            }
                            repository.createAgent(agent, rules)
                            onCreated(agent.id)
                        } catch (t: Throwable) {
                            error = t.message ?: "Agent creation failed"
                        }
                    }
                },
            ) {
                Text("Create agent")
            }
        }
    }
}

@Composable
private fun BlueprintPicker(
    selected: DefaultAgentBlueprint,
    options: List<DefaultAgentBlueprint>,
    onSelected: (DefaultAgentBlueprint) -> Unit,
) {
    var expanded by remember(selected) { mutableStateOf(false) }

    Column {
        Text("Template")
        OutlinedButton(onClick = { expanded = true }) {
            Text(selected.name)
        }
        DropdownMenu(
            expanded = expanded,
            onDismissRequest = { expanded = false },
        ) {
            options.forEach { option ->
                DropdownMenuItem(
                    text = {
                        Column {
                            Text(option.name)
                            Text(option.role)
                        }
                    },
                    onClick = {
                        expanded = false
                        onSelected(option)
                    },
                )
            }
        }
    }
}

package com.agentflow.ui.settings

import android.provider.OpenableColumns
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.Checkbox
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import com.agentflow.data.repository.AgentRepository
import com.agentflow.data.repository.ReferenceRepository
import com.agentflow.domain.agent.AgentCapability
import com.agentflow.domain.agent.AgentConnectionTester
import com.agentflow.domain.agent.AgentLimits
import com.agentflow.domain.agent.AgentModelCompatibilityValidator
import com.agentflow.domain.agent.DefaultAgentFactory
import com.agentflow.domain.agent.OutputStyle
import com.agentflow.domain.agent.ReasoningLevel
import com.agentflow.domain.agent.Verbosity
import com.agentflow.domain.model.Agent
import com.agentflow.domain.model.AgentRule
import com.agentflow.domain.model.InclusionMode
import com.agentflow.domain.model.Ids
import com.agentflow.domain.model.ProviderId
import com.agentflow.domain.provider.ProviderModelCatalog
import com.agentflow.domain.provider.ProviderType
import com.agentflow.domain.provider.toProviderType
import com.agentflow.domain.reference.ReferenceManager
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch
import java.util.Locale

@Composable
fun AgentSettingsScreen(
    agents: List<Agent>,
    repository: AgentRepository,
    tester: AgentConnectionTester,
    referenceRepository: ReferenceRepository,
    referenceManager: ReferenceManager,
) {
    val scope = rememberCoroutineScope()
    LazyColumn(
        modifier = Modifier.padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        item {
            Text("Agent Configuration")
            Text("Configure identity, behavior, model, capabilities, rules, and references. API keys remain under AI Providers.")
        }
        items(agents, key = { it.id }) { agent ->
            AgentEditor(
                agent = agent,
                repository = repository,
                tester = tester,
                referenceRepository = referenceRepository,
                referenceManager = referenceManager,
                scope = scope,
            )
        }
    }
}

@Composable
internal fun AgentEditor(
    agent: Agent,
    repository: AgentRepository,
    tester: AgentConnectionTester,
    referenceRepository: ReferenceRepository,
    referenceManager: ReferenceManager,
    scope: CoroutineScope,
) {
    val context = LocalContext.current
    var name by remember(agent.id, agent.name) { mutableStateOf(agent.name) }
    var role by remember(agent.id, agent.role) { mutableStateOf(agent.role) }
    var description by remember(agent.id, agent.description) { mutableStateOf(agent.description) }
    var provider by remember(agent.id, agent.providerId) { mutableStateOf(agent.providerId.toProviderType()) }
    var modelId by remember(agent.id, agent.modelId) { mutableStateOf(agent.modelId) }
    var fallbackProvider by remember(agent.id, agent.fallbackProviderId) {
        mutableStateOf(agent.fallbackProviderId?.toProviderType())
    }
    var fallbackModelId by remember(agent.id, agent.fallbackModelId) {
        mutableStateOf(agent.fallbackModelId.orEmpty())
    }
    var temperature by remember(agent.id, agent.temperature) {
        mutableStateOf(agent.temperature.toString())
    }
    var maxTokens by remember(agent.id, agent.maxOutputTokens) {
        mutableStateOf(agent.maxOutputTokens.toString())
    }
    var reasoning by remember(agent.id, agent.reasoningLevelEnum) {
        mutableStateOf(agent.reasoningLevelEnum)
    }
    var outputStyle by remember(agent.id, agent.outputStyle) { mutableStateOf(agent.outputStyle) }
    var verbosity by remember(agent.id, agent.verbosity) { mutableStateOf(agent.verbosity) }
    var exposeUncertainty by remember(agent.id, agent.exposeUncertainty) { mutableStateOf(agent.exposeUncertainty) }
    var includeAssumptions by remember(agent.id, agent.includeAssumptions) { mutableStateOf(agent.includeAssumptions) }
    var includeAlternatives by remember(agent.id, agent.includeAlternatives) { mutableStateOf(agent.includeAlternatives) }
    var capabilities by remember(agent.id, agent.capabilities) { mutableStateOf(agent.capabilities) }
    var freeOnly by remember(agent.id, agent.freeOnly) { mutableStateOf(agent.freeOnly) }
    var status by remember(agent.id, agent.status) { mutableStateOf(agent.status) }
    var message by remember(agent.id) { mutableStateOf("") }
    var newRuleName by remember(agent.id) { mutableStateOf("") }
    var newRuleInstruction by remember(agent.id) { mutableStateOf("") }
    var newRulePriority by remember(agent.id) { mutableStateOf(com.agentflow.domain.model.RulePriority.NORMAL) }
    var addTextOpen by remember(agent.id) { mutableStateOf(false) }
    var textReferenceName by remember(agent.id) { mutableStateOf("") }
    var textReferenceContent by remember(agent.id) { mutableStateOf("") }

    val references by referenceRepository.observeByProject(agent.projectId)
        .collectAsState(initial = emptyList())
    val rules by repository.observeRules(agent.id).collectAsState(initial = emptyList())

    val primaryProviderId = provider.toProviderId()
    val working = agent.copy(
        name = name,
        role = role,
        description = description,
        providerId = primaryProviderId,
        modelId = modelId,
        fallbackProviderId = fallbackProvider?.toProviderId(),
        fallbackModelId = fallbackModelId.ifBlank { null },
        temperature = temperature.toDoubleOrNull() ?: agent.temperature,
        maxOutputTokens = maxTokens.toIntOrNull() ?: agent.maxOutputTokens,
        reasoningLevelEnum = reasoning,
        freeOnly = freeOnly,
        outputStyle = outputStyle,
        verbosity = verbosity,
        exposeUncertainty = exposeUncertainty,
        includeAssumptions = includeAssumptions,
        includeAlternatives = includeAlternatives,
        capabilities = capabilities,
        status = status,
    )
    val primaryOptions = AgentModelCompatibilityValidator.allowedModels(
        agent = working,
        catalog = ProviderModelCatalog.defaults(),
        provider = provider,
        freeOnly = freeOnly,
        includeIncompatible = false,
    )
    val fallbackOptions = fallbackProvider?.let {
        AgentModelCompatibilityValidator.allowedModels(
            agent = working.copy(providerId = it.toProviderId(), modelId = fallbackModelId),
            catalog = ProviderModelCatalog.defaults(),
            provider = it,
            freeOnly = freeOnly,
            includeIncompatible = false,
        )
    }.orEmpty()

    val filePicker = rememberLauncherForActivityResult(
        ActivityResultContracts.OpenMultipleDocuments(),
    ) { uris ->
        scope.launch {
            uris.forEach { uri ->
                val bytes = context.contentResolver.openInputStream(uri)?.use { it.readBytes() }
                if (bytes == null) {
                    message = "Could not read selected file"
                    return@forEach
                }
                val fileName = context.contentResolver.query(
                    uri,
                    arrayOf(OpenableColumns.DISPLAY_NAME),
                    null,
                    null,
                    null,
                )?.use { cursor ->
                    if (cursor.moveToFirst()) cursor.getString(0) else null
                } ?: uri.lastPathSegment ?: "reference"
                try {
                    referenceManager.addFile(
                        projectId = agent.projectId,
                        name = fileName,
                        bytes = bytes,
                        mime = context.contentResolver.getType(uri),
                        agentId = agent.id,
                        inclusion = InclusionMode.RELEVANT,
                    )
                    message = "Imported ${fileName}"
                } catch (t: Throwable) {
                    message = t.message ?: "Reference import failed"
                }
            }
        }
    }

    Card {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            Text(name.ifBlank { "Unnamed agent" })

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
                minLines = 3,
            )

            HorizontalDivider()
            Text("Model")

            ProviderPicker("Primary provider", provider, ProviderType.entries) {
                provider = it
                val first = AgentModelCompatibilityValidator.allowedModels(
                    agent = working.copy(providerId = it.toProviderId()),
                    catalog = ProviderModelCatalog.defaults(),
                    provider = it,
                    freeOnly = freeOnly,
                    includeIncompatible = false,
                ).firstOrNull()?.first
                if (first != null) modelId = first.id
            }
            ModelPicker("Primary model", modelId, primaryOptions.map { it.first.id to it.first.displayName }) {
                modelId = it
            }

            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Text("Free-only")
                Switch(checked = freeOnly, onCheckedChange = { freeOnly = it })
            }

            ProviderPicker(
                label = "Fallback provider",
                selected = fallbackProvider,
                options = listOf(null) + ProviderType.entries,
                optionLabel = { it?.name ?: "None" },
            ) {
                fallbackProvider = it
                if (it == null) {
                    fallbackModelId = ""
                } else {
                    fallbackModelId = AgentModelCompatibilityValidator.allowedModels(
                        agent = working.copy(providerId = it.toProviderId(), modelId = fallbackModelId),
                        catalog = ProviderModelCatalog.defaults(),
                        provider = it,
                        freeOnly = freeOnly,
                        includeIncompatible = false,
                    ).firstOrNull()?.first?.id.orEmpty()
                }
            }
            if (fallbackProvider != null) {
                ModelPicker(
                    "Fallback model",
                    fallbackModelId,
                    fallbackOptions.map { it.first.id to it.first.displayName },
                ) { fallbackModelId = it }
            }

            OutlinedTextField(
                value = temperature,
                onValueChange = { temperature = it.filter { c -> c.isDigit() || c == '.' } },
                modifier = Modifier.fillMaxWidth(),
                label = { Text("Temperature (0.0–2.0)") },
                singleLine = true,
            )
            OutlinedTextField(
                value = maxTokens,
                onValueChange = { maxTokens = it.filter(Char::isDigit) },
                modifier = Modifier.fillMaxWidth(),
                label = { Text("Max output tokens") },
                singleLine = true,
            )

            EnumPicker("Reasoning", reasoning, ReasoningLevel.entries) { reasoning = it }
            EnumPicker("Output style", outputStyle, OutputStyle.entries) { outputStyle = it }
            EnumPicker("Verbosity", verbosity, Verbosity.entries) { verbosity = it }

            ToggleRow("Expose uncertainty", exposeUncertainty) { exposeUncertainty = it }
            ToggleRow("Include assumptions", includeAssumptions) { includeAssumptions = it }
            ToggleRow("Include alternatives", includeAlternatives) { includeAlternatives = it }

            Text("Capabilities")
            AgentCapability.entries.forEach { capability ->
                Row(modifier = Modifier.fillMaxWidth()) {
                    Checkbox(
                        checked = capability in capabilities,
                        onCheckedChange = {
                            capabilities = if (it) capabilities + capability else capabilities - capability
                        },
                    )
                    Text(capability.name.lowercase(Locale.US).replace('_', ' ').replaceFirstChar { it.uppercase() })
                }
            }

            HorizontalDivider()
            Text("Rules")
            rules.forEach { rule ->
                RuleRow(rule, repository, scope)
            }
            OutlinedTextField(
                value = newRuleName,
                onValueChange = { newRuleName = it.take(AgentLimits.MAX_RULE_NAME_LENGTH) },
                modifier = Modifier.fillMaxWidth(),
                label = { Text("New rule name") },
                singleLine = true,
            )
            OutlinedTextField(
                value = newRuleInstruction,
                onValueChange = { newRuleInstruction = it.take(AgentLimits.MAX_RULE_INSTRUCTION_LENGTH) },
                modifier = Modifier.fillMaxWidth(),
                label = { Text("New rule instruction") },
                minLines = 2,
            )
            EnumPicker("Rule priority", newRulePriority, com.agentflow.domain.model.RulePriority.entries) {
                newRulePriority = it
            }
            OutlinedButton(
                onClick = {
                    if (newRuleName.isBlank() || newRuleInstruction.isBlank()) {
                        message = "Rule name and instruction are required"
                    } else {
                        scope.launch {
                            val now = System.currentTimeMillis()
                            repository.createRule(
                                AgentRule(
                                    id = Ids.new(),
                                    agentId = agent.id,
                                    name = newRuleName.trim(),
                                    instruction = newRuleInstruction.trim(),
                                    priority = newRulePriority,
                                    createdAt = now,
                                    updatedAt = now,
                                ),
                            )
                            newRuleName = ""
                            newRuleInstruction = ""
                            message = "Rule added"
                        }
                    }
                },
            ) { Text("Add rule") }

            HorizontalDivider()
            Text("References")
            references.forEach { reference ->
                val assigned = reference.agentId == agent.id
                ReferenceRow(
                    name = reference.name,
                    assigned = assigned,
                    inclusion = reference.inclusionMode,
                    onToggle = {
                        scope.launch {
                            referenceManager.setAgentScope(
                                reference.id,
                                if (assigned) null else agent.id,
                            )
                            message = if (assigned) "Reference detached" else "Reference attached"
                        }
                    },
                    onInclusionChange = { mode ->
                        scope.launch {
                            referenceManager.setInclusion(reference.id, mode)
                            message = "Reference mode updated"
                        }
                    },
                )
            }
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedButton(onClick = { filePicker.launch(arrayOf("*/*")) }) {
                    Text("Add files")
                }
                OutlinedButton(onClick = { addTextOpen = true }) {
                    Text("Add text")
                }
            }

            HorizontalDivider()
            EnumPicker("Status", status, com.agentflow.domain.model.AgentStatus.entries) {
                status = it
            }

            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Button(
                    onClick = {
                        scope.launch {
                            try {
                                val chosen = ProviderModelCatalog.find(provider, modelId)
                                if (chosen == null) {
                                    message = "Unknown primary model"
                                    return@launch
                                }
                                val check = AgentModelCompatibilityValidator.evaluate(working, chosen, freeOnly)
                                if (!check.accepted) {
                                    message = check.reason
                                    return@launch
                                }
                                val parsedTemperature = temperature.toDoubleOrNull()
                                val parsedTokens = maxTokens.toIntOrNull()
                                if (parsedTemperature == null ||
                                    parsedTemperature !in AgentLimits.MIN_TEMPERATURE..AgentLimits.MAX_TEMPERATURE
                                ) {
                                    message = "Temperature must be between 0.0 and 2.0"
                                    return@launch
                                }
                                if (parsedTokens == null ||
                                    parsedTokens !in AgentLimits.MIN_OUTPUT_TOKENS..AgentLimits.MAX_OUTPUT_TOKENS
                                ) {
                                    message = "Max output tokens is out of range"
                                    return@launch
                                }
                                repository.updateAgent(
                                    working.copy(
                                        temperature = parsedTemperature,
                                        maxOutputTokens = parsedTokens,
                                    ),
                                )
                                message = "Agent saved"
                            } catch (t: Throwable) {
                                message = t.message ?: "Save failed"
                            }
                        }
                    },
                ) { Text("Save agent") }

                TextButton(
                    onClick = {
                        val blueprint = DefaultAgentFactory.blueprints.firstOrNull { it.name == agent.name }
                        val seeded = blueprint?.let {
                            DefaultAgentFactory.create(agent.projectId, it, ids = { agent.id })
                        }
                        if (seeded != null) {
                            scope.launch {
                                repository.updateAgent(
                                    agent.copy(
                                        providerId = seeded.agent.providerId,
                                        modelId = seeded.agent.modelId,
                                        temperature = seeded.agent.temperature,
                                        maxOutputTokens = seeded.agent.maxOutputTokens,
                                    ),
                                )
                                provider = seeded.agent.providerId.toProviderType()
                                modelId = seeded.agent.modelId
                                message = "Reset model defaults"
                            }
                        } else {
                            message = "No default blueprint for this agent"
                        }
                    },
                ) { Text("Reset model defaults") }
            }

            Button(
                onClick = {
                    scope.launch {
                        val test = tester.test(working, repository.getAgentRules(agent.id))
                        message = if (test.success) {
                            "OK ${test.provider} ${test.model} ${test.latencyMs}ms"
                        } else {
                            "${test.errorCategory}: ${test.preview}"
                        }
                    }
                },
            ) { Text("Test agent") }

            if (message.isNotBlank()) Text(message)
        }
    }

    if (addTextOpen) {
        androidx.compose.material3.AlertDialog(
            onDismissRequest = { addTextOpen = false },
            title = { Text("Add text reference") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedTextField(
                        value = textReferenceName,
                        onValueChange = { textReferenceName = it },
                        label = { Text("Name") },
                        singleLine = true,
                    )
                    OutlinedTextField(
                        value = textReferenceContent,
                        onValueChange = { textReferenceContent = it },
                        label = { Text("Content") },
                        minLines = 6,
                    )
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        scope.launch {
                            try {
                                referenceManager.addText(
                                    projectId = agent.projectId,
                                    name = textReferenceName.trim().ifBlank { "Text reference" },
                                    text = textReferenceContent,
                                    agentId = agent.id,
                                )
                                textReferenceName = ""
                                textReferenceContent = ""
                                addTextOpen = false
                                message = "Text reference added"
                            } catch (t: Throwable) {
                                message = t.message ?: "Reference creation failed"
                            }
                        }
                    },
                ) { Text("Add") }
            },
            dismissButton = {
                TextButton(onClick = { addTextOpen = false }) { Text("Cancel") }
            },
        )
    }
}

@Composable
private fun RuleRow(
    rule: AgentRule,
    repository: AgentRepository,
    scope: CoroutineScope,
) {
    Card {
        Column(Modifier.padding(10.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Text("${rule.name} · ${rule.priority.name}")
            Text(rule.instruction)
            Row {
                Text(if (rule.enabled) "Enabled" else "Disabled")
                TextButton(onClick = {
                    scope.launch {
                        if (rule.enabled) repository.disableRule(rule.id) else repository.enableRule(rule.id)
                    }
                }) {
                    Text(if (rule.enabled) "Disable" else "Enable")
                }
                TextButton(onClick = { scope.launch { repository.deleteRule(rule.id) } }) {
                    Text("Delete")
                }
            }
        }
    }
}

@Composable
private fun ReferenceRow(
    name: String,
    assigned: Boolean,
    inclusion: InclusionMode,
    onToggle: () -> Unit,
    onInclusionChange: (InclusionMode) -> Unit,
) {
    Card {
        Column(Modifier.padding(10.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Text(name)
            Text(if (assigned) "Assigned to this agent" else "Project reference")
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedButton(onClick = onToggle) {
                    Text(if (assigned) "Detach" else "Attach")
                }
                EnumPicker("Inclusion", inclusion, InclusionMode.entries, onInclusionChange)
            }
        }
    }
}

@Composable
private fun ToggleRow(label: String, checked: Boolean, onChange: (Boolean) -> Unit) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
    ) {
        Text(label)
        Switch(checked = checked, onCheckedChange = onChange)
    }
}

@Composable
private fun <T : Enum<T>> EnumPicker(
    label: String,
    selected: T,
    options: List<T>,
    onSelected: (T) -> Unit,
) {
    var expanded by remember(label, selected) { mutableStateOf(false) }
    Column {
        Text(label)
        androidx.compose.material3.TextButton(onClick = { expanded = true }) {
            Text(selected.name.replace('_', ' '))
        }
        DropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
            options.forEach { option ->
                DropdownMenuItem(
                    text = { Text(option.name.replace('_', ' ')) },
                    onClick = {
                        onSelected(option)
                        expanded = false
                    },
                )
            }
        }
    }
}

@Composable
private fun <T> ProviderPicker(
    label: String,
    selected: T,
    options: List<T>,
    optionLabel: (T) -> String = { it.toString() },
    onSelected: (T) -> Unit,
) {
    var expanded by remember(label, selected) { mutableStateOf(false) }
    Column {
        Text(label)
        TextButton(onClick = { expanded = true }) {
            Text(optionLabel(selected))
        }
        DropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
            options.forEach { option ->
                DropdownMenuItem(
                    text = { Text(optionLabel(option)) },
                    onClick = {
                        onSelected(option)
                        expanded = false
                    },
                )
            }
        }
    }
}

@Composable
private fun ModelPicker(
    label: String,
    selected: String,
    options: List<Pair<String, String>>,
    onSelected: (String) -> Unit,
) {
    var expanded by remember(label, selected) { mutableStateOf(false) }
    Column {
        Text(label)
        TextButton(onClick = { expanded = true }) {
            Text(options.firstOrNull { it.first == selected }?.second ?: selected.ifBlank { "Select model" })
        }
        DropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
            options.forEach { (id, name) ->
                DropdownMenuItem(
                    text = { Text(name) },
                    onClick = {
                        onSelected(id)
                        expanded = false
                    },
                )
            }
        }
    }
}

private fun ProviderType.toProviderId(): ProviderId = when (this) {
    ProviderType.GEMINI -> ProviderId.GEMINI
    ProviderType.GROQ -> ProviderId.GROQ
    ProviderType.OPEN_ROUTER -> ProviderId.OPENROUTER
}


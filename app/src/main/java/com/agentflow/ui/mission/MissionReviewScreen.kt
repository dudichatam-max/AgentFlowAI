package com.agentflow.ui.mission

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.agentflow.domain.model.MissionStatus

@Composable
fun MissionReviewScreen(
    vm: InspectorReviewViewModel,
    onBack: () -> Unit,
) {
    val state by vm.state.collectAsState()
    Column(
        Modifier.padding(16.dp).verticalScroll(rememberScrollState()),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        TextButton(onClick = onBack) { Text("Back to mission") }
        val mission = state.mission
        Text(mission?.title ?: "Inspector", style = MaterialTheme.typography.headlineSmall)
        Text("Status: ${mission?.status?.name ?: "loading"}")
        state.error?.let { Text("Error: $it") }
        state.info?.let { Text(it) }
        val artifact = state.artifact
        if (artifact == null) {
            Text("No implementation plan artifact yet. Inspector cannot review.")
        } else {
            Text("Artifact v${artifact.version} · ${artifact.name}")
            state.previousArtifact?.let { Text("Previous v${it.version}") }
            Text(state.artifactContent ?: "(content unavailable)")
        }
        state.review?.let {
            Text("Last decision: ${it.status.name}")
            Text(it.summary)
            Text(it.reason, style = MaterialTheme.typography.bodySmall)
        }
        Text("Issues")
        if (state.issues.isEmpty()) Text("None")
        state.issues.forEach { issue ->
            Text("${issue.severity} · ${issue.description}")
            Text(issue.requiredAction, style = MaterialTheme.typography.bodySmall)
        }
        var showOverride by remember { mutableStateOf(false) }
        var overrideReason by remember { mutableStateOf("") }
        val canAct = mission?.status in setOf(MissionStatus.REVIEWING, MissionStatus.REVISION_REQUIRED)
        if (canAct && artifact != null && !state.submitting) {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Button(onClick = vm::approve) { Text("Approve") }
                Button(onClick = vm::reject) { Text("Reject") }
                Button(onClick = vm::escalate) { Text("Escalate") }
            }
        }
        if (mission != null && MissionUiPolicy.canUserOverride(mission.status) && !state.submitting) {
            TextButton(onClick = { showOverride = true }) { Text("Approve Anyway") }
        }
        if (showOverride) {
            AlertDialog(
                onDismissRequest = { showOverride = false },
                title = { Text("Approve anyway?") },
                text = {
                    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Text("This bypasses the Inspector approval gate. Record a reason.")
                        OutlinedTextField(
                            value = overrideReason,
                            onValueChange = { overrideReason = it },
                            label = { Text("Reason") },
                            singleLine = true,
                        )
                    }
                },
                confirmButton = {
                    Button(onClick = {
                        showOverride = false
                        vm.approveAnyway(overrideReason)
                        overrideReason = ""
                    }) { Text("Confirm") }
                },
                dismissButton = { TextButton(onClick = { showOverride = false }) { Text("Cancel") } },
            )
        }
    }
}

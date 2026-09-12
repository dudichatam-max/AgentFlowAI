package com.agentflow.ui.mission

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Button
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.agentflow.domain.model.TaskStatus

@Composable
fun MissionWorkspaceScreen(
    vm: MissionViewModel,
    onReview: () -> Unit,
) {
    val state by vm.state.collectAsState()
    val mission = state.mission
    Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text(mission?.title ?: "Mission")
        Text("Status: ${mission?.status?.name ?: "…"}")
        Text("Tasks ${state.tasks.size}  ready ${state.readyCount}  run ${state.runningCount}  done ${state.completedCount}  fail ${state.failedCount}  wait ${state.waitingCount}")
        state.artifact?.let { Text("Artifact v${it.version} ${it.name} ${it.contentHash?.take(8) ?: ""}") }
        state.error?.let { Text("Error: $it") }
        if (mission != null && MissionUiPolicy.canStart(mission.status)) {
            Button(onClick = vm::start) { Text("Start") }
        }
        if (mission != null && MissionUiPolicy.canPause(mission.status)) {
            Button(onClick = vm::pause) { Text("Pause") }
        }
        if (mission != null && MissionUiPolicy.canResume(mission.status)) {
            Button(onClick = vm::resume) { Text("Resume") }
        }
        if (mission != null && MissionUiPolicy.canCancel(mission.status)) {
            Button(onClick = vm::cancel) { Text("Cancel") }
        }
        if (mission != null && MissionUiPolicy.canOpenInspector(mission.status)) {
            Button(onClick = onReview) { Text("Inspector") }
        }
        Text("Dynamic task graph")
        LazyColumn {
            items(state.tasks, key = { it.id }) { task ->
                val deps = state.dependencies.filter { it.taskId == task.id }.joinToString { dep ->
                    state.tasks.firstOrNull { it.id == dep.dependsOnTaskId }?.title ?: dep.dependsOnTaskId
                }
                Text("${task.title} · ${task.status} · ${task.assignedAgentId}")
                if (deps.isNotBlank()) Text("  depends on: $deps")
            }
        }
        Text("Events")
        state.recentEvents.take(8).forEach { Text("${it.type}: ${it.message}") }
        if (mission?.status == com.agentflow.domain.model.MissionStatus.REVIEWING) {
            Text("Waiting for Inspector")
        }
        if (state.tasks.any { it.status == TaskStatus.WAITING_FOR_USER }) {
            Text("Waiting for user input")
        }
    }
}

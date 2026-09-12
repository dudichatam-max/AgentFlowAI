package com.agentflow.ui

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import com.agentflow.AgentFlowApp
import com.agentflow.domain.model.MissionStatus
import com.agentflow.ui.chat.AgentChatScreen
import com.agentflow.ui.chat.ChatViewModel
import kotlinx.coroutines.launch

@Composable
fun AgentFlowRoot(app: AgentFlowApp, startRoute: String? = null) {
    val nav = rememberNavController()
    val scope = rememberCoroutineScope()
    val tabs = listOf("home", "projects", "agents", "missions", "settings")
    val back by nav.currentBackStackEntryAsState()
    val dest = back?.destination?.route ?: "home"
    Scaffold(
        bottomBar = {
            NavigationBar {
                tabs.forEach { tab ->
                    NavigationBarItem(
                        selected = dest.startsWith(tab),
                        onClick = { nav.navigate(tab) { launchSingleTop = true } },
                        icon = { Text(tab.take(1).uppercase()) },
                        label = { Text(tab.replaceFirstChar { it.uppercase() }) },
                    )
                }
            }
        },
    ) { padding ->
        NavHost(nav, startDestination = startRoute ?: "home", modifier = Modifier.padding(padding)) {
            composable("home") {
                val projects by app.container.projectRepository.observeActive().collectAsState(initial = emptyList())
                val missions by app.container.missionRepository.observeActive().collectAsState(initial = emptyList())
                val storage = remember { app.container.storageManager.breakdown() }
                Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text("AgentFlow AI")
                    Text("Storage ${(storage.totalBytes / 1024)} KB / ${storage.budgetBytes / (1024 * 1024)} MB")
                    Text("Needs attention")
                    missions.filter { it.status == MissionStatus.WAITING_FOR_USER || it.status == MissionStatus.ESCALATED }.forEach {
                        Text("${it.title} · ${it.status.name}", modifier = Modifier.clickable { nav.navigate("mission/${it.id}") })
                    }
                    Text("Projects")
                    projects.forEach { Text(it.name, modifier = Modifier.clickable { nav.navigate("project/${it.id}") }) }
                    Button(onClick = { nav.navigate("projects") }) { Text("New Project") }
                }
            }
            composable("projects") {
                val projects by app.container.projectRepository.observeActive().collectAsState(initial = emptyList())
                Column(Modifier.padding(16.dp)) {
                    Button(onClick = {
                        scope.launch {
                            val p = app.container.projectRepository.create("New project", "")
                            app.container.agentRepository.seedDefaults(p.id)
                            nav.navigate("project/${p.id}")
                        }
                    }) { Text("Create project") }
                    LazyColumn {
                        items(projects, key = { it.id }) { p ->
                            Text(p.name, modifier = Modifier.clickable { nav.navigate("project/${p.id}") }.padding(12.dp))
                        }
                    }
                }
            }
            composable("project/{id}", arguments = listOf(navArgument("id") { type = NavType.StringType })) { entry ->
                val id = entry.arguments?.getString("id") ?: return@composable
                val agents by app.container.agentRepository.observeAgentsForProject(id).collectAsState(initial = emptyList())
                val missions by app.container.missionRepository.observeByProject(id).collectAsState(initial = emptyList())
                Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text("Project")
                    Text("Agents")
                    agents.forEach {
                        Text(it.name, modifier = Modifier.clickable { nav.navigate("sessions/${it.projectId}/${it.id}") })
                    }
                    Text("Missions")
                    missions.forEach {
                        Text("${it.title} · ${it.status.name}", modifier = Modifier.clickable { nav.navigate("mission/${it.id}") })
                    }
                    Button(onClick = {
                        scope.launch {
                            val m = app.container.missionEngine.createMission(id, "New mission", "").getOrNull()
                            if (m != null) nav.navigate("mission/$m")
                        }
                    }) { Text("New Mission") }
                    Button(onClick = {
                        scope.launch { app.container.agentRepository.seedDefaults(id) }
                    }) { Text("Seed default agents") }
                }
            }
            composable("agents") {
                val projects by app.container.projectRepository.observeActive().collectAsState(initial = emptyList())
                Column(Modifier.padding(16.dp)) {
                    Text("Agents")
                    projects.forEach { p ->
                        Text(p.name)
                        OutlinedButton(onClick = { nav.navigate("agents/new/${p.id}") }) {
                            Text("New agent")
                        }
                        val agents by app.container.agentRepository.observeAgentsForProject(p.id).collectAsState(initial = emptyList())
                        agents.forEach { agent ->
                            Card(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clickable { nav.navigate("agents/${agent.id}") }
                                    .padding(vertical = 4.dp),
                            ) {
                                Column(Modifier.padding(12.dp)) {
                                    Text(agent.name)
                                    Text(agent.role)
                                    Text("${agent.providerId.name} · ${agent.modelId}")
                                    Text(agent.status.name)
                                }
                            }
                        }
                    }
                }
            }
            composable(
                "agents/new/{projectId}",
                arguments = listOf(navArgument("projectId") { type = NavType.StringType }),
            ) { entry ->
                val projectId = entry.arguments?.getString("projectId") ?: return@composable
                com.agentflow.ui.settings.AgentCreationScreen(
                    projectId = projectId,
                    repository = app.container.agentRepository,
                    onCreated = { agentId -> nav.navigate("agents/$agentId") {
                        popUpTo("agents/new/$projectId") { inclusive = true }
                    } },
                    onCancel = { nav.popBackStack() },
                )
            }
            composable(
                "agents/{agentId}",
                arguments = listOf(navArgument("agentId") { type = NavType.StringType }),
            ) { entry ->
                val agentId = entry.arguments?.getString("agentId") ?: return@composable
                com.agentflow.ui.settings.AgentDetailScreen(
                    agentId = agentId,
                    repository = app.container.agentRepository,
                    tester = com.agentflow.domain.agent.AgentConnectionTester(app.providers.manager),
                    referenceRepository = app.container.referenceRepository,
                    referenceManager = app.container.referenceManager,
                    onBack = { nav.popBackStack() },
                )
            }
            composable("missions") {
                val missions by app.container.missionRepository.observeActive().collectAsState(initial = emptyList())
                LazyColumn(Modifier.padding(16.dp)) {
                    items(missions, key = { it.id }) { m ->
                        Text("${m.title} · ${m.status.name}", modifier = Modifier.clickable { nav.navigate("mission/${m.id}") }.padding(12.dp))
                    }
                }
            }
            composable("mission/{id}", arguments = listOf(navArgument("id") { type = NavType.StringType })) { entry ->
                val id = entry.arguments?.getString("id") ?: return@composable
                val vm: com.agentflow.ui.mission.MissionViewModel = androidx.lifecycle.viewmodel.compose.viewModel(
                    factory = com.agentflow.ui.mission.MissionViewModel.Factory(
                        id,
                        app.container.missionRepository,
                        app.container.taskGraphRepository,
                        app.container.missionEngine,
                        app.container.artifactService,
                        app.container.workScheduler,
                    ),
                )
                com.agentflow.ui.mission.MissionWorkspaceScreen(vm) { nav.navigate("mission/$id/review") }
            }
            composable("mission/{id}/review", arguments = listOf(navArgument("id") { type = NavType.StringType })) { entry ->
                val id = entry.arguments?.getString("id") ?: return@composable
                val vm: com.agentflow.ui.mission.InspectorReviewViewModel = androidx.lifecycle.viewmodel.compose.viewModel(
                    factory = com.agentflow.ui.mission.InspectorReviewViewModel.Factory(
                        id,
                        app.container.missionRepository,
                        app.container.artifactService,
                        app.container.inspectorService,
                        app.container.reviewCatalog,
                    ),
                )
                com.agentflow.ui.mission.MissionReviewScreen(vm) { nav.popBackStack() }
            }
            composable("mission/{id}/input", arguments = listOf(navArgument("id") { type = NavType.StringType })) { entry ->
                val id = entry.arguments?.getString("id") ?: return@composable
                Column(Modifier.padding(16.dp)) {
                    Text("Mission needs your input")
                    Button(onClick = {
                        scope.launch { app.container.missionEngine.submitUserInput(id, "confirmed") }
                    }) { Text("Submit") }
                }
            }
            composable(
                "sessions/{projectId}/{agentId}",
                arguments = listOf(
                    navArgument("projectId") { type = NavType.StringType },
                    navArgument("agentId") { type = NavType.StringType },
                ),
            ) { entry ->
                val agentId = entry.arguments?.getString("agentId") ?: return@composable
                val projectId = entry.arguments?.getString("projectId") ?: return@composable
                Column(Modifier.padding(16.dp)) {
                    Text("Agent workspace")
                    TextButton(onClick = {
                        scope.launch {
                            val s = app.container.chatRepository.createSession(projectId, agentId)
                            nav.navigate("chat/${s.id}")
                        }
                    }) { Text("Open chat") }
                }
            }
            composable("chat/{sessionId}", arguments = listOf(navArgument("sessionId") { type = NavType.StringType })) { entry ->
                val sessionId = entry.arguments?.getString("sessionId") ?: return@composable
                val vm: ChatViewModel = androidx.lifecycle.viewmodel.compose.viewModel(
                    factory = ChatViewModel.Factory(
                        sessionId,
                        app.container.chatRepository,
                        app.container.agentRepository,
                        app.container.chatService,
                    ),
                )
                AgentChatScreen(vm) { nav.popBackStack() }
            }
            composable("settings") {
                val storage = remember { app.container.storageManager.breakdown() }
                Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text("Settings")
                    Button(onClick = { nav.navigate("settings/providers") }) { Text("AI Providers") }
                    Button(onClick = { nav.navigate("settings/agents") }) { Text("Agents") }
                    Text("Free-only default: on. Paid models are rejected automatically.")
                    Text("Storage user=${storage.userDataBytes} cache=${storage.cacheBytes}")
                    Button(onClick = { app.container.storageManager.cleanupCacheAndTemp() }) { Text("Clear cache") }
                }
            }
            composable("settings/providers") {
                com.agentflow.ui.settings.ProviderSettingsScreen(
                    keyStore = app.providers.keyStore,
                    manager = app.providers.manager,
                )
            }
            composable("settings/agents") {
                val projects by app.container.projectRepository.observeActive().collectAsState(initial = emptyList())
                val projectId = projects.firstOrNull()?.id
                val agents by if (projectId == null) {
                    kotlinx.coroutines.flow.flowOf(emptyList())
                } else {
                    app.container.agentRepository.observeAgentsForProject(projectId)
                }.collectAsState(initial = emptyList())
                com.agentflow.ui.settings.AgentSettingsScreen(
                    agents = agents,
                    repository = app.container.agentRepository,
                    tester = com.agentflow.domain.agent.AgentConnectionTester(app.providers.manager),
                    referenceRepository = app.container.referenceRepository,
                    referenceManager = app.container.referenceManager,
                )
            }
        }
    }
}

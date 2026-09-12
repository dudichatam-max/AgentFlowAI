package com.agentflow.di

import android.content.Context
import com.agentflow.data.database.AgentFlowDatabase
import com.agentflow.data.network.github.KtorGitHubFetcher
import com.agentflow.data.repository.AgentRepository
import com.agentflow.data.repository.ChatRepository
import com.agentflow.data.repository.ProjectRepository
import com.agentflow.data.repository.ReferenceRepository
import com.agentflow.domain.chat.ChatService
import com.agentflow.domain.context.ContextBuilder
import com.agentflow.data.repository.MissionRepository
import com.agentflow.data.repository.RoomMissionStore
import com.agentflow.data.repository.TaskGraphRepository
import com.agentflow.domain.mission.MissionContextFactory
import com.agentflow.domain.mission.MissionEngine
import com.agentflow.domain.orchestration.AgentTaskRunner
import com.agentflow.domain.reference.FileReferenceStorage
import com.agentflow.domain.reference.ReferenceManager
import java.io.File

class AppContainer(
    context: Context,
    val database: AgentFlowDatabase,
    val providers: ProviderModule,
) {
    val storageManager = com.agentflow.domain.storage.StorageManager(context.filesDir)
    val workScheduler = com.agentflow.work.MissionWorkScheduler(context)
    val projectRepository = ProjectRepository(database.projectDao())
    val agentRepository = AgentRepository(database)
    val chatRepository = ChatRepository(database)
    val referenceRepository = ReferenceRepository(database.referenceDao())
    val referenceStorage = FileReferenceStorage(File(context.filesDir, "references"))
    val referenceManager = ReferenceManager(
        catalog = referenceRepository,
        storage = referenceStorage,
        github = KtorGitHubFetcher(providers.httpClient),
    )
    val projectContextBuilder = ContextBuilder(referenceStorage)
    val chatService = ChatService(
        persistence = chatRepository,
        manager = providers.manager,
        loadAgent = { agentRepository.getAgent(it) },
        loadRules = { agentRepository.getAgentRules(it) },
        projectContextBuilder = projectContextBuilder,
        loadReferences = { referenceRepository.listByProject(it) },
    )
    val missionRepository = MissionRepository(
        database.missionDao(),
        database.missionConversationDao(),
        database.missionEventDao(),
        database.artifactDao(),
    )
    val taskGraphRepository = TaskGraphRepository(
        database,
        database.taskDao(),
        database.taskDependencyDao(),
        database.taskResultDao(),
    )
    val missionStore = RoomMissionStore(database)
    val artifactCatalog = com.agentflow.data.repository.RoomArtifactCatalog(
        database,
        root = File(context.filesDir, "mission-artifacts"),
    )
    val artifactService = com.agentflow.domain.inspector.ArtifactService(artifactCatalog)
    private val missionRuntime = com.agentflow.domain.mission.MissionRuntime.wire(
        store = missionStore,
        worker = AgentTaskRunner(providers.manager) { agentRepository.getAgentRules(it) },
        planner = com.agentflow.domain.mission.AgentMissionPlanner(
            store = missionStore,
            manager = providers.manager,
            contextFactory = MissionContextFactory(projectContextBuilder),
            loadRules = { agentRepository.getAgentRules(it) },
        ),
        artifacts = artifactService,
        contextFactory = MissionContextFactory(projectContextBuilder),
        planningRequired = true,
    )
    val missionEngine = missionRuntime.engine
    val decisionGateway = missionRuntime.gateway
    val inspectorRepository = com.agentflow.data.repository.InspectorRepository(database.inspectorDao())
    val reviewCatalog: com.agentflow.domain.inspector.ReviewCatalog =
        com.agentflow.data.repository.RoomReviewCatalog(database)
    val inspectorService = com.agentflow.domain.inspector.InspectorService(
        store = missionStore,
        reviews = reviewCatalog,
        engine = missionEngine,
        artifacts = artifactService,
    )
}

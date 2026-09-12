package com.agentflow.domain.inspector

import androidx.room.Room
import androidx.test.platform.app.InstrumentationRegistry
import com.agentflow.data.database.AgentFlowDatabase
import com.agentflow.data.entity.MissionEntity
import com.agentflow.data.entity.ProjectEntity
import com.agentflow.data.repository.RoomReviewCatalog
import com.agentflow.domain.mission.InMemoryMissionStore
import com.agentflow.domain.mission.MissionEngine
import com.agentflow.domain.model.Agent
import com.agentflow.domain.model.Mission
import com.agentflow.domain.model.MissionStatus
import com.agentflow.domain.model.ProviderId
import com.agentflow.domain.model.ReviewStatus
import com.agentflow.domain.task.TaskWorkResult
import com.google.common.truth.Truth.assertThat
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Before
import org.junit.Test

class InspectorArtifactCausalityPersistenceTest {
    private lateinit var db: AgentFlowDatabase

    @Before
    fun setUp() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        db = Room.inMemoryDatabaseBuilder(context, AgentFlowDatabase::class.java)
            .allowMainThreadQueries()
            .build()
    }

    @After
    fun tearDown() = db.close()

    @Test
    fun rejectionArtifactSurvivesReloadAndStillBlocksApproval() = runTest {
        val missionId = "m1"
        db.projectDao().insert(ProjectEntity("p1", "Project", "", 1, 1, false))
        db.missionDao().insert(
            MissionEntity(
                missionId, "p1", "Mission", "", MissionStatus.REVIEWING.name,
                1, 1, null, null, "user",
            ),
        )

        val store = InMemoryMissionStore()
        store.missions[missionId] = Mission(
            missionId, "p1", "Mission", "", MissionStatus.REVIEWING, 1, 1,
        )
        store.agents["ins"] = Agent(
            "ins", "p1", "Inspector", "Inspector", "d", ProviderId.GROQ, "m",
            capabilities = setOf(com.agentflow.domain.agent.AgentCapability.INSPECT),
            createdAt = 1, updatedAt = 1,
        )

        val artifacts = ArtifactService(InMemoryArtifactCatalog())
        val engine = MissionEngine(
            store,
            { _: com.agentflow.domain.model.Task, _, _, _ -> TaskWorkResult("ok") },
            artifacts = artifacts,
        )
        val firstService = InspectorService(store, RoomReviewCatalog(db), engine, artifacts)
        val artifact = artifacts.publish(missionId, "plan", "v1").artifact

        firstService.reject(
            missionId, "gaps", "incomplete", "missing detail", "add detail",
        ).getOrThrow()

        store.missions[missionId] = store.missions[missionId]!!.copy(status = MissionStatus.REVIEWING)

        val reloadedReviews = RoomReviewCatalog(db)
        val persisted = reloadedReviews.list(missionId).single()
        assertThat(persisted.status).isEqualTo(ReviewStatus.REJECTED)
        assertThat(persisted.rejectedArtifactId).isEqualTo(artifact.id)
        assertThat(persisted.rejectedArtifactVersion).isEqualTo(artifact.version)

        val reloadedService = InspectorService(store, reloadedReviews, engine, artifacts)
        val result = reloadedService.approve(missionId)

        assertThat(result.isFailure).isTrue()
        assertThat(store.missions[missionId]!!.status).isEqualTo(MissionStatus.REVIEWING)
    }
}

package com.agentflow.domain.inspector

import com.agentflow.domain.mission.InMemoryMissionStore
import com.agentflow.domain.mission.MissionEngine
import com.agentflow.domain.model.Agent
import com.agentflow.domain.model.MissionStatus
import com.agentflow.domain.model.ProviderId
import com.agentflow.domain.model.ReviewStatus
import com.agentflow.domain.task.TaskWorkResult
import com.google.common.truth.Truth.assertThat
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.test.runTest
import org.junit.Test

class InspectorFlowTest {
    private fun env(): Triple<InMemoryMissionStore, InspectorService, ArtifactService> {
        val store = InMemoryMissionStore()
        store.agents["rd"] = Agent(
            "rd", "p", "R&D", "Research and development orchestrator", "d", ProviderId.GROQ, "m",
            capabilities = setOf(com.agentflow.domain.agent.AgentCapability.ORCHESTRATE, com.agentflow.domain.agent.AgentCapability.SYNTHESIZE),
            createdAt = 1, updatedAt = 1,
        )
        store.agents["ins"] = Agent(
            "ins", "p", "Inspector", "Quality inspector", "d", ProviderId.GROQ, "m",
            capabilities = setOf(com.agentflow.domain.agent.AgentCapability.INSPECT),
            createdAt = 1, updatedAt = 1,
        )
        val engine = MissionEngine(store, { task, _, _, _ -> TaskWorkResult("ok") })
        val artifacts = ArtifactService(InMemoryArtifactCatalog())
        val svc = InspectorService(store, InMemoryReviewCatalog(), engine, artifacts)
        return Triple(store, svc, artifacts)
    }

    @Test
    fun rejectThenApproveKeepsHistory() = runTest {
        val (store, svc, artifacts) = env()
        val engine = MissionEngine(store, { task, _, _, _ -> TaskWorkResult("ok") })
        val reviews = InMemoryReviewCatalog()
        val service = InspectorService(store, reviews, engine, artifacts)
        val id = engine.createMission("p", "Add offline support", "offline").getOrThrow()
        engine.startMission(id).getOrThrow()
        artifacts.publish(id, "plan", "v1 missing conflict resolution")
        store.missions[id] = store.missions[id]!!.copy(status = MissionStatus.REVIEWING)
        val r1 = service.applyProposal(
            id,
            """{"version":1,"decision":"REJECTED","summary":"incomplete","reason":"no conflict resolution","confidence":0.93,"severity":"HIGH","issues":[{"severity":"HIGH","description":"No conflict resolution strategy","requiredAction":"Define rules","category":"ARCHITECTURE"}]}""",
        ).getOrThrow()
        assertThat(r1.status).isEqualTo(ReviewStatus.REJECTED)
        assertThat(store.missions[id]!!.status).isEqualTo(MissionStatus.REVISION_REQUIRED)
        assertThat(store.tasks.values.any { it.title.contains("Inspector Findings") }).isTrue()
        val v2 = artifacts.publish(id, "plan", "v2 with conflict resolution last-write-wins")
        assertThat(v2.artifact.version).isEqualTo(2)
        store.missions[id] = store.missions[id]!!.copy(status = MissionStatus.REVIEWING)
        val r2 = service.applyProposal(
            id,
            """{"version":1,"decision":"APPROVED","summary":"covers requirements","reason":"architecture and sync defined","confidence":0.96,"severity":"NONE","issues":[]}""",
        ).getOrThrow()
        assertThat(r2.status).isEqualTo(ReviewStatus.APPROVED)
        assertThat(store.missions[id]!!.status).isEqualTo(MissionStatus.APPROVED)
        assertThat(reviews.list(id).map { it.status }).containsExactly(ReviewStatus.REJECTED, ReviewStatus.APPROVED).inOrder()
        assertThat(artifacts.latest(id)!!.version).isEqualTo(2)
    }

    @Test
    fun thirdRejectionEscalates() = runTest {
        val (store, _, artifacts) = env()
        val engine = MissionEngine(store, { task, _, _, _ -> TaskWorkResult("ok") })
        val reviews = InMemoryReviewCatalog()
        val service = InspectorService(store, reviews, engine, artifacts)
        val id = engine.createMission("p", "t", "d").getOrThrow()
        engine.startMission(id).getOrThrow()
        store.missions[id] = store.missions[id]!!.copy(status = MissionStatus.REVIEWING)
        val rejected = """{"version":1,"decision":"REJECTED","summary":"no","reason":"no","confidence":0.9,"severity":"HIGH","issues":[{"severity":"HIGH","description":"x","requiredAction":"y","category":"ARCHITECTURE"}]}"""
        service.applyProposal(id, rejected)
        store.missions[id] = store.missions[id]!!.copy(status = MissionStatus.REVIEWING)
        service.applyProposal(id, rejected)
        store.missions[id] = store.missions[id]!!.copy(status = MissionStatus.REVIEWING)
        val third = service.applyProposal(id, rejected)
        assertThat(third.isFailure).isTrue()
        assertThat(store.missions[id]!!.status).isEqualTo(MissionStatus.ESCALATED)
    }

    @Test
    fun concurrentRejectionsRespectRejectionCap() = runTest {
        val (store, _, artifacts) = env()
        val engine = MissionEngine(store, { _, _, _, _ -> TaskWorkResult("ok") })
        val reviews = InMemoryReviewCatalog()
        val service = InspectorService(store, reviews, engine, artifacts)
        val id = engine.createMission("p", "t", "d").getOrThrow()
        engine.startMission(id).getOrThrow()
        store.missions[id] = store.missions[id]!!.copy(status = MissionStatus.REVIEWING)
        val rejected = """{"version":1,"decision":"REJECTED","summary":"no","reason":"no","confidence":0.9,"severity":"HIGH","issues":[{"severity":"HIGH","description":"x","requiredAction":"y","category":"ARCHITECTURE"}]}"""

        coroutineScope {
            (1..5).map { async { service.applyProposal(id, rejected) } }.awaitAll()
        }

        assertThat(reviews.list(id).count { it.status == ReviewStatus.REJECTED })
            .isAtMost(InspectorReviewLimits.MAX_INSPECTOR_REJECTIONS)
        assertThat(store.missions[id]!!.status).isEqualTo(MissionStatus.ESCALATED)
    }

    @Test
    fun duplicateHashNotNewVersion() = runTest {
        val artifacts = ArtifactService(InMemoryArtifactCatalog())
        val a = artifacts.publish("m", "plan", "same")
        val b = artifacts.publish("m", "plan", "same")
        assertThat(a.unchanged).isFalse()
        assertThat(b.unchanged).isTrue()
        assertThat(b.artifact.version).isEqualTo(1)
    }

    @Test
    fun userOverrideKeepsRejectedReview() = runTest {
        val (store, _, artifacts) = env()
        val engine = MissionEngine(store, { task, _, _, _ -> TaskWorkResult("ok") })
        val reviews = InMemoryReviewCatalog()
        val service = InspectorService(store, reviews, engine, artifacts)
        val id = engine.createMission("p", "t", "d").getOrThrow()
        engine.startMission(id).getOrThrow()
        store.missions[id] = store.missions[id]!!.copy(status = MissionStatus.REVIEWING)
        service.applyProposal(
            id,
            """{"version":1,"decision":"REJECTED","summary":"no","reason":"no","confidence":0.9,"severity":"HIGH","issues":[{"severity":"HIGH","description":"x","requiredAction":"y","category":"UI"}]}""",
        )
        service.userApproveAnyway(id, "ship it").getOrThrow()
        assertThat(store.missions[id]!!.status).isEqualTo(MissionStatus.APPROVED)
        assertThat(reviews.list(id).single().status).isEqualTo(ReviewStatus.REJECTED)
        assertThat(store.events.any { it.message.contains("USER_OVERRIDE") }).isTrue()
    }


    @Test
    fun approvalAfterRejectionAcceptsNewArtifactWhenClockSharesSameMillisecond() = runTest {
        val clock = { 1000L }
        val catalog = InMemoryArtifactCatalog()
        val artifacts = ArtifactService(catalog, clock)
        val store = InMemoryMissionStore()
        store.agents["rd"] = Agent(
            "rd", "p", "R&D", "orchestrator", "d", ProviderId.GROQ, "m",
            capabilities = setOf(com.agentflow.domain.agent.AgentCapability.ORCHESTRATE),
            createdAt = 1, updatedAt = 1,
        )
        store.agents["ins"] = Agent(
            "ins", "p", "Inspector", "inspector", "d", ProviderId.GROQ, "m",
            capabilities = setOf(com.agentflow.domain.agent.AgentCapability.INSPECT),
            createdAt = 1, updatedAt = 1,
        )
        val engine = MissionEngine(store, { _, _, _, _ -> TaskWorkResult("ok") }, now = clock, artifacts = artifacts)
        val service = InspectorService(store, InMemoryReviewCatalog(), engine, artifacts, now = clock)
        val id = engine.createMission("p", "same-clock", "d").getOrThrow()
        engine.startMission(id).getOrThrow()
        artifacts.publish(id, "plan", "v1")
        store.missions[id] = store.missions[id]!!.copy(status = MissionStatus.REVIEWING)
        service.reject(id, "gaps", "incomplete", "missing sync", "add sync").getOrThrow()
        artifacts.publish(id, "plan", "v2")
        store.missions[id] = store.missions[id]!!.copy(status = MissionStatus.REVIEWING)

        service.approve(id).getOrThrow()

        assertThat(store.missions[id]!!.status).isEqualTo(MissionStatus.APPROVED)
        assertThat(artifacts.latest(id)!!.version).isEqualTo(2)
    }

    @Test
    fun approvalAfterRejectionRequiresNewArtifact() = runTest {
        val (store, _, artifacts) = env()
        val engine = MissionEngine(store, { _, _, _, _ -> TaskWorkResult("ok") })
        val reviews = InMemoryReviewCatalog()
        val service = InspectorService(store, reviews, engine, artifacts)
        val id = engine.createMission("p", "t", "d").getOrThrow()
        engine.startMission(id).getOrThrow()
        artifacts.publish(id, "plan", "v1")
        store.missions[id] = store.missions[id]!!.copy(status = MissionStatus.REVIEWING)
        service.reject(id, "gap", "fix required", "missing detail", "add detail").getOrThrow()
        store.missions[id] = store.missions[id]!!.copy(status = MissionStatus.REVIEWING)

        val result = service.approve(id)

        assertThat(result.isFailure).isTrue()
        assertThat(store.missions[id]!!.status).isEqualTo(MissionStatus.REVIEWING)
    }

    @Test
    fun userOverrideRollsBackMissionWhenAuditEventFails() = runTest {
        val (baseStore, _, artifacts) = env()
        val idEngine = MissionEngine(baseStore, { _, _, _, _ -> TaskWorkResult("ok") })
        val id = idEngine.createMission("p", "t", "d").getOrThrow()
        baseStore.missions[id] = baseStore.missions[id]!!.copy(status = MissionStatus.REVIEWING)
        val failingStore = FailingEventMissionStore(baseStore)
        val service = InspectorService(
            failingStore,
            InMemoryReviewCatalog(),
            MissionEngine(failingStore, { _, _, _, _ -> TaskWorkResult("ok") }),
            artifacts,
        )

        val result = service.userApproveAnyway(id, "operator override")

        assertThat(result.isFailure).isTrue()
        assertThat(baseStore.missions[id]!!.status).isEqualTo(MissionStatus.REVIEWING)
    }

    @Test
    fun userOverrideWritesApprovedEventAndStatusAtomically() = runTest {
        val (store, _, artifacts) = env()
        val engine = MissionEngine(store, { _, _, _, _ -> TaskWorkResult("ok") })
        val service = InspectorService(store, InMemoryReviewCatalog(), engine, artifacts)
        val id = engine.createMission("p", "t", "d").getOrThrow()
        store.missions[id] = store.missions[id]!!.copy(status = MissionStatus.ESCALATED)

        service.userApproveAnyway(id, "operator override").getOrThrow()

        assertThat(store.missions[id]!!.status).isEqualTo(MissionStatus.APPROVED)
        assertThat(store.events.last().type).isEqualTo(com.agentflow.domain.model.MissionEventType.INSPECTOR_APPROVED)
        assertThat(store.events.last().message).contains("USER_OVERRIDE_APPROVAL")
    }

    @Test
    fun userOverrideRejectsBlankReason() = runTest {
        val (store, _, artifacts) = env()
        val engine = MissionEngine(store, { _, _, _, _ -> TaskWorkResult("ok") })
        val service = InspectorService(store, InMemoryReviewCatalog(), engine, artifacts)
        val id = engine.createMission("p", "t", "d").getOrThrow()
        store.missions[id] = store.missions[id]!!.copy(status = MissionStatus.REVIEWING)

        val result = service.userApproveAnyway(id, "   ")

        assertThat(result.isFailure).isTrue()
        assertThat(store.missions[id]!!.status).isEqualTo(MissionStatus.REVIEWING)
    }

    @Test
    fun userOverrideOnlyWorksFromReviewStates() = runTest {
        val (store, _, artifacts) = env()
        val engine = MissionEngine(store, { _, _, _, _ -> TaskWorkResult("ok") })
        val service = InspectorService(store, InMemoryReviewCatalog(), engine, artifacts)
        val id = engine.createMission("p", "t", "d").getOrThrow()

        val result = service.userApproveAnyway(id, "override")

        assertThat(result.isFailure).isTrue()
        assertThat(store.missions[id]!!.status).isEqualTo(MissionStatus.CREATED)
    }

    @Test
    fun typedRejectCreatesRevisionTask() = runTest {
        val (store, _, artifacts) = env()
        val engine = MissionEngine(store, { task, _, _, _ -> TaskWorkResult("ok") })
        val reviews = InMemoryReviewCatalog()
        val service = InspectorService(store, reviews, engine, artifacts)
        val id = engine.createMission("p", "plan", "d").getOrThrow()
        engine.startMission(id).getOrThrow()
        artifacts.publish(id, "plan", "v1")
        store.missions[id] = store.missions[id]!!.copy(status = MissionStatus.REVIEWING)
        service.reject(id, "gaps", "incomplete", "missing sync", "add sync").getOrThrow()
        assertThat(store.missions[id]!!.status).isEqualTo(MissionStatus.REVISION_REQUIRED)
        assertThat(store.tasks.values.map { it.title }).contains("Review Inspector Findings")
        artifacts.publish(id, "plan", "v2 fixed")
        store.missions[id] = store.missions[id]!!.copy(status = MissionStatus.REVIEWING)
        service.approve(id).getOrThrow()
        assertThat(store.missions[id]!!.status).isEqualTo(MissionStatus.APPROVED)
    }

private class FailingEventMissionStore(
    private val delegate: InMemoryMissionStore,
) : com.agentflow.domain.mission.MissionStore by delegate {
    override suspend fun appendEvent(event: com.agentflow.domain.model.MissionEvent) {
        throw IllegalStateException("audit event write failed")
    }
}

}

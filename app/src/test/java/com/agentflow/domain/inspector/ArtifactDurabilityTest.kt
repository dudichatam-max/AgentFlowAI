package com.agentflow.domain.inspector

import com.agentflow.domain.model.ArtifactType
import com.google.common.truth.Truth.assertThat
import org.junit.Test

class ArtifactDurabilityTest {
    @Test
    fun failedPublicationLeavesPendingContentForRecovery() = kotlinx.coroutines.test.runTest {
        val catalog = FailingArtifactCatalog()
        val service = ArtifactService(catalog)

        val result = runCatching { service.publish("m1", "plan", "content") }
        assertThat(result.isFailure).isTrue()
        assertThat(catalog.pending()).hasSize(1)

        catalog.failWrites = false
        assertThat(service.recoverPending()).isEqualTo(1)
        assertThat(service.latest("m1")!!.contentHash).isNotNull()
        assertThat(catalog.readContent(catalog.list("m1").single().id)).isEqualTo("content")
    }

    private class FailingArtifactCatalog : DurableArtifactCatalog {
        val artifacts = mutableListOf<com.agentflow.domain.model.MissionArtifact>()
        val contents = mutableMapOf<String, String>()
        val pendingEntries = mutableMapOf<String, Pair<com.agentflow.domain.model.MissionArtifact, String>>()
        var failWrites = true
        override suspend fun list(missionId: String) = artifacts.filter { it.missionId == missionId }
        override suspend fun insert(artifact: com.agentflow.domain.model.MissionArtifact) { artifacts += artifact }
        override fun readContent(artifactId: String) = contents[artifactId]
        override fun writeContent(artifactId: String, content: String) { if (failWrites) error("disk failure"); contents[artifactId] = content }
        override suspend fun insertPending(artifact: com.agentflow.domain.model.MissionArtifact, content: String) { pendingEntries[artifact.id] = artifact to content }
        override suspend fun finalizePending(artifact: com.agentflow.domain.model.MissionArtifact) { artifacts += artifact; pendingEntries.remove(artifact.id) }
        override suspend fun pending() = pendingEntries.values.toList()
    }
}

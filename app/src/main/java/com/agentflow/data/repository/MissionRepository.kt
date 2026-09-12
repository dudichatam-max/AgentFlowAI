package com.agentflow.data.repository
import com.agentflow.data.dao.ArtifactDao
import com.agentflow.data.dao.MissionConversationDao
import com.agentflow.data.dao.MissionDao
import com.agentflow.data.dao.MissionEventDao
import com.agentflow.data.mapper.toDomain
import com.agentflow.data.mapper.toEntity
import com.agentflow.domain.model.Ids
import com.agentflow.domain.model.Mission
import com.agentflow.domain.model.MissionArtifact
import com.agentflow.domain.model.MissionConversation
import com.agentflow.domain.model.MissionEvent
import com.agentflow.domain.model.MissionEventType
import com.agentflow.domain.model.MissionStatus
import com.agentflow.domain.validation.MissionStateMachine
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

class MissionRepository(
    private val missionDao: MissionDao,
    private val conversationDao: MissionConversationDao,
    private val eventDao: MissionEventDao,
    private val artifactDao: ArtifactDao,
) {
    fun observeActive(): Flow<List<Mission>> = missionDao.observeActive().map { list -> list.map { it.toDomain() } }
    fun observeByProject(projectId: String): Flow<List<Mission>> =
        missionDao.observeByProject(projectId).map { list -> list.map { it.toDomain() } }
    suspend fun get(id: String): Mission? = missionDao.getById(id)?.toDomain()
    suspend fun insert(mission: Mission) {
        missionDao.insert(mission.toEntity())
        appendEvent(mission.id, MissionEventType.MISSION_CREATED, "Mission created: ${mission.title}")
    }
    suspend fun transition(missionId: String, to: MissionStatus, now: Long = System.currentTimeMillis()) {
        val current = missionDao.getById(missionId) ?: return
        val from = MissionStatus.valueOf(current.status)
        MissionStateMachine.transition(from, to)
        val startedAt = current.startedAt ?: if (to != MissionStatus.CREATED) now else null
        val completedAt = if (MissionStateMachine.isTerminal(to)) now else current.completedAt
        missionDao.update(current.copy(status = to.name, updatedAt = now, startedAt = startedAt, completedAt = completedAt))
        val eventType = when (to) {
            MissionStatus.PAUSED -> MissionEventType.MISSION_PAUSED
            MissionStatus.WAITING_FOR_USER -> MissionEventType.USER_INPUT_REQUIRED
            MissionStatus.APPROVED -> MissionEventType.MISSION_COMPLETED
            MissionStatus.FAILED -> MissionEventType.MISSION_FAILED
            MissionStatus.CANCELLED -> MissionEventType.MISSION_CANCELLED
            MissionStatus.PLANNING -> MissionEventType.MISSION_PLANNING
            MissionStatus.REVIEWING -> MissionEventType.MISSION_REVIEWING
            MissionStatus.ESCALATED -> MissionEventType.MISSION_ESCALATED
            else -> if (from == MissionStatus.PAUSED) MissionEventType.MISSION_RESUMED else MissionEventType.MISSION_STARTED
        }
        appendEvent(missionId, eventType, "Mission $from → $to")
    }
    suspend fun addConversation(conversation: MissionConversation) {
        conversationDao.insert(conversation.toEntity())
    }
    suspend fun appendEvent(
        missionId: String, type: MissionEventType, message: String,
        taskId: String? = null, messageId: String? = null, now: Long = System.currentTimeMillis(),
    ) {
        eventDao.insert(
            MissionEvent(id = Ids.new(), missionId = missionId, type = type, message = message,
                relatedTaskId = taskId, relatedMessageId = messageId, createdAt = now).toEntity(),
        )
    }
    suspend fun pageEvents(missionId: String, limit: Int, offset: Int): List<MissionEvent> =
        eventDao.pageChronological(missionId, limit, offset).map { it.toDomain() }
    suspend fun addArtifact(artifact: MissionArtifact) {
        artifactDao.insert(artifact.toEntity())
        appendEvent(artifact.missionId, MissionEventType.ARTIFACT_CREATED, "Artifact ${artifact.name}")
    }
}

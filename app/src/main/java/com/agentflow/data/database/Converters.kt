package com.agentflow.data.database
import androidx.room.TypeConverter
import com.agentflow.domain.model.*

class Converters {
    @TypeConverter fun fromMissionStatus(v: MissionStatus?): String? = v?.name
    @TypeConverter fun toMissionStatus(v: String?): MissionStatus? = v?.let { MissionStatus.valueOf(it) }
    @TypeConverter fun fromTaskStatus(v: TaskStatus?): String? = v?.name
    @TypeConverter fun toTaskStatus(v: String?): TaskStatus? = v?.let { TaskStatus.valueOf(it) }
    @TypeConverter fun fromProviderId(v: ProviderId?): String? = v?.name
    @TypeConverter fun toProviderId(v: String?): ProviderId? = v?.let { ProviderId.valueOf(it) }
    @TypeConverter fun fromAgentStatus(v: AgentStatus?): String? = v?.name
    @TypeConverter fun toAgentStatus(v: String?): AgentStatus? = v?.let {
        if (it == "ENABLED") AgentStatus.READY else AgentStatus.valueOf(it)
    }
    @TypeConverter fun fromPriority(v: Priority?): String? = v?.name
    @TypeConverter fun toPriority(v: String?): Priority? = v?.let { Priority.valueOf(it) }
    @TypeConverter fun fromRulePriority(v: RulePriority?): String? = v?.name
    @TypeConverter fun toRulePriority(v: String?): RulePriority? = v?.let { RulePriority.valueOf(it) }
    @TypeConverter fun fromReferenceType(v: ReferenceType?): String? = v?.name
    @TypeConverter fun toReferenceType(v: String?): ReferenceType? = v?.let { ReferenceType.valueOf(it) }
    @TypeConverter fun fromInclusionMode(v: InclusionMode?): String? = v?.name
    @TypeConverter fun toInclusionMode(v: String?): InclusionMode? = v?.let { InclusionMode.valueOf(it) }
    @TypeConverter fun fromCreatedByType(v: CreatedByType?): String? = v?.name
    @TypeConverter fun toCreatedByType(v: String?): CreatedByType? = v?.let { CreatedByType.valueOf(it) }
    @TypeConverter fun fromDependencyType(v: DependencyType?): String? = v?.name
    @TypeConverter fun toDependencyType(v: String?): DependencyType? = v?.let { DependencyType.valueOf(it) }
    @TypeConverter fun fromActorType(v: ActorType?): String? = v?.name
    @TypeConverter fun toActorType(v: String?): ActorType? = v?.let { ActorType.valueOf(it) }
    @TypeConverter fun fromMessageType(v: MessageType?): String? = v?.name
    @TypeConverter fun toMessageType(v: String?): MessageType? = v?.let { MessageType.valueOf(it) }
    @TypeConverter fun fromReviewStatus(v: ReviewStatus?): String? = v?.name
    @TypeConverter fun toReviewStatus(v: String?): ReviewStatus? = v?.let { ReviewStatus.valueOf(it) }
    @TypeConverter fun fromSeverity(v: Severity?): String? = v?.name
    @TypeConverter fun toSeverity(v: String?): Severity? = v?.let { Severity.valueOf(it) }
    @TypeConverter fun fromArtifactType(v: ArtifactType?): String? = v?.name
    @TypeConverter fun toArtifactType(v: String?): ArtifactType? = v?.let { ArtifactType.valueOf(it) }
    @TypeConverter fun fromMissionEventType(v: MissionEventType?): String? = v?.name
    @TypeConverter fun toMissionEventType(v: String?): MissionEventType? = v?.let { MissionEventType.valueOf(it) }
}

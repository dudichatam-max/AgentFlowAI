package com.agentflow.data.database
import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.TypeConverters
import com.agentflow.data.dao.*
import com.agentflow.data.entity.*

@Database(
    entities = [
        ProjectEntity::class, AgentEntity::class, AgentRuleEntity::class, ReferenceEntity::class,
        MissionEntity::class, MissionConversationEntity::class, TaskEntity::class,
        TaskDependencyEntity::class, TaskResultEntity::class, AgentMessageEntity::class,
        InspectorReviewEntity::class, InspectorIssueEntity::class, MissionArtifactEntity::class,
        MissionEventEntity::class, ProviderConfigEntity::class,
        ChatSessionEntity::class, ChatMessageEntity::class, ChatAttachmentEntity::class,
        MissionLoopGuardEntity::class, MissionArtifactPendingEntity::class,
    ],
    version = 6,
    exportSchema = false,
)
@TypeConverters(Converters::class)
abstract class AgentFlowDatabase : RoomDatabase() {
    abstract fun projectDao(): ProjectDao
    abstract fun agentDao(): AgentDao
    abstract fun agentRuleDao(): AgentRuleDao
    abstract fun referenceDao(): ReferenceDao
    abstract fun missionDao(): MissionDao
    abstract fun missionConversationDao(): MissionConversationDao
    abstract fun taskDao(): TaskDao
    abstract fun taskDependencyDao(): TaskDependencyDao
    abstract fun taskResultDao(): TaskResultDao
    abstract fun agentMessageDao(): AgentMessageDao
    abstract fun inspectorDao(): InspectorDao
    abstract fun artifactDao(): ArtifactDao
    abstract fun missionEventDao(): MissionEventDao
    abstract fun providerConfigDao(): ProviderConfigDao
    abstract fun chatSessionDao(): ChatSessionDao
    abstract fun chatMessageDao(): ChatMessageDao
    abstract fun chatAttachmentDao(): ChatAttachmentDao
    abstract fun missionLoopGuardDao(): MissionLoopGuardDao
    abstract fun missionArtifactPendingDao(): MissionArtifactPendingDao

    companion object {
        const val NAME = "agentflow.db"
        fun build(context: Context): AgentFlowDatabase =
            Room.databaseBuilder(context, AgentFlowDatabase::class.java, NAME)
                .addMigrations(MIGRATION_1_2, MIGRATION_2_3, MIGRATION_3_4, MIGRATION_4_5, MIGRATION_5_6)
                .build()
    }
}

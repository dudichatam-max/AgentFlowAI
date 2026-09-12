package com.agentflow.data.database

import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

val MIGRATION_1_2 = object : Migration(1, 2) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL("ALTER TABLE agents ADD COLUMN outputStyle TEXT NOT NULL DEFAULT 'BALANCED'")
        db.execSQL("ALTER TABLE agents ADD COLUMN verbosity TEXT NOT NULL DEFAULT 'NORMAL'")
        db.execSQL("ALTER TABLE agents ADD COLUMN exposeUncertainty INTEGER NOT NULL DEFAULT 1")
        db.execSQL("ALTER TABLE agents ADD COLUMN includeAssumptions INTEGER NOT NULL DEFAULT 1")
        db.execSQL("ALTER TABLE agents ADD COLUMN includeAlternatives INTEGER NOT NULL DEFAULT 1")
        db.execSQL("ALTER TABLE agents ADD COLUMN capabilities TEXT NOT NULL DEFAULT ''")
        db.execSQL("UPDATE agents SET status = 'READY' WHERE status = 'ENABLED'")
    }
}

val MIGRATION_2_3 = object : Migration(2, 3) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL(
            "CREATE TABLE IF NOT EXISTS chat_sessions (id TEXT NOT NULL PRIMARY KEY, projectId TEXT NOT NULL, agentId TEXT NOT NULL, title TEXT NOT NULL, status TEXT NOT NULL, createdAt INTEGER NOT NULL, updatedAt INTEGER NOT NULL, lastMessageAt INTEGER NOT NULL, FOREIGN KEY(projectId) REFERENCES projects(id) ON DELETE CASCADE, FOREIGN KEY(agentId) REFERENCES agents(id) ON DELETE CASCADE)",
        )
        db.execSQL("CREATE INDEX IF NOT EXISTS index_chat_sessions_projectId ON chat_sessions(projectId)")
        db.execSQL("CREATE INDEX IF NOT EXISTS index_chat_sessions_agentId ON chat_sessions(agentId)")
        db.execSQL("CREATE INDEX IF NOT EXISTS index_chat_sessions_updatedAt ON chat_sessions(updatedAt)")
        db.execSQL("CREATE INDEX IF NOT EXISTS index_chat_sessions_lastMessageAt ON chat_sessions(lastMessageAt)")
        db.execSQL(
            "CREATE TABLE IF NOT EXISTS chat_messages (id TEXT NOT NULL PRIMARY KEY, sessionId TEXT NOT NULL, role TEXT NOT NULL, content TEXT NOT NULL, status TEXT NOT NULL, createdAt INTEGER NOT NULL, updatedAt INTEGER NOT NULL, provider TEXT, model TEXT, latencyMs INTEGER, inputTokens INTEGER, outputTokens INTEGER, errorCode TEXT, generationGroupId TEXT, bookmarked INTEGER NOT NULL, FOREIGN KEY(sessionId) REFERENCES chat_sessions(id) ON DELETE CASCADE)",
        )
        db.execSQL("CREATE INDEX IF NOT EXISTS index_chat_messages_sessionId ON chat_messages(sessionId)")
        db.execSQL("CREATE INDEX IF NOT EXISTS index_chat_messages_createdAt ON chat_messages(createdAt)")
        db.execSQL("CREATE INDEX IF NOT EXISTS index_chat_messages_status ON chat_messages(status)")
        db.execSQL(
            "CREATE TABLE IF NOT EXISTS chat_attachments (id TEXT NOT NULL PRIMARY KEY, messageId TEXT NOT NULL, name TEXT NOT NULL, mimeType TEXT NOT NULL, sizeBytes INTEGER NOT NULL, path TEXT NOT NULL, FOREIGN KEY(messageId) REFERENCES chat_messages(id) ON DELETE CASCADE)",
        )
        db.execSQL("CREATE INDEX IF NOT EXISTS index_chat_attachments_messageId ON chat_attachments(messageId)")
    }
}


val MIGRATION_3_4 = object : Migration(3, 4) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL("CREATE TABLE IF NOT EXISTS mission_loop_guard (missionId TEXT NOT NULL, signature TEXT NOT NULL, count INTEGER NOT NULL, updatedAt INTEGER NOT NULL, PRIMARY KEY(missionId, signature), FOREIGN KEY(missionId) REFERENCES missions(id) ON DELETE CASCADE)")
        db.execSQL("CREATE INDEX IF NOT EXISTS index_mission_loop_guard_missionId ON mission_loop_guard(missionId)")
        db.execSQL("CREATE INDEX IF NOT EXISTS index_mission_loop_guard_updatedAt ON mission_loop_guard(updatedAt)")
    }
}


val MIGRATION_4_5 = object : Migration(4, 5) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL("CREATE TABLE IF NOT EXISTS mission_artifact_pending (id TEXT NOT NULL PRIMARY KEY, missionId TEXT NOT NULL, type TEXT NOT NULL, name TEXT NOT NULL, path TEXT, contentHash TEXT, sizeBytes INTEGER NOT NULL, mimeType TEXT, version INTEGER NOT NULL, createdAt INTEGER NOT NULL, updatedAt INTEGER NOT NULL, content TEXT NOT NULL, FOREIGN KEY(missionId) REFERENCES missions(id) ON DELETE CASCADE)")
        db.execSQL("CREATE INDEX IF NOT EXISTS index_mission_artifact_pending_missionId ON mission_artifact_pending(missionId)")
    }
}

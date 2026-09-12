package com.agentflow.data.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.agentflow.data.entity.MissionArtifactPendingEntity

@Dao
interface MissionArtifactPendingDao {
    @Query("SELECT * FROM mission_artifact_pending")
    suspend fun listAll(): List<MissionArtifactPendingEntity>
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(entity: MissionArtifactPendingEntity)
    @Query("DELETE FROM mission_artifact_pending WHERE id = :id")
    suspend fun delete(id: String)
}

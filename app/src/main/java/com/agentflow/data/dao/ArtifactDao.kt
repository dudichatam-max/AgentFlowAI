package com.agentflow.data.dao
import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import com.agentflow.data.entity.MissionArtifactEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface ArtifactDao {
    @Query("SELECT * FROM mission_artifacts WHERE missionId = :missionId ORDER BY version DESC")
    fun observeByMission(missionId: String): Flow<List<MissionArtifactEntity>>
    @Query("SELECT * FROM mission_artifacts WHERE missionId = :missionId")
    suspend fun listByMission(missionId: String): List<MissionArtifactEntity>
    @Query("SELECT * FROM mission_artifacts")
    suspend fun listAll(): List<MissionArtifactEntity>
    @Insert(onConflict = OnConflictStrategy.ABORT)
    suspend fun insert(entity: MissionArtifactEntity)
    @Update
    suspend fun update(entity: MissionArtifactEntity)
}

package com.agentflow.data.dao
import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.agentflow.data.entity.MissionEventEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface MissionEventDao {
    @Query("SELECT * FROM mission_events WHERE missionId = :missionId ORDER BY createdAt ASC LIMIT :limit OFFSET :offset")
    suspend fun pageChronological(missionId: String, limit: Int, offset: Int): List<MissionEventEntity>
    @Query("SELECT * FROM mission_events WHERE missionId = :missionId ORDER BY createdAt ASC")
    suspend fun allChronological(missionId: String): List<MissionEventEntity>

    @Query("SELECT * FROM mission_events WHERE missionId = :missionId ORDER BY createdAt ASC")
    fun observeChronological(missionId: String): Flow<List<MissionEventEntity>>
    @Insert(onConflict = OnConflictStrategy.ABORT)
    suspend fun insert(entity: MissionEventEntity)
}

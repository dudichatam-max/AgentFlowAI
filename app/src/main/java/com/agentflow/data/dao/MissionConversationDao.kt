package com.agentflow.data.dao
import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.agentflow.data.entity.MissionConversationEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface MissionConversationDao {
    @Query("SELECT * FROM mission_conversations WHERE missionId = :missionId")
    fun observeByMission(missionId: String): Flow<List<MissionConversationEntity>>
    @Insert(onConflict = OnConflictStrategy.ABORT)
    suspend fun insert(entity: MissionConversationEntity)
}

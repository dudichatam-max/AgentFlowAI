package com.agentflow.data.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.agentflow.data.entity.MissionLoopGuardEntity

@Dao
interface MissionLoopGuardDao {
    @Query("SELECT * FROM mission_loop_guard WHERE missionId = :missionId")
    suspend fun list(missionId: String): List<MissionLoopGuardEntity>

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insertIfAbsent(entity: MissionLoopGuardEntity): Long

    @Query("UPDATE mission_loop_guard SET count = count + 1, updatedAt = :updatedAt WHERE missionId = :missionId AND signature = :signature")
    suspend fun increment(missionId: String, signature: String, updatedAt: Long): Int

    @Query("DELETE FROM mission_loop_guard WHERE missionId = :missionId")
    suspend fun clear(missionId: String)
}

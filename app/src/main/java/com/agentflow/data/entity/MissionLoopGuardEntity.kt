package com.agentflow.data.entity

import androidx.room.Entity
import androidx.room.Index

@Entity(
    tableName = "mission_loop_guard",
    primaryKeys = ["missionId", "signature"],
    indices = [Index("missionId"), Index("updatedAt")],
)
data class MissionLoopGuardEntity(
    val missionId: String,
    val signature: String,
    val count: Int,
    val updatedAt: Long,
)

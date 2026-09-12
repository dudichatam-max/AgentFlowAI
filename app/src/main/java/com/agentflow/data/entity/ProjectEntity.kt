package com.agentflow.data.entity
import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(tableName = "projects", indices = [Index("isArchived"), Index("updatedAt")])
data class ProjectEntity(
    @PrimaryKey val id: String,
    val name: String, val description: String,
    val createdAt: Long, val updatedAt: Long, val isArchived: Boolean,
)

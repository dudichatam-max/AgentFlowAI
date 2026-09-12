package com.agentflow.data.entity
import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(tableName = "provider_configs", indices = [Index(value = ["providerId"], unique = true)])
data class ProviderConfigEntity(
    @PrimaryKey val id: String,
    val providerId: String, val enabled: Boolean, val freeOnly: Boolean,
    val defaultModelId: String, val createdAt: Long, val updatedAt: Long,
)

package com.agentflow.data.dao
import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import com.agentflow.data.entity.ProviderConfigEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface ProviderConfigDao {
    @Query("SELECT * FROM provider_configs")
    fun observeAll(): Flow<List<ProviderConfigEntity>>
    @Query("SELECT * FROM provider_configs WHERE providerId = :providerId")
    suspend fun getByProvider(providerId: String): ProviderConfigEntity?
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(entity: ProviderConfigEntity)
    @Update
    suspend fun update(entity: ProviderConfigEntity)
}

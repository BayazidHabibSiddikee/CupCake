package com.cupcake.data.source.remote

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import com.cupcake.data.model.ApiProvider
import kotlinx.coroutines.flow.Flow

@Dao
interface ApiProviderDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(provider: ApiProvider)

    @Update
    suspend fun update(provider: ApiProvider)

    @Query("DELETE FROM api_providers WHERE id = :id")
    suspend fun delete(id: String)

    @Query("SELECT * FROM api_providers ORDER BY createdAt DESC")
    fun getAll(): Flow<List<ApiProvider>>

    @Query("SELECT * FROM api_providers WHERE isEnabled = 1")
    fun getEnabled(): Flow<List<ApiProvider>>

    @Query("SELECT * FROM api_providers WHERE id = :id")
    suspend fun getById(id: String): ApiProvider?
}

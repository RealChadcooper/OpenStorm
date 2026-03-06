package com.openstorm.core.data.local.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.openstorm.core.data.local.entity.AlertEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface AlertDao {

    @Query("SELECT * FROM alerts WHERE expiresEpochMs > :nowEpochMs ORDER BY severity ASC")
    fun observeActive(nowEpochMs: Long): Flow<List<AlertEntity>>

    @Query("SELECT * FROM alerts WHERE id = :id")
    suspend fun getById(id: String): AlertEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAll(alerts: List<AlertEntity>)

    @Query("DELETE FROM alerts WHERE expiresEpochMs < :nowEpochMs")
    suspend fun deleteExpired(nowEpochMs: Long)
}

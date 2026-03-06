package com.openstorm.core.data.local.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.openstorm.core.data.local.entity.RadarStationEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface RadarStationDao {

    @Query("SELECT * FROM radar_stations ORDER BY name ASC")
    fun observeAll(): Flow<List<RadarStationEntity>>

    @Query("SELECT * FROM radar_stations WHERE id = :id")
    suspend fun getById(id: String): RadarStationEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAll(stations: List<RadarStationEntity>)

    @Query("DELETE FROM radar_stations")
    suspend fun deleteAll()
}

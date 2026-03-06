package com.openstorm.core.data.local

import androidx.room.Database
import androidx.room.RoomDatabase
import com.openstorm.core.data.local.dao.AlertDao
import com.openstorm.core.data.local.dao.RadarStationDao
import com.openstorm.core.data.local.entity.AlertEntity
import com.openstorm.core.data.local.entity.RadarStationEntity

@Database(
    entities = [RadarStationEntity::class, AlertEntity::class],
    version = 1,
    exportSchema = true,
)
abstract class OpenStormDatabase : RoomDatabase() {
    abstract fun radarStationDao(): RadarStationDao
    abstract fun alertDao(): AlertDao
}

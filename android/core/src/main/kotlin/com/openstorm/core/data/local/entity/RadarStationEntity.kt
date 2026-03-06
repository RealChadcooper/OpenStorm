package com.openstorm.core.data.local.entity

import androidx.room.Entity
import androidx.room.PrimaryKey
import com.openstorm.core.domain.model.RadarStation
import com.openstorm.core.domain.model.StationStatus
import com.openstorm.core.domain.model.StationType

@Entity(tableName = "radar_stations")
data class RadarStationEntity(
    @PrimaryKey val id: String,
    val name: String,
    val lat: Double,
    val lon: Double,
    val elevation: Double,
    val stationType: String,
    val status: String,
    val updatedAtEpochMs: Long = System.currentTimeMillis(),
) {
    fun toDomain(distanceKm: Double? = null, products: List<String> = emptyList()) = RadarStation(
        id = id,
        name = name,
        lat = lat,
        lon = lon,
        elevation = elevation,
        stationType = runCatching { StationType.valueOf(stationType) }.getOrDefault(StationType.WSR88D),
        status = runCatching { StationStatus.valueOf(status) }.getOrDefault(StationStatus.ACTIVE),
        distanceKm = distanceKm,
        products = products,
    )

    companion object {
        fun fromDomain(station: RadarStation) = RadarStationEntity(
            id = station.id,
            name = station.name,
            lat = station.lat,
            lon = station.lon,
            elevation = station.elevation,
            stationType = station.stationType.name,
            status = station.status.name,
        )
    }
}

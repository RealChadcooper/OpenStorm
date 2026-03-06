package com.openstorm.backend.service

import com.openstorm.backend.model.RadarFrame
import com.openstorm.backend.model.RadarStation
import com.openstorm.backend.provider.radar.RadarProvider
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.sin
import kotlin.math.sqrt

class RadarService(private val provider: RadarProvider) {

    suspend fun getNearbyStations(lat: Double, lon: Double, radiusKm: Double): List<RadarStation> {
        return provider.getStations()
            .map { it.copy(distanceKm = haversineKm(lat, lon, it.lat, it.lon)) }
            .filter { (it.distanceKm ?: Double.MAX_VALUE) <= radiusKm }
            .sortedBy { it.distanceKm }
    }

    suspend fun getStation(stationId: String): RadarStation? {
        return provider.getStation(stationId)
    }

    suspend fun getFrames(stationId: String, product: String, count: Int): List<RadarFrame> {
        return provider.getFrames(stationId, product, count)
    }

    companion object {
        private const val EARTH_RADIUS_KM = 6371.0

        fun haversineKm(lat1: Double, lon1: Double, lat2: Double, lon2: Double): Double {
            val dLat = Math.toRadians(lat2 - lat1)
            val dLon = Math.toRadians(lon2 - lon1)
            val a = sin(dLat / 2) * sin(dLat / 2) +
                cos(Math.toRadians(lat1)) * cos(Math.toRadians(lat2)) *
                sin(dLon / 2) * sin(dLon / 2)
            val c = 2 * atan2(sqrt(a), sqrt(1 - a))
            return EARTH_RADIUS_KM * c
        }
    }
}

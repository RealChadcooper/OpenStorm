package com.openstorm.core.data.remote

import com.openstorm.core.data.remote.dto.AlertListResponse
import com.openstorm.core.data.remote.dto.ArchiveFramesResponseDto
import com.openstorm.core.data.remote.dto.HealthResponse
import com.openstorm.core.data.remote.dto.NearestFrameResponseDto
import com.openstorm.core.data.remote.dto.PlaybackManifestDto
import com.openstorm.core.data.remote.dto.RadarFramesResponse
import com.openstorm.core.data.remote.dto.StationListResponse
import retrofit2.http.GET
import retrofit2.http.Path
import retrofit2.http.Query

interface OpenStormApi {

    @GET("api/v1/health")
    suspend fun health(): HealthResponse

    @GET("api/v1/radar/stations")
    suspend fun getStations(
        @Query("lat") lat: Double,
        @Query("lon") lon: Double,
        @Query("radius") radiusKm: Double = 200.0,
    ): StationListResponse

    @GET("api/v1/radar/stations/{stationId}/products/{product}")
    suspend fun getRadarFrames(
        @Path("stationId") stationId: String,
        @Path("product") product: String,
        @Query("frames") frameCount: Int = 10,
    ): RadarFramesResponse

    @GET("api/v1/alerts")
    suspend fun getAlerts(
        @Query("lat") lat: Double,
        @Query("lon") lon: Double,
        @Query("radius") radiusKm: Double = 150.0,
    ): AlertListResponse

    // ── Archive endpoints ──

    @GET("api/v1/radar/archive/{stationId}/{product}/frames")
    suspend fun getArchiveFrames(
        @Path("stationId") stationId: String,
        @Path("product") product: String,
        @Query("start") start: String,
        @Query("end") end: String,
        @Query("limit") limit: Int = 200,
    ): ArchiveFramesResponseDto

    @GET("api/v1/radar/archive/{stationId}/{product}/nearest")
    suspend fun getNearestFrame(
        @Path("stationId") stationId: String,
        @Path("product") product: String,
        @Query("timestamp") timestamp: String,
    ): NearestFrameResponseDto

    @GET("api/v1/radar/archive/{stationId}/{product}/manifest")
    suspend fun getPlaybackManifest(
        @Path("stationId") stationId: String,
        @Path("product") product: String,
        @Query("start") start: String,
        @Query("end") end: String,
    ): PlaybackManifestDto
}

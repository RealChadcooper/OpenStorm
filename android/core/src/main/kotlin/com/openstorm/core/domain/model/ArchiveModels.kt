package com.openstorm.core.domain.model

import java.time.Instant

/**
 * Query parameters for browsing archived radar frames.
 */
data class ArchiveQuery(
    val stationId: String,
    val product: String,
    val start: Instant,
    val end: Instant,
    val limit: Int = 200,
)

/**
 * Lightweight frame summary for archive listing.
 */
data class RadarFrameSummary(
    val timestamp: Instant,
    val tileUrl: String,
)

/**
 * Playback manifest returned by the backend — all frames
 * within a time window plus metadata.
 */
data class RadarPlaybackManifest(
    val station: String,
    val product: String,
    val frames: List<RadarFrameSummary>,
    val startTime: Instant,
    val endTime: Instant,
    val frameCount: Int,
    val retentionHours: Int,
)

package com.openstorm.core.domain.model

import java.time.Instant

/**
 * SPC Convective Outlook — categorical or probabilistic risk areas
 * issued by the Storm Prediction Center.
 *
 * Day 1 outlooks update at 06Z, 13Z, 1630Z, 20Z, and 01Z.
 * Day 2 at 06Z and 1730Z. Day 3 at 0730Z.
 *
 * GeoJSON source: https://www.spc.noaa.gov/products/outlook/
 */
data class SpcOutlook(
    val day: Int,                    // 1, 2, or 3
    val type: OutlookType,
    val issuedAt: Instant,
    val expiresAt: Instant,
    val riskAreas: List<SpcRiskArea>,
)

data class SpcRiskArea(
    val riskLevel: SpcRiskLevel,
    val label: String,               // "MRGL", "SLGT", etc.
    val geometry: GeoPolygon,
)

/**
 * SPC categorical risk levels with standard NWS colors.
 * Order matches severity: TSTM is lowest, HIGH is highest.
 */
enum class SpcRiskLevel(
    val label: String,
    val displayName: String,
    val colorArgb: Long,
) {
    /** General thunderstorms (light green) */
    TSTM("TSTM", "Thunderstorm", 0xFFC1E9C1),

    /** Marginal risk (dark green) */
    MRGL("MRGL", "Marginal", 0xFF66A366),

    /** Slight risk (yellow) */
    SLGT("SLGT", "Slight", 0xFFFFE066),

    /** Enhanced risk (orange) */
    ENH("ENH", "Enhanced", 0xFFFFA500),

    /** Moderate risk (red) */
    MDT("MDT", "Moderate", 0xFFFF0000),

    /** High risk (magenta) — rare, ~1% of outlooks */
    HIGH("HIGH", "High", 0xFFFF00FF),
}

/**
 * SPC outlook type — categorical vs. probabilistic.
 */
enum class OutlookType {
    /** Categorical risk areas (MRGL/SLGT/ENH/MDT/HIGH) */
    CATEGORICAL,
    /** Tornado probability contours */
    TORNADO,
    /** Wind probability contours */
    WIND,
    /** Hail probability contours */
    HAIL,
}

/**
 * SPC Mesoscale Discussion — short-fuse analysis products
 * covering areas of active or imminent severe weather.
 *
 * GeoJSON source: https://www.spc.noaa.gov/products/md/
 */
data class SpcMesoscaleDiscussion(
    val id: String,                  // e.g., "MD-2345"
    val number: Int,
    val issuedAt: Instant,
    val expiresAt: Instant,
    val affectedArea: String,
    val summary: String,
    val watchLikely: Boolean,
    val geometry: GeoPolygon?,
)

/**
 * SPC Watch — tornado or severe thunderstorm watch.
 */
data class SpcWatch(
    val id: String,
    val number: Int,
    val type: WatchType,
    val issuedAt: Instant,
    val expiresAt: Instant,
    val summary: String,
    val geometry: GeoPolygon?,
)

enum class WatchType {
    TORNADO,
    SEVERE_THUNDERSTORM,
}

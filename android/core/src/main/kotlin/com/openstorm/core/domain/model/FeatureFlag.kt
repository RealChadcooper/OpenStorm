package com.openstorm.core.domain.model

/**
 * Feature flags control availability of features that depend on
 * data sources which may not be available (commercial licensing,
 * backend processing capacity, etc.).
 */
enum class FeatureFlag(val key: String, val defaultEnabled: Boolean) {
    RADAR_LIVE("radar_live", true),
    RADAR_VELOCITY("radar_velocity", true),
    RADAR_DUAL_POL("radar_dual_pol", false),
    ALERTS("alerts", true),
    SPC_OUTLOOKS("spc_outlooks", true),
    SPC_MESOSCALE("spc_mesoscale", true),
    LOCAL_STORM_REPORTS("local_storm_reports", false),
    SATELLITE_GOES("satellite_goes", false),
    SOUNDING("sounding", false),
    LIGHTNING("lightning", false),
    ARCHIVE_PLAYBACK("archive_playback", true),
    NATIONAL_MOSAIC("national_mosaic", false),
    ROUTE_WEATHER("route_weather", false),
    DUAL_PANE("dual_pane", false),
}

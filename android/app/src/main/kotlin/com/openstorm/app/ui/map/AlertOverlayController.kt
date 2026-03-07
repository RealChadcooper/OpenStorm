package com.openstorm.app.ui.map

import com.openstorm.core.domain.model.Alert
import com.openstorm.core.domain.model.AlertSeverity
import org.maplibre.android.maps.MapLibreMap
import org.maplibre.android.maps.Style
import org.maplibre.android.style.expressions.Expression
import org.maplibre.android.style.layers.FillLayer
import org.maplibre.android.style.layers.LineLayer
import org.maplibre.android.style.layers.PropertyFactory
import org.maplibre.android.style.sources.GeoJsonSource
import org.json.JSONArray
import org.json.JSONObject
import timber.log.Timber

/**
 * Renders NWS alert polygons on the map.
 * Each alert severity level gets a distinct color:
 * - EXTREME: red
 * - SEVERE: orange
 * - MODERATE: amber
 * - MINOR: blue
 */
class AlertOverlayController {

    companion object {
        private const val SOURCE_ID = "alerts-source"
        private const val FILL_LAYER_ID = "alerts-fill"
        private const val LINE_LAYER_ID = "alerts-outline"
    }

    private var isInitialized = false

    fun initialize(style: Style) {
        removeIfExists(style)

        // Empty GeoJSON source
        val source = GeoJsonSource(SOURCE_ID)
        style.addSource(source)

        // Semi-transparent fill colored by severity
        val fillLayer = FillLayer(FILL_LAYER_ID, SOURCE_ID).apply {
            setProperties(
                PropertyFactory.fillColor(
                    Expression.match(
                        Expression.get("severity"),
                        Expression.literal("EXTREME"), Expression.color(android.graphics.Color.parseColor("#DC262660")),
                        Expression.literal("SEVERE"), Expression.color(android.graphics.Color.parseColor("#EA580C50")),
                        Expression.literal("MODERATE"), Expression.color(android.graphics.Color.parseColor("#D9770640")),
                        Expression.literal("MINOR"), Expression.color(android.graphics.Color.parseColor("#2563EB30")),
                        Expression.color(android.graphics.Color.parseColor("#94A3B830")),
                    )
                ),
                PropertyFactory.fillOpacity(0.3f),
            )
        }

        // Solid outline
        val lineLayer = LineLayer(LINE_LAYER_ID, SOURCE_ID).apply {
            setProperties(
                PropertyFactory.lineColor(
                    Expression.match(
                        Expression.get("severity"),
                        Expression.literal("EXTREME"), Expression.color(android.graphics.Color.parseColor("#DC2626")),
                        Expression.literal("SEVERE"), Expression.color(android.graphics.Color.parseColor("#EA580C")),
                        Expression.literal("MODERATE"), Expression.color(android.graphics.Color.parseColor("#D97706")),
                        Expression.literal("MINOR"), Expression.color(android.graphics.Color.parseColor("#2563EB")),
                        Expression.color(android.graphics.Color.parseColor("#94A3B8")),
                    )
                ),
                PropertyFactory.lineWidth(2f),
                PropertyFactory.lineOpacity(0.8f),
            )
        }

        style.addLayer(fillLayer)
        style.addLayer(lineLayer)
        isInitialized = true
    }

    /**
     * Update alert polygons on the map.
     * Converts alert list to GeoJSON FeatureCollection.
     */
    fun updateAlerts(style: Style, alerts: List<Alert>) {
        if (!isInitialized) return

        val source = style.getSourceAs<GeoJsonSource>(SOURCE_ID) ?: return
        val geojson = alertsToGeoJson(alerts)
        source.setGeoJson(geojson)
        Timber.d("Updated ${alerts.size} alert polygons on map")
    }

    fun removeIfExists(style: Style) {
        try { style.removeLayer(LINE_LAYER_ID) } catch (_: Exception) {}
        try { style.removeLayer(FILL_LAYER_ID) } catch (_: Exception) {}
        try { style.removeSource(SOURCE_ID) } catch (_: Exception) {}
        isInitialized = false
    }

    private fun alertsToGeoJson(alerts: List<Alert>): String {
        val features = JSONArray()

        for (alert in alerts) {
            val polygon = alert.geometry ?: continue
            if (polygon.coordinates.isEmpty()) continue

            val feature = JSONObject().apply {
                put("type", "Feature")
                put("properties", JSONObject().apply {
                    put("id", alert.id)
                    put("event", alert.event)
                    put("severity", alert.severity.name)
                    put("headline", alert.headline)
                })
                put("geometry", JSONObject().apply {
                    put("type", "Polygon")
                    val coords = JSONArray()
                    for (ring in polygon.coordinates) {
                        val ringArray = JSONArray()
                        for (point in ring) {
                            ringArray.put(JSONArray().apply {
                                put(point.lon)
                                put(point.lat)
                            })
                        }
                        coords.put(ringArray)
                    }
                    put("coordinates", coords)
                })
            }
            features.put(feature)
        }

        return JSONObject().apply {
            put("type", "FeatureCollection")
            put("features", features)
        }.toString()
    }
}

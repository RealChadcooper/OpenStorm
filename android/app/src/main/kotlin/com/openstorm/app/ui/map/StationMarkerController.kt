package com.openstorm.app.ui.map

import android.graphics.Color
import com.openstorm.core.domain.model.RadarStation
import org.maplibre.android.maps.Style
import org.maplibre.android.style.layers.CircleLayer
import org.maplibre.android.style.layers.PropertyFactory
import org.maplibre.android.style.layers.SymbolLayer
import org.maplibre.android.style.sources.GeoJsonSource
import org.json.JSONArray
import org.json.JSONObject
import timber.log.Timber

/**
 * Renders radar station markers on the map.
 * Active station is highlighted; nearby stations shown as smaller dots.
 */
class StationMarkerController {

    companion object {
        private const val SOURCE_ID = "stations-source"
        private const val CIRCLE_LAYER_ID = "stations-circles"
        private const val ACTIVE_SOURCE_ID = "active-station-source"
        private const val ACTIVE_LAYER_ID = "active-station-circle"
        private const val RANGE_SOURCE_ID = "radar-range-source"
        private const val RANGE_LAYER_ID = "radar-range-circle"
    }

    private var isInitialized = false

    fun initialize(style: Style) {
        removeIfExists(style)

        // All stations source
        style.addSource(GeoJsonSource(SOURCE_ID))
        style.addLayer(CircleLayer(CIRCLE_LAYER_ID, SOURCE_ID).apply {
            setProperties(
                PropertyFactory.circleRadius(4f),
                PropertyFactory.circleColor(Color.parseColor("#94A3B8")),
                PropertyFactory.circleStrokeColor(Color.parseColor("#64748B")),
                PropertyFactory.circleStrokeWidth(1f),
                PropertyFactory.circleOpacity(0.7f),
            )
        })

        // Active station source (larger, highlighted)
        style.addSource(GeoJsonSource(ACTIVE_SOURCE_ID))
        style.addLayer(CircleLayer(ACTIVE_LAYER_ID, ACTIVE_SOURCE_ID).apply {
            setProperties(
                PropertyFactory.circleRadius(8f),
                PropertyFactory.circleColor(Color.parseColor("#38BDF8")),
                PropertyFactory.circleStrokeColor(Color.WHITE),
                PropertyFactory.circleStrokeWidth(2f),
            )
        })

        // Radar range ring (approximately 230km radius for NEXRAD)
        style.addSource(GeoJsonSource(RANGE_SOURCE_ID))
        // Range rings are complex in MapLibre (no native circle geometry).
        // We approximate with a GeoJSON polygon in updateActiveStation.

        isInitialized = true
    }

    fun updateStations(style: Style, stations: List<RadarStation>) {
        if (!isInitialized) return
        val source = style.getSourceAs<GeoJsonSource>(SOURCE_ID) ?: return
        source.setGeoJson(stationsToGeoJson(stations))
    }

    fun updateActiveStation(style: Style, station: RadarStation?) {
        if (!isInitialized || station == null) return
        val source = style.getSourceAs<GeoJsonSource>(ACTIVE_SOURCE_ID) ?: return

        val feature = JSONObject().apply {
            put("type", "FeatureCollection")
            put("features", JSONArray().apply {
                put(JSONObject().apply {
                    put("type", "Feature")
                    put("geometry", JSONObject().apply {
                        put("type", "Point")
                        put("coordinates", JSONArray().apply {
                            put(station.lon)
                            put(station.lat)
                        })
                    })
                    put("properties", JSONObject().apply {
                        put("id", station.id)
                        put("name", station.name)
                    })
                })
            })
        }
        source.setGeoJson(feature.toString())
    }

    fun removeIfExists(style: Style) {
        listOf(RANGE_LAYER_ID, ACTIVE_LAYER_ID, CIRCLE_LAYER_ID).forEach { id ->
            try { style.removeLayer(id) } catch (_: Exception) {}
        }
        listOf(RANGE_SOURCE_ID, ACTIVE_SOURCE_ID, SOURCE_ID).forEach { id ->
            try { style.removeSource(id) } catch (_: Exception) {}
        }
        isInitialized = false
    }

    private fun stationsToGeoJson(stations: List<RadarStation>): String {
        val features = JSONArray()
        for (station in stations) {
            features.put(JSONObject().apply {
                put("type", "Feature")
                put("geometry", JSONObject().apply {
                    put("type", "Point")
                    put("coordinates", JSONArray().apply {
                        put(station.lon)
                        put(station.lat)
                    })
                })
                put("properties", JSONObject().apply {
                    put("id", station.id)
                    put("name", station.name)
                })
            })
        }
        return JSONObject().apply {
            put("type", "FeatureCollection")
            put("features", features)
        }.toString()
    }
}

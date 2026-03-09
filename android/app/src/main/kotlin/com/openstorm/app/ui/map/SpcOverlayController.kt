package com.openstorm.app.ui.map

import com.openstorm.core.domain.model.SpcOutlook
import com.openstorm.core.domain.model.SpcRiskArea
import com.openstorm.core.domain.model.SpcRiskLevel
import com.openstorm.core.domain.model.SpcWatch
import com.openstorm.core.domain.model.WatchType
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
 * Renders SPC convective outlook risk areas and watches on the map.
 *
 * Risk areas are rendered as semi-transparent filled polygons with
 * standard NWS color coding. Rendered below the radar overlay but
 * above the base map.
 *
 * Watches are rendered as dashed outlines (red for tornado, yellow
 * for severe thunderstorm).
 */
class SpcOverlayController {

    companion object {
        private const val OUTLOOK_SOURCE = "spc-outlook-source"
        private const val OUTLOOK_FILL_LAYER = "spc-outlook-fill"
        private const val OUTLOOK_LINE_LAYER = "spc-outlook-line"
        private const val WATCH_SOURCE = "spc-watch-source"
        private const val WATCH_LINE_LAYER = "spc-watch-line"
    }

    private var isInitialized = false

    fun initialize(style: Style) {
        removeIfExists(style)

        // Outlook source + layers
        style.addSource(GeoJsonSource(OUTLOOK_SOURCE))

        style.addLayer(FillLayer(OUTLOOK_FILL_LAYER, OUTLOOK_SOURCE).apply {
            setProperties(
                PropertyFactory.fillColor(Expression.toColor(Expression.get("fill"))),
                PropertyFactory.fillOpacity(0.3f),
            )
        })

        style.addLayer(LineLayer(OUTLOOK_LINE_LAYER, OUTLOOK_SOURCE).apply {
            setProperties(
                PropertyFactory.lineColor(Expression.toColor(Expression.get("stroke"))),
                PropertyFactory.lineWidth(1.5f),
                PropertyFactory.lineOpacity(0.6f),
            )
        })

        // Watch source + layer
        style.addSource(GeoJsonSource(WATCH_SOURCE))

        style.addLayer(LineLayer(WATCH_LINE_LAYER, WATCH_SOURCE).apply {
            setProperties(
                PropertyFactory.lineColor(Expression.toColor(Expression.get("stroke"))),
                PropertyFactory.lineWidth(3f),
                PropertyFactory.lineOpacity(0.8f),
                PropertyFactory.lineDasharray(arrayOf(4f, 3f)),
            )
        })

        isInitialized = true
    }

    /**
     * Update the outlook overlay with risk areas from a Day 1/2/3 outlook.
     */
    fun updateOutlook(style: Style, outlook: SpcOutlook?) {
        if (!isInitialized) return
        val source = style.getSourceAs<GeoJsonSource>(OUTLOOK_SOURCE) ?: return

        if (outlook == null || outlook.riskAreas.isEmpty()) {
            source.setGeoJson(emptyFeatureCollection())
            return
        }

        val geojson = riskAreasToGeoJson(outlook.riskAreas)
        source.setGeoJson(geojson)
        Timber.d("Updated SPC Day ${outlook.day} outlook: ${outlook.riskAreas.size} risk areas")
    }

    /**
     * Update the watch overlay with active watches.
     */
    fun updateWatches(style: Style, watches: List<SpcWatch>) {
        if (!isInitialized) return
        val source = style.getSourceAs<GeoJsonSource>(WATCH_SOURCE) ?: return

        if (watches.isEmpty()) {
            source.setGeoJson(emptyFeatureCollection())
            return
        }

        val geojson = watchesToGeoJson(watches)
        source.setGeoJson(geojson)
        Timber.d("Updated SPC watches: ${watches.size}")
    }

    fun removeIfExists(style: Style) {
        listOf(WATCH_LINE_LAYER, OUTLOOK_LINE_LAYER, OUTLOOK_FILL_LAYER).forEach { id ->
            try { style.removeLayer(id) } catch (_: Exception) {}
        }
        listOf(WATCH_SOURCE, OUTLOOK_SOURCE).forEach { id ->
            try { style.removeSource(id) } catch (_: Exception) {}
        }
        isInitialized = false
    }

    private fun riskAreasToGeoJson(areas: List<SpcRiskArea>): String {
        val features = JSONArray()

        for (area in areas) {
            val color = riskLevelToHexColor(area.riskLevel)
            val feature = JSONObject().apply {
                put("type", "Feature")
                put("properties", JSONObject().apply {
                    put("risk", area.riskLevel.label)
                    put("displayName", area.riskLevel.displayName)
                    put("fill", color)
                    put("fill-opacity", 0.3)
                    put("stroke", color)
                })
                put("geometry", polygonToGeoJson(area.geometry))
            }
            features.put(feature)
        }

        return JSONObject().apply {
            put("type", "FeatureCollection")
            put("features", features)
        }.toString()
    }

    private fun watchesToGeoJson(watches: List<SpcWatch>): String {
        val features = JSONArray()

        for (watch in watches) {
            val geometry = watch.geometry ?: continue
            val color = when (watch.type) {
                WatchType.TORNADO -> "#FF0000"
                WatchType.SEVERE_THUNDERSTORM -> "#FFFF00"
            }
            val feature = JSONObject().apply {
                put("type", "Feature")
                put("properties", JSONObject().apply {
                    put("id", watch.id)
                    put("type", watch.type.name)
                    put("stroke", color)
                })
                put("geometry", polygonToGeoJson(geometry))
            }
            features.put(feature)
        }

        return JSONObject().apply {
            put("type", "FeatureCollection")
            put("features", features)
        }.toString()
    }

    private fun polygonToGeoJson(polygon: com.openstorm.core.domain.model.GeoPolygon): JSONObject {
        return JSONObject().apply {
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
        }
    }

    private fun riskLevelToHexColor(level: SpcRiskLevel): String {
        return when (level) {
            SpcRiskLevel.TSTM -> "#C1E9C1"
            SpcRiskLevel.MRGL -> "#66A366"
            SpcRiskLevel.SLGT -> "#FFE066"
            SpcRiskLevel.ENH -> "#FFA500"
            SpcRiskLevel.MDT -> "#FF0000"
            SpcRiskLevel.HIGH -> "#FF00FF"
        }
    }

    private fun emptyFeatureCollection(): String {
        return """{"type":"FeatureCollection","features":[]}"""
    }
}

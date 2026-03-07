package com.openstorm.app.ui.map

import android.content.Context
import org.json.JSONArray
import org.json.JSONObject

/**
 * Builds MapLibre GL style JSON for OpenStorm.
 *
 * Base layer: OpenFreeMap (https://openfreemap.org) — free, no API key.
 * Uses the "positron" style for light mode and "dark-matter" for dark mode.
 *
 * The style JSON is constructed programmatically so we can inject
 * our radar tile source dynamically without re-downloading style files.
 */
object MapStyleBuilder {

    // OpenFreeMap tile URLs — no API key required
    // These are Protomaps-based vector tiles served by OpenFreeMap
    private const val OPENFREEMAP_LIGHT = "https://tiles.openfreemap.org/styles/positron"
    private const val OPENFREEMAP_DARK = "https://tiles.openfreemap.org/styles/dark"

    // Fallback: MapTiler demo key-free styles from OpenMapTiles
    private const val FALLBACK_LIGHT = "https://demotiles.maplibre.org/style.json"

    /**
     * Returns a style URL for MapLibre.
     * OpenFreeMap provides pre-built GL styles, so we use them directly.
     */
    fun getStyleUrl(isDark: Boolean): String {
        return if (isDark) OPENFREEMAP_DARK else OPENFREEMAP_LIGHT
    }

    /**
     * Build a minimal inline style JSON when we need full control
     * (e.g., to embed the radar source directly in the style).
     *
     * This produces a valid MapLibre GL style spec with:
     * - OpenFreeMap vector tiles as base
     * - A slot for the radar raster source
     */
    fun buildInlineStyle(isDark: Boolean, radarTileUrl: String? = null): String {
        val style = JSONObject().apply {
            put("version", 8)
            put("name", if (isDark) "OpenStorm Dark" else "OpenStorm Light")

            // Sources
            val sources = JSONObject()

            // Base map tiles
            sources.put("openmaptiles", JSONObject().apply {
                put("type", "vector")
                put("url", "https://tiles.openfreemap.org/planet")
            })

            // Radar raster source (if we have a tile URL)
            if (radarTileUrl != null) {
                sources.put("radar", JSONObject().apply {
                    put("type", "raster")
                    put("tiles", JSONArray().apply { put(radarTileUrl) })
                    put("tileSize", 256)
                    put("attribution", "NOAA/NWS")
                    put("minzoom", 2)
                    put("maxzoom", 10)
                })
            }

            put("sources", sources)

            // Simplified layer stack
            val layers = JSONArray()

            // Background
            val bgColor = if (isDark) "#0D1B2A" else "#F8FAFC"
            layers.put(JSONObject().apply {
                put("id", "background")
                put("type", "background")
                put("paint", JSONObject().apply {
                    put("background-color", bgColor)
                })
            })

            // Radar overlay layer
            if (radarTileUrl != null) {
                layers.put(JSONObject().apply {
                    put("id", "radar-overlay")
                    put("type", "raster")
                    put("source", "radar")
                    put("paint", JSONObject().apply {
                        put("raster-opacity", 0.75)
                        put("raster-fade-duration", 200)
                    })
                })
            }

            put("layers", layers)
        }

        return style.toString()
    }
}

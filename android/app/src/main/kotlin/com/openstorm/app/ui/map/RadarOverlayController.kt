package com.openstorm.app.ui.map

import org.maplibre.android.maps.MapLibreMap
import org.maplibre.android.maps.Style
import org.maplibre.android.style.layers.PropertyFactory
import org.maplibre.android.style.layers.RasterLayer
import org.maplibre.android.style.sources.RasterSource
import org.maplibre.android.style.sources.TileSet
import timber.log.Timber

/**
 * Manages the radar tile overlay on the MapLibre map.
 *
 * Strategy for loop animation:
 * - Maintain two alternating raster sources/layers ("radar-a" and "radar-b")
 * - On frame change, update the inactive source to the new tile URL
 * - Crossfade by ramping opacity: active → 0, inactive → target opacity
 * - This avoids a flash/flicker when swapping a single source
 *
 * Tile URL format from backend:
 *   https://cdn.openstorm.app/tiles/{station}/{product}/{timestamp}/{z}/{x}/{y}.webp
 */
class RadarOverlayController {

    companion object {
        private const val SOURCE_A = "radar-source-a"
        private const val SOURCE_B = "radar-source-b"
        private const val LAYER_A = "radar-layer-a"
        private const val LAYER_B = "radar-layer-b"
        private const val RADAR_OPACITY = 0.75f
        private const val MIN_ZOOM = 2
        private const val MAX_ZOOM = 10
        private const val TILE_SIZE = 256
    }

    private var activeIsA = true
    private var currentTileUrl: String? = null
    private var isInitialized = false

    /**
     * Initialize the dual radar sources and layers on the map style.
     * Call this once after the style is loaded.
     */
    fun initialize(map: MapLibreMap, style: Style) {
        // Remove any existing radar layers/sources from previous style loads
        removeIfExists(style)

        // Create two raster sources with a blank initial URL
        val blankTileUrl = "https://tiles.openfreemap.org/blank/{z}/{x}/{y}.png"

        val tileSetA = TileSet("tileset-a", blankTileUrl)
        tileSetA.minZoom = MIN_ZOOM.toFloat()
        tileSetA.maxZoom = MAX_ZOOM.toFloat()

        val tileSetB = TileSet("tileset-b", blankTileUrl)
        tileSetB.minZoom = MIN_ZOOM.toFloat()
        tileSetB.maxZoom = MAX_ZOOM.toFloat()

        val sourceA = RasterSource(SOURCE_A, tileSetA, TILE_SIZE)
        val sourceB = RasterSource(SOURCE_B, tileSetB, TILE_SIZE)

        style.addSource(sourceA)
        style.addSource(sourceB)

        // Layer A — starts visible
        val layerA = RasterLayer(LAYER_A, SOURCE_A).apply {
            setProperties(
                PropertyFactory.rasterOpacity(RADAR_OPACITY),
                PropertyFactory.rasterFadeDuration(0),
                PropertyFactory.rasterResampling("linear"),
            )
        }

        // Layer B — starts invisible
        val layerB = RasterLayer(LAYER_B, SOURCE_B).apply {
            setProperties(
                PropertyFactory.rasterOpacity(0f),
                PropertyFactory.rasterFadeDuration(0),
                PropertyFactory.rasterResampling("linear"),
            )
        }

        style.addLayer(layerA)
        style.addLayer(layerB)

        activeIsA = true
        isInitialized = true
        Timber.d("Radar overlay initialized with dual-buffer layers")
    }

    /**
     * Show radar tiles for a specific frame.
     * Swaps the inactive source to the new tile URL, then crossfades.
     */
    fun showFrame(map: MapLibreMap, tileUrlTemplate: String) {
        val style = map.style ?: return
        if (!isInitialized) {
            Timber.w("Radar overlay not initialized, call initialize() first")
            return
        }

        // Skip if same URL
        if (tileUrlTemplate == currentTileUrl) return
        currentTileUrl = tileUrlTemplate

        // Determine which source/layer pair to activate next
        val nextSourceId = if (activeIsA) SOURCE_B else SOURCE_A
        val nextLayerId = if (activeIsA) LAYER_B else LAYER_A
        val prevLayerId = if (activeIsA) LAYER_A else LAYER_B

        // Replace the inactive source with new tiles
        // MapLibre requires removing and re-adding the source to change tiles
        val nextLayer = style.getLayer(nextLayerId)
        if (nextLayer != null) {
            style.removeLayer(nextLayerId)
        }
        style.removeSource(nextSourceId)

        val newTileSet = TileSet("tileset-${if (activeIsA) "b" else "a"}", tileUrlTemplate)
        newTileSet.minZoom = MIN_ZOOM.toFloat()
        newTileSet.maxZoom = MAX_ZOOM.toFloat()

        val newSource = RasterSource(nextSourceId, newTileSet, TILE_SIZE)
        style.addSource(newSource)

        val newLayer = RasterLayer(nextLayerId, nextSourceId).apply {
            setProperties(
                PropertyFactory.rasterOpacity(RADAR_OPACITY),
                PropertyFactory.rasterFadeDuration(200),
                PropertyFactory.rasterResampling("linear"),
            )
        }
        style.addLayer(newLayer)

        // Fade out the previous layer
        style.getLayer(prevLayerId)?.let { layer ->
            (layer as? RasterLayer)?.setProperties(
                PropertyFactory.rasterOpacity(0f),
            )
        }

        activeIsA = !activeIsA
        Timber.d("Radar frame switched to: $tileUrlTemplate")
    }

    /**
     * Set the radar overlay opacity (0.0 to 1.0).
     */
    fun setOpacity(style: Style, opacity: Float) {
        val activeLayerId = if (activeIsA) LAYER_A else LAYER_B
        style.getLayer(activeLayerId)?.let { layer ->
            (layer as? RasterLayer)?.setProperties(
                PropertyFactory.rasterOpacity(opacity),
            )
        }
    }

    /**
     * Hide the radar overlay completely.
     */
    fun hide(style: Style) {
        style.getLayer(LAYER_A)?.let { (it as? RasterLayer)?.setProperties(PropertyFactory.rasterOpacity(0f)) }
        style.getLayer(LAYER_B)?.let { (it as? RasterLayer)?.setProperties(PropertyFactory.rasterOpacity(0f)) }
    }

    /**
     * Remove all radar sources and layers.
     */
    fun removeIfExists(style: Style) {
        try { style.removeLayer(LAYER_A) } catch (_: Exception) {}
        try { style.removeLayer(LAYER_B) } catch (_: Exception) {}
        try { style.removeSource(SOURCE_A) } catch (_: Exception) {}
        try { style.removeSource(SOURCE_B) } catch (_: Exception) {}
        isInitialized = false
    }
}

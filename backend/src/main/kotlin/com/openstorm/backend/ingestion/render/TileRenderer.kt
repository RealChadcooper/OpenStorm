package com.openstorm.backend.ingestion.render

import com.openstorm.backend.ingestion.nexrad.NexradProduct
import com.openstorm.backend.ingestion.nexrad.ReflectivityColorTable
import org.slf4j.LoggerFactory
import java.awt.image.BufferedImage
import java.io.ByteArrayOutputStream
import javax.imageio.IIOImage
import javax.imageio.ImageIO
import javax.imageio.ImageWriteParam
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.floor
import kotlin.math.ln
import kotlin.math.pow
import kotlin.math.sin
import kotlin.math.sqrt
import kotlin.math.tan

/**
 * Renders decoded NEXRAD radar data into 256x256 slippy map tiles.
 *
 * Strategy:
 * - For each tile (z/x/y), compute the geographic bounds
 * - For each pixel in the tile, compute its lat/lon
 * - Convert lat/lon to polar coordinates relative to the radar site
 * - Look up the gate value from the radial data
 * - Apply the color table
 * - Write out as WebP (with PNG fallback)
 *
 * Tile coordinate system: standard Web Mercator (EPSG:3857) slippy tiles.
 * z=0 is the whole world in one 256x256 tile.
 */
class TileRenderer {

    private val logger = LoggerFactory.getLogger(javaClass)

    companion object {
        const val TILE_SIZE = 256
        private const val EARTH_RADIUS_M = 6_371_000.0

        // Zoom levels to render: 2-10 covers regional to metro scale
        val ZOOM_RANGE = 2..10
    }

    /**
     * Render all tiles that intersect the radar's coverage area.
     *
     * @param product Decoded NEXRAD product
     * @return List of rendered tiles with their coordinates and image bytes
     */
    fun renderTiles(product: NexradProduct): List<RenderedTile> {
        val tiles = mutableListOf<RenderedTile>()
        val maxRangeKm = product.maxRangeKm

        for (zoom in ZOOM_RANGE) {
            val intersecting = findIntersectingTiles(
                product.latitude, product.longitude, maxRangeKm, zoom
            )

            for ((x, y) in intersecting) {
                val tile = renderTile(product, zoom, x, y)
                if (tile != null) {
                    tiles.add(tile)
                }
            }
        }

        logger.info(
            "Rendered {} tiles for {}/{} at {}",
            tiles.size, product.stationId, product.productCode, product.timestamp
        )
        return tiles
    }

    /**
     * Render a single tile.
     * Returns null if the tile contains no radar data (all transparent).
     */
    fun renderTile(product: NexradProduct, zoom: Int, tileX: Int, tileY: Int): RenderedTile? {
        val image = BufferedImage(TILE_SIZE, TILE_SIZE, BufferedImage.TYPE_INT_ARGB)
        var hasData = false

        // Precompute: organize radials by azimuth for O(1) lookup
        val azimuthIndex = buildAzimuthIndex(product)

        // Tile bounds in lat/lon
        val north = tileToLat(tileY, zoom)
        val south = tileToLat(tileY + 1, zoom)
        val west = tileToLon(tileX, zoom)
        val east = tileToLon(tileX + 1, zoom)

        val latStep = (north - south) / TILE_SIZE
        val lonStep = (east - west) / TILE_SIZE

        for (py in 0 until TILE_SIZE) {
            val lat = north - py * latStep
            for (px in 0 until TILE_SIZE) {
                val lon = west + px * lonStep

                // Convert to polar relative to radar
                val (rangeM, azimuthDeg) = latLonToRadarPolar(
                    lat, lon, product.latitude, product.longitude
                )

                // Look up gate value
                val rangeIndex = ((rangeM - product.firstBinRange) / product.resolution).toInt()
                if (rangeIndex < 0 || rangeIndex >= product.rangeBinCount) continue

                val radial = findRadial(azimuthIndex, azimuthDeg) ?: continue
                if (rangeIndex >= radial.gates.size) continue

                val gateValue = radial.gates[rangeIndex]
                val color = ReflectivityColorTable.getColorForGate(
                    gateValue, product.dataScale, product.dataOffset
                )

                if (color != 0) {
                    image.setRGB(px, py, color)
                    hasData = true
                }
            }
        }

        if (!hasData) return null

        val imageBytes = encodeWebP(image) ?: encodePng(image)

        return RenderedTile(
            zoom = zoom,
            x = tileX,
            y = tileY,
            data = imageBytes,
            format = if (imageBytes.size > 0 && imageBytes[0] == 'R'.code.toByte()) "webp" else "png",
        )
    }

    /**
     * Find all tile coordinates at a given zoom that intersect a circle
     * centered at (lat, lon) with the given radius.
     */
    private fun findIntersectingTiles(
        centerLat: Double,
        centerLon: Double,
        radiusKm: Double,
        zoom: Int,
    ): List<Pair<Int, Int>> {
        val n = 1 shl zoom

        // Compute bounding box of the radar coverage circle
        val latDelta = radiusKm / 111.32  // ~111.32 km per degree latitude
        val lonDelta = radiusKm / (111.32 * cos(Math.toRadians(centerLat)))

        val minLat = (centerLat - latDelta).coerceIn(-85.0511, 85.0511)
        val maxLat = (centerLat + latDelta).coerceIn(-85.0511, 85.0511)
        val minLon = (centerLon - lonDelta).coerceIn(-180.0, 180.0)
        val maxLon = (centerLon + lonDelta).coerceIn(-180.0, 180.0)

        val minTileX = lonToTileX(minLon, zoom).coerceIn(0, n - 1)
        val maxTileX = lonToTileX(maxLon, zoom).coerceIn(0, n - 1)
        val minTileY = latToTileY(maxLat, zoom).coerceIn(0, n - 1)  // note: Y is inverted
        val maxTileY = latToTileY(minLat, zoom).coerceIn(0, n - 1)

        val tiles = mutableListOf<Pair<Int, Int>>()
        for (x in minTileX..maxTileX) {
            for (y in minTileY..maxTileY) {
                tiles.add(x to y)
            }
        }
        return tiles
    }

    /**
     * Build an index of radials sorted by azimuth for fast lookup.
     * Array of 3600 slots (0.1° resolution), each pointing to the nearest radial.
     */
    private fun buildAzimuthIndex(product: NexradProduct): Array<com.openstorm.backend.ingestion.nexrad.Radial?> {
        val index = arrayOfNulls<com.openstorm.backend.ingestion.nexrad.Radial>(3600)
        for (radial in product.radials) {
            val startSlot = (radial.azimuth * 10).toInt().mod(3600)
            val endSlot = ((radial.azimuth + radial.deltaAngle) * 10).toInt().mod(3600)

            if (startSlot <= endSlot) {
                for (i in startSlot until endSlot) {
                    index[i] = radial
                }
            } else {
                // Wraps around 360°
                for (i in startSlot until 3600) index[i] = radial
                for (i in 0 until endSlot) index[i] = radial
            }
        }
        return index
    }

    private fun findRadial(
        index: Array<com.openstorm.backend.ingestion.nexrad.Radial?>,
        azimuthDeg: Double,
    ): com.openstorm.backend.ingestion.nexrad.Radial? {
        val slot = (azimuthDeg * 10).toInt().mod(3600)
        return index[slot]
    }

    /**
     * Convert a point's lat/lon to range (meters) and azimuth (degrees)
     * relative to the radar site, using the Haversine formula for range
     * and forward azimuth for bearing.
     */
    private fun latLonToRadarPolar(
        lat: Double, lon: Double,
        radarLat: Double, radarLon: Double,
    ): Pair<Double, Double> {
        val lat1 = Math.toRadians(radarLat)
        val lat2 = Math.toRadians(lat)
        val dLat = lat2 - lat1
        val dLon = Math.toRadians(lon - radarLon)

        // Haversine distance
        val a = sin(dLat / 2).pow(2) + cos(lat1) * cos(lat2) * sin(dLon / 2).pow(2)
        val c = 2 * atan2(sqrt(a), sqrt(1 - a))
        val rangeM = EARTH_RADIUS_M * c

        // Forward azimuth (bearing from radar to point)
        val y = sin(dLon) * cos(lat2)
        val x = cos(lat1) * sin(lat2) - sin(lat1) * cos(lat2) * cos(dLon)
        var azimuth = Math.toDegrees(atan2(y, x))
        if (azimuth < 0) azimuth += 360.0

        return rangeM to azimuth
    }

    // ── Slippy map tile math ──

    private fun lonToTileX(lon: Double, zoom: Int): Int {
        return floor((lon + 180.0) / 360.0 * (1 shl zoom)).toInt()
    }

    private fun latToTileY(lat: Double, zoom: Int): Int {
        val latRad = Math.toRadians(lat)
        return floor((1.0 - ln(tan(latRad) + 1.0 / cos(latRad)) / PI) / 2.0 * (1 shl zoom)).toInt()
    }

    private fun tileToLat(y: Int, zoom: Int): Double {
        val n = PI - 2.0 * PI * y / (1 shl zoom)
        return Math.toDegrees(atan2(0.5 * (Math.exp(n) - Math.exp(-n)), 1.0))
    }

    private fun tileToLon(x: Int, zoom: Int): Double {
        return x.toDouble() / (1 shl zoom) * 360.0 - 180.0
    }

    // ── Image encoding ──

    private fun encodeWebP(image: BufferedImage): ByteArray? {
        // Java's ImageIO may not have WebP support without a plugin.
        // If not available, fall back to PNG.
        val writers = ImageIO.getImageWritersByMIMEType("image/webp")
        if (!writers.hasNext()) return null

        return try {
            val writer = writers.next()
            val baos = ByteArrayOutputStream()
            val ios = ImageIO.createImageOutputStream(baos)
            writer.output = ios
            val param = writer.defaultWriteParam
            if (param.canWriteCompressed()) {
                param.compressionMode = ImageWriteParam.MODE_EXPLICIT
                param.compressionQuality = 0.8f
            }
            writer.write(null, IIOImage(image, null, null), param)
            ios.flush()
            writer.dispose()
            baos.toByteArray()
        } catch (e: Exception) {
            logger.debug("WebP encoding not available, falling back to PNG: ${e.message}")
            null
        }
    }

    private fun encodePng(image: BufferedImage): ByteArray {
        val baos = ByteArrayOutputStream()
        ImageIO.write(image, "png", baos)
        return baos.toByteArray()
    }
}

data class RenderedTile(
    val zoom: Int,
    val x: Int,
    val y: Int,
    val data: ByteArray,
    val format: String, // "webp" or "png"
) {
    /** S3 key path: tiles/{station}/{product}/{timestamp}/z/x/y.{format} */
    fun toObjectKey(stationId: String, product: String, timestamp: String): String {
        return "tiles/$stationId/$product/$timestamp/$zoom/$x/$y.$format"
    }
}

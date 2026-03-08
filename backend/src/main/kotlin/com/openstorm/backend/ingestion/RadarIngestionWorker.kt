package com.openstorm.backend.ingestion

import com.openstorm.backend.config.AppConfig
import com.openstorm.backend.ingestion.nexrad.NexradLevel3Decoder
import com.openstorm.backend.ingestion.poller.TgftpPoller
import com.openstorm.backend.ingestion.render.TileRenderer
import com.openstorm.backend.ingestion.store.S3TileStore
import com.openstorm.backend.model.RadarFramesTable
import com.openstorm.backend.model.RadarStationsTable
import io.ktor.client.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.withContext
import org.jetbrains.exposed.sql.SqlExpressionBuilder.less
import org.jetbrains.exposed.sql.deleteWhere
import org.jetbrains.exposed.sql.insert
import org.jetbrains.exposed.sql.selectAll
import org.jetbrains.exposed.sql.transactions.experimental.newSuspendedTransaction
import org.slf4j.LoggerFactory
import java.time.Instant
import java.time.ZoneOffset
import java.time.format.DateTimeFormatter
import java.time.temporal.ChronoUnit

/**
 * Background worker that polls NOAA TGFTP for new NEXRAD Level III products,
 * decodes the binary data, renders it into slippy map tiles, and uploads
 * the tiles to S3-compatible object storage.
 *
 * Pipeline per station:
 *   1. Poll TGFTP for latest product file (sn.last)
 *   2. Decode binary NEXRAD Level III format → NexradProduct
 *   3. Render NexradProduct → List<RenderedTile> (256x256 WebP/PNG at z2-z10)
 *   4. Upload tiles to S3 → tile URL template
 *   5. Insert frame metadata into radar_frames table
 *   6. Clean up expired tiles from S3
 *
 * Concurrency: processes stations with a semaphore to limit concurrent
 * TGFTP requests and tile rendering load.
 */
class RadarIngestionWorker(
    private val config: AppConfig,
    private val httpClient: HttpClient,
) {
    private val logger = LoggerFactory.getLogger(javaClass)

    private val poller = TgftpPoller(httpClient, config.noaaTgftpBaseUrl)
    private val decoder = NexradLevel3Decoder()
    private val renderer = TileRenderer()
    private val tileStore = S3TileStore(config)

    private val concurrencySemaphore = Semaphore(config.ingestionStationBatchSize)

    // Track last-seen timestamps to avoid reprocessing the same data
    private val lastProcessed = mutableMapOf<String, Instant>()

    // Products to ingest (start with base reflectivity)
    private val products = listOf("N0Q")

    suspend fun start() {
        logger.info(
            "Radar ingestion worker started — interval: {}ms, batch: {}, products: {}",
            config.ingestionIntervalMs, config.ingestionStationBatchSize, products
        )

        // Ensure S3 bucket exists
        try {
            tileStore.ensureBucket()
        } catch (e: Exception) {
            logger.warn("Could not initialize S3 bucket (will retry on upload): ${e.message}")
        }

        while (true) {
            try {
                ingest()
            } catch (e: Exception) {
                logger.error("Radar ingestion cycle error", e)
            }
            delay(config.ingestionIntervalMs)
        }
    }

    private suspend fun ingest() {
        val startTime = System.currentTimeMillis()

        val stationIds = getActiveStationIds()
        if (stationIds.isEmpty()) {
            logger.warn("No active stations to ingest")
            return
        }

        logger.info("Starting ingestion cycle for ${stationIds.size} stations")

        var processedCount = 0
        var skippedCount = 0
        var errorCount = 0

        for (stationId in stationIds) {
            for (product in products) {
                concurrencySemaphore.acquire()
                try {
                    when (processStation(stationId, product)) {
                        ProcessResult.NEW_DATA -> processedCount++
                        ProcessResult.NO_CHANGE -> skippedCount++
                        ProcessResult.ERROR -> errorCount++
                    }
                } finally {
                    concurrencySemaphore.release()
                }
            }
        }

        // Clean up expired frames
        try {
            cleanupExpiredFrames()
        } catch (e: Exception) {
            logger.warn("Cleanup failed: ${e.message}")
        }

        val elapsed = System.currentTimeMillis() - startTime
        logger.info(
            "Ingestion cycle: {} new, {} unchanged, {} errors — {}ms",
            processedCount, skippedCount, errorCount, elapsed
        )
    }

    /**
     * Process a single station/product: fetch → decode → render → upload → record.
     */
    private suspend fun processStation(stationId: String, product: String): ProcessResult {
        val cacheKey = "$stationId/$product"

        return try {
            // 1. Fetch from TGFTP
            val fetchResult = poller.fetchLatestProduct(stationId, product)
            if (fetchResult == null) {
                logger.debug("No data from TGFTP for $cacheKey")
                return ProcessResult.NO_CHANGE
            }

            // 2. Decode NEXRAD binary
            val nexradProduct = try {
                decoder.decode(fetchResult.data)
            } catch (e: Exception) {
                logger.warn("Decode failed for $cacheKey: ${e.message}")
                return ProcessResult.ERROR
            }

            // Skip if we already have this timestamp
            val lastSeen = lastProcessed[cacheKey]
            if (lastSeen != null && !nexradProduct.timestamp.isAfter(lastSeen)) {
                return ProcessResult.NO_CHANGE
            }

            if (nexradProduct.radials.isEmpty()) {
                logger.debug("No radial data in $cacheKey")
                return ProcessResult.NO_CHANGE
            }

            // 3. Render tiles
            val tiles = renderer.renderTiles(nexradProduct)
            if (tiles.isEmpty()) {
                logger.debug("No tiles rendered for $cacheKey")
                return ProcessResult.NO_CHANGE
            }

            // 4. Upload to S3
            val timestampStr = TIMESTAMP_FORMAT.format(nexradProduct.timestamp)
            val tileUrlTemplate = tileStore.uploadTiles(
                tiles = tiles,
                stationId = stationId,
                product = product,
                timestamp = timestampStr,
            )

            // 5. Record frame in database
            val expiresAt = nexradProduct.timestamp.plus(FRAME_TTL_HOURS, ChronoUnit.HOURS)
            recordFrame(stationId, product, nexradProduct.timestamp, tileUrlTemplate, expiresAt)

            lastProcessed[cacheKey] = nexradProduct.timestamp

            logger.info(
                "Ingested {}: {} radials → {} tiles, ts={}",
                cacheKey, nexradProduct.radials.size, tiles.size, timestampStr
            )
            ProcessResult.NEW_DATA
        } catch (e: Exception) {
            logger.error("Error processing $cacheKey", e)
            ProcessResult.ERROR
        }
    }

    private suspend fun getActiveStationIds(): List<String> {
        return try {
            newSuspendedTransaction(Dispatchers.IO) {
                RadarStationsTable.selectAll()
                    .map { it[RadarStationsTable.id] }
            }
        } catch (e: Exception) {
            logger.debug("DB unavailable for station list, using defaults: ${e.message}")
            DEFAULT_PRIORITY_STATIONS
        }
    }

    private suspend fun recordFrame(
        stationId: String,
        product: String,
        capturedAt: Instant,
        tileBaseUrl: String,
        expiresAt: Instant,
    ) {
        try {
            newSuspendedTransaction(Dispatchers.IO) {
                RadarFramesTable.insert {
                    it[RadarFramesTable.stationId] = stationId
                    it[RadarFramesTable.product] = product
                    it[RadarFramesTable.capturedAt] = capturedAt
                    it[RadarFramesTable.tileBaseUrl] = tileBaseUrl
                    it[RadarFramesTable.expiresAt] = expiresAt
                    it[RadarFramesTable.createdAt] = Instant.now()
                }
            }
        } catch (e: Exception) {
            if (e.message?.contains("unique", ignoreCase = true) == true ||
                e.message?.contains("duplicate", ignoreCase = true) == true
            ) {
                logger.debug("Frame already exists: $stationId/$product/$capturedAt")
            } else {
                logger.warn("Failed to record frame: ${e.message}")
            }
        }
    }

    private suspend fun cleanupExpiredFrames() {
        val cutoff = Instant.now()
        try {
            val expiredFrames = newSuspendedTransaction(Dispatchers.IO) {
                RadarFramesTable.selectAll()
                    .where { RadarFramesTable.expiresAt less cutoff }
                    .map {
                        Triple(
                            it[RadarFramesTable.stationId],
                            it[RadarFramesTable.product],
                            it[RadarFramesTable.capturedAt],
                        )
                    }
            }

            for ((station, product, timestamp) in expiredFrames) {
                val tsStr = TIMESTAMP_FORMAT.format(timestamp)
                tileStore.deletePrefix("tiles/$station/$product/$tsStr/")
            }

            if (expiredFrames.isNotEmpty()) {
                newSuspendedTransaction(Dispatchers.IO) {
                    RadarFramesTable.deleteWhere {
                        RadarFramesTable.expiresAt less cutoff
                    }
                }
                logger.info("Cleaned up ${expiredFrames.size} expired frames")
            }
        } catch (e: Exception) {
            logger.debug("Cleanup skipped: ${e.message}")
        }
    }

    private enum class ProcessResult { NEW_DATA, NO_CHANGE, ERROR }

    companion object {
        private val TIMESTAMP_FORMAT = DateTimeFormatter
            .ofPattern("yyyy-MM-dd'T'HH:mm:ss'Z'")
            .withZone(ZoneOffset.UTC)

        private const val FRAME_TTL_HOURS = 24L

        // High-traffic stations always ingested when DB is unavailable
        val DEFAULT_PRIORITY_STATIONS = listOf(
            "KTLX", "KFWS", "KHGX", "KLIX", "KAMX", "KTBW", "KJAX",
            "KFFC", "KRAX", "KLWX", "KOKX", "KBOX", "KCLE", "KIND",
            "KLOT", "KMPX", "KEAX", "KLSX", "KDEN", "KATX", "KRTX",
            "KMUX", "KSOX", "KIWA",
        )
    }
}

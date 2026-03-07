package com.openstorm.backend.ingestion.poller

import io.ktor.client.*
import io.ktor.client.request.*
import io.ktor.client.statement.*
import io.ktor.http.*
import org.slf4j.LoggerFactory
import java.time.Instant
import java.time.ZoneOffset
import java.time.format.DateTimeFormatter

/**
 * Polls NOAA TGFTP for new NEXRAD Level III product files.
 *
 * TGFTP directory structure:
 *   https://tgftp.nws.noaa.gov/SL.us008001/DF.of/DC.radar/DS.{product}/SI.{station}/
 *
 * Product codes on TGFTP:
 *   - DS.p94cr  → N0Q (Base Reflectivity, 0.5° 256-level, 124nm)
 *   - DS.p99cr  → N0U (Base Velocity)
 *   - DS.p153cr → Digital reflectivity (higher res)
 *   - DS.p154cr → Digital velocity
 *
 * Each directory contains:
 *   - sn.last   → The most recent product file
 *   - sn.0001   → Previous file
 *   - sn.0002   → Older file
 *   ...
 *
 * The "sn.last" file is what we poll to get the latest data.
 * We download the binary content, decode it with NexradLevel3Decoder.
 */
class TgftpPoller(
    private val client: HttpClient,
    private val baseUrl: String,
) {
    private val logger = LoggerFactory.getLogger(javaClass)

    companion object {
        // Map from our product codes to TGFTP dataset names
        // DS.p94cr is the operational 256-level reflectivity
        // DS.p153cr is the digital (higher-res) reflectivity
        private val PRODUCT_TO_DATASET = mapOf(
            "N0Q" to "p19r0",  // Digital base reflectivity (0.5°)
            "N0U" to "p27v0",  // Digital base velocity (0.5°)
            "N0C" to "p34c0",  // Digital correlation coefficient
            "N0X" to "p33z0",  // Digital differential reflectivity
            "N0K" to "p32k0",  // Digital specific diff phase
        )

        // Fallback: legacy product dataset paths
        private val LEGACY_DATASETS = mapOf(
            "N0Q" to "p94r0",  // Base reflectivity 94
            "N0U" to "p99v0",  // Base velocity 99
        )
    }

    /**
     * Download the latest product file for a station.
     *
     * Tries the digital product first, then falls back to legacy.
     *
     * @return Raw product bytes, or null if unavailable
     */
    suspend fun fetchLatestProduct(stationId: String, product: String): FetchResult? {
        val datasets = listOfNotNull(
            PRODUCT_TO_DATASET[product],
            LEGACY_DATASETS[product],
        )

        for (dataset in datasets) {
            val result = tryFetch(stationId, dataset)
            if (result != null) return result
        }

        logger.debug("No data available for $stationId/$product")
        return null
    }

    /**
     * Fetch multiple historical files (sn.0001 through sn.{count}).
     * Useful for initial backfill of the frame loop.
     */
    suspend fun fetchRecentProducts(
        stationId: String,
        product: String,
        count: Int = 10,
    ): List<FetchResult> {
        val dataset = PRODUCT_TO_DATASET[product] ?: LEGACY_DATASETS[product] ?: return emptyList()
        val results = mutableListOf<FetchResult>()

        // sn.last is the most recent
        tryFetch(stationId, dataset)?.let { results.add(it) }

        // sn.0001, sn.0002, ... are older
        for (i in 1 until count) {
            val seqName = "sn.%04d".format(i)
            val url = buildProductUrl(stationId, dataset, seqName)
            try {
                val response = client.get(url) {
                    header("User-Agent", "(OpenStorm, contact@openstorm.app)")
                }
                if (response.status == HttpStatusCode.OK) {
                    val bytes = response.readRawBytes()
                    if (bytes.isNotEmpty()) {
                        results.add(FetchResult(
                            stationId = stationId,
                            product = product,
                            data = bytes,
                            fetchedAt = Instant.now(),
                        ))
                    }
                }
            } catch (e: Exception) {
                // Older files may not exist — that's fine
                break
            }
        }

        return results
    }

    private suspend fun tryFetch(stationId: String, dataset: String): FetchResult? {
        val url = buildProductUrl(stationId, dataset, "sn.last")

        return try {
            val response = client.get(url) {
                header("User-Agent", "(OpenStorm, contact@openstorm.app)")
            }
            if (response.status != HttpStatusCode.OK) {
                logger.debug("TGFTP returned ${response.status} for $url")
                return null
            }

            val bytes = response.readRawBytes()
            if (bytes.isEmpty()) {
                logger.debug("Empty response from $url")
                return null
            }

            // Extract product code from dataset name
            val product = PRODUCT_TO_DATASET.entries
                .find { it.value == dataset }?.key
                ?: LEGACY_DATASETS.entries.find { it.value == dataset }?.key
                ?: "UNK"

            FetchResult(
                stationId = stationId.uppercase(),
                product = product,
                data = bytes,
                fetchedAt = Instant.now(),
            )
        } catch (e: Exception) {
            logger.warn("Failed to fetch from TGFTP: $url — ${e.message}")
            null
        }
    }

    /**
     * Build the TGFTP URL for a product file.
     *
     * Format:
     *   {baseUrl}/SL.us008001/DF.of/DC.radar/DS.{dataset}/SI.{station}/{file}
     *
     * Station ID is lowercase in the path and prefixed with SI.
     */
    private fun buildProductUrl(stationId: String, dataset: String, file: String): String {
        val sid = stationId.lowercase()
        return "$baseUrl/SL.us008001/DF.of/DC.radar/DS.$dataset/SI.$sid/$file"
    }
}

data class FetchResult(
    val stationId: String,
    val product: String,
    val data: ByteArray,
    val fetchedAt: Instant,
)

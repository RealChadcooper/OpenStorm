package com.openstorm.backend.ingestion.store

import com.openstorm.backend.config.AppConfig
import com.openstorm.backend.ingestion.render.RenderedTile
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.slf4j.LoggerFactory
import software.amazon.awssdk.auth.credentials.AwsBasicCredentials
import software.amazon.awssdk.auth.credentials.StaticCredentialsProvider
import software.amazon.awssdk.core.sync.RequestBody
import software.amazon.awssdk.regions.Region
import software.amazon.awssdk.services.s3.S3Client
import software.amazon.awssdk.services.s3.model.CreateBucketRequest
import software.amazon.awssdk.services.s3.model.HeadBucketRequest
import software.amazon.awssdk.services.s3.model.NoSuchBucketException
import software.amazon.awssdk.services.s3.model.PutObjectRequest
import java.net.URI

/**
 * Uploads rendered radar tiles to S3-compatible object storage.
 *
 * Supports AWS S3, MinIO, DigitalOcean Spaces, Cloudflare R2, etc.
 *
 * Object key structure:
 *   tiles/{stationId}/{product}/{timestamp}/{z}/{x}/{y}.{format}
 *
 * Example:
 *   tiles/KTLX/N0Q/2026-03-07T12:00:00Z/7/29/49.webp
 *
 * The tile CDN URL is then:
 *   {tileCdnBaseUrl}/tiles/KTLX/N0Q/2026-03-07T12:00:00Z/{z}/{x}/{y}.webp
 */
class S3TileStore(private val config: AppConfig) {

    private val logger = LoggerFactory.getLogger(javaClass)

    private val s3: S3Client = S3Client.builder()
        .endpointOverride(URI.create(config.s3Endpoint))
        .region(Region.of(config.s3Region))
        .credentialsProvider(
            StaticCredentialsProvider.create(
                AwsBasicCredentials.create(config.s3AccessKey, config.s3SecretKey)
            )
        )
        .forcePathStyle(true) // Required for MinIO and most S3-compatible stores
        .build()

    /**
     * Ensure the tile bucket exists, creating it if necessary.
     */
    suspend fun ensureBucket() = withContext(Dispatchers.IO) {
        try {
            s3.headBucket(HeadBucketRequest.builder().bucket(config.s3Bucket).build())
            logger.info("S3 bucket '${config.s3Bucket}' exists")
        } catch (e: NoSuchBucketException) {
            logger.info("Creating S3 bucket '${config.s3Bucket}'")
            s3.createBucket(CreateBucketRequest.builder().bucket(config.s3Bucket).build())
        } catch (e: Exception) {
            logger.warn("Could not verify S3 bucket (will try uploads anyway): ${e.message}")
        }
    }

    /**
     * Upload a batch of rendered tiles for a single radar frame.
     *
     * @return The tile URL template for this frame (with {z}/{x}/{y} placeholders)
     */
    suspend fun uploadTiles(
        tiles: List<RenderedTile>,
        stationId: String,
        product: String,
        timestamp: String,
    ): String = withContext(Dispatchers.IO) {
        var uploaded = 0
        val format = tiles.firstOrNull()?.format ?: "png"

        for (tile in tiles) {
            val key = tile.toObjectKey(stationId, product, timestamp)
            val contentType = if (tile.format == "webp") "image/webp" else "image/png"

            try {
                s3.putObject(
                    PutObjectRequest.builder()
                        .bucket(config.s3Bucket)
                        .key(key)
                        .contentType(contentType)
                        .cacheControl("public, max-age=3600")
                        .build(),
                    RequestBody.fromBytes(tile.data),
                )
                uploaded++
            } catch (e: Exception) {
                logger.error("Failed to upload tile $key: ${e.message}")
            }
        }

        logger.info("Uploaded $uploaded/${tiles.size} tiles for $stationId/$product/$timestamp")

        // Return the tile URL template
        "${config.tileCdnBaseUrl}/tiles/$stationId/$product/$timestamp/{z}/{x}/{y}.$format"
    }

    /**
     * Delete old tiles for a station/product that have expired.
     * Uses S3 list + delete to clean up storage.
     */
    suspend fun deletePrefix(prefix: String) = withContext(Dispatchers.IO) {
        try {
            val listResponse = s3.listObjectsV2 {
                it.bucket(config.s3Bucket).prefix(prefix)
            }
            val keys = listResponse.contents().map { it.key() }

            if (keys.isNotEmpty()) {
                for (key in keys) {
                    s3.deleteObject { it.bucket(config.s3Bucket).key(key) }
                }
                logger.debug("Deleted ${keys.size} objects under prefix: $prefix")
            }
        } catch (e: Exception) {
            logger.warn("Failed to delete prefix $prefix: ${e.message}")
        }
    }

    fun close() {
        s3.close()
    }
}

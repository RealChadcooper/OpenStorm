package com.openstorm.backend.config

data class AppConfig(
    val port: Int,
    val devMode: Boolean,
    val databaseUrl: String,
    val databaseUser: String,
    val databasePassword: String,
    val redisUrl: String,
    val objectStorageUrl: String,
    val noaaTgftpBaseUrl: String,
    val nwsApiBaseUrl: String,
    // S3-compatible object storage for radar tiles
    val s3Endpoint: String,
    val s3Region: String,
    val s3Bucket: String,
    val s3AccessKey: String,
    val s3SecretKey: String,
    val tileCdnBaseUrl: String,
    // Ingestion tuning
    val ingestionIntervalMs: Long,
    val ingestionStationBatchSize: Int,
) {
    companion object {
        fun load() = AppConfig(
            port = env("PORT", "8080").toInt(),
            devMode = env("DEV_MODE", "false").toBoolean(),
            databaseUrl = env("DATABASE_URL", "jdbc:postgresql://localhost:5432/openstorm"),
            databaseUser = env("DATABASE_USER", "openstorm"),
            databasePassword = env("DATABASE_PASSWORD", "openstorm"),
            redisUrl = env("REDIS_URL", "redis://localhost:6379"),
            objectStorageUrl = env("OBJECT_STORAGE_URL", "http://localhost:9000"),
            noaaTgftpBaseUrl = env("NOAA_TGFTP_URL", "https://tgftp.nws.noaa.gov"),
            nwsApiBaseUrl = env("NWS_API_URL", "https://api.weather.gov"),
            s3Endpoint = env("S3_ENDPOINT", "http://localhost:9000"),
            s3Region = env("S3_REGION", "us-east-1"),
            s3Bucket = env("S3_BUCKET", "openstorm-tiles"),
            s3AccessKey = env("S3_ACCESS_KEY", "minioadmin"),
            s3SecretKey = env("S3_SECRET_KEY", "minioadmin"),
            tileCdnBaseUrl = env("TILE_CDN_BASE_URL", "http://localhost:9000/openstorm-tiles"),
            ingestionIntervalMs = env("INGESTION_INTERVAL_MS", "120000").toLong(),
            ingestionStationBatchSize = env("INGESTION_STATION_BATCH_SIZE", "20").toInt(),
        )

        private fun env(key: String, default: String): String =
            System.getenv(key) ?: default
    }
}

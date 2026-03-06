package com.openstorm.backend.config

data class AppConfig(
    val port: Int,
    val databaseUrl: String,
    val databaseUser: String,
    val databasePassword: String,
    val redisUrl: String,
    val objectStorageUrl: String,
    val noaaTgftpBaseUrl: String,
    val nwsApiBaseUrl: String,
) {
    companion object {
        fun load() = AppConfig(
            port = env("PORT", "8080").toInt(),
            databaseUrl = env("DATABASE_URL", "jdbc:postgresql://localhost:5432/openstorm"),
            databaseUser = env("DATABASE_USER", "openstorm"),
            databasePassword = env("DATABASE_PASSWORD", "openstorm"),
            redisUrl = env("REDIS_URL", "redis://localhost:6379"),
            objectStorageUrl = env("OBJECT_STORAGE_URL", "http://localhost:9000"),
            noaaTgftpBaseUrl = env("NOAA_TGFTP_URL", "https://tgftp.nws.noaa.gov"),
            nwsApiBaseUrl = env("NWS_API_URL", "https://api.weather.gov"),
        )

        private fun env(key: String, default: String): String =
            System.getenv(key) ?: default
    }
}

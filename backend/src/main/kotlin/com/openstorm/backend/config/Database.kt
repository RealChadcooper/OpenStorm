package com.openstorm.backend.config

import com.zaxxer.hikari.HikariConfig
import com.zaxxer.hikari.HikariDataSource
import io.ktor.server.application.*
import org.jetbrains.exposed.sql.Database
import org.jetbrains.exposed.sql.SchemaUtils
import org.jetbrains.exposed.sql.transactions.transaction
import com.openstorm.backend.model.RadarStationsTable
import com.openstorm.backend.model.RadarFramesTable
import com.openstorm.backend.model.AlertsTable
import org.slf4j.LoggerFactory

private val logger = LoggerFactory.getLogger("Database")

fun Application.configureDatabase(config: AppConfig) {
    val hikariConfig = HikariConfig().apply {
        jdbcUrl = config.databaseUrl
        username = config.databaseUser
        password = config.databasePassword
        driverClassName = "org.postgresql.Driver"
        maximumPoolSize = 10
        isAutoCommit = false
        transactionIsolation = "TRANSACTION_REPEATABLE_READ"
        validate()
    }

    try {
        val dataSource = HikariDataSource(hikariConfig)
        Database.connect(dataSource)

        transaction {
            SchemaUtils.create(
                RadarStationsTable,
                RadarFramesTable,
                AlertsTable,
            )
        }
        logger.info("Database connected and schema initialized")
    } catch (e: Exception) {
        logger.warn("Database connection failed (running without DB): ${e.message}")
    }
}

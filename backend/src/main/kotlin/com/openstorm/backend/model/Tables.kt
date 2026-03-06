package com.openstorm.backend.model

import org.jetbrains.exposed.sql.Table
import org.jetbrains.exposed.sql.javatime.timestamp

object RadarStationsTable : Table("radar_stations") {
    val id = varchar("id", 4)
    val name = text("name")
    val lat = double("lat")
    val lon = double("lon")
    val elevation = double("elevation").default(0.0)
    val stationType = varchar("station_type", 10).default("WSR88D")
    val status = varchar("status", 20).default("ACTIVE")
    val updatedAt = timestamp("updated_at")

    override val primaryKey = PrimaryKey(id)
}

object RadarFramesTable : Table("radar_frames") {
    val id = long("id").autoIncrement()
    val stationId = varchar("station_id", 4).references(RadarStationsTable.id)
    val product = varchar("product", 10)
    val capturedAt = timestamp("captured_at")
    val tileBaseUrl = text("tile_base_url")
    val expiresAt = timestamp("expires_at")
    val createdAt = timestamp("created_at")

    override val primaryKey = PrimaryKey(id)

    init {
        uniqueIndex(stationId, product, capturedAt)
    }
}

object AlertsTable : Table("alerts") {
    val id = text("id")
    val eventType = text("event_type")
    val event = text("event")
    val headline = text("headline").nullable()
    val description = text("description").nullable()
    val severity = varchar("severity", 20)
    val certainty = varchar("certainty", 20)
    val urgency = varchar("urgency", 20)
    val effective = timestamp("effective")
    val expires = timestamp("expires")
    val areaDesc = text("area_desc").nullable()
    val senderName = text("sender_name").nullable()
    val rawJson = text("raw_json").nullable()
    val createdAt = timestamp("created_at")

    override val primaryKey = PrimaryKey(id)
}

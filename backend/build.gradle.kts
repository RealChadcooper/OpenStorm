plugins {
    alias(libs.plugins.kotlin.jvm)
    alias(libs.plugins.ktor)
}

group = "com.openstorm"
version = "1.0.0"

application {
    mainClass.set("com.openstorm.backend.ApplicationKt")
}

ktor {
    fatJar {
        archiveFileName.set("openstorm-backend.jar")
    }
}

repositories {
    mavenCentral()
}

dependencies {
    // Ktor Server
    implementation(libs.ktor.server.core)
    implementation(libs.ktor.server.netty)
    implementation(libs.ktor.server.content.negotiation)
    implementation(libs.ktor.server.cors)
    implementation(libs.ktor.server.compression)
    implementation(libs.ktor.server.status.pages)
    implementation(libs.ktor.server.call.logging)
    implementation(libs.ktor.server.rate.limit)
    implementation(libs.ktor.serialization.jackson)

    // Ktor Client (for upstream API calls)
    implementation(libs.ktor.client.core)
    implementation(libs.ktor.client.cio)
    implementation(libs.ktor.client.content.negotiation)

    // Logging
    implementation(libs.logback)

    // Database
    implementation(libs.exposed.core)
    implementation(libs.exposed.dao)
    implementation(libs.exposed.jdbc)
    implementation(libs.exposed.java.time)
    implementation(libs.hikari)
    implementation(libs.postgres)

    // Cache
    implementation(libs.redis)

    // JSON
    implementation(libs.moshi)
    implementation(libs.jackson.datatype.jsr310)

    // AWS S3 (object storage for radar tiles)
    implementation(libs.aws.s3)

    // Testing
    testImplementation(libs.ktor.server.test)
    testImplementation(libs.kotlin.test)
}

kotlin {
    jvmToolchain(21)
}

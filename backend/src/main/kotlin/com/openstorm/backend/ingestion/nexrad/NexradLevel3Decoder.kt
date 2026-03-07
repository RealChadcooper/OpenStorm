package com.openstorm.backend.ingestion.nexrad

import org.slf4j.LoggerFactory
import java.io.ByteArrayInputStream
import java.io.DataInputStream
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneOffset
import java.util.zip.GZIPInputStream
import kotlin.math.cos
import kotlin.math.sin

/**
 * Decoder for NEXRAD Level III (ICD) product files.
 *
 * NEXRAD Level III products are binary files distributed via NOAA TGFTP
 * at: https://tgftp.nws.noaa.gov/SL.us008001/DF.of/DC.radar/
 *
 * File structure (ICD for NEXRAD):
 *   - Message Header (18 bytes)
 *   - Product Description Block (PDB, 102 bytes)
 *   - Product Symbology Block (variable)
 *     - Radial packets (type 0xAF1F or 16) containing:
 *       - Azimuth, start angle, delta angle
 *       - Range bins with encoded data values
 *
 * This decoder handles the "Digital" products (N0Q = 94/153, N0U = 99/154)
 * which use run-length encoding in radial data packets.
 *
 * Reference: ICD for the RPG to Class 1 User (2620001)
 * https://www.roc.noaa.gov/wsr88d/BuildInfo/Files.aspx
 */
class NexradLevel3Decoder {

    private val logger = LoggerFactory.getLogger(javaClass)

    /**
     * Decode a NEXRAD Level III product file.
     *
     * @param data Raw bytes (may be gzip-compressed)
     * @return Decoded radar product data with gate values in polar coordinates
     */
    fun decode(data: ByteArray): NexradProduct {
        val uncompressed = decompress(data)
        val buffer = ByteBuffer.wrap(uncompressed).order(ByteOrder.BIG_ENDIAN)

        // Skip WMO header line if present (text line ending with \r\r\n)
        val headerSkip = findBinaryStart(uncompressed)
        buffer.position(headerSkip)

        val header = readMessageHeader(buffer)
        val pdb = readProductDescription(buffer)

        logger.debug(
            "NEXRAD L3: code={}, station={}, time={}, resolution={}m",
            pdb.productCode, header.stationId, pdb.volumeScanTime, pdb.resolution
        )

        val radials = readSymbologyBlock(buffer, pdb)

        return NexradProduct(
            stationId = header.stationId,
            productCode = pdb.productCode,
            timestamp = pdb.volumeScanTime,
            resolution = pdb.resolution,
            rangeBinCount = pdb.rangeBinCount,
            firstBinRange = pdb.firstBinRange,
            radials = radials,
            latitude = pdb.radarLat,
            longitude = pdb.radarLon,
            dataScale = pdb.dataScale,
            dataOffset = pdb.dataOffset,
        )
    }

    private fun decompress(data: ByteArray): ByteArray {
        // Check for gzip magic bytes
        if (data.size >= 2 && data[0] == 0x1f.toByte() && data[1] == 0x8b.toByte()) {
            return GZIPInputStream(ByteArrayInputStream(data)).use { it.readBytes() }
        }
        return data
    }

    /**
     * NOAA TGFTP files often have a WMO text header before the binary.
     * Scan for the start of the binary Message Header.
     *
     * The Message Header starts with a halfword containing the message code.
     * We look for the \r\r\n sequence that terminates the WMO header.
     */
    private fun findBinaryStart(data: ByteArray): Int {
        // Look for \r\r\n (0x0D 0x0D 0x0A) within the first 100 bytes
        for (i in 0 until minOf(data.size - 2, 100)) {
            if (data[i] == 0x0D.toByte() && data[i + 1] == 0x0D.toByte() && data[i + 2] == 0x0A.toByte()) {
                return i + 3
            }
        }
        return 0
    }

    /**
     * Message Header Block (18 bytes):
     *   HW 1: Message code (halfword)
     *   HW 2: Date of message (modified Julian)
     *   HW 3-4: Time of message (seconds since midnight)
     *   HW 5-6: Length of message (bytes)
     *   HW 7-8: Source ID
     *   HW 9-10: Destination ID
     *   HW 11: Number of blocks
     */
    private fun readMessageHeader(buffer: ByteBuffer): MessageHeader {
        val messageCode = buffer.short.toInt()
        val dateOfMessage = buffer.short.toInt()
        val timeOfMessage = buffer.int
        val messageLength = buffer.int
        val sourceId = buffer.short.toInt()
        val destId = buffer.short.toInt()
        val numBlocks = buffer.short.toInt()

        // Source ID encodes station: upper byte = 0, lower = ICAO index
        // But typically we get station ID from the PDB
        return MessageHeader(
            messageCode = messageCode,
            stationId = "", // Populated from PDB
            timestamp = julianToInstant(dateOfMessage, timeOfMessage),
            messageLength = messageLength,
            numBlocks = numBlocks,
        )
    }

    /**
     * Product Description Block (PDB, 102 bytes / 51 halfwords):
     * Contains radar location, product parameters, data thresholds, etc.
     */
    private fun readProductDescription(buffer: ByteBuffer): ProductDescription {
        val divider = buffer.short // -1 block divider
        val radarLat = buffer.int / 1000.0
        val radarLon = buffer.int / 1000.0
        val radarHeight = buffer.short.toInt()
        val productCode = buffer.short.toInt()
        val operationalMode = buffer.short.toInt()
        val vcp = buffer.short.toInt() // Volume Coverage Pattern
        val seqNumber = buffer.short.toInt()
        val scanNumber = buffer.short.toInt()
        val scanDate = buffer.short.toInt()
        val scanTime = buffer.int
        val genDate = buffer.short.toInt()
        val genTime = buffer.int
        val p1 = buffer.short.toInt() // product-dependent
        val p2 = buffer.short.toInt()

        val elevationNumber = buffer.short.toInt()
        val p3 = buffer.short.toInt()

        // Thresholds (16 halfwords for data level coding)
        val thresholds = ShortArray(16) { buffer.short }

        val p4 = buffer.short.toInt()
        val p5 = buffer.short.toInt()
        val p6 = buffer.short.toInt()
        val p7 = buffer.short.toInt()
        val p8 = buffer.short.toInt()
        val p9 = buffer.short.toInt()
        val p10 = buffer.short.toInt()

        // Version/spot blank
        val version = buffer.get().toInt()
        val spotBlank = buffer.get().toInt()

        val symbologyOffset = buffer.int // halfwords from start of product
        val graphicOffset = buffer.int
        val tabularOffset = buffer.int

        // For digital products (N0Q=94/153, N0U=99/154):
        // The scale and offset are in p4/p5 or the threshold table
        // Scale factor and offset convert gate value to meteorological value:
        //   value = (gate - offset) / scale
        val dataScale = if (thresholds[0] != 0.toShort()) thresholds[0].toFloat() else 10.0f
        val dataOffset = if (thresholds[1] != 0.toShort()) thresholds[1].toFloat() else 0.0f

        // Resolution: product-dependent
        // N0Q (94): 1km range, 1° azimuth
        // N0Q digital (153): 0.25km range, 0.5° azimuth
        val resolution = when (productCode) {
            153, 154, 155, 156, 159 -> 250  // Digital products: 250m
            94, 99 -> 1000  // Legacy: 1km
            else -> 1000
        }

        val rangeBinCount = when (productCode) {
            153, 154, 155, 156, 159 -> 1200  // 300km / 0.25km
            94, 99 -> 460    // 460km / 1km
            else -> 460
        }

        return ProductDescription(
            radarLat = radarLat,
            radarLon = radarLon,
            productCode = productCode,
            vcp = vcp,
            volumeScanTime = julianToInstant(scanDate, scanTime),
            resolution = resolution,
            rangeBinCount = rangeBinCount,
            firstBinRange = resolution, // First bin at one resolution unit
            dataScale = dataScale,
            dataOffset = dataOffset,
            thresholds = thresholds,
            symbologyOffset = symbologyOffset,
        )
    }

    /**
     * Read the Product Symbology Block, which contains the radial data.
     *
     * Structure:
     *   Block divider (-1)
     *   Block ID (1)
     *   Block length
     *   Number of layers (usually 1)
     *   Layer divider
     *   Layer length
     *   Packets...
     *
     * Radial data packets (type 0xAF1F for 16-level, or type 16 for digital):
     *   First bin index
     *   Number of range bins
     *   I center, J center
     *   Scale factor
     *   Number of radials
     *   Then for each radial:
     *     Number of bytes in radial
     *     Start angle (0.1 degrees)
     *     Delta angle (0.1 degrees)
     *     Run-length encoded data bytes
     */
    private fun readSymbologyBlock(buffer: ByteBuffer, pdb: ProductDescription): List<Radial> {
        val radials = mutableListOf<Radial>()

        if (pdb.symbologyOffset <= 0) {
            logger.warn("No symbology block in product")
            return radials
        }

        // Skip to symbology block if needed (offset is from message start in halfwords)
        // We may already be positioned correctly
        val blockDivider = buffer.short
        if (blockDivider != (-1).toShort()) {
            logger.warn("Expected block divider -1, got $blockDivider")
            return radials
        }

        val blockId = buffer.short
        val blockLength = buffer.int
        val numLayers = buffer.short.toInt()

        for (layer in 0 until numLayers) {
            val layerDivider = buffer.short
            val layerLength = buffer.int

            val layerEnd = buffer.position() + layerLength

            while (buffer.position() < layerEnd && buffer.remaining() >= 4) {
                val packetCode = buffer.short.toInt() and 0xFFFF

                when (packetCode) {
                    // Digital Radial Data Array (packet code 16)
                    16, 0xAF1F.toInt() -> {
                        val parsedRadials = readRadialPacket(buffer, packetCode, pdb)
                        radials.addAll(parsedRadials)
                    }
                    else -> {
                        // Skip unknown packet — try to read length and skip
                        if (buffer.remaining() >= 2) {
                            val packetLen = buffer.short.toInt() and 0xFFFF
                            if (buffer.remaining() >= packetLen) {
                                buffer.position(buffer.position() + packetLen)
                            } else {
                                break
                            }
                        } else {
                            break
                        }
                    }
                }
            }
        }

        return radials
    }

    private fun readRadialPacket(
        buffer: ByteBuffer,
        packetCode: Int,
        pdb: ProductDescription,
    ): List<Radial> {
        val radials = mutableListOf<Radial>()

        val firstBinIdx = buffer.short.toInt()
        val numBins = buffer.short.toInt()
        val iCenter = buffer.short.toInt()
        val jCenter = buffer.short.toInt()
        val scaleFactor = buffer.short.toInt()
        val numRadials = buffer.short.toInt()

        for (r in 0 until numRadials) {
            if (buffer.remaining() < 4) break

            val numBytesInRadial = buffer.short.toInt() and 0xFFFF
            val startAngle = (buffer.short.toInt() and 0xFFFF) / 10.0f // 0.1° units
            val deltaAngle = (buffer.short.toInt() and 0xFFFF) / 10.0f

            if (buffer.remaining() < numBytesInRadial) break

            val gates = if (packetCode == 16) {
                // Digital product: each byte is a direct gate value
                ByteArray(numBytesInRadial).also { buffer.get(it) }
                    .map { it.toInt() and 0xFF }
            } else {
                // Legacy 16-level RLE: 4 bits run, 4 bits color
                decodeRLE(buffer, numBytesInRadial, numBins)
            }

            radials.add(
                Radial(
                    azimuth = startAngle,
                    deltaAngle = deltaAngle,
                    gates = gates,
                )
            )
        }

        return radials
    }

    /**
     * Decode run-length encoded radial data.
     * Each byte: high nibble = run length, low nibble = color code (0-15).
     */
    private fun decodeRLE(buffer: ByteBuffer, numBytes: Int, expectedBins: Int): List<Int> {
        val gates = mutableListOf<Int>()
        val rleBytes = ByteArray(numBytes)
        buffer.get(rleBytes)

        for (b in rleBytes) {
            val value = b.toInt() and 0xFF
            val runLength = (value shr 4) and 0x0F
            val colorCode = value and 0x0F
            repeat(runLength) {
                gates.add(colorCode)
            }
        }

        return gates
    }

    private fun julianToInstant(julianDate: Int, secondsSinceMidnight: Int): Instant {
        // NEXRAD uses Modified Julian Date (days since Jan 1, 1970 = day 1)
        val date = LocalDate.ofEpochDay(julianDate.toLong() - 1)
        return date.atStartOfDay(ZoneOffset.UTC).toInstant()
            .plusSeconds(secondsSinceMidnight.toLong())
    }
}

data class MessageHeader(
    val messageCode: Int,
    val stationId: String,
    val timestamp: Instant,
    val messageLength: Int,
    val numBlocks: Int,
)

data class ProductDescription(
    val radarLat: Double,
    val radarLon: Double,
    val productCode: Int,
    val vcp: Int,
    val volumeScanTime: Instant,
    val resolution: Int,  // meters
    val rangeBinCount: Int,
    val firstBinRange: Int, // meters
    val dataScale: Float,
    val dataOffset: Float,
    val thresholds: ShortArray,
    val symbologyOffset: Int,
)

/**
 * A single radial (ray) of radar data.
 */
data class Radial(
    val azimuth: Float,    // degrees, 0=north, clockwise
    val deltaAngle: Float, // width of this radial in degrees
    val gates: List<Int>,  // gate values (0=below threshold, 1=range folded, 2-255=data)
)

/**
 * Fully decoded NEXRAD product ready for rendering.
 */
data class NexradProduct(
    val stationId: String,
    val productCode: Int,
    val timestamp: Instant,
    val resolution: Int,      // gate spacing in meters
    val rangeBinCount: Int,
    val firstBinRange: Int,   // meters
    val radials: List<Radial>,
    val latitude: Double,     // radar site lat
    val longitude: Double,    // radar site lon
    val dataScale: Float,
    val dataOffset: Float,
) {
    /** Maximum range in km */
    val maxRangeKm: Double
        get() = (firstBinRange + rangeBinCount * resolution) / 1000.0

    /** Convert raw gate value to meteorological value (dBZ, m/s, etc.) */
    fun gateToValue(gateValue: Int): Float? {
        if (gateValue <= 1) return null // 0=below threshold, 1=range folded
        return (gateValue - dataOffset) / dataScale
    }
}

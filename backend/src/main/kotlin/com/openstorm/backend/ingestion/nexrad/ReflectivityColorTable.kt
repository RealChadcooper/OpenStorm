package com.openstorm.backend.ingestion.nexrad

/**
 * NOAA/NWS standard reflectivity color table for base reflectivity (N0Q).
 *
 * Maps dBZ values to ARGB colors following the standard NWS color scale.
 * Below -30 dBZ is transparent (no return).
 * Colors progress: teal → green → yellow → red → magenta → white
 *
 * This table is used by TileRenderer to color radar gate values.
 */
object ReflectivityColorTable {

    /**
     * Color table entries: (minDbz, maxDbz, argb).
     * ARGB format: 0xAARRGGBB
     */
    private val TABLE = arrayOf(
        // Below threshold — transparent
        Entry(Float.NEGATIVE_INFINITY, -30f, 0x00000000),

        // Very light returns (ND / noise)
        Entry(-30f, -20f, 0x40646464),  // Gray, very transparent
        Entry(-20f, -10f, 0x6004E9E7),  // Teal

        // Light precipitation
        Entry(-10f, 0f, 0x8004E9E7),    // Teal
        Entry(0f, 5f, 0xB001A4D4),      // Darker teal
        Entry(5f, 10f, 0xC00187C5),     // Blue-teal
        Entry(10f, 15f, 0xD003C78C),    // Green-teal
        Entry(15f, 20f, 0xE002B502),    // Green

        // Moderate precipitation
        Entry(20f, 25f, 0xF001A501),     // Dark green
        Entry(25f, 30f, 0xFFFEFA00),     // Yellow
        Entry(30f, 35f, 0xFFEBB400),     // Gold
        Entry(35f, 40f, 0xFFFF9600),     // Orange

        // Heavy precipitation
        Entry(40f, 45f, 0xFFFF0000),     // Red
        Entry(45f, 50f, 0xFFD40000),     // Dark red
        Entry(50f, 55f, 0xFFBE0000),     // Darker red

        // Severe / hail
        Entry(55f, 60f, 0xFFFE00FE),     // Magenta
        Entry(60f, 65f, 0xFF9000A0),     // Purple
        Entry(65f, 70f, 0xFFFFFFFF),     // White
        Entry(70f, 75f, 0xFFE0E0FF),     // Light blue-white

        // Extreme (EF5 debris, etc.)
        Entry(75f, Float.POSITIVE_INFINITY, 0xFFC0C0FF), // Cyan-white
    )

    /**
     * Get ARGB color for a dBZ value.
     * Returns 0 (fully transparent) for values below detection threshold.
     */
    fun getColor(dbz: Float): Int {
        for (entry in TABLE) {
            if (dbz >= entry.min && dbz < entry.max) {
                return entry.argb
            }
        }
        return 0x00000000
    }

    /**
     * Get ARGB color for a raw gate value using product scale/offset.
     * Returns 0 for gates below threshold (value 0) or range folded (value 1).
     */
    fun getColorForGate(gateValue: Int, scale: Float, offset: Float): Int {
        if (gateValue <= 1) return 0x00000000
        val dbz = (gateValue - offset) / scale
        return getColor(dbz)
    }

    private data class Entry(val min: Float, val max: Float, val argb: Int)
}

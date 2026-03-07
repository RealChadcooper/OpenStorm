package com.openstorm.backend.provider.radar

import com.openstorm.backend.model.RadarFrame
import com.openstorm.backend.model.RadarStation
import org.slf4j.LoggerFactory
import java.time.Instant
import java.time.temporal.ChronoUnit

/**
 * NOAA NEXRAD Level III radar data provider.
 * Sources: NOAA TGFTP and AWS Open Data (s3://noaa-nexrad-level2/).
 *
 * In production, this would:
 * 1. Poll TGFTP for latest product files
 * 2. Decode binary NEXRAD format
 * 3. Render to tile images
 * 4. Store in object storage
 *
 * For MVP, returns structured metadata pointing to tile URLs.
 */
class NoaaRadarProvider(
    private val devMode: Boolean = false,
    private val localBaseUrl: String = "http://localhost:8080",
) : RadarProvider {

    override val providerId = "noaa-nexrad"

    private val logger = LoggerFactory.getLogger(javaClass)

    // Hardcoded subset of NEXRAD stations for MVP
    // Full list: ~160 WSR-88D sites from NWS
    private val stations = listOf(
        RadarStation("KTLX", "Oklahoma City, OK", 35.3331, -97.2778, 370.0),
        RadarStation("KOUN", "Norman, OK", 35.2454, -97.4622, 357.0),
        RadarStation("KVNX", "Vance AFB, OK", 36.7406, -98.1279, 369.0),
        RadarStation("KINX", "Tulsa, OK", 36.175, -95.5647, 204.0),
        RadarStation("KFDR", "Frederick, OK", 34.362, -98.9764, 386.0),
        RadarStation("KDFW", "Dallas/Fort Worth, TX", 32.5731, -97.3032, 195.0),
        RadarStation("KFWS", "Fort Worth, TX", 32.5731, -97.3032, 208.0),
        RadarStation("KICT", "Wichita, KS", 37.6547, -97.4431, 407.0),
        RadarStation("KLZK", "Little Rock, AR", 34.8365, -92.2621, 173.0),
        RadarStation("KSRX", "Fort Smith, AR", 35.2904, -94.3619, 195.0),
        RadarStation("KEAX", "Kansas City, MO", 38.8103, -94.2645, 303.0),
        RadarStation("KSGF", "Springfield, MO", 37.2353, -93.4006, 390.0),
        RadarStation("KLSX", "St. Louis, MO", 38.6987, -90.6829, 185.0),
        RadarStation("KAMA", "Amarillo, TX", 35.2334, -101.7092, 1093.0),
        RadarStation("KLBB", "Lubbock, TX", 33.6541, -101.8143, 993.0),
        RadarStation("KMAF", "Midland, TX", 31.9433, -102.189, 874.0),
        RadarStation("KEWX", "Austin/San Antonio, TX", 29.7039, -98.0285, 193.0),
        RadarStation("KHGX", "Houston, TX", 29.4719, -95.0789, 5.0),
        RadarStation("KCRP", "Corpus Christi, TX", 27.7839, -97.5112, 13.0),
        RadarStation("KBRO", "Brownsville, TX", 25.9159, -97.4189, 7.0),
        RadarStation("KGRK", "Fort Hood, TX", 30.7218, -97.3831, 164.0),
        RadarStation("KSJT", "San Angelo, TX", 31.3712, -100.4925, 576.0),
        RadarStation("KDYX", "Dyess AFB, TX", 32.5384, -99.2543, 463.0),
        RadarStation("KMOB", "Mobile, AL", 30.6796, -88.2398, 63.0),
        RadarStation("KBMX", "Birmingham, AL", 33.1721, -86.7698, 197.0),
        RadarStation("KHTX", "Huntsville, AL", 34.9306, -86.0837, 536.0),
        RadarStation("KJGX", "Robins AFB, GA", 32.6753, -83.3511, 158.0),
        RadarStation("KFFC", "Atlanta, GA", 33.3636, -84.5658, 262.0),
        RadarStation("KVAX", "Moody AFB, GA", 30.8904, -83.0019, 54.0),
        RadarStation("KCLX", "Charleston, SC", 32.6555, -81.0422, 30.0),
        RadarStation("KCAE", "Columbia, SC", 33.9487, -81.1184, 70.0),
        RadarStation("KRAX", "Raleigh, NC", 35.6654, -78.4897, 106.0),
        RadarStation("KMHX", "Morehead City, NC", 34.7761, -76.8762, 9.0),
        RadarStation("KAKQ", "Wakefield, VA", 36.984, -77.008, 34.0),
        RadarStation("KLWX", "Sterling, VA", 38.9753, -77.478, 83.0),
        RadarStation("KDOX", "Dover AFB, DE", 38.8256, -75.44, 15.0),
        RadarStation("KDIX", "Philadelphia, PA", 39.947, -74.411, 45.0),
        RadarStation("KOKX", "New York City, NY", 40.8655, -72.864, 26.0),
        RadarStation("KBOX", "Boston, MA", 41.9556, -71.1369, 36.0),
        RadarStation("KENX", "Albany, NY", 42.5864, -74.0639, 557.0),
        RadarStation("KBUF", "Buffalo, NY", 42.9489, -78.7369, 211.0),
        RadarStation("KCLE", "Cleveland, OH", 41.4131, -81.8597, 233.0),
        RadarStation("KILN", "Cincinnati, OH", 39.4203, -83.8217, 322.0),
        RadarStation("KIND", "Indianapolis, IN", 39.7075, -86.2803, 241.0),
        RadarStation("KLOT", "Chicago, IL", 41.6044, -88.0847, 202.0),
        RadarStation("KILX", "Lincoln, IL", 40.1506, -89.3369, 177.0),
        RadarStation("KDVN", "Davenport, IA", 41.6117, -90.5809, 230.0),
        RadarStation("KDMX", "Des Moines, IA", 41.7312, -93.7229, 299.0),
        RadarStation("KMPX", "Minneapolis, MN", 44.8489, -93.5655, 288.0),
        RadarStation("KDLH", "Duluth, MN", 46.8369, -92.2097, 435.0),
        RadarStation("KMKX", "Milwaukee, WI", 42.9678, -88.5506, 292.0),
        RadarStation("KGRB", "Green Bay, WI", 44.4985, -88.1112, 208.0),
        RadarStation("KARX", "La Crosse, WI", 43.8228, -91.1912, 389.0),
        RadarStation("KOAX", "Omaha, NE", 41.3203, -96.3667, 350.0),
        RadarStation("KUEX", "Hastings, NE", 40.3206, -98.4418, 602.0),
        RadarStation("KLNX", "North Platte, NE", 41.9579, -100.5762, 905.0),
        RadarStation("KABR", "Aberdeen, SD", 45.4558, -98.4132, 397.0),
        RadarStation("KUDX", "Rapid City, SD", 44.125, -102.8298, 919.0),
        RadarStation("KBIS", "Bismarck, ND", 46.7709, -100.7605, 505.0),
        RadarStation("KMBX", "Minot, ND", 48.3925, -100.8644, 455.0),
        RadarStation("KDEN", "Denver, CO", 39.7867, -104.546, 1710.0, stationType = "WSR88D"),
        RadarStation("KPUX", "Pueblo, CO", 38.4595, -104.1817, 1600.0),
        RadarStation("KGJX", "Grand Junction, CO", 39.0622, -108.2137, 3046.0),
        RadarStation("KRIW", "Riverton, WY", 43.0661, -108.4773, 1697.0),
        RadarStation("KCYS", "Cheyenne, WY", 41.1519, -104.8061, 1868.0),
        RadarStation("KSLC", "Salt Lake City, UT", 40.9725, -111.93, 1288.0),
        RadarStation("KICX", "Cedar City, UT", 37.5908, -112.8622, 3231.0),
        RadarStation("KFSX", "Flagstaff, AZ", 34.574, -111.198, 2261.0),
        RadarStation("KIWA", "Phoenix, AZ", 33.289, -111.6692, 412.0),
        RadarStation("KEMX", "Tucson, AZ", 31.8937, -110.6303, 1586.0),
        RadarStation("KYUX", "Yuma, AZ", 32.4953, -114.6567, 53.0),
        RadarStation("KABX", "Albuquerque, NM", 35.1497, -106.824, 1789.0),
        RadarStation("KFDX", "Cannon AFB, NM", 34.6341, -103.6186, 1417.0),
        RadarStation("KHDX", "Holloman AFB, NM", 33.0769, -106.1232, 1287.0),
        RadarStation("KLRX", "Elko, NV", 40.7397, -116.8025, 2056.0),
        RadarStation("KESX", "Las Vegas, NV", 35.7011, -114.8919, 1483.0),
        RadarStation("KRGX", "Reno, NV", 39.7542, -119.462, 2530.0),
        RadarStation("KVBX", "Vandenberg AFB, CA", 34.8383, -120.3978, 373.0),
        RadarStation("KSOX", "Santa Ana Mtns, CA", 33.8178, -117.636, 923.0),
        RadarStation("KNKX", "San Diego, CA", 32.9189, -117.0419, 291.0),
        RadarStation("KMUX", "San Francisco, CA", 37.1553, -121.8983, 1057.0),
        RadarStation("KDAX", "Sacramento, CA", 38.5011, -121.678, 9.0),
        RadarStation("KBHX", "Eureka, CA", 40.4986, -124.2919, 732.0),
        RadarStation("KMAX", "Medford, OR", 42.0811, -122.7172, 2290.0),
        RadarStation("KRTX", "Portland, OR", 45.715, -122.9656, 479.0),
        RadarStation("KPDT", "Pendleton, OR", 45.6906, -118.8529, 462.0),
        RadarStation("KATX", "Seattle, WA", 48.1946, -122.4957, 151.0),
        RadarStation("KOTX", "Spokane, WA", 47.6803, -117.6267, 727.0),
        RadarStation("KBYX", "Key West, FL", 24.5975, -81.7033, 3.0),
        RadarStation("KAMX", "Miami, FL", 25.6111, -80.4128, 4.0),
        RadarStation("KMLB", "Melbourne, FL", 28.1133, -80.6544, 11.0),
        RadarStation("KTBW", "Tampa Bay, FL", 27.7056, -82.4017, 12.0),
        RadarStation("KJAX", "Jacksonville, FL", 30.4847, -81.7019, 10.0),
        RadarStation("KTLH", "Tallahassee, FL", 30.3975, -84.3289, 19.0),
        RadarStation("KEVX", "Pensacola, FL", 30.5644, -85.9214, 43.0),
        RadarStation("KLIX", "New Orleans, LA", 30.3367, -89.8256, 7.0),
        RadarStation("KLCH", "Lake Charles, LA", 30.125, -93.2161, 4.0),
        RadarStation("KSHV", "Shreveport, LA", 32.4508, -93.8414, 83.0),
        RadarStation("KPOE", "Fort Polk, LA", 31.1556, -92.9762, 124.0),
        RadarStation("KDGX", "Jackson, MS", 32.28, -89.9844, 153.0),
        RadarStation("KGWX", "Columbus AFB, MS", 33.8967, -88.3289, 145.0),
        RadarStation("KMRX", "Knoxville, TN", 36.1686, -83.4017, 408.0),
        RadarStation("KNQA", "Memphis, TN", 35.3447, -89.8733, 86.0),
        RadarStation("KOHX", "Nashville, TN", 36.2472, -86.5625, 177.0),
        RadarStation("KHPX", "Fort Campbell, KY", 36.7369, -87.285, 176.0),
        RadarStation("KLVX", "Louisville, KY", 37.975, -85.9439, 219.0),
        RadarStation("KJKL", "Jackson, KY", 37.5908, -83.3133, 416.0),
        RadarStation("KRLX", "Charleston, WV", 38.3111, -81.7231, 329.0),
        RadarStation("KCCX", "State College, PA", 40.9231, -78.0039, 733.0),
        RadarStation("KPBZ", "Pittsburgh, PA", 40.5317, -80.0181, 361.0),
        RadarStation("KGYX", "Portland, ME", 43.8914, -70.2567, 125.0),
        RadarStation("KCBW", "Caribou, ME", 46.0392, -67.8067, 227.0),
    )

    override suspend fun getStations(): List<RadarStation> = stations

    override suspend fun getStation(stationId: String): RadarStation? {
        return stations.find { it.id.equals(stationId, ignoreCase = true) }
    }

    override suspend fun getFrames(stationId: String, product: String, count: Int): List<RadarFrame> {
        val now = Instant.now()

        if (devMode) {
            // In dev mode, all frames point to the local tile proxy which serves
            // live IEM NEXRAD mosaic tiles. The product controls which layer (N0Q/N0U).
            val productLower = product.lowercase()
            val tileUrl = "$localBaseUrl/api/v1/dev/tiles/$productLower/{z}/{x}/{y}.png"
            return (0 until count).reversed().map { i ->
                val timestamp = now.minus((i * 5).toLong(), ChronoUnit.MINUTES)
                RadarFrame(
                    timestamp = timestamp,
                    tileUrl = tileUrl,
                    expiresAt = timestamp.plus(10, ChronoUnit.MINUTES),
                )
            }
        }

        // Production: query actual tiles from object storage
        return (0 until count).reversed().map { i ->
            val timestamp = now.minus((i * 5).toLong(), ChronoUnit.MINUTES)
            RadarFrame(
                timestamp = timestamp,
                tileUrl = "https://cdn.openstorm.app/tiles/$stationId/$product/${timestamp}/{z}/{x}/{y}.webp",
                expiresAt = timestamp.plus(10, ChronoUnit.MINUTES),
            )
        }
    }
}

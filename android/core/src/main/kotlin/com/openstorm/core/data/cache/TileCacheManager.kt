package com.openstorm.core.data.cache

import android.content.Context
import dagger.hilt.android.qualifiers.ApplicationContext
import timber.log.Timber
import java.io.File
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Manages on-disk cache for radar tile images.
 * LRU eviction when cache exceeds the size limit.
 */
@Singleton
class TileCacheManager @Inject constructor(
    @ApplicationContext private val context: Context,
) {
    private val cacheDir: File
        get() = File(context.cacheDir, "radar_tiles").also { it.mkdirs() }

    val maxCacheBytes: Long = 100L * 1024 * 1024 // 100MB

    fun getCachedTile(stationId: String, product: String, timestamp: String): File? {
        val file = tileFile(stationId, product, timestamp)
        return if (file.exists()) file else null
    }

    fun cacheTile(stationId: String, product: String, timestamp: String, data: ByteArray): File {
        val file = tileFile(stationId, product, timestamp)
        file.parentFile?.mkdirs()
        file.writeBytes(data)
        evictIfNeeded()
        return file
    }

    fun evictIfNeeded() {
        val files = cacheDir.walkTopDown().filter { it.isFile }.toList()
        val totalSize = files.sumOf { it.length() }
        if (totalSize > maxCacheBytes) {
            val sorted = files.sortedBy { it.lastModified() }
            var freed = 0L
            val target = totalSize - (maxCacheBytes * 0.8).toLong()
            for (file in sorted) {
                if (freed >= target) break
                freed += file.length()
                file.delete()
            }
            Timber.d("Cache eviction: freed ${freed / 1024}KB")
        }
    }

    fun clearCache() {
        cacheDir.deleteRecursively()
    }

    private fun tileFile(stationId: String, product: String, timestamp: String): File {
        return File(cacheDir, "$stationId/$product/$timestamp.webp")
    }
}

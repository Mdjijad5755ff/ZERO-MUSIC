package com.example.util

import android.content.Context
import coil.ImageLoader
import coil.disk.DiskCache
import coil.memory.MemoryCache
import coil.request.CachePolicy
import java.io.File

/**
 * Disk-based caching manager for local audio library album art using Coil.
 * Configures high-performance disk and memory caches specifically tuned for
 * local music library browsing with zero ContentResolver IPC overhead on cache hits.
 */
object AlbumArtDiskCache {

    const val DISK_CACHE_DIR_NAME = "coil_album_art_disk_cache"
    const val MAX_DISK_CACHE_SIZE_BYTES = 100L * 1024 * 1024 // 100 MB dedicated disk cache
    const val MAX_MEMORY_CACHE_PERCENT = 0.25 // 25% of available app memory

    fun getDiskCacheDir(context: Context): File {
        return File(context.cacheDir, DISK_CACHE_DIR_NAME)
    }

    /**
     * Calculates the total size of cached local album art files on disk.
     */
    fun getDiskCacheSizeBytes(context: Context): Long {
        val dir = getDiskCacheDir(context)
        if (!dir.exists() || !dir.isDirectory) return 0L
        return try {
            dir.walkTopDown().filter { it.isFile }.sumOf { it.length() }
        } catch (_: Exception) {
            0L
        }
    }

    /**
     * Human-readable formatted cache size for diagnostics and settings UI.
     */
    fun formatCacheSize(bytes: Long): String {
        return when {
            bytes <= 0L -> "0 KB"
            bytes < 1024L -> "$bytes B"
            bytes < 1024L * 1024L -> String.format("%.1f KB", bytes / 1024.0)
            else -> String.format("%.2f MB", bytes / (1024.0 * 1024.0))
        }
    }

    /**
     * Clears all cached album art on disk.
     */
    fun clearDiskCache(context: Context): Boolean {
        LocalAlbumArtFetcher.clearNegativeCache()
        return try {
            val dir = getDiskCacheDir(context)
            if (dir.exists()) {
                dir.deleteRecursively()
                dir.mkdirs()
            }
            true
        } catch (e: Exception) {
            e.printStackTrace()
            false
        }
    }

    /**
     * Builds and optimizes the app-wide Coil ImageLoader with local album art disk caching.
     */
    fun createImageLoader(context: Context): ImageLoader {
        return ImageLoader.Builder(context)
            .memoryCache {
                MemoryCache.Builder(context)
                    .maxSizePercent(MAX_MEMORY_CACHE_PERCENT)
                    .build()
            }
            .diskCache {
                DiskCache.Builder()
                    .directory(getDiskCacheDir(context))
                    .maxSizeBytes(MAX_DISK_CACHE_SIZE_BYTES)
                    .build()
            }
            .diskCachePolicy(CachePolicy.ENABLED)
            .memoryCachePolicy(CachePolicy.ENABLED)
            .respectCacheHeaders(false) // Crucial for offline and local content URIs
            .components {
                add(LocalAlbumArtFetcher.SongFactory(context))
                add(LocalAlbumArtFetcher.UriFactory(context))
                add(SongKeyer())
                add(AudioUriKeyer())
            }
            .build()
    }
}

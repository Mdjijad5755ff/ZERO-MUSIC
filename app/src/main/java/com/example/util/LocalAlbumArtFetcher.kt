package com.example.util

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.media.MediaMetadataRetriever
import android.net.Uri
import android.os.Build
import android.util.Size
import coil.ImageLoader
import coil.decode.DataSource
import coil.decode.ImageSource
import coil.fetch.FetchResult
import coil.fetch.Fetcher
import coil.fetch.SourceResult
import coil.key.Keyer
import coil.request.Options
import com.example.model.Song
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okio.Buffer
import okio.buffer
import java.io.ByteArrayOutputStream
import java.util.concurrent.ConcurrentHashMap

/**
 * Custom Coil Fetcher that implements a persistent disk-based caching strategy
 * for local Android library album art.
 *
 * Fast path: Serves cached downsampled thumbnails directly from Coil's DiskCache without IPC.
 * Slow path: Extracts embedded artwork or MediaStore art, downsamples to 512px max, writes to
 * Coil's DiskCache, and commits the snapshot for subsequent millisecond reads.
 *
 * Includes negative caching and file-format safety guards to prevent repetitive calls to
 * MediaMetadataRetriever on files lacking ID3 APIC frames (such as WAV or generated demo tracks),
 * completely eliminating MediaMetadataRetrieverJNI getEmbeddedPicture error logs.
 */
class LocalAlbumArtFetcher private constructor(
    private val context: Context,
    private val songId: Long,
    private val albumArtUri: String?,
    private val dataUri: String?,
    private val isBuiltIn: Boolean,
    private val options: Options,
    private val imageLoader: ImageLoader
) : Fetcher {

    override suspend fun fetch(): FetchResult? = withContext(Dispatchers.IO) {
        val diskCache = imageLoader.diskCache
        val cacheKey = options.diskCacheKey ?: "local_art_song_$songId"

        // 1. Fast Path: Read directly from Coil DiskCache if available
        if (diskCache != null && options.diskCachePolicy.readEnabled) {
            val snapshot = diskCache.openSnapshot(cacheKey)
            if (snapshot != null) {
                return@withContext SourceResult(
                    source = ImageSource(
                        file = snapshot.data,
                        fileSystem = diskCache.fileSystem,
                        diskCacheKey = cacheKey,
                        closeable = snapshot
                    ),
                    mimeType = "image/jpeg",
                    dataSource = DataSource.DISK
                )
            }
        }

        // 2. Negative Cache Check: If this song was previously determined to have no artwork, skip
        if (noArtworkSongIds.contains(songId)) {
            return@withContext null
        }

        // Built-in demo tracks are synthesized audio files that do not contain embedded pictures
        if (isBuiltIn || (dataUri != null && dataUri.contains("demo_") && dataUri.endsWith(".wav", ignoreCase = true))) {
            noArtworkSongIds.add(songId)
            return@withContext null
        }

        // 3. Slow Path: Extract from local Android MediaStore or embedded ID3 metadata
        val rawBytes = extractRawArtworkBytes()
        if (rawBytes == null) {
            // Record in negative cache so we never re-query MediaMetadataRetriever or MediaStore for this song
            noArtworkSongIds.add(songId)
            return@withContext null
        }

        val processedBytes = processAndDownsample(rawBytes, targetMaxDimension = 512)

        // 4. Write processed thumbnail into Coil's DiskCache
        if (diskCache != null && options.diskCachePolicy.writeEnabled) {
            val editor = diskCache.openEditor(cacheKey)
            if (editor != null) {
                try {
                    diskCache.fileSystem.sink(editor.data).buffer().use { sink ->
                        sink.write(processedBytes)
                    }
                    val snapshot = editor.commitAndOpenSnapshot()
                    if (snapshot != null) {
                        return@withContext SourceResult(
                            source = ImageSource(
                                file = snapshot.data,
                                fileSystem = diskCache.fileSystem,
                                diskCacheKey = cacheKey,
                                closeable = snapshot
                            ),
                            mimeType = "image/jpeg",
                            dataSource = DataSource.DISK
                        )
                    }
                } catch (e: Exception) {
                    editor.abort()
                }
            }
        }

        // Fallback: In-memory source if disk write wasn't possible
        val buffer = Buffer().write(processedBytes)
        SourceResult(
            source = ImageSource(buffer, context),
            mimeType = "image/jpeg",
            dataSource = DataSource.MEMORY
        )
    }

    private fun extractRawArtworkBytes(): ByteArray? {
        // Strategy A: Try ContentResolver openInputStream on albumArtUri (official MediaStore albumart URI)
        if (!albumArtUri.isNullOrBlank()) {
            try {
                context.contentResolver.openInputStream(Uri.parse(albumArtUri))?.use { input ->
                    val bytes = input.readBytes()
                    if (bytes.isNotEmpty()) return bytes
                }
            } catch (_: Exception) {}
        }

        // If dataUri is empty or points to a format without artwork support, abort early
        if (dataUri.isNullOrBlank() || isBuiltIn) {
            return null
        }

        val lowerUri = dataUri.lowercase()
        if (lowerUri.endsWith(".wav") || lowerUri.endsWith(".pcm") || lowerUri.contains("demo_")) {
            return null
        }

        // Strategy B: On Android 10+ (API 29+), use ContentResolver.loadThumbnail on dataUri
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            try {
                val uri = Uri.parse(dataUri)
                val bitmap = context.contentResolver.loadThumbnail(uri, Size(512, 512), null)
                val baos = ByteArrayOutputStream()
                bitmap.compress(Bitmap.CompressFormat.JPEG, 85, baos)
                val bytes = baos.toByteArray()
                if (bytes.isNotEmpty()) return bytes
            } catch (_: Exception) {}
        }

        // Strategy C: MediaMetadataRetriever embedded APIC picture (only for supported containers)
        try {
            val retriever = MediaMetadataRetriever()
            try {
                retriever.setDataSource(context, Uri.parse(dataUri))
                val mime = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_MIMETYPE)
                // Only invoke getEmbeddedPicture if the container format supports embedded artwork
                val isSupportedFormat = mime != null && (
                    mime.contains("mpeg") ||
                    mime.contains("mp3") ||
                    mime.contains("mp4") ||
                    mime.contains("aac") ||
                    mime.contains("flac") ||
                    mime.contains("ogg")
                )
                if (isSupportedFormat) {
                    val picture = retriever.embeddedPicture
                    if (picture != null && picture.isNotEmpty()) {
                        return picture
                    }
                }
            } finally {
                retriever.release()
            }
        } catch (_: Exception) {}

        return null
    }

    /**
     * Decodes and downsamples raw image bytes to a max dimension (e.g. 512px)
     * and compresses to JPEG to minimize disk cache size and eliminate frame drops.
     */
    private fun processAndDownsample(rawBytes: ByteArray, targetMaxDimension: Int): ByteArray {
        val boundsOptions = BitmapFactory.Options().apply {
            inJustDecodeBounds = true
        }
        BitmapFactory.decodeByteArray(rawBytes, 0, rawBytes.size, boundsOptions)

        val origWidth = boundsOptions.outWidth
        val origHeight = boundsOptions.outHeight

        // If dimensions are invalid or already small enough, return as-is
        if (origWidth <= 0 || origHeight <= 0) return rawBytes
        if (origWidth <= targetMaxDimension && origHeight <= targetMaxDimension && rawBytes.size < 120 * 1024) {
            return rawBytes
        }

        var sampleSize = 1
        val maxDim = maxOf(origWidth, origHeight)
        while ((maxDim / (sampleSize * 2)) >= targetMaxDimension) {
            sampleSize *= 2
        }

        val decodeOptions = BitmapFactory.Options().apply {
            inSampleSize = sampleSize
            inPreferredConfig = Bitmap.Config.ARGB_8888
        }
        val bitmap = BitmapFactory.decodeByteArray(rawBytes, 0, rawBytes.size, decodeOptions)
            ?: return rawBytes

        // Scale down further if needed to fit exactly within targetMaxDimension
        val finalBitmap = if (bitmap.width > targetMaxDimension || bitmap.height > targetMaxDimension) {
            val scale = targetMaxDimension.toFloat() / maxOf(bitmap.width, bitmap.height)
            val newW = (bitmap.width * scale).toInt().coerceAtLeast(1)
            val newH = (bitmap.height * scale).toInt().coerceAtLeast(1)
            Bitmap.createScaledBitmap(bitmap, newW, newH, true).also {
                if (it != bitmap) bitmap.recycle()
            }
        } else {
            bitmap
        }

        val output = ByteArrayOutputStream()
        finalBitmap.compress(Bitmap.CompressFormat.JPEG, 85, output)
        finalBitmap.recycle()
        return output.toByteArray()
    }

    companion object {
        // Fast in-memory negative cache for songs confirmed to have no album art
        private val noArtworkSongIds = ConcurrentHashMap.newKeySet<Long>()

        fun clearNegativeCache() {
            noArtworkSongIds.clear()
        }
    }

    class SongFactory(private val context: Context) : Fetcher.Factory<Song> {
        override fun create(data: Song, options: Options, imageLoader: ImageLoader): Fetcher {
            return LocalAlbumArtFetcher(
                context = context,
                songId = data.id,
                albumArtUri = data.albumArtUri,
                dataUri = data.dataUri,
                isBuiltIn = data.isBuiltIn,
                options = options,
                imageLoader = imageLoader
            )
        }
    }

    class UriFactory(private val context: Context) : Fetcher.Factory<Uri> {
        override fun create(data: Uri, options: Options, imageLoader: ImageLoader): Fetcher? {
            val str = data.toString()
            if (!str.startsWith("content://media/external/audio")) {
                return null // delegate to default Coil fetchers for web/asset URIs
            }
            val id = data.lastPathSegment?.toLongOrNull() ?: str.hashCode().toLong()
            return LocalAlbumArtFetcher(
                context = context,
                songId = id,
                albumArtUri = str,
                dataUri = null,
                isBuiltIn = false,
                options = options,
                imageLoader = imageLoader
            )
        }
    }
}

class SongKeyer : Keyer<Song> {
    override fun key(data: Song, options: Options): String {
        return "local_art_song_${data.id}"
    }
}

class AudioUriKeyer : Keyer<Uri> {
    override fun key(data: Uri, options: Options): String? {
        val str = data.toString()
        return if (str.startsWith("content://media/external/audio")) {
            val segment = data.lastPathSegment ?: str.hashCode().toString()
            "local_art_uri_$segment"
        } else {
            null
        }
    }
}

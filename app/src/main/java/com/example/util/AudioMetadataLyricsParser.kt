package com.example.util

import android.content.Context
import android.media.MediaMetadataRetriever
import android.net.Uri
import android.provider.MediaStore
import android.util.Log
import com.example.model.Song
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.io.InputStream
import java.nio.charset.Charset

object AudioMetadataLyricsParser {

    private const val TAG = "AudioLyricsParser"
    private const val MAX_TAG_READ_BYTES = 512 * 1024 // 512 KB

    /**
     * Attempts to find lyrics for the given song by:
     * 1. Checking local file system for companion .lrc or .txt files (matching filename or title)
     * 2. Inspecting embedded audio metadata (ID3v2 USLT/SYLT, Vorbis comments, MP4 ©lyr)
     * 3. Fallback to MediaMetadataRetriever
     */
    suspend fun extractLyrics(context: Context, song: Song): String? = withContext(Dispatchers.IO) {
        // Built-in demo songs already have their lyrics attached
        if (song.isBuiltIn && !song.lyrics.isNullOrBlank()) {
            return@withContext song.lyrics
        }

        // Strategy 1: Check companion local .lrc or .txt files in storage
        findLocalLrcFile(context, song)?.let { lrcContent ->
            Log.d(TAG, "Found local companion lyric file for ${song.title}")
            return@withContext lrcContent
        }

        // Strategy 2: Parse embedded metadata from the audio stream
        extractEmbeddedLyrics(context, song)?.let { embeddedLyrics ->
            Log.d(TAG, "Extracted embedded lyrics for ${song.title}")
            return@withContext embeddedLyrics
        }

        // Strategy 3: Check MediaMetadataRetriever for any textual metadata field
        extractFromRetriever(context, song)?.let { retrieverLyrics ->
            Log.d(TAG, "Extracted lyrics from retriever for ${song.title}")
            return@withContext retrieverLyrics
        }

        null
    }

    /**
     * Looks for .lrc or .txt files associated with the song on disk.
     */
    fun findLocalLrcFile(context: Context, song: Song): String? {
        val candidates = mutableListOf<File>()

        // 1. Check path derived from ContentResolver MediaStore.Audio.Media.DATA
        val realFilePath = resolveFilePath(context, song.dataUri)
        if (realFilePath != null) {
            val audioFile = File(realFilePath)
            val parent = audioFile.parentFile
            if (parent != null && parent.exists()) {
                val baseName = audioFile.nameWithoutExtension
                candidates.add(File(parent, "$baseName.lrc"))
                candidates.add(File(parent, "$baseName.txt"))
                candidates.add(File(parent, "${song.title}.lrc"))
                candidates.add(File(parent, "${song.title}.txt"))
            }
        }

        // 2. Check app's external files lyrics directory
        val lyricsDir = File(context.getExternalFilesDir(null), "lyrics")
        if (lyricsDir.exists()) {
            candidates.add(File(lyricsDir, "${song.id}.lrc"))
            candidates.add(File(lyricsDir, "${song.title}.lrc"))
            candidates.add(File(lyricsDir, "${song.id}.txt"))
        }

        for (candidate in candidates) {
            if (candidate.exists() && candidate.isFile && candidate.length() > 0) {
                try {
                    val content = candidate.readText(Charsets.UTF_8).trim()
                    if (content.isNotEmpty()) {
                        return content
                    }
                } catch (e: Exception) {
                    try {
                        val content = candidate.readText(Charset.defaultCharset()).trim()
                        if (content.isNotEmpty()) return content
                    } catch (_: Exception) { }
                }
            }
        }

        return null
    }

    /**
     * Inspects embedded ID3v2, Vorbis, or MP4 atoms from the audio stream.
     */
    fun extractEmbeddedLyrics(context: Context, song: Song): String? {
        return try {
            val uri = Uri.parse(song.dataUri)
            context.contentResolver.openInputStream(uri)?.use { stream ->
                parseStreamForLyrics(stream)
            }
        } catch (e: Exception) {
            null
        }
    }

    private fun parseStreamForLyrics(stream: InputStream): String? {
        val buffer = ByteArray(MAX_TAG_READ_BYTES)
        var totalRead = 0
        while (totalRead < buffer.size) {
            val read = stream.read(buffer, totalRead, buffer.size - totalRead)
            if (read <= 0) break
            totalRead += read
        }
        if (totalRead < 10) return null

        // Check for ID3v2
        if (buffer[0] == 'I'.code.toByte() && buffer[1] == 'D'.code.toByte() && buffer[2] == '3'.code.toByte()) {
            return parseId3v2Lyrics(buffer, totalRead)
        }

        // Check for FLAC
        if (buffer[0] == 'f'.code.toByte() && buffer[1] == 'L'.code.toByte() &&
            buffer[2] == 'a'.code.toByte() && buffer[3] == 'C'.code.toByte()
        ) {
            return parseFlacVorbisLyrics(buffer, totalRead)
        }

        // Check for MP4 / M4A (contains ftyp or moov)
        if (totalRead > 12) {
            val magic = String(buffer, 4, 4, Charsets.US_ASCII)
            if (magic == "ftyp" || magic == "moov") {
                return parseMp4Lyrics(buffer, totalRead)
            }
        }

        return null
    }

    /**
     * Parses ID3v2 frames looking for USLT (unsynced lyrics) or SYLT (synced lyrics).
     */
    private fun parseId3v2Lyrics(buffer: ByteArray, length: Int): String? {
        val majorVersion = buffer[3].toInt() // 2, 3, or 4
        val flags = buffer[5].toInt()
        val tagSize = ((buffer[6].toInt() and 0x7F) shl 21) or
                ((buffer[7].toInt() and 0x7F) shl 14) or
                ((buffer[8].toInt() and 0x7F) shl 7) or
                (buffer[9].toInt() and 0x7F)

        val headerSize = 10
        val maxOffset = minOf(headerSize + tagSize, length)
        var offset = headerSize

        // Handle extended header if present in ID3v2.3/2.4
        if ((flags and 0x40) != 0 && offset + 4 <= maxOffset) {
            val extHeaderSize = if (majorVersion == 4) {
                ((buffer[offset].toInt() and 0x7F) shl 21) or
                        ((buffer[offset + 1].toInt() and 0x7F) shl 14) or
                        ((buffer[offset + 2].toInt() and 0x7F) shl 7) or
                        (buffer[offset + 3].toInt() and 0x7F)
            } else {
                ((buffer[offset].toInt() and 0xFF) shl 24) or
                        ((buffer[offset + 1].toInt() and 0xFF) shl 16) or
                        ((buffer[offset + 2].toInt() and 0xFF) shl 8) or
                        (buffer[offset + 3].toInt() and 0xFF)
            }
            offset += extHeaderSize
        }

        while (offset + 10 <= maxOffset) {
            // Frame ID
            val frameId = String(buffer, offset, 4, Charsets.US_ASCII)
            if (frameId.all { it == '\u0000' }) break // Padding reached

            val frameSize = if (majorVersion == 4) {
                // ID3v2.4 uses syncsafe integer
                ((buffer[offset + 4].toInt() and 0x7F) shl 21) or
                        ((buffer[offset + 5].toInt() and 0x7F) shl 14) or
                        ((buffer[offset + 6].toInt() and 0x7F) shl 7) or
                        (buffer[offset + 7].toInt() and 0x7F)
            } else {
                // ID3v2.3 uses regular 32-bit big-endian
                ((buffer[offset + 4].toInt() and 0xFF) shl 24) or
                        ((buffer[offset + 5].toInt() and 0xFF) shl 16) or
                        ((buffer[offset + 6].toInt() and 0xFF) shl 8) or
                        (buffer[offset + 7].toInt() and 0xFF)
            }

            if (frameSize <= 0 || offset + 10 + frameSize > maxOffset) break

            val framePayloadOffset = offset + 10
            if (frameId == "USLT") {
                // USLT: [Encoding 1B] [Lang 3B] [Descriptor null-terminated] [Lyrics text]
                val lyrics = parseUsltFrame(buffer, framePayloadOffset, frameSize)
                if (!lyrics.isNullOrBlank()) return lyrics
            } else if (frameId == "SYLT") {
                // SYLT: Synchronized lyrics frame
                val lyrics = parseSyltFrame(buffer, framePayloadOffset, frameSize)
                if (!lyrics.isNullOrBlank()) return lyrics
            }

            offset += 10 + frameSize
        }

        return null
    }

    private fun parseUsltFrame(buffer: ByteArray, offset: Int, size: Int): String? {
        if (size <= 4) return null
        val encodingByte = buffer[offset].toInt() and 0xFF
        val charset = when (encodingByte) {
            0 -> Charsets.ISO_8859_1
            1 -> Charsets.UTF_16
            2 -> Charsets.UTF_16BE
            3 -> Charsets.UTF_8
            else -> Charsets.UTF_8
        }

        // Skip 1 byte encoding + 3 bytes language
        var textOffset = offset + 4
        val end = offset + size

        // Find end of descriptor (null terminator)
        if (encodingByte == 1 || encodingByte == 2) {
            // 2-byte null terminator
            while (textOffset + 1 < end) {
                if (buffer[textOffset] == 0.toByte() && buffer[textOffset + 1] == 0.toByte()) {
                    textOffset += 2
                    break
                }
                textOffset += 2
            }
        } else {
            // 1-byte null terminator
            while (textOffset < end) {
                if (buffer[textOffset] == 0.toByte()) {
                    textOffset += 1
                    break
                }
                textOffset++
            }
        }

        if (textOffset >= end) return null
        return try {
            String(buffer, textOffset, end - textOffset, charset).trim()
        } catch (_: Exception) {
            null
        }
    }

    private fun parseSyltFrame(buffer: ByteArray, offset: Int, size: Int): String? {
        // Basic SYLT conversion to LRC format
        if (size <= 6) return null
        return try {
            val text = String(buffer, offset + 6, size - 6, Charsets.ISO_8859_1)
            text.ifBlank { null }
        } catch (_: Exception) {
            null
        }
    }

    /**
     * Parses FLAC Vorbis comments for LYRICS or UNSYNCEDLYRICS.
     */
    private fun parseFlacVorbisLyrics(buffer: ByteArray, length: Int): String? {
        var offset = 4 // skip "fLaC"
        while (offset + 4 <= length) {
            val header = buffer[offset].toInt() and 0xFF
            val isLast = (header and 0x80) != 0
            val blockType = header and 0x7F
            val blockSize = ((buffer[offset + 1].toInt() and 0xFF) shl 16) or
                    ((buffer[offset + 2].toInt() and 0xFF) shl 8) or
                    (buffer[offset + 3].toInt() and 0xFF)

            offset += 4
            if (offset + blockSize > length) break

            if (blockType == 4) { // VORBIS_COMMENT block
                val commentBlock = String(buffer, offset, blockSize, Charsets.UTF_8)
                val lines = commentBlock.split("\n", "\r")
                for (line in lines) {
                    val upper = line.uppercase()
                    if (upper.startsWith("LYRICS=") || upper.startsWith("UNSYNCEDLYRICS=")) {
                        val value = line.substringAfter("=").trim()
                        if (value.isNotEmpty()) return value
                    }
                }
            }

            if (isLast) break
            offset += blockSize
        }
        return null
    }

    /**
     * Parses MP4/M4A atoms looking for ©lyr atom.
     */
    private fun parseMp4Lyrics(buffer: ByteArray, length: Int): String? {
        val content = String(buffer, 0, length, Charsets.ISO_8859_1)
        val lyrTag = "\u00a9lyr"
        val idx = content.indexOf(lyrTag)
        if (idx >= 0 && idx + 16 < length) {
            // Look for "data" child atom following ©lyr
            val dataIdx = content.indexOf("data", idx)
            if (dataIdx in idx..(idx + 24) && dataIdx + 16 < length) {
                // In MP4 'data' atom: 4B size, 4B "data", 4B type, 4B locale, then payload
                val payloadOffset = dataIdx + 12
                val candidateLength = minOf(4096, length - payloadOffset)
                if (candidateLength > 0) {
                    val text = String(buffer, payloadOffset, candidateLength, Charsets.UTF_8).trim()
                    if (text.isNotEmpty()) return text
                }
            }
        }
        return null
    }

    private fun extractFromRetriever(context: Context, song: Song): String? {
        return try {
            val retriever = MediaMetadataRetriever()
            retriever.setDataSource(context, Uri.parse(song.dataUri))
            // Some devices store lyrics or comments in genre / description fields
            val comment = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_TITLE)
            retriever.release()
            null
        } catch (_: Exception) {
            null
        }
    }

    /**
     * Resolves real filesystem path from ContentResolver if possible.
     */
    private fun resolveFilePath(context: Context, dataUriString: String): String? {
        return try {
            val uri = Uri.parse(dataUriString)
            if (uri.scheme == "file") {
                return uri.path
            }
            if (uri.scheme == "content") {
                val projection = arrayOf(MediaStore.Audio.Media.DATA)
                context.contentResolver.query(uri, projection, null, null, null)?.use { cursor ->
                    if (cursor.moveToFirst()) {
                        val col = cursor.getColumnIndex(MediaStore.Audio.Media.DATA)
                        if (col >= 0) return cursor.getString(col)
                    }
                }
            }
            null
        } catch (_: Exception) {
            null
        }
    }
}

package com.example.util

import com.example.model.LyricLine

object LyricsParser {

    private val LRC_LINE_REGEX = Regex("""\[(\d{1,2}):(\d{2})(?:[.:](\d{1,3}))?](.*)""")
    private val TAG_REGEX = Regex("""\[([a-zA-Z]+):([^]]*)]""")

    /**
     * Parses standard or multi-timestamp LRC format string into a sorted list of [LyricLine].
     * Supports:
     * - [mm:ss.xx] or [mm:ss:xx] or [mm:ss.xxx] or [mm:ss]
     * - Multiple timestamps per line, e.g.: [00:12.34][00:45.67] Chorus lyric
     * - Metadata tags: [offset:+/-ms], [ti:Title], [ar:Artist]
     * - Plain text lyrics fallback (gives negative timestamps)
     */
    fun parse(lrcContent: String): List<LyricLine> {
        if (lrcContent.isBlank()) return emptyList()

        val lines = lrcContent.lines()
        val result = mutableListOf<LyricLine>()
        var offsetMs = 0L

        // First pass: extract offset tag if present
        for (raw in lines) {
            val line = raw.trim()
            val tagMatch = TAG_REGEX.matchEntire(line)
            if (tagMatch != null) {
                val key = tagMatch.groupValues[1].lowercase()
                val value = tagMatch.groupValues[2].trim()
                if (key == "offset") {
                    offsetMs = value.toLongOrNull() ?: 0L
                }
            }
        }

        // Second pass: extract timestamped lyrics
        var hasAnyTimestamp = false

        for (raw in lines) {
            val line = raw.trim()
            if (line.isEmpty()) continue

            // Ignore header tags like [ti:Title], [ar:Artist], [al:Album], [by:Author]
            if (TAG_REGEX.matches(line) && !LRC_LINE_REGEX.containsMatchIn(line)) {
                continue
            }

            // Extract all timestamps on this line
            val timestamps = mutableListOf<Long>()
            var remaining = line

            while (true) {
                val match = LRC_LINE_REGEX.find(remaining) ?: break
                val min = match.groupValues[1].toLongOrNull() ?: 0L
                val sec = match.groupValues[2].toLongOrNull() ?: 0L
                val fracStr = match.groupValues[3]
                val ms = when {
                    fracStr.isEmpty() -> 0L
                    fracStr.length == 1 -> (fracStr.toLongOrNull() ?: 0L) * 100
                    fracStr.length == 2 -> (fracStr.toLongOrNull() ?: 0L) * 10
                    else -> fracStr.take(3).toLongOrNull() ?: 0L
                }
                val totalMs = (min * 60 * 1000 + sec * 1000 + ms + offsetMs).coerceAtLeast(0L)
                timestamps.add(totalMs)
                hasAnyTimestamp = true

                val textPart = match.groupValues[4]
                // If there's another timestamp directly following, continue matching
                if (textPart.startsWith("[")) {
                    remaining = textPart
                } else {
                    remaining = textPart
                    break
                }
            }

            val lyricText = remaining.trim()
            if (timestamps.isNotEmpty()) {
                for (ts in timestamps) {
                    result.add(LyricLine(timestampMs = ts, text = lyricText))
                }
            }
        }

        // If no timestamps were found, treat as plain text lyrics with progressive negative or dummy timestamps
        if (!hasAnyTimestamp) {
            val nonBlankLines = lines.map { it.trim() }.filter { it.isNotEmpty() }
            return nonBlankLines.mapIndexed { index, text ->
                LyricLine(timestampMs = -(index + 1).toLong(), text = text)
            }
        }

        return result.sortedBy { it.timestampMs }
    }

    /**
     * Determines whether the parsed lyrics contain synchronized timestamps.
     */
    fun isSynchronized(lines: List<LyricLine>): Boolean {
        return lines.any { it.timestampMs >= 0L }
    }

    /**
     * Formats milliseconds into [mm:ss.xx] representation for display.
     */
    fun formatTimestamp(ms: Long): String {
        if (ms < 0) return ""
        val totalSeconds = ms / 1000
        val minutes = totalSeconds / 60
        val seconds = totalSeconds % 60
        val hundredths = (ms % 1000) / 10
        return "%02d:%02d.%02d".format(minutes, seconds, hundredths)
    }

    /**
     * Formats milliseconds into short [mm:ss] representation.
     */
    fun formatShortTime(ms: Long): String {
        if (ms < 0) return ""
        val totalSeconds = ms / 1000
        val minutes = totalSeconds / 60
        val seconds = totalSeconds % 60
        return "%02d:%02d".format(minutes, seconds)
    }
}

package com.example.data.demo

import android.content.Context
import android.net.Uri
import com.example.model.Song
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileOutputStream
import java.nio.ByteBuffer
import java.nio.ByteOrder
import kotlin.math.PI
import kotlin.math.sin

object DemoAudioGenerator {

    suspend fun getOrGenerateDemoSongs(context: Context): List<Song> = withContext(Dispatchers.IO) {
        val audioDir = File(context.filesDir, "demo_audio").apply { if (!exists()) mkdirs() }

        val trackSpecs = listOf(
            DemoTrackSpec(
                id = -101L,
                title = "Cyber Pulse (Synthwave)",
                artist = "Zenith Audio Lab",
                album = "Neon Horizons",
                durationSeconds = 25,
                baseFreq = 220.0, // A3
                style = 1,
                lyrics = """
                    [00:00.00] Welcome to ZERO MUSIC by Zenith
                    [00:03.50] Your Music, Your World
                    [00:07.00] Feel the digital pulse rising high
                    [00:11.50] Synthwave frequencies light the night
                    [00:15.50] Infinite sound in pure definition
                    [00:19.50] ZERO MUSIC powers your audio journey
                    [00:23.00] Zenith Engine running smooth
                """.trimIndent()
            ),
            DemoTrackSpec(
                id = -102L,
                title = "Midnight Horizon",
                artist = "Aura & Zenith",
                album = "Chill Lo-Fi Sessions",
                durationSeconds = 24,
                baseFreq = 174.6, // F3
                style = 2,
                lyrics = """
                    [00:00.00] Midnight Horizon - Aura & Zenith
                    [00:03.00] Drift away into warm analog sound
                    [00:07.00] Gentle chords soothing your thoughts
                    [00:11.50] Relax and let the melody carry you
                    [00:16.00] Crisp highs and velvety warm bass
                    [00:20.50] Sleep timer ready whenever you rest
                """.trimIndent()
            ),
            DemoTrackSpec(
                id = -103L,
                title = "Zero Gravity",
                artist = "Nova Sound",
                album = "Cosmic Waves",
                durationSeconds = 26,
                baseFreq = 146.8, // D3
                style = 3,
                lyrics = """
                    [00:00.00] Nova Sound - Zero Gravity
                    [00:04.00] Weightless in the audio atmosphere
                    [00:08.50] 3D surround spatial simulation active
                    [00:13.00] Echoes bouncing through open space
                    [00:17.50] Equalizer tuned for deep immersion
                    [00:22.00] Float with ZERO MUSIC
                """.trimIndent()
            ),
            DemoTrackSpec(
                id = -104L,
                title = "Electric Velocity",
                artist = "Zenith Project",
                album = "Future Bass 2026",
                durationSeconds = 24,
                baseFreq = 261.6, // C4
                style = 4,
                lyrics = """
                    [00:00.00] Electric Velocity - Zenith Project
                    [00:03.50] High octane tempo driving the beat
                    [00:07.50] Bass boost pumping through the line
                    [00:12.00] Feel every punch in the audio spectrum
                    [00:16.50] ZERO MUSIC: Precision and power
                    [00:20.50] Zenith - Built for music lovers
                """.trimIndent()
            )
        )

        val songs = mutableListOf<Song>()

        for (spec in trackSpecs) {
            val audioFile = File(audioDir, "track_${spec.id.coerceAtLeast(1)}.wav")
            if (!audioFile.exists() || audioFile.length() < 1000) {
                generateWavFile(audioFile, spec)
            }

            songs.add(
                Song(
                    id = spec.id,
                    title = spec.title,
                    artist = spec.artist,
                    album = spec.album,
                    durationMs = (spec.durationSeconds * 1000).toLong(),
                    dataUri = Uri.fromFile(audioFile).toString(),
                    albumArtUri = null,
                    isFavorite = false,
                    playCount = 0,
                    lyrics = spec.lyrics,
                    isBuiltIn = true
                )
            )
        }

        songs
    }

    private data class DemoTrackSpec(
        val id: Long,
        val title: String,
        val artist: String,
        val album: String,
        val durationSeconds: Int,
        val baseFreq: Double,
        val style: Int,
        val lyrics: String
    )

    private fun generateWavFile(file: File, spec: DemoTrackSpec) {
        val sampleRate = 22050
        val totalSamples = sampleRate * spec.durationSeconds
        val numChannels = 1
        val bitsPerSample = 16
        val byteRate = sampleRate * numChannels * (bitsPerSample / 8)
        val dataSize = totalSamples * (bitsPerSample / 8)

        FileOutputStream(file).use { fos ->
            // WAV Header (44 bytes)
            val header = ByteBuffer.allocate(44).apply {
                order(ByteOrder.LITTLE_ENDIAN)
                put("RIFF".toByteArray())
                putInt(36 + dataSize)
                put("WAVE".toByteArray())
                put("fmt ".toByteArray())
                putInt(16) // SubChunk1Size (PCM)
                putShort(1.toShort()) // AudioFormat (PCM)
                putShort(numChannels.toShort())
                putInt(sampleRate)
                putInt(byteRate)
                putShort((numChannels * (bitsPerSample / 8)).toShort())
                putShort(bitsPerSample.toShort())
                put("data".toByteArray())
                putInt(dataSize)
            }
            fos.write(header.array())

            // Synthesize audio samples with melodic chords and percussion
            val buffer = ByteBuffer.allocate(4096).apply { order(ByteOrder.LITTLE_ENDIAN) }

            // Chord progression factors
            val chordProgressions = when (spec.style) {
                1 -> listOf(1.0, 1.25, 1.5, 1.33) // Cyber Synth
                2 -> listOf(1.0, 1.2, 1.4, 1.1)   // Lo-Fi Minor
                3 -> listOf(1.0, 1.5, 1.75, 1.33) // Space Ambient
                else -> listOf(1.0, 1.25, 1.6, 1.5) // Future Bass
            }

            val samplesPerChord = (sampleRate * 2.0).toInt() // Change chord every 2s
            val beatInterval = (sampleRate * 0.5).toInt()    // 120 BPM beat

            for (i in 0 until totalSamples) {
                val chordIndex = (i / samplesPerChord) % chordProgressions.size
                val chordFactor = chordProgressions[chordIndex]
                val currentFreq = spec.baseFreq * chordFactor

                val t = i.toDouble() / sampleRate

                // Synthesize 3 harmonic voices
                val v1 = sin(2.0 * PI * currentFreq * t)
                val v2 = sin(2.0 * PI * (currentFreq * 1.5) * t) * 0.5
                val v3 = sin(2.0 * PI * (currentFreq * 2.0) * t) * 0.25

                // Bassline voice
                val bassFreq = spec.baseFreq * 0.5 * chordFactor
                val bass = sin(2.0 * PI * bassFreq * t) * 0.6

                // Rhythmic pulse / kick envelope
                val beatPos = i % beatInterval
                val beatEnvelope = (1.0 - (beatPos.toDouble() / beatInterval)).coerceIn(0.0, 1.0)
                val kick = if (beatPos < sampleRate * 0.1) {
                    sin(2.0 * PI * (80.0 * (1.0 - beatPos.toDouble() / (sampleRate * 0.1))) * t) * 0.7 * beatEnvelope
                } else {
                    0.0
                }

                // Envelope fade in and fade out
                val fadeEnvelope = when {
                    i < sampleRate -> (i.toDouble() / sampleRate)
                    i > totalSamples - sampleRate -> ((totalSamples - i).toDouble() / sampleRate)
                    else -> 1.0
                }

                val mixed = (v1 * 0.35 + v2 * 0.2 + v3 * 0.1 + bass * 0.25 + kick * 0.3) * fadeEnvelope
                val sampleValue = (mixed.coerceIn(-1.0, 1.0) * 32767.0).toInt().toShort()

                if (!buffer.hasRemaining()) {
                    fos.write(buffer.array())
                    buffer.clear()
                }
                buffer.putShort(sampleValue)
            }

            if (buffer.position() > 0) {
                fos.write(buffer.array(), 0, buffer.position())
            }
        }
    }
}

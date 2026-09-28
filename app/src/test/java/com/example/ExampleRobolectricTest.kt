package com.example

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import com.example.model.EqualizerState
import com.example.model.RepeatMode
import com.example.model.Song
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class ExampleRobolectricTest {

    @Test
    fun `read app name string from context`() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val appName = context.getString(R.string.app_name)
        assertEquals("ZERO MUSIC", appName)
    }

    @Test
    fun `song formatted duration test`() {
        val song = Song(
            id = 1L,
            title = "Cyber Pulse",
            artist = "Zenith",
            album = "Neon",
            durationMs = 154000L,
            dataUri = "content://media/audio/1"
        )
        assertEquals("2:34", song.formattedDuration)
    }

    @Test
    fun `theme mode enum values test`() {
        assertEquals(3, com.example.ui.theme.ThemeMode.values().size)
        assertEquals("System Default", com.example.ui.theme.ThemeMode.SYSTEM.displayName)
        assertEquals("Light Mode", com.example.ui.theme.ThemeMode.LIGHT.displayName)
        assertEquals("Dark Mode", com.example.ui.theme.ThemeMode.DARK.displayName)
    }

    @Test
    fun `support our music constants and strings test`() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val title = context.getString(R.string.support_our_music_title)
        val desc = context.getString(R.string.support_our_music_desc)
        val network = context.getString(R.string.support_our_music_network)
        val address = context.getString(R.string.support_our_music_address)

        assertEquals("Support Our Music", title)
        assertEquals("If you enjoy listening to our music, you can support us with a crypto donation. Your support helps us create more music.", desc)
        assertEquals("Crypto Donation · BNB Smart Chain (BEP-20)", network)
        assertEquals("0xd62c6f92872A3ee4b6Db1459A1FC142fE94Cf6Fb", address)

        assertEquals(com.example.ui.components.SUPPORT_WALLET_ADDRESS, address)
        assertEquals(com.example.ui.components.SUPPORT_HEADER_TITLE, title)
    }

    @Test
    fun `widget resources and action constants test`() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        assertEquals("Zero Music", context.getString(R.string.widget_name))
        assertEquals("No song playing", context.getString(R.string.widget_no_song_title))
        assertEquals("Tap to open Zero Music", context.getString(R.string.widget_tap_to_play))
        assertEquals("Play", context.getString(R.string.widget_action_play))
        assertEquals("Pause", context.getString(R.string.widget_action_pause))

        assertEquals("com.example.ACTION_PLAY_PAUSE", com.example.widget.MusicWidgetProvider.ACTION_PLAY_PAUSE)
        assertEquals("com.example.ACTION_PREV", com.example.widget.MusicWidgetProvider.ACTION_PREV)
        assertEquals("com.example.ACTION_NEXT", com.example.widget.MusicWidgetProvider.ACTION_NEXT)
        assertEquals("com.example.ACTION_UPDATE_WIDGET", com.example.widget.MusicWidgetProvider.ACTION_UPDATE_WIDGET)
    }

    @OptIn(coil.annotation.ExperimentalCoilApi::class)
    @Test
    fun `album art disk cache configuration and size formatting test`() {
        val context = ApplicationProvider.getApplicationContext<Context>()

        val cacheDir = com.example.util.AlbumArtDiskCache.getDiskCacheDir(context)
        assertNotNull(cacheDir)
        assertEquals(com.example.util.AlbumArtDiskCache.DISK_CACHE_DIR_NAME, cacheDir.name)

        // Test size formatting
        assertEquals("0 KB", com.example.util.AlbumArtDiskCache.formatCacheSize(0L))
        assertEquals("500 B", com.example.util.AlbumArtDiskCache.formatCacheSize(500L))
        assertEquals("1.5 KB", com.example.util.AlbumArtDiskCache.formatCacheSize(1536L))
        assertEquals("2.00 MB", com.example.util.AlbumArtDiskCache.formatCacheSize(2L * 1024 * 1024))

        // Test clearing cache
        val clearResult = com.example.util.AlbumArtDiskCache.clearDiskCache(context)
        assertTrue(clearResult)
        assertEquals(0L, com.example.util.AlbumArtDiskCache.getDiskCacheSizeBytes(context))

        // Test ImageLoader builder creates active disk cache
        val imageLoader = com.example.util.AlbumArtDiskCache.createImageLoader(context)
        assertNotNull(imageLoader)
        assertNotNull(imageLoader.diskCache)
        assertNotNull(imageLoader.memoryCache)
        assertEquals(com.example.util.AlbumArtDiskCache.MAX_DISK_CACHE_SIZE_BYTES, imageLoader.diskCache?.maxSize)
    }

    @Test
    fun `song and audio uri keyers test`() {
        val song = Song(
            id = 42L,
            title = "Starlight",
            artist = "Aura",
            album = "Cosmos",
            durationMs = 210000L,
            dataUri = "content://media/external/audio/media/42",
            albumArtUri = "content://media/external/audio/albumart/5"
        )

        val keyer = com.example.util.SongKeyer()
        val key = keyer.key(song, coil.request.Options(ApplicationProvider.getApplicationContext()))
        assertEquals("local_art_song_42", key)

        val uriKeyer = com.example.util.AudioUriKeyer()
        val uri = android.net.Uri.parse("content://media/external/audio/albumart/5")
        val uriKey = uriKeyer.key(uri, coil.request.Options(ApplicationProvider.getApplicationContext()))
        assertEquals("local_art_uri_5", uriKey)
    }

    @Test
    fun `lyrics parser lrc timestamps and multi-line test`() {
        val lrc = """
            [ti:Test Title]
            [ar:Test Artist]
            [offset:+500]
            [00:02.00] First lyric line
            [00:05.50][00:10.20] Chorus repeated line
            [01:00.00] Final outro
        """.trimIndent()

        val parsed = com.example.util.LyricsParser.parse(lrc)
        assertTrue(com.example.util.LyricsParser.isSynchronized(parsed))
        assertEquals(4, parsed.size)

        // 2000ms + 500ms offset = 2500ms
        assertEquals(2500L, parsed[0].timestampMs)
        assertEquals("First lyric line", parsed[0].text)

        // Multi-timestamp line: 5500 + 500 = 6000ms, and 10200 + 500 = 10700ms
        assertEquals(6000L, parsed[1].timestampMs)
        assertEquals("Chorus repeated line", parsed[1].text)
        assertEquals(10700L, parsed[2].timestampMs)
        assertEquals("Chorus repeated line", parsed[2].text)

        // Final line: 60000 + 500 = 60500ms
        assertEquals(60500L, parsed[3].timestampMs)
        assertEquals("Final outro", parsed[3].text)
    }

    @Test
    fun `lyrics parser plain text fallback test`() {
        val plainText = """
            First verse of song
            Second line of melody
            Third line of harmony
        """.trimIndent()

        val parsed = com.example.util.LyricsParser.parse(plainText)
        assertEquals(3, parsed.size)
        // Plain text has negative timestamps indicating unsynchronized
        assertTrue(parsed[0].timestampMs < 0)
        assertEquals("First verse of song", parsed[0].text)
        assertEquals(false, com.example.util.LyricsParser.isSynchronized(parsed))
    }
}


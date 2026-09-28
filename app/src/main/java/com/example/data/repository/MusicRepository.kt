package com.example.data.repository

import android.content.ContentUris
import android.content.Context
import android.net.Uri
import android.provider.MediaStore
import com.example.data.demo.DemoAudioGenerator
import com.example.data.local.MusicDao
import com.example.data.local.PlaylistEntity
import com.example.data.local.PlaylistSongCrossRef
import com.example.data.local.SongEntity
import com.example.model.EqualizerState
import com.example.model.Playlist
import com.example.model.Song
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.withContext

class MusicRepository(
    private val context: Context,
    private val musicDao: MusicDao
) {
    private val prefs = context.getSharedPreferences("zero_music_prefs", Context.MODE_PRIVATE)

    val allSongs: Flow<List<Song>> = musicDao.getAllSongs().map { list -> list.map { it.toSong() } }
    val favoriteSongs: Flow<List<Song>> = musicDao.getFavoriteSongs().map { list -> list.map { it.toSong() } }
    val recentlyPlayed: Flow<List<Song>> = musicDao.getRecentlyPlayed().map { list -> list.map { it.toSong() } }
    val mostPlayed: Flow<List<Song>> = musicDao.getMostPlayed().map { list -> list.map { it.toSong() } }

    val allPlaylists: Flow<List<Playlist>> = musicDao.getAllPlaylists().map { list ->
        list.map { entity ->
            Playlist(
                id = entity.id,
                name = entity.name,
                description = entity.description,
                createdAt = entity.createdAt
            )
        }
    }

    suspend fun initializeBuiltInSongsIfNeeded() = withContext(Dispatchers.IO) {
        val existing = musicDao.getAllSongs().first()
        if (existing.isEmpty()) {
            val demoSongs = DemoAudioGenerator.getOrGenerateDemoSongs(context)
            musicDao.insertSongs(demoSongs.map { SongEntity.fromSong(it) })
            // Create default playlists
            val p1 = musicDao.insertPlaylist(PlaylistEntity(name = "Chill Vibes", description = "Relaxing & Lo-Fi rhythms"))
            val p2 = musicDao.insertPlaylist(PlaylistEntity(name = "High Velocity", description = "Futuristic bass & synth beats"))
            if (demoSongs.isNotEmpty()) {
                musicDao.addSongToPlaylist(PlaylistSongCrossRef(p1, demoSongs[0].id, 0))
                if (demoSongs.size > 1) {
                    musicDao.addSongToPlaylist(PlaylistSongCrossRef(p1, demoSongs[1].id, 1))
                }
                if (demoSongs.size > 2) {
                    musicDao.addSongToPlaylist(PlaylistSongCrossRef(p2, demoSongs[2].id, 0))
                }
                if (demoSongs.size > 3) {
                    musicDao.addSongToPlaylist(PlaylistSongCrossRef(p2, demoSongs[3].id, 1))
                }
            }
        }
    }

    suspend fun scanStorage(): Int = withContext(Dispatchers.IO) {
        var scannedCount = 0
        val scannedSongs = mutableListOf<SongEntity>()

        val projection = arrayOf(
            MediaStore.Audio.Media._ID,
            MediaStore.Audio.Media.TITLE,
            MediaStore.Audio.Media.ARTIST,
            MediaStore.Audio.Media.ALBUM,
            MediaStore.Audio.Media.DURATION,
            MediaStore.Audio.Media.ALBUM_ID
        )

        val selection = "${MediaStore.Audio.Media.IS_MUSIC} != 0 AND ${MediaStore.Audio.Media.DURATION} >= 10000"

        try {
            // Pre-fetch existing songs in a single query for O(1) in-memory lookup
            val existingMap = musicDao.getAllSongsList().associateBy { it.id }

            context.contentResolver.query(
                MediaStore.Audio.Media.EXTERNAL_CONTENT_URI,
                projection,
                selection,
                null,
                "${MediaStore.Audio.Media.TITLE} ASC"
            )?.use { cursor ->
                val idCol = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media._ID)
                val titleCol = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media.TITLE)
                val artistCol = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media.ARTIST)
                val albumCol = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media.ALBUM)
                val durationCol = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media.DURATION)
                val albumIdCol = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media.ALBUM_ID)

                while (cursor.moveToNext()) {
                    val id = cursor.getLong(idCol)
                    val title = cursor.getString(titleCol) ?: "Unknown Track"
                    val artist = cursor.getString(artistCol) ?: "Unknown Artist"
                    val album = cursor.getString(albumCol) ?: "Unknown Album"
                    val duration = cursor.getLong(durationCol)
                    val albumId = cursor.getLong(albumIdCol)

                    val contentUri = ContentUris.withAppendedId(
                        MediaStore.Audio.Media.EXTERNAL_CONTENT_URI,
                        id
                    ).toString()

                    val albumArtUri = ContentUris.withAppendedId(
                        Uri.parse("content://media/external/audio/albumart"),
                        albumId
                    ).toString()

                    // Check if existing in DB to retain user favorites/lyrics/play count
                    val existing = existingMap[id]

                    val entity = SongEntity(
                        id = id,
                        title = title,
                        artist = artist,
                        album = album,
                        durationMs = duration,
                        dataUri = contentUri,
                        albumArtUri = albumArtUri,
                        isFavorite = existing?.isFavorite ?: false,
                        playCount = existing?.playCount ?: 0,
                        lastPlayedTime = existing?.lastPlayedTime ?: 0L,
                        lyrics = existing?.lyrics,
                        isBuiltIn = false
                    )
                    scannedSongs.add(entity)
                    scannedCount++
                }
            }

            if (scannedSongs.isNotEmpty()) {
                musicDao.upsertSongs(scannedSongs)
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }

        // Always ensure demo songs are present so user is never stranded with 0 tracks
        val demoSongs = DemoAudioGenerator.getOrGenerateDemoSongs(context)
        for (demo in demoSongs) {
            val existing = musicDao.getSongById(demo.id)
            if (existing == null) {
                musicDao.upsertSong(SongEntity.fromSong(demo))
                scannedCount++
            }
        }

        scannedCount
    }

    suspend fun toggleFavorite(songId: Long, currentIsFavorite: Boolean) = withContext(Dispatchers.IO) {
        musicDao.updateFavorite(songId, !currentIsFavorite)
    }

    suspend fun recordSongPlayed(songId: Long) = withContext(Dispatchers.IO) {
        musicDao.incrementPlayCount(songId)
    }

    suspend fun saveLyrics(songId: Long, lyrics: String) = withContext(Dispatchers.IO) {
        musicDao.updateLyrics(songId, lyrics)
    }

    suspend fun createPlaylist(name: String, description: String = ""): Long = withContext(Dispatchers.IO) {
        musicDao.insertPlaylist(PlaylistEntity(name = name, description = description))
    }

    suspend fun deletePlaylist(playlistId: Long) = withContext(Dispatchers.IO) {
        musicDao.deletePlaylist(playlistId)
        musicDao.deletePlaylistSongs(playlistId)
    }

    suspend fun addSongToPlaylist(playlistId: Long, songId: Long) = withContext(Dispatchers.IO) {
        musicDao.addSongToPlaylist(PlaylistSongCrossRef(playlistId, songId))
    }

    suspend fun removeSongFromPlaylist(playlistId: Long, songId: Long) = withContext(Dispatchers.IO) {
        musicDao.removeSongFromPlaylist(playlistId, songId)
    }

    fun getSongsForPlaylist(playlistId: Long): Flow<List<Song>> {
        return musicDao.getSongsForPlaylist(playlistId).map { list -> list.map { it.toSong() } }
    }

    // Equalizer persistence
    fun loadEqualizerState(): EqualizerState {
        val enabled = prefs.getBoolean("eq_enabled", true)
        val band0 = prefs.getInt("eq_band_0", 0)
        val band1 = prefs.getInt("eq_band_1", 0)
        val band2 = prefs.getInt("eq_band_2", 0)
        val band3 = prefs.getInt("eq_band_3", 0)
        val band4 = prefs.getInt("eq_band_4", 0)
        val bassBoost = prefs.getInt("eq_bass_boost", 200)
        val virtualizer = prefs.getInt("eq_virtualizer", 150)
        val preset = prefs.getString("eq_preset", "Normal") ?: "Normal"

        return EqualizerState(
            isEnabled = enabled,
            bandLevels = listOf(band0, band1, band2, band3, band4),
            bassBoost = bassBoost,
            virtualizer = virtualizer,
            currentPreset = preset
        )
    }

    fun saveEqualizerState(state: EqualizerState) {
        prefs.edit().apply {
            putBoolean("eq_enabled", state.isEnabled)
            putInt("eq_band_0", state.bandLevels.getOrElse(0) { 0 })
            putInt("eq_band_1", state.bandLevels.getOrElse(1) { 0 })
            putInt("eq_band_2", state.bandLevels.getOrElse(2) { 0 })
            putInt("eq_band_3", state.bandLevels.getOrElse(3) { 0 })
            putInt("eq_band_4", state.bandLevels.getOrElse(4) { 0 })
            putInt("eq_bass_boost", state.bassBoost)
            putInt("eq_virtualizer", state.virtualizer)
            putString("eq_preset", state.currentPreset)
            apply()
        }
    }

    // Theme persistence
    fun loadThemeIndex(): Int = prefs.getInt("selected_theme", 0)
    fun saveThemeIndex(index: Int) = prefs.edit().putInt("selected_theme", index).apply()

    fun loadThemeMode(): com.example.ui.theme.ThemeMode {
        val name = prefs.getString("theme_mode", com.example.ui.theme.ThemeMode.SYSTEM.name)
        return try {
            com.example.ui.theme.ThemeMode.valueOf(name ?: com.example.ui.theme.ThemeMode.SYSTEM.name)
        } catch (e: Exception) {
            com.example.ui.theme.ThemeMode.SYSTEM
        }
    }

    fun saveThemeMode(mode: com.example.ui.theme.ThemeMode) {
        prefs.edit().putString("theme_mode", mode.name).apply()
    }

    fun loadDynamicColor(): Boolean = prefs.getBoolean("use_dynamic_color", true)
    fun saveDynamicColor(enabled: Boolean) = prefs.edit().putBoolean("use_dynamic_color", enabled).apply()
}

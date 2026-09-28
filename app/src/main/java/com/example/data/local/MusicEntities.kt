package com.example.data.local

import androidx.room.Entity
import androidx.room.PrimaryKey
import com.example.model.Song

@Entity(tableName = "songs")
data class SongEntity(
    @PrimaryKey val id: Long,
    val title: String,
    val artist: String,
    val album: String,
    val durationMs: Long,
    val dataUri: String,
    val albumArtUri: String? = null,
    val isFavorite: Boolean = false,
    val playCount: Int = 0,
    val lastPlayedTime: Long = 0L,
    val lyrics: String? = null,
    val isBuiltIn: Boolean = false
) {
    fun toSong(): Song = Song(
        id = id,
        title = title,
        artist = artist,
        album = album,
        durationMs = durationMs,
        dataUri = dataUri,
        albumArtUri = albumArtUri,
        isFavorite = isFavorite,
        playCount = playCount,
        lyrics = lyrics,
        isBuiltIn = isBuiltIn
    )

    companion object {
        fun fromSong(song: Song, lastPlayedTime: Long = 0L): SongEntity = SongEntity(
            id = song.id,
            title = song.title,
            artist = song.artist,
            album = song.album,
            durationMs = song.durationMs,
            dataUri = song.dataUri,
            albumArtUri = song.albumArtUri,
            isFavorite = song.isFavorite,
            playCount = song.playCount,
            lastPlayedTime = lastPlayedTime,
            lyrics = song.lyrics,
            isBuiltIn = song.isBuiltIn
        )
    }
}

@Entity(tableName = "playlists")
data class PlaylistEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val name: String,
    val description: String = "",
    val createdAt: Long = System.currentTimeMillis()
)

@Entity(
    tableName = "playlist_songs",
    primaryKeys = ["playlistId", "songId"]
)
data class PlaylistSongCrossRef(
    val playlistId: Long,
    val songId: Long,
    val orderIndex: Int = 0
)

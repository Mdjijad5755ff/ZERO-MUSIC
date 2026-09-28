package com.example.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.MusicNote
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import coil.compose.SubcomposeAsyncImage
import coil.request.ImageRequest
import com.example.model.Song

/**
 * High-performance Composable that displays local album artwork using Coil's
 * disk-based caching strategy. Automatically caches decoded, downsampled thumbnails
 * to disk for instant loading when scrolling through the library.
 */
@Composable
fun SongAlbumArt(
    song: Song,
    modifier: Modifier = Modifier,
    shape: Shape = RoundedCornerShape(10.dp),
    contentScale: ContentScale = ContentScale.Crop,
    showPlayingIndicator: Boolean = false,
    isPlaying: Boolean = false
) {
    val context = LocalContext.current

    Box(
        modifier = modifier.clip(shape),
        contentAlignment = Alignment.Center
    ) {
        SubcomposeAsyncImage(
            model = ImageRequest.Builder(context)
                .data(song)
                .crossfade(true)
                .build(),
            contentDescription = "${song.title} artwork",
            contentScale = contentScale,
            modifier = Modifier.fillMaxSize(),
            loading = {
                FallbackArtwork(
                    song = song,
                    modifier = Modifier.fillMaxSize(),
                    shape = shape
                )
            },
            error = {
                FallbackArtwork(
                    song = song,
                    modifier = Modifier.fillMaxSize(),
                    shape = shape
                )
            }
        )

        // Overlay equalizer bars when currently playing
        if (showPlayingIndicator && isPlaying) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(Color.Black.copy(alpha = 0.45f)),
                contentAlignment = Alignment.Center
            ) {
                AnimatedEqualizerIndicator()
            }
        }
    }
}

@Composable
private fun FallbackArtwork(
    song: Song,
    modifier: Modifier = Modifier,
    shape: Shape = RoundedCornerShape(10.dp)
) {
    // Generate a deterministic gradient color based on the song title/album
    val hash = (song.title + song.artist).hashCode()
    val hue = (hash and 0x7FFFFFFF) % 360f

    Box(
        modifier = modifier
            .clip(shape)
            .background(
                Brush.linearGradient(
                    listOf(
                        MaterialTheme.colorScheme.surfaceVariant,
                        MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.7f),
                        MaterialTheme.colorScheme.primary.copy(alpha = 0.25f)
                    )
                )
            ),
        contentAlignment = Alignment.Center
    ) {
        Icon(
            imageVector = Icons.Default.MusicNote,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.primary.copy(alpha = 0.8f),
            modifier = Modifier.size(24.dp)
        )
    }
}

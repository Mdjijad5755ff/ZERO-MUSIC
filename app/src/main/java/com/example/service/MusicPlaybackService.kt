package com.example.service

import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.media.audiofx.BassBoost
import android.media.audiofx.Equalizer
import android.media.audiofx.Virtualizer
import android.net.Uri
import android.os.Build
import android.os.PowerManager
import androidx.annotation.OptIn
import androidx.media3.common.AudioAttributes
import androidx.media3.common.C
import androidx.media3.common.ForwardingPlayer
import androidx.media3.common.MediaItem
import androidx.media3.common.MediaMetadata
import androidx.media3.common.PlaybackException
import androidx.media3.common.Player
import androidx.media3.common.util.UnstableApi
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.session.MediaSession
import androidx.media3.session.MediaSessionService
import com.example.MainActivity
import com.example.model.EqualizerState
import com.example.model.Song
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

@OptIn(UnstableApi::class)
class MusicPlaybackService : MediaSessionService() {

    private var mediaSession: MediaSession? = null
    private var exoPlayer: ExoPlayer? = null
    private var forwardingPlayer: ForwardingPlayer? = null

    // Audio effects
    private var equalizer: Equalizer? = null
    private var bassBoost: BassBoost? = null
    private var virtualizer: Virtualizer? = null

    // Dedicated Partial WakeLock to guarantee uninterrupted audio when screen is turned off
    private var wakeLock: PowerManager.WakeLock? = null

    private val serviceScope = CoroutineScope(Dispatchers.Main + Job())
    private var progressTrackingJob: Job? = null

    override fun onGetSession(controllerInfo: MediaSession.ControllerInfo): MediaSession? = mediaSession

    override fun onCreate() {
        super.onCreate()

        // Initialize PowerManager WakeLock
        val powerManager = getSystemService(Context.POWER_SERVICE) as PowerManager
        wakeLock = powerManager.newWakeLock(
            PowerManager.PARTIAL_WAKE_LOCK,
            "ZeroMusic:AudioPlaybackWakeLock"
        ).apply {
            setReferenceCounted(false)
        }

        // Initialize ExoPlayer with AudioAttributes, AudioBecomingNoisy, and WakeMode
        val player = ExoPlayer.Builder(this)
            .setAudioAttributes(
                AudioAttributes.Builder()
                    .setContentType(C.AUDIO_CONTENT_TYPE_MUSIC)
                    .setUsage(C.USAGE_MEDIA)
                    .build(),
                /* handleAudioFocus= */ true
            )
            .setWakeMode(C.WAKE_MODE_LOCAL)
            .setHandleAudioBecomingNoisy(true)
            .build()
        exoPlayer = player

        // Forward next/previous commands to MusicPlayerManager
        val forwarder = object : ForwardingPlayer(player) {
            override fun seekToNext() {
                MusicPlayerManager.playNext()
            }

            override fun seekToNextMediaItem() {
                MusicPlayerManager.playNext()
            }

            override fun seekToPrevious() {
                MusicPlayerManager.playPrevious()
            }

            override fun seekToPreviousMediaItem() {
                MusicPlayerManager.playPrevious()
            }
        }
        forwardingPlayer = forwarder

        // Build MediaSession linked with MainActivity pending intent
        val openAppIntent = Intent(this, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_SINGLE_TOP or Intent.FLAG_ACTIVITY_CLEAR_TOP
        }
        val openAppPendingIntent = PendingIntent.getActivity(
            this,
            0,
            openAppIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        mediaSession = MediaSession.Builder(this, forwarder)
            .setSessionActivity(openAppPendingIntent)
            .build()

        // Player listener for playback changes, song completion, and equalizer session ID
        player.addListener(object : Player.Listener {
            override fun onIsPlayingChanged(isPlaying: Boolean) {
                val currentSong = MusicPlayerManager.currentSong.value
                val duration = if (player.duration > 0) player.duration else (currentSong?.durationMs ?: 0L)
                MusicPlayerManager.onPlaybackStateChanged(
                    playing = isPlaying,
                    positionMs = player.currentPosition,
                    durationMs = duration
                )

                if (isPlaying) {
                    acquireWakeLock()
                    startProgressTracker()
                } else {
                    releaseWakeLock()
                    stopProgressTracker()
                }
                com.example.widget.MusicWidgetProvider.updateAllWidgets(this@MusicPlaybackService)
            }

            override fun onPlaybackStateChanged(playbackState: Int) {
                if (playbackState == Player.STATE_ENDED) {
                    MusicPlayerManager.onSongCompleted()
                }
            }

            override fun onAudioSessionIdChanged(audioSessionId: Int) {
                setupAudioEffects(audioSessionId)
                applyEqualizerSettings(MusicPlayerManager.equalizerState.value)
            }

            override fun onPlayerError(error: PlaybackException) {
                error.printStackTrace()
                // Move safely to next track on read/playback error
                MusicPlayerManager.playNext()
            }
        })

        // Listen to UI/Manager commands
        MusicPlayerManager.serviceActionCallback = { action ->
            when (action) {
                is MusicPlayerManager.ServiceAction.Play -> playSong(action.song)
                is MusicPlayerManager.ServiceAction.Resume -> resumePlayback()
                is MusicPlayerManager.ServiceAction.Pause -> pausePlayback()
                is MusicPlayerManager.ServiceAction.Seek -> seekTo(action.positionMs)
                is MusicPlayerManager.ServiceAction.Stop -> stopPlayback()
                is MusicPlayerManager.ServiceAction.ApplyEqualizer -> applyEqualizerSettings(action.state)
                is MusicPlayerManager.ServiceAction.SetVolume -> setVolume(action.volume)
            }
        }
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        super.onStartCommand(intent, flags, startId)
        when (intent?.action) {
            com.example.widget.MusicWidgetProvider.ACTION_PLAY_PAUSE -> {
                MusicPlayerManager.playPause()
            }
            com.example.widget.MusicWidgetProvider.ACTION_PREV -> {
                MusicPlayerManager.playPrevious()
            }
            com.example.widget.MusicWidgetProvider.ACTION_NEXT -> {
                MusicPlayerManager.playNext()
            }
        }
        return START_STICKY
    }

    override fun onTaskRemoved(rootIntent: Intent?) {
        super.onTaskRemoved(rootIntent)
        val player = exoPlayer
        // When the user swipes away/closes the app from Recents,
        // continue playing in background if playback is currently active.
        if (player == null || !player.isPlaying) {
            stopSelf()
        }
    }

    private fun playSong(song: Song) {
        val player = exoPlayer ?: return

        try {
            val metadata = MediaMetadata.Builder()
                .setTitle(song.title)
                .setArtist(song.artist)
                .setAlbumTitle(song.album)
                .build()

            val mediaItem = MediaItem.Builder()
                .setUri(Uri.parse(song.dataUri))
                .setMediaMetadata(metadata)
                .build()

            player.setMediaItem(mediaItem)
            player.prepare()
            player.play()

            // Setup audio effects for this session
            val sessionId = player.audioSessionId
            if (sessionId != C.AUDIO_SESSION_ID_UNSET) {
                setupAudioEffects(sessionId)
                applyEqualizerSettings(MusicPlayerManager.equalizerState.value)
            }

            acquireWakeLock()
            startProgressTracker()
        } catch (e: Exception) {
            e.printStackTrace()
            MusicPlayerManager.playNext()
        }
    }

    private fun resumePlayback() {
        exoPlayer?.let { player ->
            player.play()
            acquireWakeLock()
            startProgressTracker()
        } ?: run {
            MusicPlayerManager.currentSong.value?.let { playSong(it) }
        }
    }

    private fun pausePlayback() {
        exoPlayer?.pause()
        releaseWakeLock()
        stopProgressTracker()
    }

    private fun seekTo(positionMs: Long) {
        exoPlayer?.seekTo(positionMs)
    }

    private fun stopPlayback() {
        releaseWakeLock()
        stopProgressTracker()
        exoPlayer?.stop()
        stopSelf()
    }

    private fun setVolume(volume: Float) {
        val clamped = volume.coerceIn(0f, 1f)
        exoPlayer?.volume = clamped
    }

    private fun acquireWakeLock() {
        try {
            if (wakeLock?.isHeld == false) {
                wakeLock?.acquire(12 * 60 * 60 * 1000L) // 12 hours max safety timeout
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    private fun releaseWakeLock() {
        try {
            if (wakeLock?.isHeld == true) {
                wakeLock?.release()
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    private fun setupAudioEffects(audioSessionId: Int) {
        if (audioSessionId == C.AUDIO_SESSION_ID_UNSET) return
        releaseAudioEffects()
        try {
            equalizer = Equalizer(0, audioSessionId).apply { enabled = true }
        } catch (e: Exception) {
            e.printStackTrace()
        }
        try {
            bassBoost = BassBoost(0, audioSessionId).apply { enabled = true }
        } catch (e: Exception) {
            e.printStackTrace()
        }
        try {
            virtualizer = Virtualizer(0, audioSessionId).apply { enabled = true }
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    private fun applyEqualizerSettings(state: EqualizerState) {
        try {
            equalizer?.let { eq ->
                eq.enabled = state.isEnabled
                if (state.isEnabled) {
                    val numBands = eq.numberOfBands.toInt()
                    val minBandLevel = eq.bandLevelRange[0]
                    val maxBandLevel = eq.bandLevelRange[1]

                    for (i in 0 until minOf(numBands, state.bandLevels.size)) {
                        val level = state.bandLevels[i].toShort()
                        val clamped = level.coerceIn(minBandLevel, maxBandLevel)
                        eq.setBandLevel(i.toShort(), clamped)
                    }
                }
            }
            bassBoost?.let { bb ->
                bb.enabled = state.isEnabled
                if (state.isEnabled && bb.strengthSupported) {
                    bb.setStrength(state.bassBoost.toShort().coerceIn(0, 1000))
                }
            }
            virtualizer?.let { vz ->
                vz.enabled = state.isEnabled
                if (state.isEnabled && vz.strengthSupported) {
                    vz.setStrength(state.virtualizer.toShort().coerceIn(0, 1000))
                }
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    private fun startProgressTracker() {
        stopProgressTracker()
        progressTrackingJob = serviceScope.launch {
            while (isActive) {
                exoPlayer?.let { player ->
                    if (player.isPlaying) {
                        val currentSong = MusicPlayerManager.currentSong.value
                        val duration = if (player.duration > 0) player.duration else (currentSong?.durationMs ?: 0L)
                        MusicPlayerManager.onPlaybackStateChanged(
                            playing = true,
                            positionMs = player.currentPosition,
                            durationMs = duration
                        )
                    }
                }
                delay(500)
            }
        }
    }

    private fun stopProgressTracker() {
        progressTrackingJob?.cancel()
        progressTrackingJob = null
    }

    private fun releaseAudioEffects() {
        try {
            equalizer?.release()
            bassBoost?.release()
            virtualizer?.release()
        } catch (e: Exception) {
            e.printStackTrace()
        }
        equalizer = null
        bassBoost = null
        virtualizer = null
    }

    override fun onDestroy() {
        releaseAudioEffects()
        releaseWakeLock()
        wakeLock = null
        stopProgressTracker()

        mediaSession?.run {
            player.release()
            release()
            mediaSession = null
        }
        exoPlayer = null
        forwardingPlayer = null

        super.onDestroy()
    }

    companion object {
        fun startService(context: Context) {
            val intent = Intent(context, MusicPlaybackService::class.java)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                context.startForegroundService(intent)
            } else {
                context.startService(intent)
            }
        }
    }
}

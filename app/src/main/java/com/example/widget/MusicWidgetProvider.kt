package com.example.widget

import android.app.PendingIntent
import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.graphics.LinearGradient
import android.graphics.Paint
import android.graphics.Path
import android.graphics.Rect
import android.graphics.RectF
import android.graphics.Shader
import android.net.Uri
import android.os.Build
import android.util.Size
import android.widget.RemoteViews
import com.example.MainActivity
import com.example.R
import com.example.model.Song
import com.example.service.MusicPlaybackService
import com.example.service.MusicPlayerManager
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * Android Home Screen Music Player Widget Provider.
 * Displays currently playing song, artist, album artwork, and real-time playback controls.
 */
class MusicWidgetProvider : AppWidgetProvider() {

    override fun onUpdate(
        context: Context,
        appWidgetManager: AppWidgetManager,
        appWidgetIds: IntArray
    ) {
        super.onUpdate(context, appWidgetManager, appWidgetIds)
        for (appWidgetId in appWidgetIds) {
            updateAppWidget(context, appWidgetManager, appWidgetId)
        }
    }

    override fun onReceive(context: Context, intent: Intent) {
        super.onReceive(context, intent)
        val action = intent.action ?: return

        when (action) {
            ACTION_PLAY_PAUSE -> {
                MusicPlaybackService.startService(context.applicationContext)
                if (MusicPlayerManager.currentSong.value == null && MusicPlayerManager.queue.value.isEmpty()) {
                    widgetScope.launch(Dispatchers.IO) {
                        try {
                            val db = com.example.data.local.MusicDatabase.getInstance(context.applicationContext)
                            val entities = db.musicDao().getAllSongsList()
                            if (entities.isNotEmpty()) {
                                val songs = entities.map { it.toSong() }
                                withContext(Dispatchers.Main) {
                                    MusicPlayerManager.playSong(songs.first(), songs)
                                    updateAllWidgets(context)
                                }
                            } else {
                                withContext(Dispatchers.Main) {
                                    MusicPlayerManager.playPause()
                                    updateAllWidgets(context)
                                }
                            }
                        } catch (_: Exception) {
                            withContext(Dispatchers.Main) {
                                MusicPlayerManager.playPause()
                                updateAllWidgets(context)
                            }
                        }
                    }
                } else {
                    MusicPlayerManager.playPause()
                    updateAllWidgets(context)
                }
            }
            ACTION_PREV -> {
                MusicPlaybackService.startService(context.applicationContext)
                if (MusicPlayerManager.currentSong.value == null && MusicPlayerManager.queue.value.isEmpty()) {
                    widgetScope.launch(Dispatchers.IO) {
                        try {
                            val db = com.example.data.local.MusicDatabase.getInstance(context.applicationContext)
                            val entities = db.musicDao().getAllSongsList()
                            if (entities.isNotEmpty()) {
                                val songs = entities.map { it.toSong() }
                                withContext(Dispatchers.Main) {
                                    MusicPlayerManager.playSong(songs.first(), songs)
                                    updateAllWidgets(context)
                                }
                            } else {
                                withContext(Dispatchers.Main) {
                                    MusicPlayerManager.playPrevious()
                                    updateAllWidgets(context)
                                }
                            }
                        } catch (_: Exception) {
                            withContext(Dispatchers.Main) {
                                MusicPlayerManager.playPrevious()
                                updateAllWidgets(context)
                            }
                        }
                    }
                } else {
                    MusicPlayerManager.playPrevious()
                    updateAllWidgets(context)
                }
            }
            ACTION_NEXT -> {
                MusicPlaybackService.startService(context.applicationContext)
                if (MusicPlayerManager.currentSong.value == null && MusicPlayerManager.queue.value.isEmpty()) {
                    widgetScope.launch(Dispatchers.IO) {
                        try {
                            val db = com.example.data.local.MusicDatabase.getInstance(context.applicationContext)
                            val entities = db.musicDao().getAllSongsList()
                            if (entities.isNotEmpty()) {
                                val songs = entities.map { it.toSong() }
                                withContext(Dispatchers.Main) {
                                    MusicPlayerManager.playSong(songs.first(), songs)
                                    updateAllWidgets(context)
                                }
                            } else {
                                withContext(Dispatchers.Main) {
                                    MusicPlayerManager.playNext()
                                    updateAllWidgets(context)
                                }
                            }
                        } catch (_: Exception) {
                            withContext(Dispatchers.Main) {
                                MusicPlayerManager.playNext()
                                updateAllWidgets(context)
                            }
                        }
                    }
                } else {
                    MusicPlayerManager.playNext()
                    updateAllWidgets(context)
                }
            }
            ACTION_UPDATE_WIDGET -> {
                updateAllWidgets(context)
            }
        }
    }

    companion object {
        const val ACTION_PLAY_PAUSE = "com.example.ACTION_PLAY_PAUSE"
        const val ACTION_PREV = "com.example.ACTION_PREV"
        const val ACTION_NEXT = "com.example.ACTION_NEXT"
        const val ACTION_UPDATE_WIDGET = "com.example.ACTION_UPDATE_WIDGET"

        private val widgetScope = CoroutineScope(Dispatchers.Main + SupervisorJob())

        /**
         * Broadcast helper to trigger update across all active widget instances.
         */
        fun updateAllWidgets(context: Context) {
            widgetScope.launch {
                val appWidgetManager = AppWidgetManager.getInstance(context)
                val componentName = ComponentName(context, MusicWidgetProvider::class.java)
                val widgetIds = appWidgetManager.getAppWidgetIds(componentName)
                if (widgetIds != null && widgetIds.isNotEmpty()) {
                    val currentSong = MusicPlayerManager.currentSong.value
                    val isPlaying = MusicPlayerManager.isPlaying.value
                    val positionMs = MusicPlayerManager.currentPositionMs.value
                    val durationMs = MusicPlayerManager.durationMs.value

                    // Decode artwork in IO dispatcher
                    val artworkBitmap = withContext(Dispatchers.IO) {
                        loadAlbumArt(context, currentSong)
                    }

                    for (widgetId in widgetIds) {
                        val views = buildRemoteViews(
                            context = context,
                            song = currentSong,
                            isPlaying = isPlaying,
                            positionMs = positionMs,
                            durationMs = durationMs,
                            artwork = artworkBitmap
                        )
                        appWidgetManager.updateAppWidget(widgetId, views)
                    }
                }
            }
        }

        fun updateAppWidget(
            context: Context,
            appWidgetManager: AppWidgetManager,
            appWidgetId: Int
        ) {
            widgetScope.launch {
                val currentSong = MusicPlayerManager.currentSong.value
                val isPlaying = MusicPlayerManager.isPlaying.value
                val positionMs = MusicPlayerManager.currentPositionMs.value
                val durationMs = MusicPlayerManager.durationMs.value

                val artworkBitmap = withContext(Dispatchers.IO) {
                    loadAlbumArt(context, currentSong)
                }

                val views = buildRemoteViews(
                    context = context,
                    song = currentSong,
                    isPlaying = isPlaying,
                    positionMs = positionMs,
                    durationMs = durationMs,
                    artwork = artworkBitmap
                )
                appWidgetManager.updateAppWidget(appWidgetId, views)
            }
        }

        private fun buildRemoteViews(
            context: Context,
            song: Song?,
            isPlaying: Boolean,
            positionMs: Long,
            durationMs: Long,
            artwork: Bitmap
        ): RemoteViews {
            val views = RemoteViews(context.packageName, R.layout.widget_music_player)

            // Track title & artist
            if (song != null) {
                views.setTextViewText(R.id.widget_song_title, song.title)
                views.setTextViewText(R.id.widget_song_artist, song.artist)
            } else {
                views.setTextViewText(R.id.widget_song_title, context.getString(R.string.widget_no_song_title))
                views.setTextViewText(R.id.widget_song_artist, context.getString(R.string.widget_tap_to_play))
            }

            // Progress bar
            val maxProgress = 1000
            val currentProgress = if (durationMs > 0) {
                ((positionMs.toFloat() / durationMs.toFloat()) * maxProgress).toInt().coerceIn(0, maxProgress)
            } else {
                0
            }
            views.setProgressBar(R.id.widget_progress, maxProgress, currentProgress, false)

            // Play / Pause button icon & description
            if (isPlaying) {
                views.setImageViewResource(R.id.widget_btn_play_pause, R.drawable.ic_widget_pause)
                views.setContentDescription(R.id.widget_btn_play_pause, context.getString(R.string.widget_action_pause))
            } else {
                views.setImageViewResource(R.id.widget_btn_play_pause, R.drawable.ic_widget_play)
                views.setContentDescription(R.id.widget_btn_play_pause, context.getString(R.string.widget_action_play))
            }

            // Album Artwork
            views.setImageViewBitmap(R.id.widget_album_art, artwork)

            // PendingIntent: Open App when tapping album art or song info container
            val openAppIntent = Intent(context, MainActivity::class.java).apply {
                flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_SINGLE_TOP or Intent.FLAG_ACTIVITY_CLEAR_TOP
            }
            val openAppPendingIntent = PendingIntent.getActivity(
                context,
                0,
                openAppIntent,
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
            )
            views.setOnClickPendingIntent(R.id.widget_album_art, openAppPendingIntent)
            views.setOnClickPendingIntent(R.id.widget_info_container, openAppPendingIntent)

            // PendingIntent: Previous Button
            val prevIntent = Intent(context, MusicWidgetProvider::class.java).apply {
                action = ACTION_PREV
            }
            val prevPendingIntent = PendingIntent.getBroadcast(
                context,
                1,
                prevIntent,
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
            )
            views.setOnClickPendingIntent(R.id.widget_btn_prev, prevPendingIntent)

            // PendingIntent: Play / Pause Button
            val playPauseIntent = Intent(context, MusicWidgetProvider::class.java).apply {
                action = ACTION_PLAY_PAUSE
            }
            val playPausePendingIntent = PendingIntent.getBroadcast(
                context,
                2,
                playPauseIntent,
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
            )
            views.setOnClickPendingIntent(R.id.widget_btn_play_pause, playPausePendingIntent)

            // PendingIntent: Next Button
            val nextIntent = Intent(context, MusicWidgetProvider::class.java).apply {
                action = ACTION_NEXT
            }
            val nextPendingIntent = PendingIntent.getBroadcast(
                context,
                3,
                nextIntent,
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
            )
            views.setOnClickPendingIntent(R.id.widget_btn_next, nextPendingIntent)

            return views
        }

        /**
         * Loads and scales album artwork into a rounded bitmap.
         * Falls back to a beautifully rendered dark vinyl artwork if not found.
         */
        private fun loadAlbumArt(context: Context, song: Song?): Bitmap {
            val targetSizePx = (64 * context.resources.displayMetrics.density).toInt().coerceAtLeast(128)

            if (song != null) {
                // Try reading from albumArtUri or content URI
                val uriString = song.albumArtUri
                if (!uriString.isNullOrBlank()) {
                    try {
                        val uri = Uri.parse(uriString)
                        val rawBitmap: Bitmap? = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                            try {
                                context.contentResolver.loadThumbnail(uri, Size(targetSizePx, targetSizePx), null)
                            } catch (_: Exception) {
                                decodeStreamBitmap(context, uri, targetSizePx)
                            }
                        } else {
                            decodeStreamBitmap(context, uri, targetSizePx)
                        }

                        if (rawBitmap != null) {
                            return createRoundedBitmap(rawBitmap, targetSizePx, 24f)
                        }
                    } catch (e: Exception) {
                        // Fallback gracefully to custom artwork
                    }
                }
            }

            // Fallback: Generate polished vinyl artwork with neon music note
            return generateFallbackArtwork(targetSizePx, song != null)
        }

        private fun decodeStreamBitmap(context: Context, uri: Uri, targetSize: Int): Bitmap? {
            return try {
                context.contentResolver.openInputStream(uri)?.use { stream ->
                    val options = BitmapFactory.Options().apply {
                        inJustDecodeBounds = true
                    }
                    BitmapFactory.decodeStream(stream, null, options)

                    val sampleSize = calculateInSampleSize(options, targetSize, targetSize)
                    context.contentResolver.openInputStream(uri)?.use { secondStream ->
                        val decodeOptions = BitmapFactory.Options().apply {
                            inSampleSize = sampleSize
                            inPreferredConfig = Bitmap.Config.ARGB_8888
                        }
                        BitmapFactory.decodeStream(secondStream, null, decodeOptions)
                    }
                }
            } catch (_: Exception) {
                null
            }
        }

        private fun calculateInSampleSize(options: BitmapFactory.Options, reqWidth: Int, reqHeight: Int): Int {
            val (height: Int, width: Int) = options.run { outHeight to outWidth }
            var inSampleSize = 1
            if (height > reqHeight || width > reqWidth) {
                val halfHeight: Int = height / 2
                val halfWidth: Int = width / 2
                while (halfHeight / inSampleSize >= reqHeight && halfWidth / inSampleSize >= reqWidth) {
                    inSampleSize *= 2
                }
            }
            return inSampleSize
        }

        private fun createRoundedBitmap(src: Bitmap, targetSize: Int, cornerRadiusDp: Float): Bitmap {
            val output = Bitmap.createBitmap(targetSize, targetSize, Bitmap.Config.ARGB_8888)
            val canvas = Canvas(output)
            val paint = Paint(Paint.ANTI_ALIAS_FLAG or Paint.FILTER_BITMAP_FLAG)

            val srcRect = Rect(0, 0, src.width, src.height)
            val dstRect = RectF(0f, 0f, targetSize.toFloat(), targetSize.toFloat())

            val clipPath = Path().apply {
                addRoundRect(dstRect, cornerRadiusDp, cornerRadiusDp, Path.Direction.CW)
            }
            canvas.clipPath(clipPath)
            canvas.drawBitmap(src, srcRect, dstRect, paint)

            return output
        }

        private fun generateFallbackArtwork(targetSize: Int, hasSong: Boolean): Bitmap {
            val bitmap = Bitmap.createBitmap(targetSize, targetSize, Bitmap.Config.ARGB_8888)
            val canvas = Canvas(bitmap)

            val rect = RectF(0f, 0f, targetSize.toFloat(), targetSize.toFloat())
            val cornerRadius = 24f

            // Card background gradient
            val bgPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                shader = LinearGradient(
                    0f, 0f, targetSize.toFloat(), targetSize.toFloat(),
                    if (hasSong) intArrayOf(0xFF1E2838.toInt(), 0xFF121620.toInt(), 0xFF0D1017.toInt())
                    else intArrayOf(0xFF181C26.toInt(), 0xFF10131B.toInt()),
                    null,
                    Shader.TileMode.CLAMP
                )
            }
            canvas.drawRoundRect(rect, cornerRadius, cornerRadius, bgPaint)

            // Neon border ring
            val borderPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                style = Paint.Style.STROKE
                strokeWidth = 2f
                color = if (hasSong) 0x664DD0E1.toInt() else 0x338899AA.toInt()
            }
            canvas.drawRoundRect(rect, cornerRadius, cornerRadius, borderPaint)

            // Concentric vinyl grooves
            val cx = targetSize / 2f
            val cy = targetSize / 2f
            val groovePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                style = Paint.Style.STROKE
                strokeWidth = 1.5f
                color = 0x22FFFFFF.toInt()
            }
            canvas.drawCircle(cx, cy, targetSize * 0.40f, groovePaint)
            canvas.drawCircle(cx, cy, targetSize * 0.30f, groovePaint)

            // Center Disc Label
            val discPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                style = Paint.Style.FILL
                color = if (hasSong) 0xFF4DD0E1.toInt() else 0xFF2A3446.toInt()
            }
            canvas.drawCircle(cx, cy, targetSize * 0.20f, discPaint)

            // Center hole
            val holePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                style = Paint.Style.FILL
                color = 0xFF0C1017.toInt()
            }
            canvas.drawCircle(cx, cy, targetSize * 0.08f, holePaint)

            return bitmap
        }
    }
}

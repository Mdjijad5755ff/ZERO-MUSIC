package com.example

import android.app.Application
import coil.Coil
import coil.ImageLoader
import coil.ImageLoaderFactory
import com.example.data.local.MusicDatabase
import com.example.data.repository.MusicRepository
import com.example.util.AlbumArtDiskCache
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch

class ZeroMusicApp : Application(), ImageLoaderFactory {

    lateinit var repository: MusicRepository
        private set

    private val applicationScope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

    override fun newImageLoader(): ImageLoader {
        return AlbumArtDiskCache.createImageLoader(this)
    }

    override fun onCreate() {
        super.onCreate()
        instance = this

        // Initialize optimized Coil disk-based caching for album art
        Coil.setImageLoader(this)

        val database = MusicDatabase.getInstance(this)
        repository = MusicRepository(this, database.musicDao())

        applicationScope.launch {
            repository.initializeBuiltInSongsIfNeeded()
        }

        // Keep Home Screen widgets in sync with playback and track changes
        applicationScope.launch {
            com.example.service.MusicPlayerManager.currentSong.collect {
                com.example.widget.MusicWidgetProvider.updateAllWidgets(this@ZeroMusicApp)
            }
        }
        applicationScope.launch {
            com.example.service.MusicPlayerManager.isPlaying.collect {
                com.example.widget.MusicWidgetProvider.updateAllWidgets(this@ZeroMusicApp)
            }
        }
    }

    companion object {
        lateinit var instance: ZeroMusicApp
            private set
    }
}

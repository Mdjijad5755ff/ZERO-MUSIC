package com.example.ui.viewmodel

import android.content.Context
import android.net.Uri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.ZeroMusicApp
import com.example.data.repository.MusicRepository
import com.example.model.EqualizerState
import com.example.model.Playlist
import com.example.model.RepeatMode
import com.example.model.Song
import com.example.service.MusicPlaybackService
import com.example.service.MusicPlayerManager
import com.example.ui.theme.ThemeMode
import com.example.ui.theme.ZeroTheme
import com.example.util.AudioMetadataLyricsParser
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

enum class MainTab(val title: String) {
    TRACKS("Tracks"),
    PLAYLISTS("Playlists"),
    FAVORITES("Favorites"),
    EQUALIZER("Equalizer"),
    SETTINGS("Zenith")
}

enum class SortOrder(val label: String) {
    TITLE_ASC("Title (A-Z)"),
    TITLE_DESC("Title (Z-A)"),
    ARTIST("Artist"),
    DURATION("Duration")
}

class MusicViewModel(
    private val repository: MusicRepository = ZeroMusicApp.instance.repository
) : ViewModel() {

    // Main Tab
    private val _currentTab = MutableStateFlow(MainTab.TRACKS)
    val currentTab: StateFlow<MainTab> = _currentTab.asStateFlow()

    // Search & Sort
    private val _searchQuery = MutableStateFlow("")
    val searchQuery: StateFlow<String> = _searchQuery.asStateFlow()

    private val _sortOrder = MutableStateFlow(SortOrder.TITLE_ASC)
    val sortOrder: StateFlow<SortOrder> = _sortOrder.asStateFlow()

    // Database Flows
    val allSongs: StateFlow<List<Song>> = combine(
        repository.allSongs,
        _searchQuery,
        _sortOrder
    ) { songs, query, sort ->
        var list = if (query.isBlank()) {
            songs
        } else {
            songs.filter {
                it.title.contains(query, ignoreCase = true) ||
                it.artist.contains(query, ignoreCase = true) ||
                it.album.contains(query, ignoreCase = true)
            }
        }
        list = when (sort) {
            SortOrder.TITLE_ASC -> list.sortedBy { it.title.lowercase() }
            SortOrder.TITLE_DESC -> list.sortedByDescending { it.title.lowercase() }
            SortOrder.ARTIST -> list.sortedBy { it.artist.lowercase() }
            SortOrder.DURATION -> list.sortedByDescending { it.durationMs }
        }
        list
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val favoriteSongs: StateFlow<List<Song>> = repository.favoriteSongs
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val recentlyPlayed: StateFlow<List<Song>> = repository.recentlyPlayed
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val mostPlayed: StateFlow<List<Song>> = repository.mostPlayed
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val playlists: StateFlow<List<Playlist>> = repository.allPlaylists
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    // Selected Playlist view
    private val _selectedPlaylist = MutableStateFlow<Playlist?>(null)
    val selectedPlaylist: StateFlow<Playlist?> = _selectedPlaylist.asStateFlow()

    private val _playlistSongs = MutableStateFlow<List<Song>>(emptyList())
    val playlistSongs: StateFlow<List<Song>> = _playlistSongs.asStateFlow()

    // UI overlays
    private val _isFullPlayerVisible = MutableStateFlow(false)
    val isFullPlayerVisible: StateFlow<Boolean> = _isFullPlayerVisible.asStateFlow()

    private val _isLyricsSheetVisible = MutableStateFlow(false)
    val isLyricsSheetVisible: StateFlow<Boolean> = _isLyricsSheetVisible.asStateFlow()

    private val _isSleepTimerDialogVisible = MutableStateFlow(false)
    val isSleepTimerDialogVisible: StateFlow<Boolean> = _isSleepTimerDialogVisible.asStateFlow()

    private val _isQueueSheetVisible = MutableStateFlow(false)
    val isQueueSheetVisible: StateFlow<Boolean> = _isQueueSheetVisible.asStateFlow()

    private val _isAddToPlaylistDialogVisible = MutableStateFlow<Song?>(null)
    val isAddToPlaylistDialogVisible: StateFlow<Song?> = _isAddToPlaylistDialogVisible.asStateFlow()

    // Scanning status
    private val _isScanning = MutableStateFlow(false)
    val isScanning: StateFlow<Boolean> = _isScanning.asStateFlow()

    private val _scanMessage = MutableStateFlow<String?>(null)
    val scanMessage: StateFlow<String?> = _scanMessage.asStateFlow()

    // Theme
    private val _selectedTheme = MutableStateFlow(ZeroTheme.ZENITH_DARK)
    val selectedTheme: StateFlow<ZeroTheme> = _selectedTheme.asStateFlow()

    private val _themeMode = MutableStateFlow(ThemeMode.SYSTEM)
    val themeMode: StateFlow<ThemeMode> = _themeMode.asStateFlow()

    private val _dynamicColor = MutableStateFlow(true)
    val dynamicColor: StateFlow<Boolean> = _dynamicColor.asStateFlow()

    // Player State forwarded from MusicPlayerManager
    val currentSong: StateFlow<Song?> = MusicPlayerManager.currentSong
    val isPlaying: StateFlow<Boolean> = MusicPlayerManager.isPlaying
    val currentPositionMs: StateFlow<Long> = MusicPlayerManager.currentPositionMs
    val durationMs: StateFlow<Long> = MusicPlayerManager.durationMs
    val queue: StateFlow<List<Song>> = MusicPlayerManager.queue
    val isShuffle: StateFlow<Boolean> = MusicPlayerManager.isShuffle
    val repeatMode: StateFlow<RepeatMode> = MusicPlayerManager.repeatMode
    val equalizerState: StateFlow<EqualizerState> = MusicPlayerManager.equalizerState
    val isSleepTimerActive: StateFlow<Boolean> = MusicPlayerManager.isSleepTimerActive
    val sleepTimerRemainingSeconds: StateFlow<Int> = MusicPlayerManager.sleepTimerRemainingSeconds

    init {
        // Load persisted theme
        val savedThemeIndex = repository.loadThemeIndex().coerceIn(0, ZeroTheme.values().size - 1)
        _selectedTheme.value = ZeroTheme.values()[savedThemeIndex]
        _themeMode.value = repository.loadThemeMode()
        _dynamicColor.value = repository.loadDynamicColor()

        // Load persisted Equalizer
        val savedEq = repository.loadEqualizerState()
        MusicPlayerManager.setEqualizerState(savedEq)
    }

    fun setTab(tab: MainTab) {
        _currentTab.value = tab
    }

    fun setSearchQuery(query: String) {
        _searchQuery.value = query
    }

    fun setSortOrder(order: SortOrder) {
        _sortOrder.value = order
    }

    fun setTheme(theme: ZeroTheme) {
        _selectedTheme.value = theme
        repository.saveThemeIndex(theme.ordinal)
    }

    fun setThemeMode(mode: ThemeMode) {
        _themeMode.value = mode
        repository.saveThemeMode(mode)
    }

    fun setDynamicColor(enabled: Boolean) {
        _dynamicColor.value = enabled
        repository.saveDynamicColor(enabled)
    }

    fun setFullPlayerVisible(visible: Boolean) {
        _isFullPlayerVisible.value = visible
    }

    fun setLyricsSheetVisible(visible: Boolean) {
        _isLyricsSheetVisible.value = visible
    }

    fun setSleepTimerDialogVisible(visible: Boolean) {
        _isSleepTimerDialogVisible.value = visible
    }

    fun setQueueSheetVisible(visible: Boolean) {
        _isQueueSheetVisible.value = visible
    }

    fun showAddToPlaylistDialog(song: Song?) {
        _isAddToPlaylistDialogVisible.value = song
    }

    // Playback control
    fun playSong(song: Song, customQueue: List<Song> = emptyList()) {
        MusicPlaybackService.startService(ZeroMusicApp.instance)
        val q = if (customQueue.isNotEmpty()) customQueue else allSongs.value
        MusicPlayerManager.playSong(song, q)
        viewModelScope.launch {
            repository.recordSongPlayed(song.id)
        }
    }

    fun playPause() {
        MusicPlaybackService.startService(ZeroMusicApp.instance)
        MusicPlayerManager.playPause()
    }

    fun playNext() {
        MusicPlayerManager.playNext()
    }

    fun playPrevious() {
        MusicPlayerManager.playPrevious()
    }

    fun seekTo(positionMs: Long) {
        MusicPlayerManager.seekTo(positionMs)
    }

    fun toggleShuffle() {
        MusicPlayerManager.toggleShuffle()
    }

    fun toggleRepeat() {
        MusicPlayerManager.toggleRepeat()
    }

    fun toggleFavorite(song: Song) {
        viewModelScope.launch {
            repository.toggleFavorite(song.id, song.isFavorite)
            // If current playing song is this one, update instance
            if (currentSong.value?.id == song.id) {
                // repository flow will auto-update
            }
        }
    }

    // Equalizer controls
    fun toggleEqualizer(enabled: Boolean) {
        val updated = equalizerState.value.copy(isEnabled = enabled)
        MusicPlayerManager.setEqualizerState(updated)
        repository.saveEqualizerState(updated)
    }

    fun applyEqualizerPreset(presetName: String) {
        val bands = EqualizerState.PRESETS[presetName] ?: listOf(0, 0, 0, 0, 0)
        val updated = equalizerState.value.copy(
            bandLevels = bands,
            currentPreset = presetName
        )
        MusicPlayerManager.setEqualizerState(updated)
        repository.saveEqualizerState(updated)
    }

    fun setBandLevel(bandIndex: Int, levelMillibels: Int) {
        val list = equalizerState.value.bandLevels.toMutableList()
        if (bandIndex in list.indices) {
            list[bandIndex] = levelMillibels
            val updated = equalizerState.value.copy(
                bandLevels = list,
                currentPreset = "Custom"
            )
            MusicPlayerManager.setEqualizerState(updated)
            repository.saveEqualizerState(updated)
        }
    }

    fun setBassBoost(level: Int) {
        val updated = equalizerState.value.copy(bassBoost = level)
        MusicPlayerManager.setEqualizerState(updated)
        repository.saveEqualizerState(updated)
    }

    fun setVirtualizer(level: Int) {
        val updated = equalizerState.value.copy(virtualizer = level)
        MusicPlayerManager.setEqualizerState(updated)
        repository.saveEqualizerState(updated)
    }

    // Sleep timer
    fun startSleepTimer(minutes: Int, finishSong: Boolean) {
        MusicPlayerManager.startSleepTimer(minutes, finishSong)
        _isSleepTimerDialogVisible.value = false
    }

    fun extendSleepTimer(minutes: Int = 5) {
        MusicPlayerManager.extendSleepTimer(minutes)
    }

    fun cancelSleepTimer() {
        MusicPlayerManager.cancelSleepTimer()
    }

    // Playlists
    fun selectPlaylist(playlist: Playlist?) {
        _selectedPlaylist.value = playlist
        if (playlist != null) {
            viewModelScope.launch {
                repository.getSongsForPlaylist(playlist.id).collect { songs ->
                    _playlistSongs.value = songs
                }
            }
        } else {
            _playlistSongs.value = emptyList()
        }
    }

    fun createPlaylist(name: String, description: String = "") {
        viewModelScope.launch {
            repository.createPlaylist(name, description)
        }
    }

    fun deletePlaylist(playlistId: Long) {
        viewModelScope.launch {
            repository.deletePlaylist(playlistId)
            if (_selectedPlaylist.value?.id == playlistId) {
                _selectedPlaylist.value = null
            }
        }
    }

    fun addSongToPlaylist(playlistId: Long, songId: Long) {
        viewModelScope.launch {
            repository.addSongToPlaylist(playlistId, songId)
        }
    }

    fun removeSongFromPlaylist(playlistId: Long, songId: Long) {
        viewModelScope.launch {
            repository.removeSongFromPlaylist(playlistId, songId)
        }
    }

    // Queue
    fun addToQueue(song: Song) {
        MusicPlayerManager.addToQueue(song)
    }

    fun playNextInQueue(song: Song) {
        MusicPlayerManager.playNextInQueue(song)
    }

    fun removeFromQueue(songId: Long) {
        MusicPlayerManager.removeFromQueue(songId)
    }

    fun clearQueue() {
        MusicPlayerManager.clearQueue()
    }

    // Storage Scanner
    fun scanDeviceAudio() {
        viewModelScope.launch {
            _isScanning.value = true
            _scanMessage.value = "Scanning local storage for audio..."
            val count = repository.scanStorage()
            _isScanning.value = false
            _scanMessage.value = "Audio scan complete: $count tracks available"
        }
    }

    fun clearScanMessage() {
        _scanMessage.value = null
    }

    fun saveLyrics(songId: Long, lyrics: String) {
        viewModelScope.launch {
            repository.saveLyrics(songId, lyrics)
            MusicPlayerManager.updateSongLyrics(songId, lyrics)
        }
    }

    suspend fun autoDetectLyricsForSong(context: Context, song: Song): Boolean = withContext(Dispatchers.IO) {
        val extracted = AudioMetadataLyricsParser.extractLyrics(context, song)
        if (!extracted.isNullOrBlank()) {
            saveLyrics(song.id, extracted)
            true
        } else {
            false
        }
    }

    suspend fun importLyricsFromUri(context: Context, songId: Long, uri: Uri): Boolean = withContext(Dispatchers.IO) {
        try {
            val content = context.contentResolver.openInputStream(uri)?.use { stream ->
                stream.bufferedReader(Charsets.UTF_8).use { it.readText() }
            }
            if (!content.isNullOrBlank()) {
                saveLyrics(songId, content)
                true
            } else {
                false
            }
        } catch (e: Exception) {
            e.printStackTrace()
            false
        }
    }
}

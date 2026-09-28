package com.example.service

import com.example.model.EqualizerState
import com.example.model.RepeatMode
import com.example.model.Song
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

object MusicPlayerManager {

    private val scope = CoroutineScope(Dispatchers.Main + Job())

    private val _currentSong = MutableStateFlow<Song?>(null)
    val currentSong: StateFlow<Song?> = _currentSong.asStateFlow()

    private val _isPlaying = MutableStateFlow(false)
    val isPlaying: StateFlow<Boolean> = _isPlaying.asStateFlow()

    private val _currentPositionMs = MutableStateFlow(0L)
    val currentPositionMs: StateFlow<Long> = _currentPositionMs.asStateFlow()

    private val _durationMs = MutableStateFlow(0L)
    val durationMs: StateFlow<Long> = _durationMs.asStateFlow()

    private val _queue = MutableStateFlow<List<Song>>(emptyList())
    val queue: StateFlow<List<Song>> = _queue.asStateFlow()

    private val _isShuffle = MutableStateFlow(false)
    val isShuffle: StateFlow<Boolean> = _isShuffle.asStateFlow()

    private val _repeatMode = MutableStateFlow(RepeatMode.ALL)
    val repeatMode: StateFlow<RepeatMode> = _repeatMode.asStateFlow()

    private val _equalizerState = MutableStateFlow(EqualizerState())
    val equalizerState: StateFlow<EqualizerState> = _equalizerState.asStateFlow()

    // Sleep Timer
    private val _sleepTimerRemainingSeconds = MutableStateFlow(0)
    val sleepTimerRemainingSeconds: StateFlow<Int> = _sleepTimerRemainingSeconds.asStateFlow()

    private val _isSleepTimerActive = MutableStateFlow(false)
    val isSleepTimerActive: StateFlow<Boolean> = _isSleepTimerActive.asStateFlow()

    private var sleepTimerJob: Job? = null
    private var finishSongBeforeSleep = false

    // Service callback
    var serviceActionCallback: ((action: ServiceAction) -> Unit)? = null

    sealed class ServiceAction {
        data class Play(val song: Song) : ServiceAction()
        object Resume : ServiceAction()
        object Pause : ServiceAction()
        data class Seek(val positionMs: Long) : ServiceAction()
        object Stop : ServiceAction()
        data class ApplyEqualizer(val state: EqualizerState) : ServiceAction()
        data class SetVolume(val volume: Float) : ServiceAction()
    }

    // Callbacks from Service
    fun onPlaybackStateChanged(playing: Boolean, positionMs: Long, durationMs: Long) {
        _isPlaying.value = playing
        _currentPositionMs.value = positionMs
        if (durationMs > 0) {
            _durationMs.value = durationMs
        }
    }

    fun onSongCompleted() {
        if (_isSleepTimerActive.value && finishSongBeforeSleep) {
            cancelSleepTimer()
            pause()
            return
        }

        when (_repeatMode.value) {
            RepeatMode.ONE -> {
                seekTo(0)
                resume()
            }
            RepeatMode.ALL -> {
                playNext()
            }
            RepeatMode.OFF -> {
                val currentList = _queue.value
                val currentIndex = currentList.indexOfFirst { it.id == _currentSong.value?.id }
                if (currentIndex >= 0 && currentIndex < currentList.lastIndex) {
                    playNext()
                } else {
                    pause()
                    seekTo(0)
                }
            }
        }
    }

    fun playSong(song: Song, newQueue: List<Song> = emptyList()) {
        if (newQueue.isNotEmpty()) {
            _queue.value = newQueue
        } else if (!_queue.value.any { it.id == song.id }) {
            _queue.value = listOf(song) + _queue.value
        }
        _currentSong.value = song
        _durationMs.value = song.durationMs
        _currentPositionMs.value = 0L
        _isPlaying.value = true

        serviceActionCallback?.invoke(ServiceAction.Play(song))
    }

    fun playPause() {
        if (_isPlaying.value) {
            pause()
        } else {
            resume()
        }
    }

    fun resume() {
        val song = _currentSong.value
        if (song != null) {
            _isPlaying.value = true
            serviceActionCallback?.invoke(ServiceAction.Resume)
        } else if (_queue.value.isNotEmpty()) {
            playSong(_queue.value.first())
        }
    }

    fun pause() {
        _isPlaying.value = false
        serviceActionCallback?.invoke(ServiceAction.Pause)
    }

    fun seekTo(positionMs: Long) {
        _currentPositionMs.value = positionMs
        serviceActionCallback?.invoke(ServiceAction.Seek(positionMs))
    }

    fun updateSongLyrics(songId: Long, lyrics: String) {
        val current = _currentSong.value
        if (current?.id == songId) {
            _currentSong.value = current.copy(lyrics = lyrics)
        }
        _queue.value = _queue.value.map {
            if (it.id == songId) it.copy(lyrics = lyrics) else it
        }
    }

    fun playNext() {
        val currentList = _queue.value
        if (currentList.isEmpty()) return

        val currentIndex = currentList.indexOfFirst { it.id == _currentSong.value?.id }
        val nextIndex = if (_isShuffle.value && currentList.size > 1) {
            var rand = (0 until currentList.size).random()
            if (rand == currentIndex) {
                rand = (rand + 1) % currentList.size
            }
            rand
        } else {
            if (currentIndex >= 0) (currentIndex + 1) % currentList.size else 0
        }

        playSong(currentList[nextIndex], currentList)
    }

    fun playPrevious() {
        val currentList = _queue.value
        if (currentList.isEmpty()) return

        // If played more than 3 seconds, restart current track
        if (_currentPositionMs.value > 3000) {
            seekTo(0)
            return
        }

        val currentIndex = currentList.indexOfFirst { it.id == _currentSong.value?.id }
        val prevIndex = if (currentIndex > 0) {
            currentIndex - 1
        } else {
            currentList.lastIndex
        }

        playSong(currentList[prevIndex], currentList)
    }

    fun toggleShuffle() {
        _isShuffle.value = !_isShuffle.value
    }

    fun toggleRepeat() {
        _repeatMode.value = when (_repeatMode.value) {
            RepeatMode.OFF -> RepeatMode.ALL
            RepeatMode.ALL -> RepeatMode.ONE
            RepeatMode.ONE -> RepeatMode.OFF
        }
    }

    fun setEqualizerState(state: EqualizerState) {
        _equalizerState.value = state
        serviceActionCallback?.invoke(ServiceAction.ApplyEqualizer(state))
    }

    // Queue management
    fun addToQueue(song: Song) {
        if (!_queue.value.any { it.id == song.id }) {
            _queue.value = _queue.value + song
        }
    }

    fun playNextInQueue(song: Song) {
        val currentList = _queue.value.toMutableList()
        currentList.removeAll { it.id == song.id }
        val currentIndex = currentList.indexOfFirst { it.id == _currentSong.value?.id }
        if (currentIndex >= 0 && currentIndex < currentList.size) {
            currentList.add(currentIndex + 1, song)
        } else {
            currentList.add(song)
        }
        _queue.value = currentList
    }

    fun removeFromQueue(songId: Long) {
        val currentList = _queue.value.filterNot { it.id == songId }
        _queue.value = currentList
        if (_currentSong.value?.id == songId) {
            if (currentList.isNotEmpty()) {
                playNext()
            } else {
                pause()
                _currentSong.value = null
            }
        }
    }

    fun reorderQueue(fromIndex: Int, toIndex: Int) {
        if (fromIndex in _queue.value.indices && toIndex in _queue.value.indices) {
            val list = _queue.value.toMutableList()
            val item = list.removeAt(fromIndex)
            list.add(toIndex, item)
            _queue.value = list
        }
    }

    fun clearQueue() {
        val current = _currentSong.value
        if (current != null) {
            _queue.value = listOf(current)
        } else {
            _queue.value = emptyList()
        }
    }

    // Sleep Timer
    fun startSleepTimer(minutes: Int, finishCurrentSong: Boolean = false) {
        sleepTimerJob?.cancel()
        finishSongBeforeSleep = finishCurrentSong
        val totalSeconds = minutes * 60
        _sleepTimerRemainingSeconds.value = totalSeconds
        _isSleepTimerActive.value = true

        sleepTimerJob = scope.launch {
            var remaining = totalSeconds
            while (isActive && remaining > 0) {
                delay(1000)
                remaining--
                _sleepTimerRemainingSeconds.value = remaining

                // Fade out volume during last 15 seconds if enabled
                if (remaining in 1..15 && !finishSongBeforeSleep) {
                    val vol = remaining / 15f
                    serviceActionCallback?.invoke(ServiceAction.SetVolume(vol))
                }
            }

            if (!finishSongBeforeSleep) {
                pause()
                serviceActionCallback?.invoke(ServiceAction.SetVolume(1.0f))
                _isSleepTimerActive.value = false
                _sleepTimerRemainingSeconds.value = 0
            }
        }
    }

    fun extendSleepTimer(extraMinutes: Int = 5) {
        val additional = extraMinutes * 60
        val current = _sleepTimerRemainingSeconds.value
        startSleepTimer((current + additional) / 60, finishSongBeforeSleep)
    }

    fun cancelSleepTimer() {
        sleepTimerJob?.cancel()
        sleepTimerJob = null
        _isSleepTimerActive.value = false
        _sleepTimerRemainingSeconds.value = 0
        serviceActionCallback?.invoke(ServiceAction.SetVolume(1.0f))
    }
}

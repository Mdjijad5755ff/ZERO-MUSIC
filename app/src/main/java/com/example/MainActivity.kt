package com.example

import android.Manifest
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Sort
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.GraphicEq
import androidx.compose.material.icons.filled.MusicNote
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.PlaylistPlay
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Shuffle
import androidx.compose.material.icons.filled.Tune
import androidx.compose.material3.Button
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationBarItemDefaults
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.tween
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.model.Song
import com.example.ui.components.AddToPlaylistDialog
import com.example.ui.components.EqualizerView
import com.example.ui.components.FullPlayerSheet
import com.example.ui.components.LyricsView
import com.example.ui.components.MiniPlayer
import com.example.ui.components.PlaylistsView
import com.example.ui.components.QueueSheet
import com.example.ui.components.SettingsView
import com.example.ui.components.SleepTimerDialog
import com.example.ui.components.SongItemCard
import com.example.ui.components.SupportUsDialog
import com.example.ui.theme.ZeroMusicTheme
import com.example.ui.viewmodel.MainTab
import com.example.ui.viewmodel.MusicViewModel
import com.example.ui.viewmodel.SortOrder

class MainActivity : ComponentActivity() {

    private val viewModel: MusicViewModel by viewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        setContent {
            val selectedTheme by viewModel.selectedTheme.collectAsStateWithLifecycle()
            val themeMode by viewModel.themeMode.collectAsStateWithLifecycle()
            val dynamicColor by viewModel.dynamicColor.collectAsStateWithLifecycle()

            ZeroMusicTheme(
                themeMode = themeMode,
                selectedTheme = selectedTheme,
                dynamicColor = dynamicColor
            ) {
                MainAppScreen(viewModel = viewModel)
            }
        }
    }
}

@Composable
fun MainAppScreen(viewModel: MusicViewModel) {
    val context = LocalContext.current

    // State flows
    val currentTab by viewModel.currentTab.collectAsStateWithLifecycle()
    val allSongs by viewModel.allSongs.collectAsStateWithLifecycle()
    val favoriteSongs by viewModel.favoriteSongs.collectAsStateWithLifecycle()
    val recentlyPlayed by viewModel.recentlyPlayed.collectAsStateWithLifecycle()
    val mostPlayed by viewModel.mostPlayed.collectAsStateWithLifecycle()
    val playlists by viewModel.playlists.collectAsStateWithLifecycle()
    val selectedPlaylist by viewModel.selectedPlaylist.collectAsStateWithLifecycle()
    val playlistSongs by viewModel.playlistSongs.collectAsStateWithLifecycle()
    val selectedTheme by viewModel.selectedTheme.collectAsStateWithLifecycle()
    val themeMode by viewModel.themeMode.collectAsStateWithLifecycle()
    val dynamicColor by viewModel.dynamicColor.collectAsStateWithLifecycle()
    val searchQuery by viewModel.searchQuery.collectAsStateWithLifecycle()
    val sortOrder by viewModel.sortOrder.collectAsStateWithLifecycle()

    val currentSong by viewModel.currentSong.collectAsStateWithLifecycle()
    val isPlaying by viewModel.isPlaying.collectAsStateWithLifecycle()
    val currentPositionMs by viewModel.currentPositionMs.collectAsStateWithLifecycle()
    val durationMs by viewModel.durationMs.collectAsStateWithLifecycle()
    val queue by viewModel.queue.collectAsStateWithLifecycle()
    val isShuffle by viewModel.isShuffle.collectAsStateWithLifecycle()
    val repeatMode by viewModel.repeatMode.collectAsStateWithLifecycle()
    val equalizerState by viewModel.equalizerState.collectAsStateWithLifecycle()
    val isSleepTimerActive by viewModel.isSleepTimerActive.collectAsStateWithLifecycle()
    val sleepTimerRemainingSeconds by viewModel.sleepTimerRemainingSeconds.collectAsStateWithLifecycle()

    val isFullPlayerVisible by viewModel.isFullPlayerVisible.collectAsStateWithLifecycle()
    val isLyricsSheetVisible by viewModel.isLyricsSheetVisible.collectAsStateWithLifecycle()
    val isSleepTimerDialogVisible by viewModel.isSleepTimerDialogVisible.collectAsStateWithLifecycle()
    val isQueueSheetVisible by viewModel.isQueueSheetVisible.collectAsStateWithLifecycle()
    val addToPlaylistSong by viewModel.isAddToPlaylistDialogVisible.collectAsStateWithLifecycle()

    val isScanning by viewModel.isScanning.collectAsStateWithLifecycle()
    val scanMessage by viewModel.scanMessage.collectAsStateWithLifecycle()

    var showSearchRow by remember { mutableStateOf(false) }
    var showSortMenu by remember { mutableStateOf(false) }
    var showSupportDialog by remember { mutableStateOf(false) }

    // Permission handling
    val requiredPermission = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
        Manifest.permission.READ_MEDIA_AUDIO
    } else {
        Manifest.permission.READ_EXTERNAL_STORAGE
    }

    var hasAudioPermission by remember {
        mutableStateOf(
            ContextCompat.checkSelfPermission(context, requiredPermission) == PackageManager.PERMISSION_GRANTED
        )
    }

    val permissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission()
    ) { isGranted ->
        hasAudioPermission = isGranted
        if (isGranted) {
            viewModel.scanDeviceAudio()
        }
    }

    val notifPermissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission()
    ) { /* Notification permission callback */ }

    LaunchedEffect(Unit) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            if (ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED) {
                notifPermissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
            }
        }
    }

    Scaffold(
        bottomBar = {
            Column(modifier = Modifier.fillMaxWidth()) {
                // Floating MiniPlayer docked above NavigationBar
                currentSong?.let { song ->
                    MiniPlayer(
                        currentSong = song,
                        isPlaying = isPlaying,
                        currentPositionMs = currentPositionMs,
                        durationMs = durationMs,
                        onPlayPauseClick = { viewModel.playPause() },
                        onPreviousClick = { viewModel.playPrevious() },
                        onNextClick = { viewModel.playNext() },
                        onFavoriteClick = { viewModel.toggleFavorite(song) },
                        onExpandClick = { viewModel.setFullPlayerVisible(true) }
                    )
                }

                // Material 3 Navigation Bar
                NavigationBar(
                    containerColor = MaterialTheme.colorScheme.surface,
                    tonalElevation = 8.dp
                ) {
                    NavigationBarItem(
                        selected = currentTab == MainTab.TRACKS,
                        onClick = { viewModel.setTab(MainTab.TRACKS) },
                        icon = { Icon(Icons.Default.MusicNote, contentDescription = "Tracks") },
                        label = { Text("Tracks") },
                        colors = NavigationBarItemDefaults.colors(
                            selectedIconColor = MaterialTheme.colorScheme.primary,
                            indicatorColor = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.5f)
                        )
                    )
                    NavigationBarItem(
                        selected = currentTab == MainTab.PLAYLISTS,
                        onClick = { viewModel.setTab(MainTab.PLAYLISTS) },
                        icon = { Icon(Icons.Default.PlaylistPlay, contentDescription = "Playlists") },
                        label = { Text("Playlists") },
                        colors = NavigationBarItemDefaults.colors(
                            selectedIconColor = MaterialTheme.colorScheme.primary,
                            indicatorColor = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.5f)
                        )
                    )
                    NavigationBarItem(
                        selected = currentTab == MainTab.FAVORITES,
                        onClick = { viewModel.setTab(MainTab.FAVORITES) },
                        icon = { Icon(Icons.Default.Favorite, contentDescription = "Favorites") },
                        label = { Text("Favorites") },
                        colors = NavigationBarItemDefaults.colors(
                            selectedIconColor = MaterialTheme.colorScheme.primary,
                            indicatorColor = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.5f)
                        )
                    )
                    NavigationBarItem(
                        selected = currentTab == MainTab.EQUALIZER,
                        onClick = { viewModel.setTab(MainTab.EQUALIZER) },
                        icon = { Icon(Icons.Default.GraphicEq, contentDescription = "Equalizer") },
                        label = { Text("Equalizer") },
                        colors = NavigationBarItemDefaults.colors(
                            selectedIconColor = MaterialTheme.colorScheme.primary,
                            indicatorColor = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.5f)
                        )
                    )
                    NavigationBarItem(
                        selected = currentTab == MainTab.SETTINGS,
                        onClick = { viewModel.setTab(MainTab.SETTINGS) },
                        icon = { Icon(Icons.Default.Tune, contentDescription = "Settings") },
                        label = { Text("Zenith") },
                        colors = NavigationBarItemDefaults.colors(
                            selectedIconColor = MaterialTheme.colorScheme.primary,
                            indicatorColor = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.5f)
                        )
                    )
                }
            }
        },
        containerColor = MaterialTheme.colorScheme.background
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .statusBarsPadding()
        ) {
            // Top App Bar / Brand Header
            Surface(
                color = MaterialTheme.colorScheme.background,
                tonalElevation = 2.dp,
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Text(
                                    text = "ZERO MUSIC",
                                    style = MaterialTheme.typography.titleLarge.copy(
                                        fontWeight = FontWeight.Black,
                                        letterSpacing = 1.5.sp
                                    ),
                                    color = MaterialTheme.colorScheme.primary
                                )
                                Spacer(modifier = Modifier.width(6.dp))
                                Surface(
                                    color = MaterialTheme.colorScheme.secondary.copy(alpha = 0.2f),
                                    shape = RoundedCornerShape(6.dp)
                                ) {
                                    Text(
                                        text = "ZENITH",
                                        style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold),
                                        color = MaterialTheme.colorScheme.secondary,
                                        modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                                    )
                                }
                            }
                            Text(
                                text = "Your Music, Your World.",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }

                        Row(verticalAlignment = Alignment.CenterVertically) {
                            IconButton(onClick = { showSearchRow = !showSearchRow }) {
                                Icon(
                                    imageVector = if (showSearchRow) Icons.Default.Close else Icons.Default.Search,
                                    contentDescription = "Search",
                                    tint = MaterialTheme.colorScheme.onSurface
                                )
                            }

                            Box {
                                IconButton(onClick = { showSortMenu = true }) {
                                    Icon(
                                        imageVector = Icons.AutoMirrored.Filled.Sort,
                                        contentDescription = "Sort",
                                        tint = MaterialTheme.colorScheme.onSurface
                                    )
                                }
                                DropdownMenu(
                                    expanded = showSortMenu,
                                    onDismissRequest = { showSortMenu = false }
                                ) {
                                    SortOrder.values().forEach { order ->
                                        DropdownMenuItem(
                                            text = { Text(order.label) },
                                            onClick = {
                                                viewModel.setSortOrder(order)
                                                showSortMenu = false
                                            }
                                        )
                                    }
                                }
                            }

                            IconButton(
                                onClick = { showSupportDialog = true },
                                modifier = Modifier.testTag("support_us_top_button")
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Favorite,
                                    contentDescription = "Support Our Music",
                                    tint = Color(0xFFFF4081)
                                )
                            }

                            IconButton(onClick = { viewModel.scanDeviceAudio() }) {
                                Icon(
                                    imageVector = Icons.Default.Refresh,
                                    contentDescription = "Scan Audio",
                                    tint = MaterialTheme.colorScheme.primary
                                )
                            }
                        }
                    }

                    // Collapsible Search Bar
                    AnimatedVisibility(visible = showSearchRow) {
                        OutlinedTextField(
                            value = searchQuery,
                            onValueChange = { viewModel.setSearchQuery(it) },
                            placeholder = { Text("Search songs, artists, albums...") },
                            singleLine = true,
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(vertical = 8.dp),
                            shape = RoundedCornerShape(12.dp)
                        )
                    }
                }
            }

            // Main Tab Content
            when (currentTab) {
                MainTab.TRACKS -> {
                    TracksTabContent(
                        songs = allSongs,
                        currentSong = currentSong,
                        isPlaying = isPlaying,
                        hasPermission = hasAudioPermission,
                        onRequestPermission = { permissionLauncher.launch(requiredPermission) },
                        onPlaySong = { song -> viewModel.playSong(song, allSongs) },
                        onShuffleAll = {
                            if (allSongs.isNotEmpty()) {
                                val shuffled = allSongs.shuffled()
                                viewModel.playSong(shuffled.first(), shuffled)
                            }
                        },
                        onFavoriteClick = { viewModel.toggleFavorite(it) },
                        onPlayNext = { viewModel.playNextInQueue(it) },
                        onAddToQueue = { viewModel.addToQueue(it) },
                        onAddToPlaylist = { viewModel.showAddToPlaylistDialog(it) }
                    )
                }
                MainTab.PLAYLISTS -> {
                    PlaylistsView(
                        playlists = playlists,
                        favoriteSongs = favoriteSongs,
                        recentlyPlayed = recentlyPlayed,
                        mostPlayed = mostPlayed,
                        selectedPlaylist = selectedPlaylist,
                        selectedPlaylistSongs = playlistSongs,
                        currentSong = currentSong,
                        isPlaying = isPlaying,
                        onSelectPlaylist = { viewModel.selectPlaylist(it) },
                        onCreatePlaylist = { name, desc -> viewModel.createPlaylist(name, desc) },
                        onDeletePlaylist = { viewModel.deletePlaylist(it) },
                        onPlaySong = { song, queueList -> viewModel.playSong(song, queueList) },
                        onFavoriteClick = { viewModel.toggleFavorite(it) },
                        onPlayNext = { viewModel.playNextInQueue(it) },
                        onAddToQueue = { viewModel.addToQueue(it) },
                        onRemoveFromPlaylist = { pId, sId -> viewModel.removeSongFromPlaylist(pId, sId) }
                    )
                }
                MainTab.FAVORITES -> {
                    FavoritesTabContent(
                        favoriteSongs = favoriteSongs,
                        currentSong = currentSong,
                        isPlaying = isPlaying,
                        onPlaySong = { song -> viewModel.playSong(song, favoriteSongs) },
                        onShuffleFavorites = {
                            if (favoriteSongs.isNotEmpty()) {
                                val shuffled = favoriteSongs.shuffled()
                                viewModel.playSong(shuffled.first(), shuffled)
                            }
                        },
                        onFavoriteClick = { viewModel.toggleFavorite(it) },
                        onPlayNext = { viewModel.playNextInQueue(it) },
                        onAddToQueue = { viewModel.addToQueue(it) },
                        onAddToPlaylist = { viewModel.showAddToPlaylistDialog(it) }
                    )
                }
                MainTab.EQUALIZER -> {
                    EqualizerView(
                        state = equalizerState,
                        isPlaying = isPlaying,
                        onToggleEnabled = { viewModel.toggleEqualizer(it) },
                        onSelectPreset = { viewModel.applyEqualizerPreset(it) },
                        onBandChange = { band, level -> viewModel.setBandLevel(band, level) },
                        onBassBoostChange = { viewModel.setBassBoost(it) },
                        onVirtualizerChange = { viewModel.setVirtualizer(it) }
                    )
                }
                MainTab.SETTINGS -> {
                    SettingsView(
                        currentTheme = selectedTheme,
                        onSelectTheme = { viewModel.setTheme(it) },
                        themeMode = themeMode,
                        onSelectThemeMode = { viewModel.setThemeMode(it) },
                        dynamicColor = dynamicColor,
                        onToggleDynamicColor = { viewModel.setDynamicColor(it) },
                        onScanAudio = { viewModel.scanDeviceAudio() },
                        isScanning = isScanning,
                        scanMessage = scanMessage,
                        hasStoragePermission = hasAudioPermission,
                        onRequestPermission = { permissionLauncher.launch(requiredPermission) },
                        currentSong = currentSong,
                        isPlaying = isPlaying
                    )
                }
            }
        }
    }

    // Full Player Modal Screen
    AnimatedVisibility(
        visible = isFullPlayerVisible,
        enter = slideInVertically(initialOffsetY = { it }, animationSpec = tween(350, easing = FastOutSlowInEasing)) + fadeIn(),
        exit = slideOutVertically(targetOffsetY = { it }, animationSpec = tween(300, easing = FastOutSlowInEasing)) + fadeOut()
    ) {
        currentSong?.let { song ->
            FullPlayerSheet(
                song = song,
                isPlaying = isPlaying,
                currentPositionMs = currentPositionMs,
                durationMs = durationMs,
                isShuffle = isShuffle,
                repeatMode = repeatMode,
                isSleepTimerActive = isSleepTimerActive,
                sleepTimerSecondsRemaining = sleepTimerRemainingSeconds,
                onDismiss = { viewModel.setFullPlayerVisible(false) },
                onPlayPauseClick = { viewModel.playPause() },
                onNextClick = { viewModel.playNext() },
                onPreviousClick = { viewModel.playPrevious() },
                onSeek = { viewModel.seekTo(it) },
                onShuffleClick = { viewModel.toggleShuffle() },
                onRepeatClick = { viewModel.toggleRepeat() },
                onFavoriteClick = { viewModel.toggleFavorite(song) },
                onAddToPlaylistClick = { viewModel.showAddToPlaylistDialog(song) },
                onOpenLyricsClick = { viewModel.setLyricsSheetVisible(true) },
                onOpenEqualizerClick = {
                    viewModel.setFullPlayerVisible(false)
                    viewModel.setTab(MainTab.EQUALIZER)
                },
                onOpenQueueClick = { viewModel.setQueueSheetVisible(true) },
                onOpenSleepTimerClick = { viewModel.setSleepTimerDialogVisible(true) }
            )
        }
    }

    // Lyrics Modal
    AnimatedVisibility(
        visible = isLyricsSheetVisible,
        enter = slideInVertically(initialOffsetY = { it }) + fadeIn(),
        exit = slideOutVertically(targetOffsetY = { it }) + fadeOut()
    ) {
        currentSong?.let { song ->
            LyricsView(
                song = song,
                currentPositionMs = currentPositionMs,
                onSeekTo = { viewModel.seekTo(it) },
                onSaveLyrics = { viewModel.saveLyrics(song.id, it) },
                onClose = { viewModel.setLyricsSheetVisible(false) },
                onAutoDetectLyrics = { viewModel.autoDetectLyricsForSong(context, song) },
                onImportLyricsUri = { uri -> viewModel.importLyricsFromUri(context, song.id, uri) }
            )
        }
    }

    // Queue Sheet
    AnimatedVisibility(
        visible = isQueueSheetVisible,
        enter = slideInVertically(initialOffsetY = { it }) + fadeIn(),
        exit = slideOutVertically(targetOffsetY = { it }) + fadeOut()
    ) {
        QueueSheet(
            queue = queue,
            currentSong = currentSong,
            isPlaying = isPlaying,
            onSongClick = { viewModel.playSong(it, queue) },
            onRemoveFromQueue = { viewModel.removeFromQueue(it) },
            onClearQueue = { viewModel.clearQueue() },
            onClose = { viewModel.setQueueSheetVisible(false) }
        )
    }

    // Sleep Timer Dialog
    if (isSleepTimerDialogVisible) {
        SleepTimerDialog(
            isActive = isSleepTimerActive,
            remainingSeconds = sleepTimerRemainingSeconds,
            onStartTimer = { mins, finishSong -> viewModel.startSleepTimer(mins, finishSong) },
            onExtendTimer = { viewModel.extendSleepTimer(it) },
            onCancelTimer = { viewModel.cancelSleepTimer() },
            onDismiss = { viewModel.setSleepTimerDialogVisible(false) }
        )
    }

    // Add to Playlist Dialog
    addToPlaylistSong?.let { song ->
        AddToPlaylistDialog(
            song = song,
            playlists = playlists,
            onSelectPlaylist = { playlistId -> viewModel.addSongToPlaylist(playlistId, song.id) },
            onCreateAndAdd = { name ->
                viewModel.createPlaylist(name)
                // Flow will update
            },
            onDismiss = { viewModel.showAddToPlaylistDialog(null) }
        )
    }

    // Support Our Music Dialog
    if (showSupportDialog) {
        SupportUsDialog(onDismiss = { showSupportDialog = false })
    }
}

@Composable
fun TracksTabContent(
    songs: List<Song>,
    currentSong: Song?,
    isPlaying: Boolean,
    hasPermission: Boolean,
    onRequestPermission: () -> Unit,
    onPlaySong: (Song) -> Unit,
    onShuffleAll: () -> Unit,
    onFavoriteClick: (Song) -> Unit,
    onPlayNext: (Song) -> Unit,
    onAddToQueue: (Song) -> Unit,
    onAddToPlaylist: (Song) -> Unit
) {
    LazyColumn(
        modifier = Modifier.fillMaxSize()
    ) {
        // Permission Banner if permission missing
        if (!hasPermission) {
            item {
                Surface(
                    color = MaterialTheme.colorScheme.secondary.copy(alpha = 0.15f),
                    shape = RoundedCornerShape(12.dp),
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 6.dp)
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(12.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = "Scan Device Audio",
                                style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.Bold),
                                color = MaterialTheme.colorScheme.secondary
                            )
                            Text(
                                text = "Grant storage access to load your personal music library.",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                        Button(onClick = onRequestPermission) {
                            Text("Grant")
                        }
                    }
                }
            }
        }

        // Action Header (Play All, Shuffle, Track Count)
        item {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 8.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "${songs.size} TRACKS AVAILABLE",
                    style = MaterialTheme.typography.labelMedium.copy(
                        fontWeight = FontWeight.Bold,
                        letterSpacing = 1.sp
                    ),
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )

                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Button(
                        onClick = { if (songs.isNotEmpty()) onPlaySong(songs.first()) },
                        enabled = songs.isNotEmpty()
                    ) {
                        Icon(Icons.Default.PlayArrow, contentDescription = null, modifier = Modifier.size(18.dp))
                        Spacer(modifier = Modifier.width(4.dp))
                        Text("Play All")
                    }

                    FilledTonalButton(
                        onClick = onShuffleAll,
                        enabled = songs.isNotEmpty()
                    ) {
                        Icon(Icons.Default.Shuffle, contentDescription = null, modifier = Modifier.size(18.dp))
                        Spacer(modifier = Modifier.width(4.dp))
                        Text("Shuffle")
                    }
                }
            }
        }

        // Track items
        items(songs, key = { it.id }) { song ->
            SongItemCard(
                song = song,
                isCurrentSong = currentSong?.id == song.id,
                isPlaying = isPlaying,
                onSongClick = { onPlaySong(song) },
                onFavoriteClick = { onFavoriteClick(song) },
                onPlayNextClick = { onPlayNext(song) },
                onAddToQueueClick = { onAddToQueue(song) },
                onAddToPlaylistClick = { onAddToPlaylist(song) },
                modifier = Modifier.animateItem()
            )
        }

        item {
            Spacer(modifier = Modifier.height(80.dp))
        }
    }
}

@Composable
fun FavoritesTabContent(
    favoriteSongs: List<Song>,
    currentSong: Song?,
    isPlaying: Boolean,
    onPlaySong: (Song) -> Unit,
    onShuffleFavorites: () -> Unit,
    onFavoriteClick: (Song) -> Unit,
    onPlayNext: (Song) -> Unit,
    onAddToQueue: (Song) -> Unit,
    onAddToPlaylist: (Song) -> Unit
) {
    if (favoriteSongs.isEmpty()) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(32.dp),
            contentAlignment = Alignment.Center
        ) {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Icon(
                    imageVector = Icons.Default.Favorite,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.outline,
                    modifier = Modifier.size(64.dp)
                )
                Spacer(modifier = Modifier.height(16.dp))
                Text(
                    text = "No Favorites Yet",
                    style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                    color = MaterialTheme.colorScheme.onSurface
                )
                Spacer(modifier = Modifier.height(6.dp))
                Text(
                    text = "Tap the heart icon next to any song to add it to your favorite tracks.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    textAlign = androidx.compose.ui.text.style.TextAlign.Center
                )
            }
        }
    } else {
        LazyColumn(modifier = Modifier.fillMaxSize()) {
            item {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 10.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "${favoriteSongs.size} FAVORITED TRACKS",
                        style = MaterialTheme.typography.labelMedium.copy(
                            fontWeight = FontWeight.Bold,
                            letterSpacing = 1.sp
                        ),
                        color = MaterialTheme.colorScheme.primary
                    )

                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        Button(onClick = { onPlaySong(favoriteSongs.first()) }) {
                            Icon(Icons.Default.PlayArrow, contentDescription = null, modifier = Modifier.size(18.dp))
                            Spacer(modifier = Modifier.width(4.dp))
                            Text("Play All")
                        }
                        FilledTonalButton(onClick = onShuffleFavorites) {
                            Icon(Icons.Default.Shuffle, contentDescription = null, modifier = Modifier.size(18.dp))
                            Spacer(modifier = Modifier.width(4.dp))
                            Text("Shuffle")
                        }
                    }
                }
            }

            items(favoriteSongs, key = { it.id }) { song ->
                SongItemCard(
                    song = song,
                    isCurrentSong = currentSong?.id == song.id,
                    isPlaying = isPlaying,
                    onSongClick = { onPlaySong(song) },
                    onFavoriteClick = { onFavoriteClick(song) },
                    onPlayNextClick = { onPlayNext(song) },
                    onAddToQueueClick = { onAddToQueue(song) },
                    onAddToPlaylistClick = { onAddToPlaylist(song) },
                    modifier = Modifier.animateItem()
                )
            }

            item {
                Spacer(modifier = Modifier.height(80.dp))
            }
        }
    }
}

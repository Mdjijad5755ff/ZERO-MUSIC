package com.example.ui.components

import android.content.Context
import android.net.Uri
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.FileOpen
import androidx.compose.material.icons.filled.GraphicEq
import androidx.compose.material.icons.filled.MyLocation
import androidx.compose.material.icons.filled.Subtitles
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ElevatedCard
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.model.LyricLine
import com.example.model.Song
import com.example.util.LyricsParser
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

@Composable
fun LyricsView(
    song: Song,
    currentPositionMs: Long,
    onSeekTo: (Long) -> Unit,
    onSaveLyrics: (String) -> Unit,
    onClose: () -> Unit,
    onAutoDetectLyrics: suspend () -> Boolean = { false },
    onImportLyricsUri: suspend (Uri) -> Boolean = { false },
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()

    val parsedLines = remember(song.lyrics) {
        LyricsParser.parse(song.lyrics ?: "")
    }
    val isSynced = remember(parsedLines) {
        LyricsParser.isSynchronized(parsedLines)
    }

    var isScanning by remember { mutableStateOf(false) }
    var showEditDialog by remember { mutableStateOf(false) }
    var editLyricsText by remember { mutableStateOf(song.lyrics ?: "") }
    var userManualScrolling by remember { mutableStateOf(false) }

    val listState = rememberLazyListState()

    // Pick .lrc or .txt file launcher
    val filePickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.GetContent()
    ) { uri: Uri? ->
        if (uri != null) {
            scope.launch {
                isScanning = true
                val success = onImportLyricsUri(uri)
                isScanning = false
                if (success) {
                    Toast.makeText(context, "Lyrics imported successfully", Toast.LENGTH_SHORT).show()
                } else {
                    Toast.makeText(context, "Could not read lyrics from chosen file", Toast.LENGTH_SHORT).show()
                }
            }
        }
    }

    // Auto-detect lyrics from audio metadata/local files if current song has no lyrics
    LaunchedEffect(song.id) {
        if (song.lyrics.isNullOrBlank()) {
            isScanning = true
            onAutoDetectLyrics()
            isScanning = false
        }
    }

    // Find current active lyric index based on timestamp
    val activeIndex by remember(parsedLines, currentPositionMs, isSynced) {
        derivedStateOf {
            if (parsedLines.isEmpty() || !isSynced) -1
            else {
                val idx = parsedLines.indexOfLast { it.timestampMs <= currentPositionMs }
                if (idx >= 0) idx else 0
            }
        }
    }

    // Detect user manual dragging to temporarily pause auto-scroll
    LaunchedEffect(listState) {
        snapshotFlow { listState.isScrollInProgress }.collect { isScrolling ->
            if (isScrolling) {
                userManualScrolling = true
            }
        }
    }

    // Reset userManualScrolling after 4 seconds of inactivity so auto-scroll resumes
    LaunchedEffect(userManualScrolling) {
        if (userManualScrolling) {
            delay(4000)
            userManualScrolling = false
        }
    }

    // Auto-scroll to active lyric line smoothly
    LaunchedEffect(activeIndex, userManualScrolling) {
        if (!userManualScrolling && activeIndex >= 0 && parsedLines.isNotEmpty()) {
            // Keep active lyric comfortably centered (offset by 2 items)
            val targetIndex = (activeIndex - 2).coerceAtLeast(0)
            listState.animateScrollToItem(
                index = targetIndex,
                scrollOffset = 0
            )
        }
    }

    Surface(
        modifier = modifier
            .fillMaxSize()
            .testTag("lyrics_view"),
        color = MaterialTheme.colorScheme.background
    ) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(
                    Brush.verticalGradient(
                        listOf(
                            MaterialTheme.colorScheme.primary.copy(alpha = 0.15f),
                            MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.35f),
                            MaterialTheme.colorScheme.background
                        )
                    )
                )
        ) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(horizontal = 20.dp, vertical = 12.dp)
            ) {
                // Header Bar
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = 4.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.weight(1f)
                    ) {
                        Box(
                            modifier = Modifier
                                .size(40.dp)
                                .clip(CircleShape)
                                .background(MaterialTheme.colorScheme.primaryContainer),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                imageVector = Icons.Default.Subtitles,
                                contentDescription = "Lyrics",
                                tint = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.size(22.dp)
                            )
                        }
                        Spacer(modifier = Modifier.width(12.dp))
                        Column {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Text(
                                    text = "Lyrics",
                                    style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                                    color = MaterialTheme.colorScheme.onBackground
                                )
                                if (isSynced) {
                                    Spacer(modifier = Modifier.width(6.dp))
                                    Surface(
                                        shape = RoundedCornerShape(6.dp),
                                        color = MaterialTheme.colorScheme.primary.copy(alpha = 0.2f)
                                    ) {
                                        Text(
                                            text = "SYNCED",
                                            style = MaterialTheme.typography.labelSmall.copy(
                                                fontSize = 9.sp,
                                                fontWeight = FontWeight.Bold
                                            ),
                                            color = MaterialTheme.colorScheme.primary,
                                            modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                                        )
                                    }
                                }
                            }
                            Text(
                                text = "${song.title} • ${song.artist}",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                        }
                    }

                    // Action Icons
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        // Scan / Auto-detect Button
                        IconButton(
                            onClick = {
                                scope.launch {
                                    isScanning = true
                                    val found = onAutoDetectLyrics()
                                    isScanning = false
                                    if (found) {
                                        Toast.makeText(context, "Lyrics found and synced!", Toast.LENGTH_SHORT).show()
                                    } else {
                                        Toast.makeText(context, "No embedded or local lyrics found", Toast.LENGTH_SHORT).show()
                                    }
                                }
                            },
                            enabled = !isScanning,
                            modifier = Modifier.testTag("lyrics_scan_btn")
                        ) {
                            if (isScanning) {
                                CircularProgressIndicator(
                                    modifier = Modifier.size(18.dp),
                                    strokeWidth = 2.dp,
                                    color = MaterialTheme.colorScheme.primary
                                )
                            } else {
                                Icon(
                                    imageVector = Icons.Default.AutoAwesome,
                                    contentDescription = "Scan metadata / local files",
                                    tint = MaterialTheme.colorScheme.onBackground,
                                    modifier = Modifier.size(22.dp)
                                )
                            }
                        }

                        // Pick .lrc / .txt File Button
                        IconButton(
                            onClick = { filePickerLauncher.launch("*/*") },
                            modifier = Modifier.testTag("lyrics_import_btn")
                        ) {
                            Icon(
                                imageVector = Icons.Default.FileOpen,
                                contentDescription = "Import .lrc / .txt file",
                                tint = MaterialTheme.colorScheme.onBackground,
                                modifier = Modifier.size(22.dp)
                            )
                        }

                        // Edit / Paste Button
                        IconButton(
                            onClick = {
                                editLyricsText = song.lyrics ?: ""
                                showEditDialog = true
                            },
                            modifier = Modifier.testTag("lyrics_edit_btn")
                        ) {
                            Icon(
                                imageVector = Icons.Default.Edit,
                                contentDescription = "Edit Lyrics",
                                tint = MaterialTheme.colorScheme.onBackground,
                                modifier = Modifier.size(22.dp)
                            )
                        }

                        // Close Button
                        IconButton(
                            onClick = onClose,
                            modifier = Modifier.testTag("lyrics_close_btn")
                        ) {
                            Icon(
                                imageVector = Icons.Default.Close,
                                contentDescription = "Close",
                                tint = MaterialTheme.colorScheme.onBackground,
                                modifier = Modifier.size(24.dp)
                            )
                        }
                    }
                }

                Spacer(modifier = Modifier.height(12.dp))

                // Lyrics Content Area
                if (parsedLines.isEmpty()) {
                    // Empty State: prompt user to scan, import, or paste
                    Column(
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(24.dp),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.Center
                    ) {
                        Box(
                            modifier = Modifier
                                .size(80.dp)
                                .clip(CircleShape)
                                .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.6f)),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                imageVector = Icons.Default.Subtitles,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.outline,
                                modifier = Modifier.size(44.dp)
                            )
                        }

                        Spacer(modifier = Modifier.height(16.dp))

                        Text(
                            text = "No lyrics found for this track",
                            style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.SemiBold),
                            color = MaterialTheme.colorScheme.onSurface
                        )

                        Spacer(modifier = Modifier.height(6.dp))

                        Text(
                            text = "ZERO MUSIC can parse metadata tags (ID3/Vorbis/MP4) or companion .lrc files from your device storage.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            textAlign = TextAlign.Center
                        )

                        Spacer(modifier = Modifier.height(24.dp))

                        // Scan metadata button
                        Button(
                            onClick = {
                                scope.launch {
                                    isScanning = true
                                    val found = onAutoDetectLyrics()
                                    isScanning = false
                                    if (found) {
                                        Toast.makeText(context, "Lyrics parsed and loaded!", Toast.LENGTH_SHORT).show()
                                    } else {
                                        Toast.makeText(context, "No metadata or companion .lrc found", Toast.LENGTH_SHORT).show()
                                    }
                                }
                            },
                            modifier = Modifier
                                .fillMaxWidth(0.85f)
                                .testTag("lyrics_empty_scan_btn"),
                            colors = ButtonDefaults.buttonColors(
                                containerColor = MaterialTheme.colorScheme.primary
                            )
                        ) {
                            Icon(Icons.Default.AutoAwesome, contentDescription = null, modifier = Modifier.size(18.dp))
                            Spacer(modifier = Modifier.width(8.dp))
                            Text("Scan Audio Metadata & Storage")
                        }

                        Spacer(modifier = Modifier.height(10.dp))

                        // Import file button
                        OutlinedButton(
                            onClick = { filePickerLauncher.launch("*/*") },
                            modifier = Modifier
                                .fillMaxWidth(0.85f)
                                .testTag("lyrics_empty_import_btn")
                        ) {
                            Icon(Icons.Default.FileOpen, contentDescription = null, modifier = Modifier.size(18.dp))
                            Spacer(modifier = Modifier.width(8.dp))
                            Text("Import .lrc or .txt File")
                        }

                        Spacer(modifier = Modifier.height(10.dp))

                        // Paste lyrics button
                        OutlinedButton(
                            onClick = {
                                editLyricsText = ""
                                showEditDialog = true
                            },
                            modifier = Modifier
                                .fillMaxWidth(0.85f)
                                .testTag("lyrics_empty_add_btn")
                        ) {
                            Icon(Icons.Default.Edit, contentDescription = null, modifier = Modifier.size(18.dp))
                            Spacer(modifier = Modifier.width(8.dp))
                            Text("Write / Paste Lyrics")
                        }
                    }
                } else {
                    // Scrolling Lyrics List
                    Box(modifier = Modifier.fillMaxSize()) {
                        LazyColumn(
                            state = listState,
                            modifier = Modifier
                                .fillMaxSize()
                                .testTag("lyrics_scroll_list"),
                            contentPadding = PaddingValues(vertical = 48.dp),
                            verticalArrangement = Arrangement.spacedBy(14.dp)
                        ) {
                            itemsIndexed(parsedLines) { index, line ->
                                val isActive = isSynced && index == activeIndex

                                val animatedBgColor by animateColorAsState(
                                    targetValue = if (isActive) MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.5f)
                                    else Color.Transparent,
                                    animationSpec = spring(stiffness = Spring.StiffnessMediumLow),
                                    label = "lyric_bg"
                                )

                                val animatedTextColor by animateColorAsState(
                                    targetValue = if (isActive) MaterialTheme.colorScheme.primary
                                    else if (!isSynced) MaterialTheme.colorScheme.onBackground
                                    else if (index < activeIndex) MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f)
                                    else MaterialTheme.colorScheme.onBackground.copy(alpha = 0.75f),
                                    animationSpec = spring(stiffness = Spring.StiffnessMediumLow),
                                    label = "lyric_color"
                                )

                                Box(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .clip(RoundedCornerShape(16.dp))
                                        .background(animatedBgColor)
                                        .then(
                                            if (isActive) Modifier.border(
                                                1.dp,
                                                MaterialTheme.colorScheme.primary.copy(alpha = 0.35f),
                                                RoundedCornerShape(16.dp)
                                            ) else Modifier
                                        )
                                        .clickable(
                                            interactionSource = remember { MutableInteractionSource() },
                                            indication = null
                                        ) {
                                            if (line.timestampMs >= 0) {
                                                onSeekTo(line.timestampMs)
                                                userManualScrolling = false
                                            }
                                        }
                                        .padding(horizontal = 16.dp, vertical = 12.dp)
                                        .testTag("lyric_line_$index"),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Row(
                                        modifier = Modifier.fillMaxWidth(),
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.Center
                                    ) {
                                        if (isActive) {
                                            Icon(
                                                imageVector = Icons.Default.GraphicEq,
                                                contentDescription = "Currently playing",
                                                tint = MaterialTheme.colorScheme.primary,
                                                modifier = Modifier
                                                    .size(20.dp)
                                                    .padding(end = 6.dp)
                                            )
                                        }

                                        Text(
                                            text = line.text,
                                            style = if (isActive) {
                                                MaterialTheme.typography.titleLarge.copy(
                                                    fontSize = 21.sp,
                                                    fontWeight = FontWeight.Bold,
                                                    lineHeight = 28.sp
                                                )
                                            } else {
                                                MaterialTheme.typography.bodyLarge.copy(
                                                    fontSize = 17.sp,
                                                    fontWeight = FontWeight.Normal,
                                                    lineHeight = 24.sp
                                                )
                                            },
                                            color = animatedTextColor,
                                            textAlign = TextAlign.Center,
                                            modifier = Modifier.weight(1f)
                                        )

                                        if (isSynced && line.timestampMs >= 0) {
                                            Text(
                                                text = LyricsParser.formatShortTime(line.timestampMs),
                                                style = MaterialTheme.typography.labelSmall.copy(fontSize = 11.sp),
                                                color = if (isActive) MaterialTheme.colorScheme.primary
                                                else MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.4f),
                                                modifier = Modifier.padding(start = 8.dp)
                                            )
                                        }
                                    }
                                }
                            }
                        }

                        // Floating "Sync with song" chip shown when user manually scrolls away
                        androidx.compose.animation.AnimatedVisibility(
                            visible = userManualScrolling && isSynced && activeIndex >= 0,
                            enter = slideInVertically(initialOffsetY = { it }) + fadeIn(),
                            exit = slideOutVertically(targetOffsetY = { it }) + fadeOut(),
                            modifier = Modifier
                                .align(Alignment.BottomCenter)
                                .padding(bottom = 16.dp)
                        ) {
                            Surface(
                                shape = RoundedCornerShape(24.dp),
                                color = MaterialTheme.colorScheme.primary,
                                shadowElevation = 8.dp,
                                modifier = Modifier
                                    .clickable {
                                        userManualScrolling = false
                                        scope.launch {
                                            val target = (activeIndex - 2).coerceAtLeast(0)
                                            listState.animateScrollToItem(target)
                                        }
                                    }
                                    .testTag("lyrics_resync_btn")
                            ) {
                                Row(
                                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 10.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.MyLocation,
                                        contentDescription = null,
                                        tint = MaterialTheme.colorScheme.onPrimary,
                                        modifier = Modifier.size(18.dp)
                                    )
                                    Spacer(modifier = Modifier.width(8.dp))
                                    Text(
                                        text = "Sync to playback",
                                        style = MaterialTheme.typography.labelLarge.copy(fontWeight = FontWeight.Bold),
                                        color = MaterialTheme.colorScheme.onPrimary
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }
    }

    // Edit Lyrics Dialog
    if (showEditDialog) {
        AlertDialog(
            onDismissRequest = { showEditDialog = false },
            title = {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Default.Edit, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                    Spacer(modifier = Modifier.width(8.dp))
                    Text("Edit Track Lyrics")
                }
            },
            text = {
                Column {
                    Text(
                        text = "Paste synchronized LRC format (e.g. [00:15.30] Lyric line) or plain text lyrics. ZERO MUSIC will parse and auto-scroll in real-time.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Spacer(modifier = Modifier.height(12.dp))
                    OutlinedTextField(
                        value = editLyricsText,
                        onValueChange = { editLyricsText = it },
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(260.dp)
                            .testTag("lyrics_edit_input"),
                        placeholder = {
                            Text(
                                "[00:00.00] Song Title\n[00:04.50] First verse...\n[00:10.00] Chorus starts..."
                            )
                        }
                    )
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        onSaveLyrics(editLyricsText)
                        showEditDialog = false
                    },
                    modifier = Modifier.testTag("lyrics_save_btn")
                ) {
                    Text("Save Lyrics")
                }
            },
            dismissButton = {
                OutlinedButton(
                    onClick = { showEditDialog = false },
                    modifier = Modifier.testTag("lyrics_cancel_btn")
                ) {
                    Text("Cancel")
                }
            }
        )
    }
}

/**
 * Compact, inline scrolling lyrics view suitable for embedding directly inside the
 * full player sheet in place of the vinyl turntable!
 */
@Composable
fun InlineLyricsViewer(
    song: Song,
    currentPositionMs: Long,
    onSeekTo: (Long) -> Unit,
    onExpandFullLyrics: () -> Unit,
    modifier: Modifier = Modifier
) {
    val parsedLines = remember(song.lyrics) {
        LyricsParser.parse(song.lyrics ?: "")
    }
    val isSynced = remember(parsedLines) {
        LyricsParser.isSynchronized(parsedLines)
    }

    val listState = rememberLazyListState()

    val activeIndex by remember(parsedLines, currentPositionMs, isSynced) {
        derivedStateOf {
            if (parsedLines.isEmpty() || !isSynced) -1
            else {
                val idx = parsedLines.indexOfLast { it.timestampMs <= currentPositionMs }
                if (idx >= 0) idx else 0
            }
        }
    }

    // Auto-scroll inline lyrics
    LaunchedEffect(activeIndex) {
        if (activeIndex >= 0 && parsedLines.isNotEmpty()) {
            val target = (activeIndex - 1).coerceAtLeast(0)
            listState.animateScrollToItem(target)
        }
    }

    Surface(
        modifier = modifier
            .clip(RoundedCornerShape(24.dp))
            .border(
                1.dp,
                MaterialTheme.colorScheme.outline.copy(alpha = 0.2f),
                RoundedCornerShape(24.dp)
            )
            .testTag("inline_lyrics_viewer"),
        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f)
    ) {
        if (parsedLines.isEmpty()) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .clickable { onExpandFullLyrics() }
                    .padding(20.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center
            ) {
                Icon(
                    imageVector = Icons.Default.Subtitles,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(36.dp)
                )
                Spacer(modifier = Modifier.height(10.dp))
                Text(
                    text = "No lyrics loaded",
                    style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                    color = MaterialTheme.colorScheme.onSurface
                )
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = "Tap to parse metadata or add LRC file",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    textAlign = TextAlign.Center
                )
            }
        } else {
            Box(modifier = Modifier.fillMaxSize()) {
                LazyColumn(
                    state = listState,
                    modifier = Modifier.fillMaxSize(),
                    contentPadding = PaddingValues(vertical = 32.dp, horizontal = 16.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    itemsIndexed(parsedLines) { index, line ->
                        val isActive = isSynced && index == activeIndex

                        Text(
                            text = line.text,
                            style = if (isActive) {
                                MaterialTheme.typography.titleMedium.copy(
                                    fontSize = 18.sp,
                                    fontWeight = FontWeight.Bold,
                                    lineHeight = 24.sp
                                )
                            } else {
                                MaterialTheme.typography.bodyMedium.copy(
                                    fontSize = 14.sp,
                                    lineHeight = 20.sp
                                )
                            },
                            color = if (isActive) MaterialTheme.colorScheme.primary
                            else MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f),
                            textAlign = TextAlign.Center,
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(12.dp))
                                .background(
                                    if (isActive) MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.4f)
                                    else Color.Transparent
                                )
                                .clickable {
                                    if (line.timestampMs >= 0) {
                                        onSeekTo(line.timestampMs)
                                    }
                                }
                                .padding(vertical = 8.dp, horizontal = 12.dp)
                        )
                    }
                }
            }
        }
    }
}

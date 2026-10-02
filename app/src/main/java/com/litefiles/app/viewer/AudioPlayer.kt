package com.litefiles.app.viewer

import android.media.AudioAttributes
import android.media.AudioFocusRequest
import android.media.AudioManager
import android.media.MediaPlayer
import android.media.ThumbnailUtils
import android.util.Size
import androidx.compose.foundation.Image
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
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Forward10
import androidx.compose.material.icons.filled.MusicNote
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Replay10
import androidx.compose.material.icons.filled.SkipNext
import androidx.compose.material.icons.filled.SkipPrevious
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilledIconButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Slider
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LifecycleEventEffect
import com.litefiles.app.R
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext
import java.io.File

/**
 * Platform MediaPlayer: MP3, WAV, FLAC, OGG, AAC/M4A, Opus, AMR, MIDI. Plays the folder (or the list you opened it from)
 * track after track. Playback pauses when the screen is left: there is deliberately no background service.
 */
@Composable
fun AudioPlayer(path: String, onBack: () -> Unit) {
    val playlist = rememberPlaylist(path, ViewerType.AUDIO)
    if (playlist == null) {
        Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) { CircularProgressIndicator() }
    } else {
        AudioScreen(playlist, playlist.indexOf(path).coerceAtLeast(0), onBack)
    }
}

@Composable
private fun AudioScreen(playlist: List<String>, startIndex: Int, onBack: () -> Unit) {
    val ctx = LocalContext.current
    var index by remember { mutableIntStateOf(startIndex) }
    val path = playlist[index]

    val player = remember { MediaPlayer() }
    var prepared by remember { mutableStateOf(false) }
    var playing by remember { mutableStateOf(false) }
    var failed by remember { mutableStateOf(false) }
    var duration by remember { mutableIntStateOf(0) }
    var position by remember { mutableIntStateOf(0) }
    var seeking by remember { mutableStateOf(false) }

    val attrs = remember {
        AudioAttributes.Builder()
            .setContentType(AudioAttributes.CONTENT_TYPE_MUSIC)
            .setUsage(AudioAttributes.USAGE_MEDIA)
            .build()
    }
    val audioManager = remember { ctx.getSystemService(AudioManager::class.java) }
    val focusRequest = remember {
        AudioFocusRequest.Builder(AudioManager.AUDIOFOCUS_GAIN)
            .setAudioAttributes(attrs)
            .setOnAudioFocusChangeListener { change ->
                // another app took the audio: pause instead of talking over it
                if (change == AudioManager.AUDIOFOCUS_LOSS || change == AudioManager.AUDIOFOCUS_LOSS_TRANSIENT) {
                    try {
                        if (player.isPlaying) player.pause()
                    } catch (e: IllegalStateException) {
                        // player already released
                    }
                    playing = false
                }
            }
            .build()
    }

    DisposableEffect(Unit) {
        onDispose {
            audioManager?.abandonAudioFocusRequest(focusRequest)
            player.release()
        }
    }
    LifecycleEventEffect(Lifecycle.Event.ON_STOP) {
        try {
            if (prepared && player.isPlaying) player.pause()
        } catch (e: IllegalStateException) {
            // ignore
        }
        playing = false
    }

    LaunchedEffect(path) {
        prepared = false
        failed = false
        position = 0
        duration = 0
        try {
            player.reset()
            player.setAudioAttributes(attrs)
            player.setDataSource(path)
            player.setOnPreparedListener { mp ->
                duration = mp.duration
                prepared = true
                if (audioManager?.requestAudioFocus(focusRequest) == AudioManager.AUDIOFOCUS_REQUEST_GRANTED) {
                    mp.start()
                    playing = true
                }
            }
            player.setOnCompletionListener {
                playing = false
                if (index < playlist.lastIndex) index++ // next track
            }
            player.setOnErrorListener { _, _, _ ->
                failed = true
                playing = false
                true
            }
            player.prepareAsync()
        } catch (e: Exception) {
            failed = true
        }
    }

    LaunchedEffect(playing, prepared) {
        while (playing && prepared) {
            if (!seeking) position = player.currentPosition
            delay(250)
        }
    }

    val art by produceState<ImageBitmap?>(null, path) {
        value = withContext(Dispatchers.IO) {
            try {
                ThumbnailUtils.createAudioThumbnail(File(path), Size(640, 640), null).asImageBitmap()
            } catch (e: Exception) {
                null
            }
        }
    }

    fun seekBy(deltaMs: Int) {
        if (!prepared) return
        val target = (player.currentPosition + deltaMs).coerceIn(0, duration)
        player.seekTo(target)
        position = target
    }

    Scaffold(
        topBar = {
            ViewerTopBar(
                title = File(path).name,
                subtitle = if (playlist.size > 1) "${index + 1} / ${playlist.size}" else null,
                onBack = onBack,
                actions = { ViewerMenu(path) },
            )
        },
    ) { pad ->
        if (failed) {
            ErrorPane(stringResource(R.string.error_audio), path, Modifier.padding(pad))
        } else {
            Column(
                Modifier.padding(pad).fillMaxSize().padding(horizontal = 24.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center,
            ) {
                Box(
                    Modifier
                        .size(280.dp)
                        .clip(RoundedCornerShape(24.dp))
                        .background(MaterialTheme.colorScheme.surfaceContainer),
                    contentAlignment = Alignment.Center,
                ) {
                    val bmp = art
                    if (bmp != null) {
                        Image(bmp, null, Modifier.fillMaxSize(), contentScale = ContentScale.Crop)
                    } else {
                        Icon(
                            Icons.Filled.MusicNote,
                            null,
                            Modifier.size(96.dp),
                            tint = MaterialTheme.colorScheme.primary,
                        )
                    }
                }
                Spacer(Modifier.height(24.dp))
                Text(
                    File(path).nameWithoutExtension,
                    style = MaterialTheme.typography.titleLarge,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                    textAlign = TextAlign.Center,
                )
                Text(
                    File(path).parentFile?.name.orEmpty(),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                )
                Spacer(Modifier.height(16.dp))
                Slider(
                    value = position.toFloat(),
                    onValueChange = { seeking = true; position = it.toInt() },
                    onValueChangeFinished = {
                        player.seekTo(position)
                        seeking = false
                    },
                    valueRange = 0f..maxOf(duration, 1).toFloat(),
                    enabled = prepared,
                    modifier = Modifier.fillMaxWidth(),
                )
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                    Text(formatTime(position), style = MaterialTheme.typography.labelMedium)
                    Text(formatTime(duration), style = MaterialTheme.typography.labelMedium)
                }
                Spacer(Modifier.height(8.dp))
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                    IconButton(onClick = { if (index > 0) index-- }, enabled = index > 0) {
                        Icon(Icons.Filled.SkipPrevious, stringResource(R.string.previous))
                    }
                    IconButton(onClick = { seekBy(-10_000) }) {
                        Icon(Icons.Filled.Replay10, stringResource(R.string.back_10s))
                    }
                    FilledIconButton(
                        onClick = {
                            if (playing) {
                                player.pause()
                                playing = false
                            } else if (prepared) {
                                if (audioManager?.requestAudioFocus(focusRequest) == AudioManager.AUDIOFOCUS_REQUEST_GRANTED) {
                                    if (duration > 0 && position >= duration - 500) player.seekTo(0)
                                    player.start()
                                    playing = true
                                }
                            }
                        },
                        modifier = Modifier.size(64.dp),
                    ) {
                        Icon(
                            if (playing) Icons.Filled.Pause else Icons.Filled.PlayArrow,
                            stringResource(if (playing) R.string.pause else R.string.play),
                            Modifier.size(36.dp),
                        )
                    }
                    IconButton(onClick = { seekBy(10_000) }) {
                        Icon(Icons.Filled.Forward10, stringResource(R.string.forward_10s))
                    }
                    IconButton(onClick = { if (index < playlist.lastIndex) index++ }, enabled = index < playlist.lastIndex) {
                        Icon(Icons.Filled.SkipNext, stringResource(R.string.next))
                    }
                }
            }
        }
    }
}

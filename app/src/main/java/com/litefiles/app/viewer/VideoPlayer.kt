package com.litefiles.app.viewer

import android.widget.VideoView
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Forward10
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Replay10
import androidx.compose.material.icons.filled.SkipNext
import androidx.compose.material.icons.filled.SkipPrevious
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Slider
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LifecycleEventEffect
import com.litefiles.app.R
import kotlinx.coroutines.delay
import java.io.File

/**
 * Uses the platform VideoView (zero APK size). What plays depends on the device's codecs:
 * MP4/H.264, HEVC, 3GP, WebM and usually MKV work; AVI and some MOV/MKV codecs may not.
 * When playback fails the screen offers "Open with another app".
 */
@Composable
fun VideoPlayer(path: String, onBack: () -> Unit) {
    BlackSystemBars()
    val playlist = rememberPlaylist(path, ViewerType.VIDEO)
    if (playlist == null) {
        Box(Modifier.fillMaxSize().background(Color.Black), contentAlignment = Alignment.Center) {
            CircularProgressIndicator(color = Color.White)
        }
    } else {
        VideoScreen(playlist, playlist.indexOf(path).coerceAtLeast(0), onBack)
    }
}

@Composable
private fun VideoScreen(playlist: List<String>, startIndex: Int, onBack: () -> Unit) {
    val ctx = LocalContext.current
    val view = LocalView.current
    val activity = ctx.findActivity()

    var index by remember { mutableIntStateOf(startIndex) }
    val path = playlist[index]
    val videoView = remember { VideoView(ctx) }
    var prepared by remember { mutableStateOf(false) }
    var playing by remember { mutableStateOf(false) }
    var failed by remember { mutableStateOf(false) }
    var duration by remember { mutableIntStateOf(0) }
    var position by remember { mutableIntStateOf(0) }
    var seeking by remember { mutableStateOf(false) }
    var controls by remember { mutableStateOf(true) }

    DisposableEffect(Unit) { onDispose { videoView.stopPlayback() } }
    LifecycleEventEffect(Lifecycle.Event.ON_STOP) {
        if (prepared && videoView.isPlaying) videoView.pause()
        playing = false
    }

    LaunchedEffect(path) {
        prepared = false
        failed = false
        position = 0
        duration = 0
        videoView.setOnPreparedListener { mp ->
            duration = mp.duration
            prepared = true
            videoView.start()
            playing = true
        }
        videoView.setOnCompletionListener {
            playing = false
            if (index < playlist.lastIndex) index++ // continue with the next video
        }
        videoView.setOnErrorListener { _, _, _ ->
            failed = true
            playing = false
            true
        }
        videoView.setVideoPath(path)
    }

    LaunchedEffect(playing, prepared) {
        while (playing && prepared) {
            if (!seeking) position = videoView.currentPosition
            delay(250)
        }
    }

    // hide the controls (and system bars) a few seconds after playback starts
    LaunchedEffect(controls, playing) {
        if (controls && playing) {
            delay(3000)
            controls = false
        }
    }
    LaunchedEffect(controls) {
        activity?.window?.let { w ->
            val c = WindowCompat.getInsetsController(w, view)
            c.systemBarsBehavior = WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
            if (controls) c.show(WindowInsetsCompat.Type.systemBars()) else c.hide(WindowInsetsCompat.Type.systemBars())
        }
    }
    DisposableEffect(activity) {
        onDispose {
            activity?.window?.let { WindowCompat.getInsetsController(it, view).show(WindowInsetsCompat.Type.systemBars()) }
        }
    }

    fun seekBy(deltaMs: Int) {
        if (!prepared) return
        val target = (videoView.currentPosition + deltaMs).coerceIn(0, duration)
        videoView.seekTo(target)
        position = target
    }

    Box(Modifier.fillMaxSize().background(Color.Black)) {
        AndroidView(factory = { videoView }, modifier = Modifier.fillMaxSize())

        // tap anywhere to show/hide the controls
        Box(Modifier.fillMaxSize().pointerInput(Unit) { detectTapGestures { controls = !controls } })

        if (failed) {
            ErrorPane(stringResource(R.string.error_video), path, color = Color.White)
        } else if (!prepared) {
            CircularProgressIndicator(Modifier.align(Alignment.Center), color = Color.White)
        }

        AnimatedVisibility(
            visible = controls,
            modifier = Modifier.align(Alignment.TopCenter),
            enter = fadeIn(),
            exit = fadeOut(),
        ) {
            ViewerTopBar(
                title = File(path).name,
                subtitle = if (playlist.size > 1) "${index + 1} / ${playlist.size}" else null,
                onBack = onBack,
                overlay = true,
                actions = { ViewerMenu(path) },
            )
        }

        AnimatedVisibility(
            visible = controls && !failed,
            modifier = Modifier.align(Alignment.BottomCenter),
            enter = fadeIn(),
            exit = fadeOut(),
        ) {
            Column(
                Modifier
                    .fillMaxWidth()
                    .background(Color.Black.copy(alpha = 0.6f))
                    .navigationBarsPadding()
                    .padding(horizontal = 12.dp, vertical = 8.dp),
            ) {
                Slider(
                    value = position.toFloat(),
                    onValueChange = { seeking = true; position = it.toInt() },
                    onValueChangeFinished = {
                        videoView.seekTo(position)
                        seeking = false
                    },
                    valueRange = 0f..maxOf(duration, 1).toFloat(),
                    enabled = prepared,
                )
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text("${formatTime(position)} / ${formatTime(duration)}", color = Color.White)
                    Spacer(Modifier.weight(1f))
                    if (playlist.size > 1) {
                        IconButton(onClick = { if (index > 0) index-- }) {
                            Icon(Icons.Filled.SkipPrevious, stringResource(R.string.previous), tint = Color.White)
                        }
                    }
                    IconButton(onClick = { seekBy(-10_000) }) {
                        Icon(Icons.Filled.Replay10, stringResource(R.string.back_10s), tint = Color.White)
                    }
                    IconButton(
                        onClick = {
                            if (playing) {
                                videoView.pause()
                                playing = false
                            } else if (prepared) {
                                if (duration > 0 && position >= duration - 500) videoView.seekTo(0)
                                videoView.start()
                                playing = true
                            }
                        },
                    ) {
                        Icon(
                            if (playing) Icons.Filled.Pause else Icons.Filled.PlayArrow,
                            stringResource(if (playing) R.string.pause else R.string.play),
                            tint = Color.White,
                            modifier = Modifier.size(36.dp),
                        )
                    }
                    IconButton(onClick = { seekBy(10_000) }) {
                        Icon(Icons.Filled.Forward10, stringResource(R.string.forward_10s), tint = Color.White)
                    }
                    if (playlist.size > 1) {
                        IconButton(onClick = { if (index < playlist.lastIndex) index++ }) {
                            Icon(Icons.Filled.SkipNext, stringResource(R.string.next), tint = Color.White)
                        }
                    }
                }
            }
        }
    }
}

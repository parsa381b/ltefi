package com.litefiles.app.viewer

import android.graphics.ImageDecoder
import android.graphics.drawable.AnimatedImageDrawable
import android.graphics.drawable.BitmapDrawable
import android.widget.ImageView
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.calculatePan
import androidx.compose.foundation.gestures.calculateZoom
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.input.pointer.positionChanged
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.viewinterop.AndroidView
import com.litefiles.app.R
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import kotlin.math.max
import kotlin.math.roundToInt

private const val MAX_EDGE = 2560 // decode at most ~2.5k px on the long edge: sharp when zoomed, safe for memory

private sealed interface ImageResult {
    class Still(val bitmap: ImageBitmap) : ImageResult
    class Anim(val drawable: AnimatedImageDrawable) : ImageResult
    data object Failed : ImageResult
}

/** ImageDecoder handles JPEG, PNG, GIF, WebP (incl. animated), BMP and, where the device supports it, HEIF/AVIF. */
private fun decodeImage(path: String): ImageResult = try {
    val source = ImageDecoder.createSource(File(path))
    val drawable = ImageDecoder.decodeDrawable(source) { decoder, info, _ ->
        val longEdge = max(info.size.width, info.size.height)
        if (longEdge > MAX_EDGE) {
            val s = MAX_EDGE.toFloat() / longEdge
            decoder.setTargetSize(
                (info.size.width * s).roundToInt().coerceAtLeast(1),
                (info.size.height * s).roundToInt().coerceAtLeast(1),
            )
        }
    }
    when (drawable) {
        is AnimatedImageDrawable -> ImageResult.Anim(drawable)
        is BitmapDrawable -> ImageResult.Still(drawable.bitmap.asImageBitmap())
        else -> ImageResult.Failed
    }
} catch (e: Exception) {
    ImageResult.Failed
}

@Composable
fun ImageViewer(path: String, onBack: () -> Unit) {
    BlackSystemBars()
    val playlist = rememberPlaylist(path, ViewerType.IMAGE)
    if (playlist == null) {
        Box(Modifier.fillMaxSize().background(Color.Black), contentAlignment = Alignment.Center) {
            CircularProgressIndicator(color = Color.White)
        }
    } else {
        ImagePager(playlist, playlist.indexOf(path).coerceAtLeast(0), onBack)
    }
}

@Composable
private fun ImagePager(paths: List<String>, start: Int, onBack: () -> Unit) {
    val pager = rememberPagerState(initialPage = start) { paths.size }
    var bars by remember { mutableStateOf(true) }
    val current = paths[pager.currentPage.coerceIn(0, paths.lastIndex)]

    Box(Modifier.fillMaxSize().background(Color.Black)) {
        HorizontalPager(pager, Modifier.fillMaxSize(), key = { paths[it] }) { page ->
            ImagePage(paths[page]) { bars = !bars }
        }
        AnimatedVisibility(
            visible = bars,
            modifier = Modifier.align(Alignment.TopCenter),
            enter = fadeIn(),
            exit = fadeOut(),
        ) {
            ViewerTopBar(
                title = File(current).name,
                subtitle = if (paths.size > 1) "${pager.currentPage + 1} / ${paths.size}" else null,
                onBack = onBack,
                overlay = true,
                actions = { ViewerMenu(current) },
            )
        }
    }
}

@Composable
private fun ImagePage(path: String, onTap: () -> Unit) {
    val result by produceState<ImageResult?>(null, path) {
        value = withContext(Dispatchers.IO) { decodeImage(path) }
    }
    ZoomBox(onTap) {
        when (val r = result) {
            null -> CircularProgressIndicator(color = Color.White)
            is ImageResult.Still -> Image(r.bitmap, null, Modifier.fillMaxSize(), contentScale = ContentScale.Fit)
            is ImageResult.Anim -> {
                DisposableEffect(r.drawable) {
                    r.drawable.start()
                    onDispose { r.drawable.stop() }
                }
                AndroidView(
                    factory = { c ->
                        ImageView(c).apply {
                            scaleType = ImageView.ScaleType.FIT_CENTER
                            setImageDrawable(r.drawable)
                        }
                    },
                    modifier = Modifier.fillMaxSize(),
                )
            }
            ImageResult.Failed -> ErrorPane(stringResource(R.string.error_image), path, color = Color.White)
        }
    }
}

/**
 * Pinch to zoom (1x-6x), drag to pan, double-tap to toggle zoom, tap for [onTap].
 * Single-finger drags are only consumed while zoomed in, so the pager can still swipe at 1x.
 */
@Composable
private fun ZoomBox(onTap: () -> Unit, content: @Composable () -> Unit) {
    var scale by remember { mutableFloatStateOf(1f) }
    var offset by remember { mutableStateOf(Offset.Zero) }
    var size by remember { mutableStateOf(IntSize.Zero) }

    Box(
        Modifier
            .fillMaxSize()
            .clipToBounds()
            .onSizeChanged { size = it }
            .pointerInput(Unit) {
                detectTapGestures(
                    onTap = { onTap() },
                    onDoubleTap = {
                        if (scale > 1f) {
                            scale = 1f
                            offset = Offset.Zero
                        } else {
                            scale = 2.5f
                        }
                    },
                )
            }
            .pointerInput(Unit) {
                awaitEachGesture {
                    awaitFirstDown(requireUnconsumed = false)
                    do {
                        val event = awaitPointerEvent()
                        if (event.changes.size > 1 || scale > 1f) {
                            val zoom = event.calculateZoom()
                            val pan = event.calculatePan()
                            val newScale = (scale * zoom).coerceIn(1f, 6f)
                            val maxX = size.width * (newScale - 1f) / 2f
                            val maxY = size.height * (newScale - 1f) / 2f
                            offset = if (newScale == 1f) {
                                Offset.Zero
                            } else {
                                Offset(
                                    (offset.x + pan.x).coerceIn(-maxX, maxX),
                                    (offset.y + pan.y).coerceIn(-maxY, maxY),
                                )
                            }
                            scale = newScale
                            event.changes.forEach { if (it.positionChanged()) it.consume() }
                        }
                    } while (event.changes.any { it.pressed })
                }
            }
            .graphicsLayer {
                scaleX = scale
                scaleY = scale
                translationX = offset.x
                translationY = offset.y
            },
        contentAlignment = Alignment.Center,
    ) { content() }
}

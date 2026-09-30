package com.litefiles.app.ui

import android.content.Context
import android.graphics.Bitmap
import android.media.ThumbnailUtils
import android.os.CancellationSignal
import android.util.LruCache
import android.util.Size
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material3.Icon
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.core.graphics.drawable.toBitmap
import com.litefiles.app.data.FileItem
import com.litefiles.app.data.Kind
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withPermit
import kotlinx.coroutines.withContext
import java.io.File
import java.io.IOException
import java.util.concurrent.ConcurrentHashMap

/**
 * On-demand thumbnails, no image library: photos, video frames, audio cover art (embedded or folder art)
 * and APK launcher icons.
 * - decoded by the platform (ThumbnailUtils) at 256 px, only for rows/tiles that are on screen
 * - at most 3 decodes at a time, cancelled as soon as the item scrolls away
 * - in-memory LRU capped at ~10% of the heap (8..40 MB); files that fail to decode are remembered
 */
object Thumbnails {
    private const val SIZE_PX = 256

    private val cache = object : LruCache<String, ImageBitmap>(cacheKb()) {
        override fun sizeOf(key: String, value: ImageBitmap): Int =
            ((value.width * value.height * 4) / 1024).coerceAtLeast(1)
    }
    private val failed: MutableSet<String> = ConcurrentHashMap.newKeySet()
    private val gate = Semaphore(3)

    private fun cacheKb(): Int =
        (Runtime.getRuntime().maxMemory() / 1024 / 10).toInt().coerceIn(8 * 1024, 40 * 1024)

    fun key(item: FileItem) = "${item.path}|${item.modified}"
    fun cached(key: String): ImageBitmap? = cache.get(key)
    fun isFailed(key: String) = key in failed

    /** Launcher icon of an APK file (adaptive icons come back already masked to the device's icon shape). */
    @Suppress("DEPRECATION")
    private fun apkIcon(ctx: Context, path: String): Bitmap? {
        val pm = ctx.packageManager
        val info = pm.getPackageArchiveInfo(path, 0) ?: return null
        val app = info.applicationInfo ?: return null
        app.sourceDir = path // needed so the icon resource can be loaded from the archive
        app.publicSourceDir = path
        return pm.getApplicationIcon(app).toBitmap(SIZE_PX, SIZE_PX)
    }

    suspend fun load(ctx: Context, item: FileItem, kind: Kind, key: String): ImageBitmap? {
        if (key in failed) return null
        return gate.withPermit {
            withContext(Dispatchers.IO) {
                val signal = CancellationSignal()
                val handle = coroutineContext[Job]?.invokeOnCompletion { if (it != null) signal.cancel() }
                try {
                    val file = File(item.path)
                    val size = Size(SIZE_PX, SIZE_PX)
                    val bmp: Bitmap? = when (kind) {
                        Kind.VIDEO -> ThumbnailUtils.createVideoThumbnail(file, size, signal)
                        Kind.AUDIO -> ThumbnailUtils.createAudioThumbnail(file, size, signal) // throws if no art
                        Kind.APK -> apkIcon(ctx, item.path)
                        else -> ThumbnailUtils.createImageThumbnail(file, size, signal)
                    }
                    val img = (bmp ?: throw IOException("No thumbnail")).asImageBitmap()
                    cache.put(key, img)
                    img
                } catch (e: Exception) {
                    if (!signal.isCanceled) failed.add(key) // cancelled != broken file
                    null
                } finally {
                    handle?.dispose()
                }
            }
        }
    }
}

/**
 * Fills its bounds with a thumbnail (image, video, audio cover art, APK icon), otherwise (or until loaded) a type icon.
 * The caller supplies clip/background via [modifier].
 */
@Composable
fun FileThumb(item: FileItem, kind: Kind, modifier: Modifier, tint: Color, iconSize: Dp) {
    val appCtx = LocalContext.current.applicationContext
    val media = kind == Kind.IMAGE || kind == Kind.VIDEO || kind == Kind.AUDIO || kind == Kind.APK
    val key = remember(item) { Thumbnails.key(item) }
    var bmp by remember(key) { mutableStateOf(if (media) Thumbnails.cached(key) else null) }

    if (media && bmp == null && !Thumbnails.isFailed(key)) {
        LaunchedEffect(key) {
            delay(40) // items flung past in under 40 ms never start a decode
            bmp = Thumbnails.load(appCtx, item, kind, key)
        }
    }

    Box(modifier, contentAlignment = Alignment.Center) {
        val b = bmp
        if (b != null) {
            if (kind == Kind.APK) {
                // app icons are shown whole, with a little room around them
                Image(b, null, Modifier.fillMaxSize().padding(4.dp), contentScale = ContentScale.Fit)
            } else {
                Image(b, null, Modifier.fillMaxSize(), contentScale = ContentScale.Crop)
            }
            if (kind == Kind.VIDEO) {
                Icon(
                    Icons.Filled.PlayArrow,
                    contentDescription = null,
                    tint = Color.White,
                    modifier = Modifier
                        .size(iconSize * 0.9f)
                        .background(Color.Black.copy(alpha = 0.45f), CircleShape),
                )
            }
        } else {
            Icon(kindIcon(kind), contentDescription = null, tint = tint, modifier = Modifier.size(iconSize))
        }
    }
}

package com.litefiles.app.viewer

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Color as AndroidColor
import android.graphics.pdf.PdfRenderer
import android.os.ParcelFileDescriptor
import android.util.LruCache
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ZoomIn
import androidx.compose.material.icons.filled.ZoomOut
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Scaffold
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.litefiles.app.R
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import java.io.File
import kotlin.math.roundToInt
import kotlin.math.sqrt

private const val MAX_PAGE_PIXELS = 16_000_000L

private class PdfDoc(
    private val pfd: ParcelFileDescriptor,
    private val renderer: PdfRenderer,
    /** height / width of each page, read once when the file is opened */
    val ratios: FloatArray,
) {
    private val mutex = Mutex() // PdfRenderer allows only one open page at a time
    @Volatile
    private var closed = false

    suspend fun render(index: Int, widthPx: Int): Bitmap? = mutex.withLock {
        withContext(Dispatchers.IO) {
            if (closed) return@withContext null
            var w = widthPx.coerceAtLeast(1)
            var h = (w * ratios[index]).roundToInt().coerceAtLeast(1)
            if (w.toLong() * h > MAX_PAGE_PIXELS) { // absurdly large page: render smaller, the box still scales it
                val s = sqrt(MAX_PAGE_PIXELS.toDouble() / (w.toDouble() * h))
                w = (w * s).roundToInt().coerceAtLeast(1)
                h = (h * s).roundToInt().coerceAtLeast(1)
            }
            val bmp = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888)
            bmp.eraseColor(AndroidColor.WHITE)
            renderer.openPage(index).use { it.render(bmp, null, null, PdfRenderer.Page.RENDER_MODE_FOR_DISPLAY) }
            bmp
        }
    }

    fun close() {
        closed = true
        try { renderer.close() } catch (e: Exception) { /* already closed */ }
        try { pfd.close() } catch (e: Exception) { /* already closed */ }
    }
}

private sealed interface PdfState {
    data object Loading : PdfState
    class Ready(val doc: PdfDoc) : PdfState
    class Failed(val message: String) : PdfState
}

private fun openPdf(ctx: Context, path: String): PdfState {
    return try {
        val pfd = ParcelFileDescriptor.open(File(path), ParcelFileDescriptor.MODE_READ_ONLY)
        try {
            val renderer = PdfRenderer(pfd)
            if (renderer.pageCount == 0) {
                renderer.close()
                pfd.close()
                return PdfState.Failed(ctx.getString(R.string.error_pdf_no_pages))
            }
            val ratios = FloatArray(renderer.pageCount) { i ->
                renderer.openPage(i).use { p -> if (p.width > 0) p.height.toFloat() / p.width else 1.414f }
            }
            PdfState.Ready(PdfDoc(pfd, renderer, ratios))
        } catch (e: Exception) {
            pfd.close()
            throw e
        }
    } catch (e: SecurityException) {
        PdfState.Failed(ctx.getString(R.string.error_pdf_password))
    } catch (e: Exception) {
        PdfState.Failed(ctx.getString(R.string.error_pdf_open))
    }
}

/** Platform PdfRenderer: pages are drawn on demand (only the visible ones), cached by size, fit to screen width. */
@Composable
fun PdfViewer(path: String, onBack: () -> Unit) {
    val ctx = LocalContext.current
    val state by produceState<PdfState>(PdfState.Loading, path) {
        value = withContext(Dispatchers.IO) { openPdf(ctx, path) }
    }
    when (val s = state) {
        PdfState.Loading -> Scaffold(topBar = { ViewerTopBar(File(path).name, onBack) }) { pad ->
            Box(Modifier.padding(pad).fillMaxSize(), contentAlignment = Alignment.Center) { CircularProgressIndicator() }
        }
        is PdfState.Failed -> Scaffold(topBar = { ViewerTopBar(File(path).name, onBack) }) { pad ->
            ErrorPane(s.message, path, Modifier.padding(pad))
        }
        is PdfState.Ready -> PdfScreen(path, s.doc, onBack)
    }
}

@Composable
private fun PdfScreen(path: String, doc: PdfDoc, onBack: () -> Unit) {
    var zoomed by remember { mutableStateOf(false) }
    val cache = remember(doc) {
        val kb = (Runtime.getRuntime().maxMemory() / 1024 / 8).toInt().coerceIn(16 * 1024, 64 * 1024)
        object : LruCache<String, Bitmap>(kb) {
            override fun sizeOf(key: String, value: Bitmap): Int = (value.byteCount / 1024).coerceAtLeast(1)
        }
    }
    DisposableEffect(doc) { onDispose { doc.close() } }

    val listState = rememberLazyListState()
    val pageNo by remember { derivedStateOf { listState.firstVisibleItemIndex + 1 } }

    Scaffold(
        topBar = {
            ViewerTopBar(
                title = File(path).name,
                subtitle = stringResource(R.string.page_of, pageNo, doc.ratios.size),
                onBack = onBack,
                actions = {
                    IconButton(onClick = { zoomed = !zoomed }) {
                        Icon(
                            if (zoomed) Icons.Filled.ZoomOut else Icons.Filled.ZoomIn,
                            stringResource(if (zoomed) R.string.fit_width else R.string.zoom_in),
                        )
                    }
                    ViewerMenu(path)
                },
            )
        },
    ) { pad ->
        BoxWithConstraints(Modifier.padding(pad).fillMaxSize().background(Color(0xFF8A8A8A))) {
            val pageWidth = maxWidth * (if (zoomed) 2f else 1f)
            val widthPx = with(LocalDensity.current) { pageWidth.roundToPx() }.coerceAtMost(2400)
            // when zoomed the pages are wider than the screen: scroll sideways
            Box(Modifier.fillMaxSize().horizontalScroll(rememberScrollState())) {
                LazyColumn(
                    Modifier.width(pageWidth).fillMaxHeight(),
                    state = listState,
                    contentPadding = PaddingValues(8.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    items(doc.ratios.size, key = { it }) { i -> PdfPage(doc, cache, i, widthPx) }
                }
            }
        }
    }
}

@Composable
private fun PdfPage(doc: PdfDoc, cache: LruCache<String, Bitmap>, index: Int, widthPx: Int) {
    val key = "$index@$widthPx"
    val bmp by produceState<Bitmap?>(cache.get(key), key) {
        if (value == null) {
            val b = doc.render(index, widthPx)
            if (b != null) cache.put(key, b)
            value = b
        }
    }
    Box(Modifier.fillMaxWidth().aspectRatio(1f / doc.ratios[index]).background(Color.White)) {
        bmp?.let { Image(it.asImageBitmap(), null, Modifier.fillMaxSize()) }
    }
}

package com.litefiles.app.viewer

import android.os.Bundle
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalContext
import com.litefiles.app.ui.LiteFilesTheme
import com.litefiles.app.util.openExternal
import java.io.File

/** Hosts all built-in viewers. One file per launch; handles config changes itself so playback/edits survive rotation. */
class ViewerActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        enableEdgeToEdge()
        super.onCreate(savedInstanceState)
        val path = intent.getStringExtra(EXTRA_PATH)
        if (path == null || !File(path).isFile) {
            Toast.makeText(this, "File not found", Toast.LENGTH_SHORT).show()
            finish()
            return
        }
        setContent { LiteFilesTheme { ViewerApp(path, onClose = { finish() }) } }
    }

    override fun onDestroy() {
        ViewerSession.paths = emptyList()
        super.onDestroy()
    }

    companion object {
        const val EXTRA_PATH = "path"
    }
}

@Composable
private fun ViewerApp(path: String, onClose: () -> Unit) {
    val ctx = LocalContext.current
    val type = remember(path) { viewerTypeOf(File(path)) }
    when (type) {
        ViewerType.IMAGE -> ImageViewer(path, onClose)
        ViewerType.VIDEO -> VideoPlayer(path, onClose)
        ViewerType.AUDIO -> AudioPlayer(path, onClose)
        ViewerType.PDF -> PdfViewer(path, onClose)
        ViewerType.TEXT -> TextEditor(path, onClose)
        null -> LaunchedEffect(Unit) {
            openExternal(ctx, path)
            onClose()
        }
    }
}

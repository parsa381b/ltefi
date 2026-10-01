package com.litefiles.app.viewer

import android.app.Activity
import android.content.Context
import android.content.ContextWrapper
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material3.Button
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.core.view.WindowCompat
import com.litefiles.app.util.openExternal
import com.litefiles.app.util.shareFiles
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

tailrec fun Context.findActivity(): Activity? = when (this) {
    is Activity -> this
    is ContextWrapper -> baseContext.findActivity()
    else -> null
}

fun formatTime(ms: Int): String {
    val total = (ms / 1000).coerceAtLeast(0)
    val h = total / 3600
    val m = (total % 3600) / 60
    val s = total % 60
    return if (h > 0) "%d:%02d:%02d".format(h, m, s) else "%d:%02d".format(m, s)
}

/** null while the playlist is being read (a few ms), then the paths to swipe/skip through. */
@Composable
fun rememberPlaylist(path: String, type: ViewerType): List<String>? =
    produceState<List<String>?>(null, path) {
        value = withContext(Dispatchers.IO) { loadPlaylist(path, type) }
    }.value

/** Light status/navigation bar icons for screens with a black background. */
@Composable
fun BlackSystemBars() {
    val view = LocalView.current
    val activity = LocalContext.current.findActivity()
    DisposableEffect(activity) {
        if (activity == null) {
            onDispose { }
        } else {
            val c = WindowCompat.getInsetsController(activity.window, view)
            val oldStatus = c.isAppearanceLightStatusBars
            val oldNav = c.isAppearanceLightNavigationBars
            c.isAppearanceLightStatusBars = false
            c.isAppearanceLightNavigationBars = false
            onDispose {
                c.isAppearanceLightStatusBars = oldStatus
                c.isAppearanceLightNavigationBars = oldNav
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ViewerTopBar(
    title: String,
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
    subtitle: String? = null,
    overlay: Boolean = false,
    actions: @Composable RowScope.() -> Unit = {},
) {
    val colors = if (overlay) {
        TopAppBarDefaults.topAppBarColors(
            containerColor = Color.Black.copy(alpha = 0.55f),
            titleContentColor = Color.White,
            navigationIconContentColor = Color.White,
            actionIconContentColor = Color.White,
        )
    } else {
        TopAppBarDefaults.topAppBarColors()
    }
    TopAppBar(
        modifier = modifier,
        colors = colors,
        title = {
            Column {
                Text(title, maxLines = 1, overflow = TextOverflow.Ellipsis)
                if (subtitle != null) {
                    Text(
                        subtitle,
                        style = MaterialTheme.typography.labelSmall,
                        color = LocalContentColor.current.copy(alpha = 0.7f),
                        maxLines = 1,
                    )
                }
            }
        },
        navigationIcon = {
            IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, "Back") }
        },
        actions = actions,
    )
}

/** "More" menu shared by all viewers: viewer-specific [extra] items, then Open with… and Share. */
@Composable
fun ViewerMenu(path: String, extra: @Composable ColumnScope.(close: () -> Unit) -> Unit = {}) {
    val ctx = LocalContext.current
    var open by remember { mutableStateOf(false) }
    Box {
        IconButton(onClick = { open = true }) { Icon(Icons.Filled.MoreVert, "More") }
        DropdownMenu(expanded = open, onDismissRequest = { open = false }) {
            extra { open = false }
            DropdownMenuItem(
                text = { Text("Open with…") },
                onClick = { open = false; openExternal(ctx, path) },
            )
            DropdownMenuItem(
                text = { Text("Share") },
                onClick = { open = false; shareFiles(ctx, listOf(path)) },
            )
        }
    }
}

@Composable
fun ErrorPane(message: String, path: String, modifier: Modifier = Modifier, color: Color = Color.Unspecified) {
    val ctx = LocalContext.current
    Column(
        modifier.fillMaxSize().padding(32.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        Text(
            message,
            textAlign = TextAlign.Center,
            color = if (color == Color.Unspecified) MaterialTheme.colorScheme.onBackground else color,
        )
        Spacer(Modifier.height(16.dp))
        Button(onClick = { openExternal(ctx, path) }) { Text("Open with another app") }
    }
}

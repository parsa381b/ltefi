package com.litefiles.app.ui

import android.content.Context
import android.text.format.Formatter
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.DialogProperties
import com.litefiles.app.R
import com.litefiles.app.data.FileItem
import java.text.DateFormat
import java.util.Date

/**
 * One list row. Everything that varies per row is derived with remember(...) keyed on the
 * item, and the parameters are stable, so scrolling only composes the handful of visible rows
 * and selection changes only recompose the rows whose `selected` flag flipped.
 */
@OptIn(ExperimentalFoundationApi::class)
@Composable
fun FileRow(
    item: FileItem,
    selected: Boolean,
    selecting: Boolean,
    showFolder: Boolean,
    deleted: Boolean,
    dateFormat: DateFormat,
    onClick: (FileItem) -> Unit,
    onLongClick: (FileItem) -> Unit,
) {
    val ctx: Context = LocalContext.current
    val kind = remember(item) { item.kind }
    val subtitle = remember(item, showFolder, deleted) {
        val formatted = dateFormat.format(Date(item.modified))
        val date = if (deleted) ctx.getString(R.string.deleted_at, formatted) else formatted
        val base = if (item.isDir) date else "$date · ${Formatter.formatShortFileSize(ctx, item.size)}"
        // in category lists, show which folder the file lives in
        if (showFolder) item.path.substringBeforeLast('/').substringAfterLast('/') + " · " + base else base
    }
    val tint = kindColor(kind)
    val rowBg = if (selected) MaterialTheme.colorScheme.primary.copy(alpha = 0.12f) else Color.Transparent

    Row(
        Modifier
            .fillMaxWidth()
            .background(rowBg)
            .combinedClickable(onClick = { onClick(item) }, onLongClick = { onLongClick(item) })
            .padding(horizontal = 16.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        FileThumb(
            item = item,
            kind = kind,
            modifier = Modifier
                .size(44.dp)
                .clip(RoundedCornerShape(12.dp))
                .background(tint.copy(alpha = 0.15f)),
            tint = tint,
            iconSize = 24.dp,
        )
        Spacer(Modifier.width(14.dp))
        Column(Modifier.weight(1f)) {
            Text(item.name, style = MaterialTheme.typography.bodyLarge, maxLines = 1, overflow = TextOverflow.Ellipsis)
            Text(
                subtitle,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
            )
        }
        if (selecting) Checkbox(checked = selected, onCheckedChange = null)
    }
}

/** Grid cell: big square thumbnail (or type icon) with the name underneath. */
@OptIn(ExperimentalFoundationApi::class)
@Composable
fun FileTile(
    item: FileItem,
    selected: Boolean,
    selecting: Boolean,
    onClick: (FileItem) -> Unit,
    onLongClick: (FileItem) -> Unit,
) {
    val kind = remember(item) { item.kind }
    val tint = kindColor(kind)
    Column(
        Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(14.dp))
            .combinedClickable(onClick = { onClick(item) }, onLongClick = { onLongClick(item) })
            .padding(4.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Box(Modifier.fillMaxWidth().aspectRatio(1f)) {
            FileThumb(
                item = item,
                kind = kind,
                modifier = Modifier
                    .fillMaxSize()
                    .clip(RoundedCornerShape(12.dp))
                    .background(tint.copy(alpha = 0.15f)),
                tint = tint,
                iconSize = 36.dp,
            )
            if (selected) {
                Box(
                    Modifier
                        .fillMaxSize()
                        .clip(RoundedCornerShape(12.dp))
                        .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.28f)),
                )
            }
            if (selecting) {
                Checkbox(
                    checked = selected,
                    onCheckedChange = null,
                    modifier = Modifier
                        .align(Alignment.TopStart)
                        .padding(4.dp)
                        .background(Color.White.copy(alpha = 0.85f), CircleShape),
                )
            }
        }
        Text(
            item.name,
            style = MaterialTheme.typography.labelMedium,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
            textAlign = TextAlign.Center,
            modifier = Modifier.padding(top = 4.dp),
        )
    }
}

@Composable
fun NameDialog(
    title: String,
    initial: String,
    confirmLabel: String,
    onDismiss: () -> Unit,
    onConfirm: (String) -> Unit,
) {
    // Pre-select just the base name (without extension), like most file managers
    var text by remember {
        mutableStateOf(TextFieldValue(initial, TextRange(0, initial.substringBeforeLast('.', initial).length)))
    }
    val focus = remember { FocusRequester() }
    LaunchedEffect(Unit) { focus.requestFocus() }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = {
            OutlinedTextField(
                value = text,
                onValueChange = { text = it },
                singleLine = true,
                modifier = Modifier.focusRequester(focus),
            )
        },
        confirmButton = {
            TextButton(onClick = { onConfirm(text.text) }, enabled = text.text.isNotBlank()) { Text(confirmLabel) }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.action_cancel)) } },
    )
}

@Composable
fun ConfirmDialog(
    title: String,
    message: String,
    confirmLabel: String,
    onDismiss: () -> Unit,
    onConfirm: () -> Unit,
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = { Text(message) },
        confirmButton = { TextButton(onClick = onConfirm) { Text(confirmLabel) } },
        dismissButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.action_cancel)) } },
    )
}

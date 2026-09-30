package com.litefiles.app.ui

import android.content.Context
import android.text.format.Formatter
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.DialogProperties
import com.litefiles.app.data.Details
import java.text.DateFormat
import java.text.NumberFormat
import java.util.Date

/** Blocking dialog for running operations. Shows progress + Cancel when the op supports it. */
@Composable
fun OperationDialog(op: Op, onCancel: () -> Unit) {
    val ctx = LocalContext.current
    AlertDialog(
        onDismissRequest = {},
        properties = DialogProperties(dismissOnBackPress = false, dismissOnClickOutside = false),
        title = { Text(op.label) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                val p = op.progress
                val fraction = p?.fraction
                if (fraction == null) {
                    LinearProgressIndicator(Modifier.fillMaxWidth())
                } else {
                    LinearProgressIndicator(progress = { fraction }, modifier = Modifier.fillMaxWidth())
                }
                if (p != null) {
                    val bytes = if (p.totalBytes > 0) {
                        "${Formatter.formatShortFileSize(ctx, p.doneBytes)} of " +
                            "${Formatter.formatShortFileSize(ctx, p.totalBytes)} · "
                    } else {
                        ""
                    }
                    Text("$bytes${p.processedItems} of ${p.totalItems} items", style = MaterialTheme.typography.bodyMedium)
                    Text(
                        p.current,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
            }
        },
        confirmButton = {
            if (op.cancellable) {
                TextButton(onClick = onCancel, enabled = !op.cancelling) {
                    Text(if (op.cancelling) "Cancelling…" else "Cancel")
                }
            }
        },
    )
}

private fun plural(n: Int, one: String, many: String) = "$n ${if (n == 1) one else many}"

private fun counts(files: Int, folders: Int) =
    "${plural(files, "file", "files")}, ${plural(folders, "folder", "folders")}"

private fun sizeText(ctx: Context, n: Long): String =
    if (n < 1024) "$n bytes"
    else "${Formatter.formatFileSize(ctx, n)} (${NumberFormat.getIntegerInstance().format(n)} bytes)"

/** Name, type, location, modified, size, and (for folders) item counts. Counts fill in live while scanning. */
@Composable
fun DetailsDialog(d: Details, onDismiss: () -> Unit) {
    val ctx = LocalContext.current
    val dateFormat = remember { DateFormat.getDateTimeInstance(DateFormat.LONG, DateFormat.MEDIUM) }
    val scanning = !(d.single && !d.isDir) && d.scan?.done != true

    val rows = remember(d) {
        val out = ArrayList<Pair<String, String>>()
        out += (if (d.single) "Name" else "Selected") to d.title
        if (d.single) d.type?.let { out += "Type" to it }
        if (!d.single) out += "Includes" to counts(d.selectedFiles, d.selectedFolders)
        d.location?.let { out += "Location" to it }
        d.modified?.let { out += "Modified" to dateFormat.format(Date(it)) }

        val scan = d.scan
        val suffix = if (scan != null && !scan.done) " …" else ""
        out += "Size" to when {
            d.fileSize != null -> sizeText(ctx, d.fileSize)
            scan != null -> sizeText(ctx, scan.bytes) + suffix
            else -> "Calculating…"
        }
        if (d.single && d.isDir) {
            out += "Items" to (scan?.direct?.toString() ?: "…")
            out += "Contains" to (if (scan != null) counts(scan.files, scan.folders) + suffix else "…")
        }
        out
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Details") },
        text = {
            Column(Modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                if (scanning) LinearProgressIndicator(Modifier.fillMaxWidth())
                rows.forEach { (label, value) ->
                    Column {
                        Text(label, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        SelectionContainer { Text(value, style = MaterialTheme.typography.bodyMedium) }
                    }
                }
            }
        },
        confirmButton = { TextButton(onClick = onDismiss) { Text("Close") } },
    )
}

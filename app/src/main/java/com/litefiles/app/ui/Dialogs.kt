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
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.DialogProperties
import com.litefiles.app.R
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
                        stringResource(
                            R.string.progress_of,
                            Formatter.formatShortFileSize(ctx, p.doneBytes),
                            Formatter.formatShortFileSize(ctx, p.totalBytes),
                        ) + " · "
                    } else {
                        ""
                    }
                    Text(
                        bytes + pluralStringResource(
                            R.plurals.items_progress,
                            p.processedItems,
                            p.processedItems,
                            p.totalItems,
                        ),
                        style = MaterialTheme.typography.bodyMedium,
                    )
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
                    Text(if (op.cancelling) stringResource(R.string.op_cancelling) else stringResource(R.string.action_cancel))
                }
            }
        },
    )
}

private fun counts(ctx: Context, files: Int, folders: Int): String {
    val res = ctx.resources
    return "${res.getQuantityString(R.plurals.n_files, files, files)}, " +
        res.getQuantityString(R.plurals.n_folders, folders, folders)
}

/** The number is formatted separately from the unit so every locale gets its own digits and wording. */
private fun bytesText(ctx: Context, n: Long): String {
    val num = NumberFormat.getIntegerInstance().format(n)
    val quantity = if (n > Int.MAX_VALUE) 2 else n.toInt()
    return ctx.resources.getQuantityString(R.plurals.n_bytes, quantity, num)
}

private fun sizeText(ctx: Context, n: Long): String =
    if (n < 1024) bytesText(ctx, n)
    else ctx.getString(R.string.size_and_bytes, Formatter.formatFileSize(ctx, n), bytesText(ctx, n))

/** Name, type, location, modified, size, and (for folders) item counts. Counts fill in live while scanning. */
@Composable
fun DetailsDialog(d: Details, onDismiss: () -> Unit) {
    val ctx = LocalContext.current
    val dateFormat = remember { DateFormat.getDateTimeInstance(DateFormat.LONG, DateFormat.MEDIUM) }
    val scanning = !(d.single && !d.isDir) && d.scan?.done != true

    val rows = remember(d) {
        val out = ArrayList<Pair<String, String>>()
        out += (if (d.single) ctx.getString(R.string.details_name) else ctx.getString(R.string.details_selected)) to d.title
        if (d.single) d.type?.let { out += ctx.getString(R.string.details_type) to it }
        if (!d.single) out += ctx.getString(R.string.details_includes) to counts(ctx, d.selectedFiles, d.selectedFolders)
        d.location?.let { out += ctx.getString(R.string.details_location) to it }
        d.modified?.let { out += ctx.getString(R.string.details_modified) to dateFormat.format(Date(it)) }

        val scan = d.scan
        val suffix = if (scan != null && !scan.done) " …" else ""
        out += ctx.getString(R.string.details_size) to when {
            d.fileSize != null -> sizeText(ctx, d.fileSize)
            scan != null -> sizeText(ctx, scan.bytes) + suffix
            else -> ctx.getString(R.string.details_calculating)
        }
        if (d.single && d.isDir) {
            out += ctx.getString(R.string.details_items) to (scan?.direct?.toString() ?: "…")
            out += ctx.getString(R.string.details_contains) to
                (if (scan != null) counts(ctx, scan.files, scan.folders) + suffix else "…")
        }
        out
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.details_title)) },
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
        confirmButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.action_close)) } },
    )
}

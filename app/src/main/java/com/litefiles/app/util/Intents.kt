package com.litefiles.app.util

import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.provider.Settings
import android.webkit.MimeTypeMap
import android.widget.Toast
import androidx.core.content.FileProvider
import androidx.core.net.toUri
import java.io.File

private fun uriFor(ctx: Context, file: File) =
    FileProvider.getUriForFile(ctx, "${ctx.packageName}.fileprovider", file)

private fun mimeOf(file: File): String =
    MimeTypeMap.getSingleton().getMimeTypeFromExtension(file.extension.lowercase()) ?: "*/*"

fun openFile(ctx: Context, path: String) {
    val file = File(path)
    val intent = Intent(Intent.ACTION_VIEW)
        .setDataAndType(uriFor(ctx, file), mimeOf(file))
        .addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
    try {
        ctx.startActivity(intent)
    } catch (e: ActivityNotFoundException) {
        Toast.makeText(ctx, "No app can open this file", Toast.LENGTH_SHORT).show()
    }
}

/** Shares files only (Android can't share folders). Folders in [paths] are ignored. */
fun shareFiles(ctx: Context, paths: List<String>) {
    val files = paths.map(::File).filter { it.isFile }
    if (files.isEmpty()) {
        Toast.makeText(ctx, "Folders can't be shared", Toast.LENGTH_SHORT).show()
        return
    }
    val uris = ArrayList(files.map { uriFor(ctx, it) })
    val mime = files.map(::mimeOf).toSet().singleOrNull() ?: "*/*"
    val intent = if (uris.size == 1) {
        Intent(Intent.ACTION_SEND).putExtra(Intent.EXTRA_STREAM, uris[0])
    } else {
        Intent(Intent.ACTION_SEND_MULTIPLE).putParcelableArrayListExtra(Intent.EXTRA_STREAM, uris)
    }
    intent.setType(mime).addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
    ctx.startActivity(Intent.createChooser(intent, null))
}

fun requestAllFilesAccess(ctx: Context) {
    val pkg = "package:${ctx.packageName}".toUri()
    try {
        ctx.startActivity(Intent(Settings.ACTION_MANAGE_APP_ALL_FILES_ACCESS_PERMISSION, pkg))
    } catch (e: ActivityNotFoundException) {
        ctx.startActivity(Intent(Settings.ACTION_MANAGE_ALL_FILES_ACCESS_PERMISSION))
    }
}

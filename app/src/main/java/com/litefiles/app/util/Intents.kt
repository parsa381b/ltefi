package com.litefiles.app.util

import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.provider.Settings
import android.webkit.MimeTypeMap
import android.widget.Toast
import androidx.core.content.FileProvider
import androidx.core.net.toUri
import com.litefiles.app.R
import com.litefiles.app.viewer.ViewerActivity
import com.litefiles.app.viewer.ViewerSession
import com.litefiles.app.viewer.viewerTypeOf
import java.io.File

private fun uriFor(ctx: Context, file: File) =
    FileProvider.getUriForFile(ctx, "${ctx.packageName}.fileprovider", file)

private fun mimeOf(file: File): String =
    MimeTypeMap.getSingleton().getMimeTypeFromExtension(file.extension.lowercase()) ?: "*/*"

/**
 * Opens a file: APKs go to the installer, images/video/audio/PDF/text to the built-in viewers
 * (with [siblings] as the swipe/next list), everything else to another app.
 */
fun openFile(ctx: Context, path: String, siblings: List<String> = emptyList()) {
    val file = File(path)
    if (file.extension.equals("apk", ignoreCase = true)) {
        installApk(ctx, file)
        return
    }
    if (viewerTypeOf(file) != null) {
        ViewerSession.paths = siblings
        ctx.startActivity(Intent(ctx, ViewerActivity::class.java).putExtra(ViewerActivity.EXTRA_PATH, path))
        return
    }
    openExternal(ctx, path)
}

/** Hands the file to another app through the system "open with" flow. */
fun openExternal(ctx: Context, path: String) {
    val file = File(path)
    val intent = Intent(Intent.ACTION_VIEW)
        .setDataAndType(uriFor(ctx, file), mimeOf(file))
        .addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
    try {
        ctx.startActivity(intent)
    } catch (e: ActivityNotFoundException) {
        Toast.makeText(ctx, ctx.getString(R.string.toast_no_app), Toast.LENGTH_SHORT).show()
    }
}

private const val APK_MIME = "application/vnd.android.package-archive"

/**
 * Hands an APK to the system installer. First time only, Android requires the user to allow
 * "Install unknown apps" for this app: we open that settings page and ask them to tap the APK again.
 */
private fun installApk(ctx: Context, file: File) {
    if (!ctx.packageManager.canRequestPackageInstalls()) {
        Toast.makeText(
            ctx,
            ctx.getString(R.string.toast_install_unknown),
            Toast.LENGTH_LONG,
        ).show()
        try {
            ctx.startActivity(
                Intent(Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES, "package:${ctx.packageName}".toUri()),
            )
        } catch (e: ActivityNotFoundException) {
            // no settings screen to open: the toast above is all we can do
        }
        return
    }
    val intent = Intent(Intent.ACTION_VIEW)
        .setDataAndType(uriFor(ctx, file), APK_MIME)
        .addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
    try {
        ctx.startActivity(intent)
    } catch (e: ActivityNotFoundException) {
        Toast.makeText(ctx, ctx.getString(R.string.toast_installer_failed), Toast.LENGTH_SHORT).show()
    }
}

/** Shares files only (Android can't share folders). Folders in [paths] are ignored. */
fun shareFiles(ctx: Context, paths: List<String>) {
    val files = paths.map(::File).filter { it.isFile }
    if (files.isEmpty()) {
        Toast.makeText(ctx, ctx.getString(R.string.toast_folders_no_share), Toast.LENGTH_SHORT).show()
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

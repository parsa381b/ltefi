package com.litefiles.app.viewer

import com.litefiles.app.data.FileItem
import java.io.File
import java.io.FileInputStream
import java.io.IOException

enum class ViewerType { IMAGE, VIDEO, AUDIO, PDF, TEXT }

/**
 * Hands the viewer the list the user was looking at (so swiping / next-track follows the same order,
 * e.g. newest-first in the Images category). Falls back to listing the file's folder when empty.
 */
object ViewerSession {
    @Volatile
    var paths: List<String> = emptyList()
}

private val IMAGE_EXT = setOf("jpg", "jpeg", "png", "gif", "webp", "bmp", "heic", "heif", "avif")
private val VIDEO_EXT = setOf("mp4", "mkv", "webm", "3gp", "avi", "mov", "m4v")
private val AUDIO_EXT = setOf("mp3", "m4a", "aac", "flac", "wav", "ogg", "opus", "amr", "mid", "midi")
private val TEXT_EXT = setOf(
    "txt", "md", "markdown", "csv", "tsv", "json", "xml", "html", "htm", "xhtml", "css", "scss", "less",
    "js", "mjs", "cjs", "jsx", "tsx", "kt", "kts", "java", "py", "pyw", "c", "h", "cpp", "cc", "cxx", "hpp",
    "cs", "go", "rs", "rb", "php", "sh", "bash", "zsh", "bat", "cmd", "yml", "yaml", "toml", "ini", "conf",
    "cfg", "properties", "gradle", "sql", "log", "srt", "vtt", "tex", "gitignore", "env", "plist", "xsl",
    "swift", "dart", "lua", "pl", "r",
    // extension-less well-known names (the "extension" of "Makefile" is the whole name)
    "makefile", "dockerfile", "license", "readme",
)

/** Type from the file name only (cheap: safe to run over thousands of list items). ".ts" is ambiguous, so null. */
fun typeByExtension(name: String): ViewerType? {
    val ext = name.substringAfterLast('.', name).lowercase()
    return when {
        ext in IMAGE_EXT -> ViewerType.IMAGE
        ext in VIDEO_EXT -> ViewerType.VIDEO
        ext in AUDIO_EXT -> ViewerType.AUDIO
        ext == "pdf" -> ViewerType.PDF
        ext in TEXT_EXT -> ViewerType.TEXT
        else -> null
    }
}

/** null = no built-in viewer (open with another app). ".ts" is TypeScript or MPEG-TS video: sniff the content. */
fun viewerTypeOf(file: File): ViewerType? {
    typeByExtension(file.name)?.let { return it }
    if (file.extension.equals("ts", ignoreCase = true)) {
        return if (looksLikeText(file)) ViewerType.TEXT else ViewerType.VIDEO
    }
    return null
}

fun looksLikeText(file: File, probe: Int = 4096): Boolean = try {
    FileInputStream(file).use { input ->
        val buf = ByteArray(probe)
        val n = input.read(buf)
        (0 until n).none { buf[it].toInt() == 0 }
    }
} catch (e: IOException) {
    false
}

/** Paths to swipe/skip through: same viewer type as [item], in the order the user sees them. */
fun viewerSiblings(items: List<FileItem>, item: FileItem): List<String> {
    val type = typeByExtension(item.name)
    if (type != ViewerType.IMAGE && type != ViewerType.VIDEO && type != ViewerType.AUDIO) return emptyList()
    return items.filter { !it.isDir && typeByExtension(it.name) == type }.map { it.path }
}

/** Playlist for [path]: the session list if it contains the file, else its folder sorted by name. */
fun loadPlaylist(path: String, type: ViewerType): List<String> {
    val session = ViewerSession.paths
    if (path in session) return session
    val dir = File(path).parentFile ?: return listOf(path)
    val files = dir.listFiles { f -> f.isFile && typeByExtension(f.name) == type } ?: return listOf(path)
    val sorted = files.sortedWith(compareBy<File>(String.CASE_INSENSITIVE_ORDER) { it.name }).map { it.path }
    return if (path in sorted) sorted else listOf(path)
}

package com.litefiles.app.data

import androidx.compose.runtime.Immutable

enum class Kind { FOLDER, IMAGE, VIDEO, AUDIO, DOC, ARCHIVE, APK, OTHER }

/**
 * One directory entry. Deliberately tiny (5 fields, no Uri/Bitmap/File object) so that
 * 100k entries stay in the low tens of MB. [kind] is derived lazily from the name only
 * for rows that are actually composed.
 */
@Immutable
class FileItem(
    val name: String,
    val path: String,
    val isDir: Boolean,
    val size: Long,
    val modified: Long,
) {
    val kind: Kind
        get() = if (isDir) Kind.FOLDER else kindOfExtension(name.substringAfterLast('.', ""))
}

private val KINDS: Map<String, Kind> = buildMap {
    fun add(kind: Kind, vararg exts: String) = exts.forEach { put(it, kind) }
    add(Kind.IMAGE, "jpg", "jpeg", "png", "gif", "webp", "bmp", "heic", "heif", "avif", "svg", "dng")
    add(Kind.VIDEO, "mp4", "mkv", "webm", "3gp", "avi", "mov", "m4v", "ts")
    add(Kind.AUDIO, "mp3", "m4a", "aac", "flac", "wav", "ogg", "opus", "amr", "mid")
    add(Kind.DOC, "pdf", "doc", "docx", "xls", "xlsx", "ppt", "pptx", "txt", "md", "csv", "rtf", "odt", "epub", "json", "xml", "html")
    add(Kind.ARCHIVE, "zip", "rar", "7z", "tar", "gz", "bz2", "xz", "apks", "xapk")
    add(Kind.APK, "apk")
}

fun kindOfExtension(ext: String): Kind = KINDS[ext.lowercase()] ?: Kind.OTHER

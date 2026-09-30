package com.litefiles.app.data

import androidx.compose.runtime.Immutable

/** Result of walking the selection: totals are recursive; [direct] = entries directly inside the first folder. */
@Immutable
class ScanResult(val bytes: Long, val files: Int, val folders: Int, val direct: Int, val done: Boolean)

/** Everything the Details dialog shows. [scan] fills in progressively for folders / multi-selections. */
@Immutable
data class Details(
    val title: String,
    val location: String?,
    val type: String?,
    val modified: Long?,
    val single: Boolean,
    val isDir: Boolean,
    /** exact size, only for a single file (folders and multi-selections use [scan]) */
    val fileSize: Long?,
    val selectedFiles: Int,
    val selectedFolders: Int,
    val scan: ScanResult? = null,
)

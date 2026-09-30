package com.litefiles.app.data

import androidx.compose.runtime.Immutable

/** Snapshot of a running copy/move, emitted a few times per second. */
@Immutable
class Progress(
    val doneBytes: Long,
    val totalBytes: Long,
    val processedItems: Int,
    val totalItems: Int,
    val current: String,
) {
    /** 0..1; driven by bytes when known, otherwise by item count. Null = nothing to measure yet. */
    val fraction: Float?
        get() = when {
            totalBytes > 0 -> (doneBytes.toFloat() / totalBytes).coerceIn(0f, 1f)
            totalItems > 0 -> (processedItems.toFloat() / totalItems).coerceIn(0f, 1f)
            else -> null
        }
}

/**
 * Counters the repository updates while transferring. Owned by the caller so the final tally
 * is still readable after the transfer was cancelled (cancellation throws, it can't return a result).
 */
class TransferStats {
    @Volatile var total = 0
    @Volatile var done = 0
    @Volatile var failed = 0
}

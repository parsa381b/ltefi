package com.litefiles.app.data

import android.content.Context
import android.os.Environment
import android.os.StatFs
import android.os.storage.StorageManager
import android.provider.MediaStore
import com.litefiles.app.R
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream
import java.io.IOException
import java.nio.file.Files
import java.nio.file.LinkOption
import java.nio.file.Path
import java.nio.file.attribute.BasicFileAttributes
import kotlin.random.Random

enum class SortBy(val labelRes: Int) {
    NAME(R.string.sort_name),
    DATE(R.string.sort_date),
    SIZE(R.string.sort_size),
}

class Volume(val name: String, val root: File, val total: Long, val free: Long, val primary: Boolean)

/** [labelRes] is a resource so the shortcut name follows the app language, not the folder name on disk. */
class Shortcut(val folder: String, val labelRes: Int, val dir: File)

/** All disk access lives here. Every function runs on Dispatchers.IO. */
object FileRepository {

    // ---------- Listing ----------

    /**
     * Lists [dir] with one stat call per entry (NIO readAttributes) and sorts in place.
     * Cooperative: checks for cancellation per entry so navigating away aborts a huge listing.
     */
    suspend fun list(
        dir: File,
        showHidden: Boolean,
        sortBy: SortBy,
        ascending: Boolean,
    ): List<FileItem> = withContext(Dispatchers.IO) {
        val out = ArrayList<FileItem>(512)
        Files.newDirectoryStream(dir.toPath()).use { stream ->
            for (p in stream) {
                ensureActive()
                val name = p.fileName.toString()
                if (!showHidden && name.startsWith('.')) continue
                try {
                    val a = Files.readAttributes(p, BasicFileAttributes::class.java)
                    val isDir = a.isDirectory
                    out.add(
                        FileItem(
                            name = name,
                            path = p.toString(),
                            isDir = isDir,
                            size = if (isDir) 0L else a.size(),
                            modified = a.lastModifiedTime().toMillis(),
                        ),
                    )
                } catch (e: IOException) {
                    // unreadable entry / broken symlink: skip it
                }
            }
        }
        ensureActive()
        out.sortWith(comparator(sortBy, ascending))
        out
    }

    fun sort(list: List<FileItem>, by: SortBy, ascending: Boolean): List<FileItem> =
        list.sortedWith(comparator(by, ascending))

    private fun comparator(by: SortBy, asc: Boolean): Comparator<FileItem> {
        val byName = Comparator<FileItem> { a, b -> a.name.compareTo(b.name, ignoreCase = true) }
        val primary: Comparator<FileItem> = when (by) {
            SortBy.NAME -> byName
            SortBy.DATE -> Comparator { a, b -> a.modified.compareTo(b.modified) }
            SortBy.SIZE -> Comparator { a, b -> a.size.compareTo(b.size) }
        }
        val dirsFirst = Comparator<FileItem> { a, b ->
            if (a.isDir == b.isDir) 0 else if (a.isDir) -1 else 1
        }
        return dirsFirst.then(if (asc) primary else primary.reversed()).then(byName)
    }

    // ---------- Categories (MediaStore index: no disk walking) ----------

    private const val APK_MIME = "application/vnd.android.package-archive"

    private val DOC_MIMES = arrayOf(
        "application/pdf",
        "application/msword",
        "application/vnd.openxmlformats-officedocument.wordprocessingml.document",
        "application/vnd.ms-excel",
        "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet",
        "application/vnd.ms-powerpoint",
        "application/vnd.openxmlformats-officedocument.presentationml.presentation",
        "application/vnd.oasis.opendocument.text",
        "application/vnd.oasis.opendocument.spreadsheet",
        "application/vnd.oasis.opendocument.presentation",
        "application/rtf",
        "application/epub+zip",
    )

    private fun categoryQuery(cat: Category): Pair<String, Array<String>> {
        val mediaType = MediaStore.Files.FileColumns.MEDIA_TYPE
        val mime = MediaStore.MediaColumns.MIME_TYPE
        val name = MediaStore.MediaColumns.DISPLAY_NAME
        return when (cat) {
            Category.IMAGES -> "$mediaType = ?" to arrayOf(MediaStore.Files.FileColumns.MEDIA_TYPE_IMAGE.toString())
            Category.VIDEOS -> "$mediaType = ?" to arrayOf(MediaStore.Files.FileColumns.MEDIA_TYPE_VIDEO.toString())
            Category.AUDIO -> "$mediaType = ?" to arrayOf(MediaStore.Files.FileColumns.MEDIA_TYPE_AUDIO.toString())
            Category.APKS -> "$mime = ? OR $name LIKE ?" to arrayOf(APK_MIME, "%.apk")
            Category.DOCUMENTS -> {
                val marks = DOC_MIMES.joinToString(",") { "?" }
                "$mime IN ($marks) OR $mime LIKE ?" to (DOC_MIMES + "text/%")
            }
        }
    }

    /**
     * All files of a category across every mounted volume, via one indexed MediaStore query.
     * Entries whose file no longer exists (stale index) are dropped with a single stat each.
     */
    @Suppress("DEPRECATION")
    suspend fun category(
        ctx: Context,
        cat: Category,
        sortBy: SortBy,
        ascending: Boolean,
    ): List<FileItem> = withContext(Dispatchers.IO) {
        val uri = MediaStore.Files.getContentUri(MediaStore.VOLUME_EXTERNAL)
        val (selection, args) = categoryQuery(cat)
        val projection = arrayOf(
            MediaStore.MediaColumns.DATA,
            MediaStore.MediaColumns.SIZE,
            MediaStore.MediaColumns.DATE_MODIFIED,
        )
        val out = ArrayList<FileItem>(1024)
        ctx.contentResolver.query(uri, projection, selection, args, null)?.use { c ->
            val iPath = c.getColumnIndexOrThrow(MediaStore.MediaColumns.DATA)
            val iSize = c.getColumnIndexOrThrow(MediaStore.MediaColumns.SIZE)
            val iDate = c.getColumnIndexOrThrow(MediaStore.MediaColumns.DATE_MODIFIED)
            while (c.moveToNext()) {
                ensureActive()
                val path = c.getString(iPath) ?: continue
                if (!File(path).isFile) continue
                out.add(
                    FileItem(
                        name = path.substringAfterLast('/'),
                        path = path,
                        isDir = false,
                        size = c.getLong(iSize),
                        modified = c.getLong(iDate) * 1000L, // MediaStore stores seconds
                    ),
                )
            }
        }
        ensureActive()
        out.sortWith(comparator(sortBy, ascending))
        out
    }

    // ---------- Storage volumes / home ----------

    fun volumes(ctx: Context): List<Volume> {
        val sm = ctx.getSystemService(StorageManager::class.java) ?: return emptyList()
        return sm.storageVolumes.mapNotNull { v ->
            val dir = v.directory ?: return@mapNotNull null
            if (v.state != Environment.MEDIA_MOUNTED) return@mapNotNull null
            try {
                val st = StatFs(dir.path)
                Volume(v.getDescription(ctx), dir, st.totalBytes, st.availableBytes, v.isPrimary)
            } catch (e: IllegalArgumentException) {
                null
            }
        }
    }

    fun shortcuts(primaryRoot: File?): List<Shortcut> {
        if (primaryRoot == null) return emptyList()
        return listOf(
            "Download" to R.string.sc_downloads,
            "DCIM" to R.string.sc_dcim,
            "Pictures" to R.string.sc_pictures,
            "Documents" to R.string.sc_documents,
            "Music" to R.string.sc_music,
            "Movies" to R.string.sc_movies,
        ).mapNotNull { (folder, labelRes) ->
            val dir = File(primaryRoot, folder)
            if (dir.isDirectory) Shortcut(folder, labelRes, dir) else null
        }
    }

    // ---------- Operations (return number of failures) ----------

    // ---------- Recycle bin ----------
    //
    // Each storage volume gets a hidden folder <volume>/.LiteFilesTrash (with a .nomedia file so gallery/music
    // apps ignore it). A trashed item is renamed (instant, same volume) to <id>, plus a tiny <id>.meta text file
    // holding its original absolute path. <id> = "<deletedAtMillis>-<hex>", so age is known from the name alone.

    const val TRASH_DIR = ".LiteFilesTrash"
    private const val TRASH_MAX_AGE_MS = 30L * 24 * 60 * 60 * 1000

    class TrashResult(val moved: Int, val deleted: Int, val failed: Int)

    fun isInTrash(path: String): Boolean = path.contains("/$TRASH_DIR/") || path.endsWith("/$TRASH_DIR")

    private fun purgeItem(f: File): Boolean {
        val ok = f.deleteRecursively()
        if (ok && isInTrash(f.path)) f.parentFile?.let { File(it, f.name + ".meta").delete() }
        return ok
    }

    /** Moves [paths] to the bin of their volume. Anything already inside a bin is deleted for good. */
    suspend fun moveToTrash(paths: List<String>, roots: List<File>): TrashResult = withContext(Dispatchers.IO) {
        var moved = 0
        var deleted = 0
        var failed = 0
        val rootPaths = roots.map { it.path }
        for (p in paths) {
            try {
                if (isInTrash(p)) {
                    if (purgeItem(File(p))) deleted++ else failed++
                    continue
                }
                val root = rootPaths.firstOrNull { p.startsWith("$it/") }
                val src = File(p)
                if (root == null || !src.exists()) { failed++; continue }

                val bin = File(root, TRASH_DIR)
                bin.mkdirs()
                val noMedia = File(bin, ".nomedia")
                if (!noMedia.exists()) noMedia.createNewFile()

                var id: String
                do {
                    id = "${System.currentTimeMillis()}-${Random.nextInt(0x10000).toString(16)}"
                } while (File(bin, id).exists()) // never rename over an existing item
                val meta = File(bin, "$id.meta")
                meta.writeText(p)
                if (src.renameTo(File(bin, id))) moved++ else { meta.delete(); failed++ }
            } catch (e: IOException) {
                failed++
            } catch (e: SecurityException) {
                failed++
            }
        }
        TrashResult(moved, deleted, failed)
    }

    /** Everything in the bins of [roots] as list items: name = original name, modified = deletion time. */
    suspend fun trashItems(roots: List<File>, sortBy: SortBy, ascending: Boolean): List<FileItem> =
        withContext(Dispatchers.IO) {
            val out = ArrayList<FileItem>()
            for (root in roots) {
                val bin = File(root, TRASH_DIR)
                if (!bin.isDirectory) continue
                Files.newDirectoryStream(bin.toPath()).use { stream ->
                    for (p in stream) {
                        ensureActive()
                        val id = p.fileName.toString()
                        if (id.endsWith(".meta") || id == ".nomedia") continue
                        val a = try {
                            Files.readAttributes(p, BasicFileAttributes::class.java, LinkOption.NOFOLLOW_LINKS)
                        } catch (e: IOException) {
                            continue
                        }
                        val orig = try { File(bin, "$id.meta").readText().trim() } catch (e: IOException) { "" }
                        val name = orig.substringAfterLast('/').ifEmpty { id }
                        val deletedAt = id.substringBefore('-').toLongOrNull() ?: a.lastModifiedTime().toMillis()
                        out.add(
                            FileItem(name, p.toString(), a.isDirectory, if (a.isDirectory) 0L else a.size(), deletedAt),
                        )
                    }
                }
            }
            ensureActive()
            out.sortWith(comparator(sortBy, ascending))
            out
        }

    /** Puts bin items back where they came from (recreating folders, renaming on conflict). Returns failures. */
    suspend fun restore(paths: List<String>): Int = withContext(Dispatchers.IO) {
        var failed = 0
        for (p in paths) {
            try {
                val item = File(p)
                val bin = item.parentFile
                val volRoot = bin?.parentFile
                if (bin == null || volRoot == null || !item.exists()) { failed++; continue }
                val meta = File(bin, item.name + ".meta")
                val orig = try { meta.readText().trim() } catch (e: IOException) { "" }
                val destDir = if (orig.isNotEmpty()) File(orig).parentFile ?: volRoot else File(volRoot, "Restored")
                val name = if (orig.isNotEmpty()) File(orig).name else item.name
                destDir.mkdirs()
                val target = uniqueTarget(destDir, name)
                if (item.renameTo(target)) meta.delete() else failed++
            } catch (e: IOException) {
                failed++
            } catch (e: SecurityException) {
                failed++
            }
        }
        failed
    }

    suspend fun deleteForever(paths: List<String>): Int = withContext(Dispatchers.IO) {
        var failed = 0
        for (p in paths) if (!purgeItem(File(p))) failed++
        failed
    }

    suspend fun emptyBin(roots: List<File>) = withContext(Dispatchers.IO) {
        for (root in roots) {
            File(root, TRASH_DIR).listFiles()?.forEach { if (it.name != ".nomedia") it.deleteRecursively() }
        }
    }

    /** Deletes bin items older than 30 days (age is parsed from the item name: no file reads). */
    suspend fun purgeExpired(roots: List<File>) = withContext(Dispatchers.IO) {
        val now = System.currentTimeMillis()
        for (root in roots) {
            val bin = File(root, TRASH_DIR)
            bin.list()?.forEach { name ->
                if (name.endsWith(".meta") || name == ".nomedia") return@forEach
                val at = name.substringBefore('-').toLongOrNull() ?: return@forEach
                if (now - at > TRASH_MAX_AGE_MS) {
                    File(bin, name).deleteRecursively()
                    File(bin, "$name.meta").delete()
                }
            }
        }
    }

    suspend fun trashCount(roots: List<File>): Int = withContext(Dispatchers.IO) {
        roots.sumOf { root ->
            File(root, TRASH_DIR).list()?.count { !it.endsWith(".meta") && it != ".nomedia" } ?: 0
        }
    }

    suspend fun rename(file: File, newName: String): Boolean = withContext(Dispatchers.IO) {
        val n = newName.trim()
        if (n.isEmpty() || n == "." || n == ".." || '/' in n) return@withContext false
        if (n == file.name) return@withContext true
        val target = File(file.parentFile, n)
        // /sdcard is case-insensitive: allow case-only renames even though target "exists"
        val free = !target.exists() || n.equals(file.name, ignoreCase = true)
        free && file.renameTo(target)
    }

    suspend fun createFolder(parent: File, name: String): Boolean = withContext(Dispatchers.IO) {
        val n = name.trim()
        if (n.isEmpty() || n == "." || n == ".." || '/' in n) return@withContext false
        val target = File(parent, n)
        !target.exists() && target.mkdir()
    }

    private const val CHUNK_BYTES = 2L * 1024 * 1024

    /**
     * Copies or moves [paths] into [destDir], reporting progress and honoring coroutine cancellation.
     * Outcome is tallied in [stats] (readable even after cancellation).
     *
     * Pass 1 validates and, for moves, tries an instant same-volume rename.
     * Pass 2 sizes whatever still needs real copying so progress can be byte-accurate.
     * Pass 3 copies in 2 MB steps (cancel is checked between steps); a cancelled item's partial
     * copy is deleted and its source is left untouched. Items finished before cancelling stay done.
     */
    suspend fun transfer(
        ctx: Context,
        paths: List<String>,
        destDir: File,
        move: Boolean,
        stats: TransferStats,
        onProgress: (Progress) -> Unit,
    ) = withContext(Dispatchers.IO) {
        class Pending(val src: File, val target: File)

        val destCanon = destDir.canonicalFile
        stats.total = paths.size
        onProgress(Progress(0L, 0L, 0, paths.size, ctx.getString(R.string.op_preparing)))

        val pending = ArrayList<Pending>()
        for (p in paths) {
            ensureActive()
            val src = File(p)
            try {
                if (!src.exists()) { stats.failed++; continue }
                if (src.isDirectory) {
                    val s = src.canonicalFile
                    if (destCanon == s || destCanon.path.startsWith(s.path + File.separator)) {
                        stats.failed++ // can't copy/move a folder into itself
                        continue
                    }
                }
                if (move && src.parentFile?.canonicalFile == destCanon) { stats.done++; continue } // already here
                val target = uniqueTarget(destDir, src.name)
                if (move && src.renameTo(target)) stats.done++ else pending.add(Pending(src, target))
            } catch (e: IOException) {
                stats.failed++
            } catch (e: SecurityException) {
                stats.failed++
            }
        }

        val totalBytes = pending.sumOf { treeSize(it.src) }
        val tracker = ProgressTracker(stats, totalBytes, onProgress)
        tracker.report("")

        for (job in pending) {
            ensureActive()
            tracker.report(job.src.name)
            val ok = try {
                copyRec(job.src, job.target, tracker)
                true
            } catch (e: CancellationException) {
                job.target.deleteRecursively() // never leave a half-copied item behind
                throw e
            } catch (e: IOException) {
                job.target.deleteRecursively()
                false
            } catch (e: SecurityException) {
                job.target.deleteRecursively()
                false
            }
            if (ok) {
                if (move && !job.src.deleteRecursively()) stats.failed++ else stats.done++
            } else {
                stats.failed++
            }
            tracker.report(job.src.name)
        }
    }

    private fun uniqueTarget(dir: File, name: String): File {
        var f = File(dir, name)
        if (!f.exists()) return f
        val dot = name.lastIndexOf('.')
        val base = if (dot > 0) name.substring(0, dot) else name
        val ext = if (dot > 0) name.substring(dot) else ""
        var i = 1
        while (true) {
            f = File(dir, "$base ($i)$ext")
            if (!f.exists()) return f
            i++
        }
    }

    private fun CoroutineScope.treeSize(f: File): Long {
        ensureActive()
        if (!f.isDirectory) return f.length()
        var sum = 0L
        f.listFiles()?.forEach { sum += treeSize(it) }
        return sum
    }

    private fun CoroutineScope.copyRec(src: File, dst: File, t: ProgressTracker) {
        ensureActive()
        if (src.isDirectory) {
            if (!dst.mkdirs() && !dst.isDirectory) throw IOException("Cannot create ${dst.path}")
            src.listFiles()?.forEach { copyRec(it, File(dst, it.name), t) }
        } else {
            // FileChannel.transferTo = kernel-side copy (sendfile); chunked so we can report and cancel
            FileInputStream(src).channel.use { input ->
                FileOutputStream(dst).channel.use { output ->
                    val size = input.size()
                    var pos = 0L
                    while (pos < size) {
                        ensureActive()
                        val n = input.transferTo(pos, minOf(size - pos, CHUNK_BYTES), output)
                        if (n <= 0L) throw IOException("Short copy of ${src.name}")
                        pos += n
                        t.advance(n, src.name)
                    }
                }
            }
            dst.setLastModified(src.lastModified())
        }
    }

    // ---------- Details ----------

    /** Cheap, immediate part of the Details dialog (a few stat calls). */
    suspend fun describe(ctx: Context, paths: List<String>): Details = withContext(Dispatchers.IO) {
        val files = paths.map(::File)
        if (files.size == 1) {
            val f = files[0]
            val dir = f.isDirectory
            Details(
                title = f.name,
                location = f.parent,
                type = if (dir) {
                    ctx.getString(R.string.type_folder)
                } else {
                    f.extension.let {
                        if (it.isEmpty()) ctx.getString(R.string.type_file)
                        else ctx.getString(R.string.type_ext_file, it.uppercase())
                    }
                },
                modified = f.lastModified(),
                single = true,
                isDir = dir,
                fileSize = if (dir) null else f.length(),
                selectedFiles = if (dir) 0 else 1,
                selectedFolders = if (dir) 1 else 0,
            )
        } else {
            var nFiles = 0
            var nFolders = 0
            for (f in files) {
                ensureActive()
                if (f.isDirectory) nFolders++ else nFiles++
            }
            Details(
                title = ctx.resources.getQuantityString(R.plurals.n_items, files.size, files.size),
                location = files.mapNotNullTo(HashSet()) { it.parent }.singleOrNull(),
                type = null,
                modified = null,
                single = false,
                isDir = false,
                fileSize = null,
                selectedFiles = nFiles,
                selectedFolders = nFolders,
            )
        }
    }

    /**
     * Walks the selection (iteratively, symlinks not followed so loops are impossible), one stat per entry.
     * Calls [onUpdate] ~7x/s so the dialog can show running totals while big folders are counted.
     */
    suspend fun scan(paths: List<String>, onUpdate: (ScanResult) -> Unit): ScanResult =
        withContext(Dispatchers.IO) {
            var bytes = 0L
            var files = 0
            var folders = 0
            var direct = 0
            var last = System.nanoTime()
            fun snapshot(done: Boolean) = ScanResult(bytes, files, folders, direct, done)

            for (p in paths) {
                val top = File(p)
                if (!top.isDirectory) {
                    bytes += top.length()
                    continue
                }
                val dirs = ArrayDeque<Path>()
                dirs.add(top.toPath())
                var isTop = true
                while (dirs.isNotEmpty()) {
                    val dir = dirs.removeLast()
                    val countDirect = isTop
                    isTop = false
                    try {
                        Files.newDirectoryStream(dir).use { stream ->
                            for (e in stream) {
                                ensureActive()
                                val a = try {
                                    Files.readAttributes(e, BasicFileAttributes::class.java, LinkOption.NOFOLLOW_LINKS)
                                } catch (x: IOException) {
                                    continue
                                }
                                if (countDirect) direct++
                                if (a.isDirectory) {
                                    folders++
                                    dirs.add(e)
                                } else {
                                    files++
                                    bytes += a.size()
                                }
                                val now = System.nanoTime()
                                if (now - last > 150_000_000L) {
                                    last = now
                                    onUpdate(snapshot(false))
                                }
                            }
                        }
                    } catch (x: IOException) {
                        // unreadable folder: skip it
                    }
                }
            }
            snapshot(true)
        }
}

private class ProgressTracker(
    private val stats: TransferStats,
    private val totalBytes: Long,
    private val emit: (Progress) -> Unit,
) {
    private var doneBytes = 0L
    private var lastEmit = 0L

    /** Called per copied chunk; emits at most ~10 updates/second. */
    fun advance(n: Long, name: String) {
        doneBytes += n
        val now = System.nanoTime()
        if (now - lastEmit > 100_000_000L) {
            lastEmit = now
            report(name)
        }
    }

    fun report(name: String) {
        emit(Progress(doneBytes, totalBytes, stats.done + stats.failed, stats.total, name))
    }
}

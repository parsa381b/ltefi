package com.litefiles.app.ui

import android.app.Application
import android.content.Context
import android.os.Environment
import androidx.compose.runtime.Immutable
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.litefiles.app.R
import com.litefiles.app.data.Category
import com.litefiles.app.data.Details
import com.litefiles.app.data.FileItem
import com.litefiles.app.data.FileRepository
import com.litefiles.app.data.Progress
import com.litefiles.app.data.Shortcut
import com.litefiles.app.data.SortBy
import com.litefiles.app.data.TransferStats
import com.litefiles.app.data.Volume
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.io.IOException

class Clipboard(val paths: List<String>, val move: Boolean)

/** A running operation shown in the progress dialog. [progress] is null until the first report. */
@Immutable
data class Op(
    val label: String,
    val cancellable: Boolean = false,
    val cancelling: Boolean = false,
    val progress: Progress? = null,
)

@Immutable
data class BrowserState(
    val hasAccess: Boolean = false,
    val volumes: List<Volume> = emptyList(),
    val shortcuts: List<Shortcut> = emptyList(),
    /** null (together with [category] null) = home screen */
    val dir: File? = null,
    /** non-null = showing a category (Images, Videos, ...) instead of a folder */
    val category: Category? = null,
    /** true = showing the Recycle bin */
    val trash: Boolean = false,
    /** file to scroll to and select once its folder has loaded ("Show in folder") */
    val reveal: String? = null,
    /** what the list shows (already filtered by [query]) */
    val items: List<FileItem> = emptyList(),
    val loading: Boolean = false,
    val error: String? = null,
    val sortBy: SortBy = SortBy.NAME,
    val ascending: Boolean = true,
    val showHidden: Boolean = false,
    /** null = search closed */
    val query: String? = null,
    val selected: Set<String> = emptySet(),
    val clipboard: Clipboard? = null,
    val viewGrid: Boolean = false,
    val trashCount: Int = 0,
    val op: Op? = null,
    val details: Details? = null,
    val message: String? = null,
)

class BrowserViewModel(private val app: Application) : AndroidViewModel(app) {

    private val prefs = app.getSharedPreferences("lite_files", Context.MODE_PRIVATE)

    /** App strings resolved here (the ViewModel has no composable context of its own). */
    private fun str(id: Int, vararg args: Any): String = app.getString(id, *args)

    private fun qty(id: Int, n: Int, vararg args: Any): String =
        app.resources.getQuantityString(id, n, *args)

    private val _state = MutableStateFlow(
        BrowserState(
            hasAccess = Environment.isExternalStorageManager(),
            viewGrid = prefs.getBoolean("grid", false),
        ),
    )
    val state: StateFlow<BrowserState> = _state.asStateFlow()

    /** Full sorted listing of the current folder; [BrowserState.items] is this filtered by the query. */
    private var all: List<FileItem> = emptyList()
    private var loadJob: Job? = null
    private var filterJob: Job? = null

    /** Folder sort saved while a category is open (categories default to newest-first). */
    private var savedSort: Pair<SortBy, Boolean>? = null

    private var opJob: Job? = null
    private var detailsJob: Job? = null

    /** path -> first visible index, so going "up" returns to where you were. */
    val scrollPositions = HashMap<String, Int>()

    // ---------- Lifecycle / permission ----------

    fun onResume() {
        val access = Environment.isExternalStorageManager()
        viewModelScope.launch {
            val (vols, shortcuts, binCount) = withContext(Dispatchers.IO) {
                if (!access) {
                    Triple(emptyList<Volume>(), emptyList<Shortcut>(), 0)
                } else {
                    val v = FileRepository.volumes(app)
                    val roots = v.map { it.root }
                    FileRepository.purgeExpired(roots) // bin items older than 30 days
                    Triple(v, FileRepository.shortcuts(v.firstOrNull { it.primary }?.root), FileRepository.trashCount(roots))
                }
            }
            _state.update { it.copy(hasAccess = access, volumes = vols, shortcuts = shortcuts, trashCount = binCount) }
            val s = _state.value
            if (access && (s.dir != null || s.category != null || s.trash)) refresh()
        }
    }

    // ---------- Navigation ----------

    fun open(dir: File?) {
        loadJob?.cancel()
        filterJob?.cancel()
        all = emptyList()
        val restore = savedSort // leaving a category: bring back the folder sort order
        savedSort = null
        _state.update {
            it.copy(
                dir = dir,
                category = null,
                trash = false,
                reveal = null,
                items = emptyList(),
                selected = emptySet(),
                query = null,
                error = null,
                loading = dir != null,
                sortBy = restore?.first ?: it.sortBy,
                ascending = restore?.second ?: it.ascending,
            )
        }
        if (dir != null) load(dir)
    }

    fun openCategory(cat: Category) {
        loadJob?.cancel()
        filterJob?.cancel()
        all = emptyList()
        if (savedSort == null) savedSort = _state.value.sortBy to _state.value.ascending
        _state.update {
            it.copy(
                dir = null,
                category = cat,
                trash = false,
                reveal = null,
                items = emptyList(),
                selected = emptySet(),
                query = null,
                error = null,
                loading = true,
                sortBy = SortBy.DATE,
                ascending = false,
            )
        }
        loadCategory(cat)
    }

    /** From a category: open the file's folder, scroll to it and select it. */
    fun showInFolder(path: String) {
        val file = File(path)
        val parent = file.parentFile ?: return
        // a dot-file would be filtered out of the listing, so make it visible first
        if (file.name.startsWith('.') && !_state.value.showHidden) {
            _state.update { it.copy(showHidden = true) }
        }
        open(parent)
        _state.update { it.copy(reveal = path) }
    }

    /** Called by the list once it has scrolled to the revealed file (or found it missing). */
    fun finishReveal(path: String, found: Boolean) {
        _state.update {
            if (it.reveal != path) it
            else it.copy(reveal = null, selected = if (found) setOf(path) else it.selected)
        }
    }

    fun openTrash() {
        loadJob?.cancel()
        filterJob?.cancel()
        all = emptyList()
        if (savedSort == null) savedSort = _state.value.sortBy to _state.value.ascending
        _state.update {
            it.copy(
                dir = null,
                category = null,
                trash = true,
                reveal = null,
                items = emptyList(),
                selected = emptySet(),
                query = null,
                error = null,
                loading = true,
                sortBy = SortBy.DATE, // newest deletions first
                ascending = false,
            )
        }
        loadTrash()
    }

    fun refresh() {
        val s = _state.value
        val cat = s.category
        val dir = s.dir
        if (s.trash) loadTrash() else if (cat != null) loadCategory(cat) else if (dir != null) load(dir)
    }

    /** Handles the system Back button. Returns false when there is nothing left to go back to. */
    fun goUp(): Boolean {
        val s = _state.value
        val dir = s.dir
        when {
            s.selected.isNotEmpty() -> clearSelection()
            s.query != null -> setQuery(null)
            s.category != null || s.trash -> open(null)
            dir == null -> return false
            else -> {
                val parent = dir.parentFile
                val atVolumeRoot = s.volumes.any { it.root.path == dir.path }
                open(if (atVolumeRoot || parent == null) null else parent)
            }
        }
        return true
    }

    private fun load(dir: File) {
        loadJob?.cancel()
        loadJob = viewModelScope.launch {
            _state.update { it.copy(loading = true, error = null) }
            val s = _state.value
            try {
                val sorted = FileRepository.list(dir, s.showHidden, s.sortBy, s.ascending)
                all = sorted
                val visible = filter(sorted, _state.value.query)
                _state.update { it.copy(items = visible, loading = false) }
            } catch (e: IOException) {
                all = emptyList()
                _state.update { it.copy(items = emptyList(), loading = false, error = str(R.string.err_open_folder)) }
            } catch (e: SecurityException) {
                all = emptyList()
                _state.update { it.copy(items = emptyList(), loading = false, error = str(R.string.err_permission)) }
            }
        }
    }

    private fun loadCategory(cat: Category) {
        loadJob?.cancel()
        loadJob = viewModelScope.launch {
            _state.update { it.copy(loading = true, error = null) }
            val s = _state.value
            try {
                val list = FileRepository.category(app, cat, s.sortBy, s.ascending)
                all = list
                val visible = filter(list, _state.value.query)
                _state.update { it.copy(items = visible, loading = false) }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                all = emptyList()
                _state.update {
                    it.copy(
                        items = emptyList(),
                        loading = false,
                        error = str(R.string.err_load_category, str(cat.labelRes)),
                    )
                }
            }
        }
    }

    private fun loadTrash() {
        loadJob?.cancel()
        loadJob = viewModelScope.launch {
            _state.update { it.copy(loading = true, error = null) }
            val s = _state.value
            try {
                val list = FileRepository.trashItems(s.volumes.map { it.root }, s.sortBy, s.ascending)
                all = list
                val visible = filter(list, _state.value.query)
                _state.update { it.copy(items = visible, loading = false) }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                all = emptyList()
                _state.update { it.copy(items = emptyList(), loading = false, error = str(R.string.err_open_trash)) }
            }
        }
    }

    // ---------- Search / sort / hidden ----------

    private suspend fun filter(list: List<FileItem>, q: String?): List<FileItem> {
        if (q.isNullOrBlank()) return list
        val needle = q.trim()
        return withContext(Dispatchers.Default) { list.filter { it.name.contains(needle, ignoreCase = true) } }
    }

    fun setQuery(q: String?) {
        _state.update { it.copy(query = q) }
        filterJob?.cancel()
        filterJob = viewModelScope.launch {
            val result = filter(all, q)
            _state.update { it.copy(items = result) }
        }
    }

    fun setSort(by: SortBy) {
        _state.update {
            it.copy(sortBy = by, ascending = if (it.sortBy == by) !it.ascending else true)
        }
        filterJob?.cancel()
        filterJob = viewModelScope.launch {
            val s = _state.value
            val sorted = withContext(Dispatchers.Default) { FileRepository.sort(all, s.sortBy, s.ascending) }
            all = sorted
            val visible = filter(sorted, s.query)
            _state.update { it.copy(items = visible) }
        }
    }

    fun toggleHidden() {
        _state.update { it.copy(showHidden = !it.showHidden) }
        refresh()
    }

    // ---------- Selection ----------

    fun toggleSelect(path: String) = _state.update {
        it.copy(selected = if (path in it.selected) it.selected - path else it.selected + path)
    }

    fun selectAll() = _state.update { s -> s.copy(selected = s.items.mapTo(HashSet(s.items.size * 2)) { it.path }) }

    fun clearSelection() = _state.update { it.copy(selected = emptySet()) }

    // ---------- Operations ----------

    fun copyOrCut(move: Boolean) = _state.update {
        it.copy(clipboard = Clipboard(it.selected.toList(), move), selected = emptySet())
    }

    fun cancelPaste() = _state.update { it.copy(clipboard = null) }

    fun paste() {
        val dir = _state.value.dir ?: return
        val cb = _state.value.clipboard ?: return
        val stats = TransferStats()
        opJob = viewModelScope.launch {
            _state.update {
                it.copy(
                    op = Op(if (cb.move) str(R.string.op_moving) else str(R.string.op_copying), cancellable = true),
                )
            }
            val msg = try {
                FileRepository.transfer(app, cb.paths, dir, cb.move, stats) { p ->
                    _state.update { s -> if (s.op == null) s else s.copy(op = s.op.copy(progress = p)) }
                }
                if (stats.failed == 0) str(R.string.msg_done)
                else qty(R.plurals.msg_failed_items, stats.failed, stats.failed)
            } catch (e: CancellationException) {
                // Deliberately not rethrown: we still have to close the dialog and refresh.
                // Finished items stay done, the interrupted item's partial copy was already removed.
                str(R.string.msg_cancelled, stats.done, stats.total)
            } catch (e: Exception) {
                str(R.string.msg_failed, e.message ?: str(R.string.msg_unknown_error))
            }
            _state.update {
                it.copy(
                    op = null,
                    message = msg,
                    selected = emptySet(),
                    clipboard = if (cb.move) null else it.clipboard,
                )
            }
            refresh()
        }
    }

    fun cancelOp() {
        _state.update { s -> if (s.op == null) s else s.copy(op = s.op.copy(cancelling = true)) }
        opJob?.cancel()
    }

    fun showDetails() {
        val paths = _state.value.selected.toList()
        if (paths.isEmpty()) return
        detailsJob?.cancel()
        detailsJob = viewModelScope.launch {
            val base = FileRepository.describe(app, paths)
            _state.update { it.copy(details = base) }
            if (base.single && !base.isDir) return@launch // a single file has nothing to scan
            val result = FileRepository.scan(paths) { r ->
                _state.update { s -> s.details?.let { d -> s.copy(details = d.copy(scan = r)) } ?: s }
            }
            _state.update { s -> s.details?.let { d -> s.copy(details = d.copy(scan = result)) } ?: s }
        }
    }

    fun closeDetails() {
        detailsJob?.cancel()
        _state.update { it.copy(details = null) }
    }

    private fun roots(): List<File> = _state.value.volumes.map { it.root }

    /** Delete = move to the Recycle bin (items already inside a bin are removed for good). */
    fun delete() {
        val paths = _state.value.selected.toList()
        val roots = roots()
        runOp(str(R.string.op_deleting)) {
            val r = FileRepository.moveToTrash(paths, roots)
            when {
                r.failed > 0 -> qty(R.plurals.msg_delete_failed, r.failed, r.failed)
                r.deleted == 0 -> qty(R.plurals.msg_moved_to_trash, r.moved, r.moved)
                else -> qty(R.plurals.msg_deleted, r.moved + r.deleted, r.moved + r.deleted)
            }
        }
    }

    fun restore() {
        val paths = _state.value.selected.toList()
        runOp(str(R.string.op_restoring)) {
            val failed = FileRepository.restore(paths)
            if (failed == 0) qty(R.plurals.msg_restored, paths.size, paths.size)
            else qty(R.plurals.msg_restore_failed, failed, failed)
        }
    }

    fun deleteForever() {
        val paths = _state.value.selected.toList()
        runOp(str(R.string.op_deleting)) {
            val failed = FileRepository.deleteForever(paths)
            if (failed == 0) qty(R.plurals.msg_deleted_permanently, paths.size, paths.size)
            else qty(R.plurals.msg_delete_failed, failed, failed)
        }
    }

    fun emptyBin() {
        val roots = roots()
        runOp(str(R.string.op_emptying_trash)) {
            FileRepository.emptyBin(roots)
            str(R.string.msg_bin_emptied)
        }
    }

    fun toggleView() {
        val grid = !_state.value.viewGrid
        prefs.edit().putBoolean("grid", grid).apply()
        _state.update { it.copy(viewGrid = grid) }
    }

    private fun refreshTrashCount() {
        val roots = roots()
        viewModelScope.launch {
            val n = withContext(Dispatchers.IO) { FileRepository.trashCount(roots) }
            _state.update { it.copy(trashCount = n) }
        }
    }

    fun rename(path: String, newName: String) = runOp(str(R.string.op_renaming)) {
        if (FileRepository.rename(File(path), newName)) str(R.string.msg_renamed)
        else str(R.string.msg_rename_failed)
    }

    fun newFolder(name: String) {
        val dir = _state.value.dir ?: return
        runOp(str(R.string.op_creating_folder)) {
            if (FileRepository.createFolder(dir, name)) str(R.string.msg_folder_created)
            else str(R.string.msg_folder_failed)
        }
    }

    fun messageShown() = _state.update { it.copy(message = null) }

    private fun runOp(label: String, block: suspend () -> String) {
        viewModelScope.launch {
            _state.update { it.copy(op = Op(label)) }
            val msg = try {
                block()
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                str(R.string.msg_failed, e.message ?: str(R.string.msg_unknown_error))
            }
            _state.update { it.copy(op = null, message = msg, selected = emptySet()) }
            refresh()
            refreshTrashCount()
        }
    }
}

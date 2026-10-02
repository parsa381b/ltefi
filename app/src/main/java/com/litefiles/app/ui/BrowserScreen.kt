package com.litefiles.app.ui

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items as gridItems
import androidx.compose.foundation.lazy.grid.rememberLazyGridState
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.ContentCut
import androidx.compose.material.icons.filled.CreateNewFolder
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.FolderOpen
import androidx.compose.material.icons.filled.DeleteForever
import androidx.compose.material.icons.filled.GridView
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Restore
import androidx.compose.material.icons.filled.ViewList
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.SelectAll
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Share
import androidx.compose.material.icons.filled.SwapVert
import androidx.compose.material3.Button
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TextField
import androidx.compose.material3.TextFieldDefaults
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.litefiles.app.R
import com.litefiles.app.data.FileItem
import com.litefiles.app.data.FileRepository
import com.litefiles.app.data.SortBy
import com.litefiles.app.util.openFile
import com.litefiles.app.util.shareFiles
import com.litefiles.app.viewer.viewerSiblings
import java.io.File
import java.text.DateFormat

private sealed interface Prompt {
    data object NewFolder : Prompt
    data class Rename(val path: String, val name: String) : Prompt
    data object Delete : Prompt
    data object EmptyBin : Prompt
}

@Composable
fun BrowserScreen(state: BrowserState, dir: File?, vm: BrowserViewModel, snackbar: SnackbarHostState) {
    val ctx = LocalContext.current
    var prompt by remember { mutableStateOf<Prompt?>(null) }

    // Back: clear selection -> close search -> go to parent -> home
    BackHandler { vm.goUp() }

    Scaffold(
        topBar = {
            BrowserTopBar(
                state, dir, vm,
                onNewFolder = { prompt = Prompt.NewFolder },
                onEmptyBin = { prompt = Prompt.EmptyBin },
            )
        },
        bottomBar = {
            val cb = state.clipboard
            when {
                state.selected.isNotEmpty() && state.trash -> Surface(tonalElevation = 3.dp) {
                    Row(
                        Modifier.fillMaxWidth().navigationBarsPadding().padding(vertical = 6.dp),
                        horizontalArrangement = Arrangement.SpaceEvenly,
                    ) {
                        BarAction(Icons.Filled.Restore, stringResource(R.string.action_restore)) { vm.restore() }
                        BarAction(Icons.Filled.DeleteForever, stringResource(R.string.action_delete)) {
                            prompt = Prompt.Delete
                        }
                    }
                }
                state.selected.isNotEmpty() -> Surface(tonalElevation = 3.dp) {
                    Row(
                        Modifier.fillMaxWidth().navigationBarsPadding().padding(vertical = 6.dp),
                        horizontalArrangement = Arrangement.SpaceEvenly,
                    ) {
                        BarAction(Icons.Filled.ContentCut, stringResource(R.string.action_move)) {
                            vm.copyOrCut(move = true)
                        }
                        BarAction(Icons.Filled.ContentCopy, stringResource(R.string.action_copy)) {
                            vm.copyOrCut(move = false)
                        }
                        BarAction(Icons.Filled.Share, stringResource(R.string.action_share)) {
                            shareFiles(ctx, state.selected.toList())
                        }
                        BarAction(Icons.Filled.Delete, stringResource(R.string.action_delete)) {
                            prompt = Prompt.Delete
                        }
                        Box {
                            var menuOpen by remember { mutableStateOf(false) }
                            BarAction(Icons.Filled.MoreVert, stringResource(R.string.action_more)) { menuOpen = true }
                            DropdownMenu(expanded = menuOpen, onDismissRequest = { menuOpen = false }) {
                                DropdownMenuItem(
                                    text = { Text(stringResource(R.string.action_rename)) },
                                    leadingIcon = { Icon(Icons.Filled.Edit, null) },
                                    enabled = state.selected.size == 1,
                                    onClick = {
                                        menuOpen = false
                                        val p = state.selected.first()
                                        prompt = Prompt.Rename(p, File(p).name)
                                    },
                                )
                                if (state.category != null) DropdownMenuItem(
                                    text = { Text(stringResource(R.string.show_in_folder)) },
                                    leadingIcon = { Icon(Icons.Filled.FolderOpen, null) },
                                    enabled = state.selected.size == 1,
                                    onClick = { menuOpen = false; vm.showInFolder(state.selected.first()) },
                                )
                                DropdownMenuItem(
                                    text = { Text(stringResource(R.string.action_details)) },
                                    leadingIcon = { Icon(Icons.Filled.Info, null) },
                                    onClick = { menuOpen = false; vm.showDetails() },
                                )
                            }
                        }
                    }
                }
                cb != null -> Surface(tonalElevation = 3.dp) {
                    Row(
                        Modifier.fillMaxWidth().navigationBarsPadding().padding(12.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        val verb = if (cb.move) stringResource(R.string.verb_move) else stringResource(R.string.verb_copy)
                        val n = cb.paths.size
                        Text(
                            if (dir == null) pluralStringResource(R.plurals.paste_need_folder, n, n, verb)
                            else pluralStringResource(R.plurals.paste_count, n, n, verb),
                            Modifier.weight(1f),
                            style = MaterialTheme.typography.bodyMedium,
                        )
                        TextButton(onClick = vm::cancelPaste) { Text(stringResource(R.string.action_cancel)) }
                        if (dir != null) {
                            Button(onClick = vm::paste) {
                                Text(
                                    if (cb.move) stringResource(R.string.move_here)
                                    else stringResource(R.string.copy_here),
                                )
                            }
                        }
                    }
                }
            }
        },
        snackbarHost = { SnackbarHost(snackbar) },
    ) { pad ->
        FileList(state, dir, vm, Modifier.padding(pad))
    }

    when (val p = prompt) {
        Prompt.NewFolder -> NameDialog(
            title = stringResource(R.string.dialog_new_folder),
            initial = "",
            confirmLabel = stringResource(R.string.action_create),
            onDismiss = { prompt = null },
        ) {
            vm.newFolder(it); prompt = null
        }
        is Prompt.Rename -> NameDialog(
            title = stringResource(R.string.dialog_rename),
            initial = p.name,
            confirmLabel = stringResource(R.string.action_rename),
            onDismiss = { prompt = null },
        ) {
            vm.rename(p.path, it); prompt = null
        }
        Prompt.Delete -> {
            val n = state.selected.size
            val permanent = state.trash || state.selected.any { FileRepository.isInTrash(it) }
            ConfirmDialog(
                title = if (permanent) {
                    pluralStringResource(R.plurals.confirm_delete_permanent_title, n, n)
                } else {
                    pluralStringResource(R.plurals.confirm_trash_title, n, n)
                },
                message = stringResource(
                    if (permanent) R.string.confirm_delete_permanent_msg else R.string.confirm_trash_msg,
                ),
                confirmLabel = stringResource(if (permanent) R.string.action_delete else R.string.action_move),
                onDismiss = { prompt = null },
            ) {
                if (state.trash) vm.deleteForever() else vm.delete()
                prompt = null
            }
        }
        Prompt.EmptyBin -> ConfirmDialog(
            title = stringResource(R.string.confirm_empty_trash_title),
            message = stringResource(R.string.confirm_empty_trash_msg),
            confirmLabel = stringResource(R.string.action_empty),
            onDismiss = { prompt = null },
        ) { vm.emptyBin(); prompt = null }
        null -> Unit
    }
}

@Composable
private fun FileList(state: BrowserState, dir: File?, vm: BrowserViewModel, modifier: Modifier) {
    val ctx = LocalContext.current
    // scroll-state / scroll-restore key: "trash", "cat:<NAME>" for a category, or the folder path
    val dirPath = when {
        state.trash -> "trash"
        state.category != null -> "cat:${state.category.name}"
        else -> dir?.path ?: ""
    }

    // Fresh scroll state per folder; restored to the saved position when coming back "up".
    key(dirPath) {
        val listState = rememberLazyListState()
        val gridState = rememberLazyGridState()
        val grid = state.viewGrid
        var pending by remember { mutableIntStateOf(vm.scrollPositions.remove(dirPath) ?: 0) }
        val hasItems = state.items.isNotEmpty()
        LaunchedEffect(hasItems) {
            if (hasItems && pending > 0) {
                val target = pending.coerceAtMost(state.items.lastIndex)
                if (grid) gridState.scrollToItem(target) else listState.scrollToItem(target)
                pending = 0
            }
        }
        // keep the reading position when switching between list and grid
        LaunchedEffect(grid) {
            if (grid) gridState.scrollToItem(listState.firstVisibleItemIndex)
            else listState.scrollToItem(gridState.firstVisibleItemIndex)
        }
        // "Show in folder": once the folder has loaded, scroll to the file and select it
        val reveal = state.reveal
        LaunchedEffect(reveal, hasItems) {
            if (reveal != null && hasItems) {
                val idx = state.items.indexOfFirst { it.path == reveal }
                if (idx >= 0) {
                    if (grid) gridState.scrollToItem(idx) else listState.scrollToItem(idx)
                }
                vm.finishReveal(reveal, found = idx >= 0)
            }
        }

        val dateFormat = remember { DateFormat.getDateTimeInstance(DateFormat.MEDIUM, DateFormat.SHORT) }
        val stateRef = rememberUpdatedState(state)

        // Stable lambdas: rows don't recompose just because unrelated state changed.
        val onClick = remember(dirPath) {
            { item: FileItem ->
                val s = stateRef.value
                when {
                    s.selected.isNotEmpty() || s.trash -> vm.toggleSelect(item.path) // bin items can't be opened
                    item.isDir -> {
                        vm.scrollPositions[dirPath] =
                            if (s.viewGrid) gridState.firstVisibleItemIndex else listState.firstVisibleItemIndex
                        vm.open(File(item.path))
                    }
                    else -> openFile(ctx, item.path, viewerSiblings(s.items, item))
                }
            }
        }
        val onLongClick = remember(dirPath) { { item: FileItem -> vm.toggleSelect(item.path) } }

        Box(modifier.fillMaxSize()) {
            if (grid) {
                LazyVerticalGrid(
                    columns = GridCells.Adaptive(104.dp),
                    state = gridState,
                    modifier = Modifier.fillMaxSize(),
                    contentPadding = PaddingValues(8.dp),
                ) {
                    gridItems(state.items, key = { it.path }, contentType = { it.isDir }) { item ->
                        FileTile(
                            item = item,
                            selected = item.path in state.selected,
                            selecting = state.selected.isNotEmpty(),
                            onClick = onClick,
                            onLongClick = onLongClick,
                        )
                    }
                }
            } else {
                LazyColumn(Modifier.fillMaxSize(), state = listState) {
                    items(state.items, key = { it.path }, contentType = { it.isDir }) { item ->
                        FileRow(
                            item = item,
                            selected = item.path in state.selected,
                            selecting = state.selected.isNotEmpty(),
                            showFolder = state.category != null,
                            deleted = state.trash,
                            dateFormat = dateFormat,
                            onClick = onClick,
                            onLongClick = onLongClick,
                        )
                    }
                }
            }
            if (state.loading) LinearProgressIndicator(Modifier.fillMaxWidth().align(Alignment.TopCenter))
            if (!state.loading && state.items.isEmpty()) {
                Text(
                    state.error ?: when {
                        !state.query.isNullOrBlank() -> stringResource(R.string.empty_results)
                        state.trash -> stringResource(R.string.empty_trash)
                        state.category != null -> stringResource(R.string.empty_no_files)
                        else -> stringResource(R.string.empty_folder)
                    },
                    Modifier.align(Alignment.Center),
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun BrowserTopBar(
    state: BrowserState,
    dir: File?,
    vm: BrowserViewModel,
    onNewFolder: () -> Unit,
    onEmptyBin: () -> Unit,
) {
    val n = state.selected.size
    var sortOpen by remember { mutableStateOf(false) }
    var moreOpen by remember { mutableStateOf(false) }
    var settingsOpen by remember { mutableStateOf(false) }

    when {
        state.query != null -> SearchTopBar(vm)

        n > 0 -> TopAppBar(
            title = { Text(pluralStringResource(R.plurals.n_selected, n, n)) },
            navigationIcon = {
                IconButton(onClick = vm::clearSelection) {
                    Icon(Icons.Filled.Close, stringResource(R.string.action_cancel_selection))
                }
            },
            actions = {
                IconButton(onClick = vm::selectAll) {
                    Icon(Icons.Filled.SelectAll, stringResource(R.string.action_select_all))
                }
            },
        )

        else -> {
            val category = state.category
            val title = when {
                state.trash -> stringResource(R.string.recycle_bin)
                category != null -> stringResource(category.labelRes)
                dir != null -> state.volumes.firstOrNull { it.root.path == dir.path }?.name ?: dir.name
                else -> ""
            }
            TopAppBar(
                title = {
                    Column {
                        Text(title, maxLines = 1, overflow = TextOverflow.Ellipsis)
                        if (dir != null) Text(
                            dir.path,
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                    }
                },
                navigationIcon = {
                    IconButton(onClick = { vm.goUp() }) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, stringResource(R.string.action_back))
                    }
                },
                actions = {
                    IconButton(onClick = { vm.setQuery("") }) {
                        Icon(Icons.Filled.Search, stringResource(R.string.action_search))
                    }
                    IconButton(onClick = vm::toggleView) {
                        Icon(
                            if (state.viewGrid) Icons.Filled.ViewList else Icons.Filled.GridView,
                            stringResource(if (state.viewGrid) R.string.list_view else R.string.grid_view),
                        )
                    }
                    Box {
                        IconButton(onClick = { sortOpen = true }) {
                            Icon(Icons.Filled.SwapVert, stringResource(R.string.action_sort))
                        }
                        DropdownMenu(expanded = sortOpen, onDismissRequest = { sortOpen = false }) {
                            SortBy.entries.forEach { by ->
                                val active = state.sortBy == by
                                val arrow = if (active) (if (state.ascending) "  ↑" else "  ↓") else ""
                                DropdownMenuItem(
                                    text = { Text(stringResource(by.labelRes) + arrow) },
                                    leadingIcon = { if (active) Icon(Icons.Filled.Check, null) },
                                    onClick = { vm.setSort(by); sortOpen = false },
                                )
                            }
                        }
                    }
                    Box {
                        IconButton(onClick = { moreOpen = true }) {
                            Icon(Icons.Filled.MoreVert, stringResource(R.string.action_more))
                        }
                        DropdownMenu(expanded = moreOpen, onDismissRequest = { moreOpen = false }) {
                            if (dir != null) DropdownMenuItem(
                                text = { Text(stringResource(R.string.new_folder)) },
                                leadingIcon = { Icon(Icons.Filled.CreateNewFolder, null) },
                                onClick = { moreOpen = false; onNewFolder() },
                            )
                            if (dir != null) DropdownMenuItem(
                                text = {
                                    Text(
                                        stringResource(
                                            if (state.showHidden) R.string.hide_hidden else R.string.show_hidden,
                                        )
                                    )
                                },
                                onClick = { moreOpen = false; vm.toggleHidden() },
                            )
                            if (state.trash) DropdownMenuItem(
                                text = { Text(stringResource(R.string.empty_recycle_bin)) },
                                leadingIcon = { Icon(Icons.Filled.DeleteForever, null) },
                                onClick = { moreOpen = false; onEmptyBin() },
                            )
                            DropdownMenuItem(
                                text = { Text(stringResource(R.string.action_refresh)) },
                                leadingIcon = { Icon(Icons.Filled.Refresh, null) },
                                onClick = { moreOpen = false; vm.refresh() },
                            )
                            DropdownMenuItem(
                                text = { Text(stringResource(R.string.action_settings)) },
                                leadingIcon = { Icon(Icons.Filled.Settings, null) },
                                onClick = { moreOpen = false; settingsOpen = true },
                            )
                        }
                    }
                },
            )
        }
    }

    if (settingsOpen) SettingsDialog(onDismiss = { settingsOpen = false })
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun SearchTopBar(vm: BrowserViewModel) {
    var text by rememberSaveable { mutableStateOf("") }
    val focus = remember { FocusRequester() }
    LaunchedEffect(Unit) { focus.requestFocus() }
    TopAppBar(
        title = {
            TextField(
                value = text,
                onValueChange = { text = it; vm.setQuery(it) },
                placeholder = { Text(stringResource(R.string.search_in_folder)) },
                singleLine = true,
                colors = TextFieldDefaults.colors(
                    focusedContainerColor = Color.Transparent,
                    unfocusedContainerColor = Color.Transparent,
                    focusedIndicatorColor = Color.Transparent,
                    unfocusedIndicatorColor = Color.Transparent,
                ),
                modifier = Modifier.fillMaxWidth().focusRequester(focus),
            )
        },
        navigationIcon = {
            IconButton(onClick = { vm.setQuery(null) }) {
                Icon(Icons.AutoMirrored.Filled.ArrowBack, stringResource(R.string.close_search))
            }
        },
    )
}

@Composable
private fun BarAction(icon: ImageVector, label: String, enabled: Boolean = true, onClick: () -> Unit) {
    Column(
        Modifier
            .clip(RoundedCornerShape(12.dp))
            .clickable(enabled = enabled, onClick = onClick)
            .padding(horizontal = 14.dp, vertical = 6.dp)
            .alpha(if (enabled) 1f else 0.38f),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Icon(icon, contentDescription = null)
        Text(label, style = MaterialTheme.typography.labelSmall)
    }
}

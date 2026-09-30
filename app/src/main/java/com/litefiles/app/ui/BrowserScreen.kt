package com.litefiles.app.ui

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
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
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.SelectAll
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
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.litefiles.app.data.FileItem
import com.litefiles.app.data.SortBy
import com.litefiles.app.util.openFile
import com.litefiles.app.util.shareFiles
import java.io.File
import java.text.DateFormat

private sealed interface Prompt {
    data object NewFolder : Prompt
    data class Rename(val path: String, val name: String) : Prompt
    data object Delete : Prompt
}

@Composable
fun BrowserScreen(state: BrowserState, dir: File?, vm: BrowserViewModel, snackbar: SnackbarHostState) {
    val ctx = LocalContext.current
    var prompt by remember { mutableStateOf<Prompt?>(null) }

    // Back: clear selection -> close search -> go to parent -> home
    BackHandler { vm.goUp() }

    Scaffold(
        topBar = { BrowserTopBar(state, dir, vm, onNewFolder = { prompt = Prompt.NewFolder }) },
        bottomBar = {
            val cb = state.clipboard
            when {
                state.selected.isNotEmpty() -> Surface(tonalElevation = 3.dp) {
                    Row(
                        Modifier.fillMaxWidth().navigationBarsPadding().padding(vertical = 6.dp),
                        horizontalArrangement = Arrangement.SpaceEvenly,
                    ) {
                        BarAction(Icons.Filled.ContentCut, "Move") { vm.copyOrCut(move = true) }
                        BarAction(Icons.Filled.ContentCopy, "Copy") { vm.copyOrCut(move = false) }
                        BarAction(Icons.Filled.Share, "Share") { shareFiles(ctx, state.selected.toList()) }
                        BarAction(Icons.Filled.Delete, "Delete") { prompt = Prompt.Delete }
                        Box {
                            var menuOpen by remember { mutableStateOf(false) }
                            BarAction(Icons.Filled.MoreVert, "More") { menuOpen = true }
                            DropdownMenu(expanded = menuOpen, onDismissRequest = { menuOpen = false }) {
                                DropdownMenuItem(
                                    text = { Text("Rename") },
                                    leadingIcon = { Icon(Icons.Filled.Edit, null) },
                                    enabled = state.selected.size == 1,
                                    onClick = {
                                        menuOpen = false
                                        val p = state.selected.first()
                                        prompt = Prompt.Rename(p, File(p).name)
                                    },
                                )
                                DropdownMenuItem(
                                    text = { Text("Details") },
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
                        val verb = if (cb.move) "move" else "copy"
                        Text(
                            if (dir == null) "Open a folder to $verb ${cb.paths.size} item(s)"
                            else "${cb.paths.size} item(s) to $verb",
                            Modifier.weight(1f),
                            style = MaterialTheme.typography.bodyMedium,
                        )
                        TextButton(onClick = vm::cancelPaste) { Text("Cancel") }
                        if (dir != null) {
                            Button(onClick = vm::paste) { Text(if (cb.move) "Move here" else "Copy here") }
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
        Prompt.NewFolder -> NameDialog("New folder", "", "Create", { prompt = null }) {
            vm.newFolder(it); prompt = null
        }
        is Prompt.Rename -> NameDialog("Rename", p.name, "Rename", { prompt = null }) {
            vm.rename(p.path, it); prompt = null
        }
        Prompt.Delete -> ConfirmDialog(
            title = "Delete ${state.selected.size} item(s)?",
            message = "This can't be undone.",
            confirmLabel = "Delete",
            onDismiss = { prompt = null },
        ) { vm.delete(); prompt = null }
        null -> Unit
    }
}

@Composable
private fun FileList(state: BrowserState, dir: File?, vm: BrowserViewModel, modifier: Modifier) {
    val ctx = LocalContext.current
    // scroll-state / scroll-restore key: the folder path, or "cat:<NAME>" for a category
    val dirPath = state.category?.let { "cat:${it.name}" } ?: dir?.path ?: ""

    // Fresh scroll state per folder; restored to the saved position when coming back "up".
    key(dirPath) {
        val listState = rememberLazyListState()
        var pending by remember { mutableIntStateOf(vm.scrollPositions.remove(dirPath) ?: 0) }
        val hasItems = state.items.isNotEmpty()
        LaunchedEffect(hasItems) {
            if (hasItems && pending > 0) {
                listState.scrollToItem(pending.coerceAtMost(state.items.lastIndex))
                pending = 0
            }
        }

        val dateFormat = remember { DateFormat.getDateTimeInstance(DateFormat.MEDIUM, DateFormat.SHORT) }
        val stateRef = rememberUpdatedState(state)

        // Stable lambdas: rows don't recompose just because unrelated state changed.
        val onClick = remember(dirPath) {
            { item: FileItem ->
                when {
                    stateRef.value.selected.isNotEmpty() -> vm.toggleSelect(item.path)
                    item.isDir -> {
                        vm.scrollPositions[dirPath] = listState.firstVisibleItemIndex
                        vm.open(File(item.path))
                    }
                    else -> openFile(ctx, item.path)
                }
            }
        }
        val onLongClick = remember(dirPath) { { item: FileItem -> vm.toggleSelect(item.path) } }

        Box(modifier.fillMaxSize()) {
            LazyColumn(Modifier.fillMaxSize(), state = listState) {
                items(state.items, key = { it.path }, contentType = { it.isDir }) { item ->
                    FileRow(
                        item = item,
                        selected = item.path in state.selected,
                        selecting = state.selected.isNotEmpty(),
                        showFolder = state.category != null,
                        dateFormat = dateFormat,
                        onClick = onClick,
                        onLongClick = onLongClick,
                    )
                }
            }
            if (state.loading) LinearProgressIndicator(Modifier.fillMaxWidth().align(Alignment.TopCenter))
            if (!state.loading && state.items.isEmpty()) {
                Text(
                    state.error ?: when {
                        !state.query.isNullOrBlank() -> "No results"
                        state.category != null -> "No files found"
                        else -> "Empty folder"
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
private fun BrowserTopBar(state: BrowserState, dir: File?, vm: BrowserViewModel, onNewFolder: () -> Unit) {
    val n = state.selected.size
    var sortOpen by remember { mutableStateOf(false) }
    var moreOpen by remember { mutableStateOf(false) }

    when {
        state.query != null -> SearchTopBar(vm)

        n > 0 -> TopAppBar(
            title = { Text("$n selected") },
            navigationIcon = {
                IconButton(onClick = vm::clearSelection) { Icon(Icons.Filled.Close, "Cancel selection") }
            },
            actions = {
                IconButton(onClick = vm::selectAll) { Icon(Icons.Filled.SelectAll, "Select all") }
            },
        )

        else -> {
            val title = remember(dir, state.volumes, state.category) {
                state.category?.label
                    ?: dir?.let { d -> state.volumes.firstOrNull { it.root.path == d.path }?.name ?: d.name }
                    ?: ""
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
                    IconButton(onClick = { vm.goUp() }) { Icon(Icons.AutoMirrored.Filled.ArrowBack, "Back") }
                },
                actions = {
                    IconButton(onClick = { vm.setQuery("") }) { Icon(Icons.Filled.Search, "Search") }
                    Box {
                        IconButton(onClick = { sortOpen = true }) { Icon(Icons.Filled.SwapVert, "Sort") }
                        DropdownMenu(expanded = sortOpen, onDismissRequest = { sortOpen = false }) {
                            SortBy.entries.forEach { by ->
                                val active = state.sortBy == by
                                val arrow = if (active) (if (state.ascending) "  ↑" else "  ↓") else ""
                                DropdownMenuItem(
                                    text = { Text(by.name.lowercase().replaceFirstChar { it.uppercase() } + arrow) },
                                    leadingIcon = { if (active) Icon(Icons.Filled.Check, null) },
                                    onClick = { vm.setSort(by); sortOpen = false },
                                )
                            }
                        }
                    }
                    Box {
                        IconButton(onClick = { moreOpen = true }) { Icon(Icons.Filled.MoreVert, "More") }
                        DropdownMenu(expanded = moreOpen, onDismissRequest = { moreOpen = false }) {
                            if (dir != null) DropdownMenuItem(
                                text = { Text("New folder") },
                                leadingIcon = { Icon(Icons.Filled.CreateNewFolder, null) },
                                onClick = { moreOpen = false; onNewFolder() },
                            )
                            if (dir != null) DropdownMenuItem(
                                text = { Text(if (state.showHidden) "Hide hidden files" else "Show hidden files") },
                                onClick = { moreOpen = false; vm.toggleHidden() },
                            )
                            DropdownMenuItem(
                                text = { Text("Refresh") },
                                leadingIcon = { Icon(Icons.Filled.Refresh, null) },
                                onClick = { moreOpen = false; vm.refresh() },
                            )
                        }
                    }
                },
            )
        }
    }
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
                placeholder = { Text("Search in this folder") },
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
            IconButton(onClick = { vm.setQuery(null) }) { Icon(Icons.AutoMirrored.Filled.ArrowBack, "Close search") }
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

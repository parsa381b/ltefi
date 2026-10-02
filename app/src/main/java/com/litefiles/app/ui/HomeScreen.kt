package com.litefiles.app.ui

import android.text.format.Formatter
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.SdCard
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Smartphone
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
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.litefiles.app.R
import com.litefiles.app.data.Category
import com.litefiles.app.data.Volume

private const val COLUMNS = 3

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HomeScreen(state: BrowserState, vm: BrowserViewModel, snackbar: SnackbarHostState) {
    var settingsOpen by remember { mutableStateOf(false) }
    val categoryRows = remember { Category.entries.chunked(COLUMNS) }
    val shortcutRows = remember(state.shortcuts) { state.shortcuts.chunked(COLUMNS) }
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.app_name), fontWeight = FontWeight.SemiBold) },
                actions = {
                    IconButton(onClick = { settingsOpen = true }) {
                        Icon(Icons.Filled.Settings, stringResource(R.string.action_settings))
                    }
                },
            )
        },
        snackbarHost = { SnackbarHost(snackbar) },
    ) { pad ->
        LazyColumn(
            Modifier.padding(pad),
            contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            item(key = "h-categories") { SectionTitle(stringResource(R.string.home_categories)) }
            items(categoryRows, key = { "c-" + it.first().name }) { row ->
                Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    row.forEach { c ->
                        HomeTile(kindIcon(c.kind), stringResource(c.labelRes), kindColor(c.kind), Modifier.weight(1f)) {
                            vm.openCategory(c)
                        }
                    }
                    repeat(COLUMNS - row.size) { Spacer(Modifier.weight(1f)) }
                }
            }

            if (shortcutRows.isNotEmpty()) {
                item(key = "h-shortcuts") { SectionTitle(stringResource(R.string.home_shortcuts)) }
                items(shortcutRows, key = { "s-" + it.first().folder }) { row ->
                    Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                        row.forEach { s ->
                            HomeTile(
                                shortcutIcon(s.folder),
                                stringResource(s.labelRes),
                                MaterialTheme.colorScheme.primary,
                                Modifier.weight(1f),
                            ) { vm.open(s.dir) }
                        }
                        repeat(COLUMNS - row.size) { Spacer(Modifier.weight(1f)) }
                    }
                }
            }

            item(key = "h-trash") { TrashCard(state.trashCount) { vm.openTrash() } }

            item(key = "h-storage") { SectionTitle(stringResource(R.string.home_storage)) }
            items(state.volumes, key = { it.root.path }) { v ->
                StorageCard(v) { vm.open(v.root) }
            }
        }
    }

    if (settingsOpen) SettingsDialog(onDismiss = { settingsOpen = false })
}

@Composable
private fun SectionTitle(text: String) {
    Text(
        text,
        style = MaterialTheme.typography.titleSmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = Modifier.padding(top = 8.dp),
    )
}

@Composable
private fun HomeTile(icon: ImageVector, label: String, tint: Color, modifier: Modifier, onClick: () -> Unit) {
    Surface(
        modifier.clickable(onClick = onClick),
        shape = RoundedCornerShape(20.dp),
        color = MaterialTheme.colorScheme.surfaceContainer,
    ) {
        Column(Modifier.padding(vertical = 16.dp).fillMaxWidth(), horizontalAlignment = Alignment.CenterHorizontally) {
            Icon(icon, null, tint = tint, modifier = Modifier.size(28.dp))
            Spacer(Modifier.size(6.dp))
            Text(label, style = MaterialTheme.typography.labelLarge, maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
    }
}

@Composable
private fun StorageCard(v: Volume, onClick: () -> Unit) {
    val ctx = LocalContext.current
    val used = (v.total - v.free).coerceAtLeast(0L)
    val usedText = remember(v) { Formatter.formatShortFileSize(ctx, used) }
    val totalText = remember(v) { Formatter.formatShortFileSize(ctx, v.total) }
    val label = stringResource(R.string.storage_used, usedText, totalText)
    Surface(
        Modifier.fillMaxWidth().clickable(onClick = onClick),
        shape = RoundedCornerShape(20.dp),
        color = MaterialTheme.colorScheme.surfaceContainer,
    ) {
        Row(Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
            Icon(
                if (v.primary) Icons.Filled.Smartphone else Icons.Filled.SdCard,
                null,
                tint = MaterialTheme.colorScheme.primary,
                modifier = Modifier.size(32.dp),
            )
            Spacer(Modifier.width(16.dp))
            Column(Modifier.weight(1f)) {
                Text(v.name, style = MaterialTheme.typography.titleMedium)
                Spacer(Modifier.size(8.dp))
                LinearProgressIndicator(
                    progress = { if (v.total > 0) used.toFloat() / v.total else 0f },
                    modifier = Modifier.fillMaxWidth(),
                )
                Spacer(Modifier.size(6.dp))
                Text(label, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
    }
}

@Composable
private fun TrashCard(count: Int, onClick: () -> Unit) {
    Surface(
        Modifier.fillMaxWidth().clickable(onClick = onClick),
        shape = RoundedCornerShape(20.dp),
        color = MaterialTheme.colorScheme.surfaceContainer,
    ) {
        Row(Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
            Icon(Icons.Filled.Delete, null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(32.dp))
            Spacer(Modifier.width(16.dp))
            Column {
                Text(stringResource(R.string.recycle_bin), style = MaterialTheme.typography.titleMedium)
                Text(
                    if (count == 0) {
                        stringResource(R.string.trash_empty_state)
                    } else {
                        pluralStringResource(R.plurals.trash_kept_hint, count, count)
                    },
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}

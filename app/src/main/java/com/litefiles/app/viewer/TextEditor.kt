package com.litefiles.app.viewer

import android.content.Context
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.consumeWindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Save
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.litefiles.app.R
import com.litefiles.app.ui.isAppDarkTheme
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File

private const val MAX_EDITABLE_BYTES = 1_000_000L // 1 MB: keeps typing and highlighting responsive

private sealed interface TextLoad {
    data object Loading : TextLoad
    class Ok(val text: String) : TextLoad
    class Error(val message: String) : TextLoad
}

private fun loadText(ctx: Context, file: File): TextLoad {
    return try {
        if (file.length() > MAX_EDITABLE_BYTES) {
            return TextLoad.Error(ctx.getString(R.string.error_text_too_large))
        }
        val bytes = file.readBytes()
        val probe = minOf(bytes.size, 8000)
        if ((0 until probe).any { bytes[it].toInt() == 0 }) {
            return TextLoad.Error(ctx.getString(R.string.error_text_not_text))
        }
        TextLoad.Ok(String(bytes, Charsets.UTF_8))
    } catch (e: Exception) {
        TextLoad.Error(ctx.getString(R.string.error_text_read))
    }
}

/** View and edit text/code. UTF-8, up to 1 MB, with syntax highlighting for common languages and a line-number gutter. */
@Composable
fun TextEditor(path: String, onBack: () -> Unit) {
    val ctx = LocalContext.current
    val loaded by produceState<TextLoad>(TextLoad.Loading, path) {
        value = withContext(Dispatchers.IO) { loadText(ctx, File(path)) }
    }
    when (val l = loaded) {
        TextLoad.Loading -> Scaffold(topBar = { ViewerTopBar(File(path).name, onBack) }) { pad ->
            Box(Modifier.padding(pad).fillMaxSize(), contentAlignment = Alignment.Center) { CircularProgressIndicator() }
        }
        is TextLoad.Error -> Scaffold(topBar = { ViewerTopBar(File(path).name, onBack) }) { pad ->
            ErrorPane(l.message, path, Modifier.padding(pad))
        }
        is TextLoad.Ok -> EditorScreen(path, l.text, onBack)
    }
}

@Composable
private fun EditorScreen(path: String, initial: String, onBack: () -> Unit) {
    val scope = rememberCoroutineScope()
    val snackbar = remember { SnackbarHostState() }
    val name = remember(path) { File(path).name }
    var value by remember { mutableStateOf(TextFieldValue(initial)) }
    var dirty by remember { mutableStateOf(false) }
    var wrap by remember {
        mutableStateOf(name.substringAfterLast('.', "").lowercase() in setOf("txt", "md", "markdown", "log", "srt", "vtt", "tex"))
    }
    var confirmExit by remember { mutableStateOf(false) }

    val savedMsg = stringResource(R.string.editor_saved)
    val saveFailedMsg = stringResource(R.string.editor_save_failed)
    val dark = isAppDarkTheme()
    val highlighter = remember(path, dark) { Highlighter(Languages.forFile(name), dark) }
    val transformation = remember(highlighter) { highlighter.asVisualTransformation() }

    fun save(then: (() -> Unit)? = null) {
        val text = value.text
        scope.launch {
            val ok = withContext(Dispatchers.IO) { runCatching { File(path).writeText(text) }.isSuccess }
            if (ok) {
                dirty = false
                if (then != null) then() else snackbar.showSnackbar(savedMsg)
            } else {
                snackbar.showSnackbar(saveFailedMsg)
            }
        }
    }

    BackHandler(enabled = dirty) { confirmExit = true }

    val textStyle = TextStyle(
        fontFamily = FontFamily.Monospace,
        fontSize = 13.sp,
        lineHeight = 19.sp,
        color = MaterialTheme.colorScheme.onBackground,
    )
    val lineCount = remember(value.text) { value.text.count { it == '\n' } + 1 }
    val gutter = remember(lineCount) { (1..lineCount).joinToString("\n") }
    val hScroll = rememberScrollState()

    Scaffold(
        topBar = {
            ViewerTopBar(
                title = if (dirty) "$name •" else name,
                subtitle = pluralStringResource(R.plurals.editor_lines, lineCount, lineCount),
                onBack = { if (dirty) confirmExit = true else onBack() },
                actions = {
                    IconButton(onClick = { save() }, enabled = dirty) {
                        Icon(Icons.Filled.Save, stringResource(R.string.action_save))
                    }
                    ViewerMenu(path) { close ->
                        DropdownMenuItem(
                            text = { Text(stringResource(R.string.editor_word_wrap)) },
                            leadingIcon = { if (wrap) Icon(Icons.Filled.Check, null) },
                            onClick = { wrap = !wrap; close() },
                        )
                    }
                },
            )
        },
        snackbarHost = { SnackbarHost(snackbar) },
    ) { pad ->
        Row(
            Modifier
                .padding(pad)
                .consumeWindowInsets(pad)
                .imePadding()
                .fillMaxSize()
                .verticalScroll(rememberScrollState()),
        ) {
            if (!wrap) {
                Text(
                    gutter,
                    style = textStyle.copy(color = MaterialTheme.colorScheme.onSurfaceVariant, textAlign = TextAlign.End),
                    modifier = Modifier.padding(start = 8.dp, end = 8.dp, top = 8.dp),
                )
            }
            val field: @Composable () -> Unit = {
                BasicTextField(
                    value = value,
                    onValueChange = {
                        if (it.text != value.text) dirty = true
                        value = it
                    },
                    textStyle = textStyle,
                    cursorBrush = SolidColor(MaterialTheme.colorScheme.primary),
                    visualTransformation = transformation,
                    modifier = Modifier
                        .padding(8.dp)
                        .then(if (wrap) Modifier.fillMaxWidth() else Modifier.widthIn(min = 320.dp)),
                )
            }
            if (wrap) {
                Box(Modifier.weight(1f)) { field() }
            } else {
                Box(Modifier.weight(1f).horizontalScroll(hScroll)) { field() }
            }
        }
    }

    if (confirmExit) {
        AlertDialog(
            onDismissRequest = { confirmExit = false },
            title = { Text(stringResource(R.string.editor_unsaved_title)) },
            text = { Text(stringResource(R.string.editor_unsaved_msg, name)) },
            confirmButton = {
                TextButton(onClick = {
                    confirmExit = false
                    save(then = onBack)
                }) { Text(stringResource(R.string.action_save)) }
            },
            dismissButton = {
                Row {
                    TextButton(onClick = { confirmExit = false }) {
                        Text(stringResource(R.string.action_cancel))
                    }
                    TextButton(onClick = {
                        confirmExit = false
                        onBack()
                    }) { Text(stringResource(R.string.editor_discard)) }
                }
            },
        )
    }
}

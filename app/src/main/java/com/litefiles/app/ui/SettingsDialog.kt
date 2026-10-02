package com.litefiles.app.ui

import android.content.Context
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.litefiles.app.AppSettings
import com.litefiles.app.LanguageMode
import com.litefiles.app.R
import com.litefiles.app.ThemeMode
import com.litefiles.app.viewer.findActivity

/**
 * Theme (System / Light / Dark) and Language (System / English / Persian).
 * The theme applies immediately; a language change recreates the activity so the new locale is
 * picked up (that is how [AppSettings.wrap] is applied to the running activity).
 */
@Composable
fun SettingsDialog(onDismiss: () -> Unit) {
    val ctx = LocalContext.current
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.settings_title)) },
        text = {
            Column(Modifier.verticalScroll(rememberScrollState())) {
                SectionLabel(stringResource(R.string.settings_theme))
                Option(stringResource(R.string.theme_system), AppSettings.themeMode == ThemeMode.SYSTEM) {
                    AppSettings.setTheme(ctx, ThemeMode.SYSTEM)
                }
                Option(stringResource(R.string.theme_light), AppSettings.themeMode == ThemeMode.LIGHT) {
                    AppSettings.setTheme(ctx, ThemeMode.LIGHT)
                }
                Option(stringResource(R.string.theme_dark), AppSettings.themeMode == ThemeMode.DARK) {
                    AppSettings.setTheme(ctx, ThemeMode.DARK)
                }

                HorizontalDivider(Modifier.padding(vertical = 12.dp))
                SectionLabel(stringResource(R.string.settings_language))
                Option(stringResource(R.string.lang_system), AppSettings.language == LanguageMode.SYSTEM) {
                    switchLanguage(ctx, LanguageMode.SYSTEM)
                }
                Option(stringResource(R.string.lang_english), AppSettings.language == LanguageMode.ENGLISH) {
                    switchLanguage(ctx, LanguageMode.ENGLISH)
                }
                Option(stringResource(R.string.lang_persian), AppSettings.language == LanguageMode.PERSIAN) {
                    switchLanguage(ctx, LanguageMode.PERSIAN)
                }
            }
        },
        confirmButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.action_close)) } },
    )
}

private fun switchLanguage(ctx: Context, mode: LanguageMode) {
    if (AppSettings.language == mode) return
    AppSettings.setLanguage(ctx, mode)
    ctx.findActivity()?.recreate() // re-reads the locale in attachBaseContext
}

@Composable
private fun SectionLabel(text: String) {
    Text(
        text,
        style = MaterialTheme.typography.titleSmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = Modifier.padding(top = 4.dp, bottom = 4.dp),
    )
}

@Composable
private fun Option(label: String, selected: Boolean, onSelect: () -> Unit) {
    Row(
        Modifier
            .fillMaxWidth()
            .selectable(selected = selected, onClick = onSelect)
            .padding(vertical = 2.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        RadioButton(selected = selected, onClick = onSelect)
        Text(label, style = MaterialTheme.typography.bodyLarge, modifier = Modifier.padding(start = 8.dp))
    }
}

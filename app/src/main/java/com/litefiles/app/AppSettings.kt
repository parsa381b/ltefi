package com.litefiles.app

import android.content.Context
import android.content.res.Configuration
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import java.util.Locale

/** Dark-mode choice picked in Settings. SYSTEM follows the device's dark theme. */
enum class ThemeMode { SYSTEM, LIGHT, DARK }

/** In-app language choice picked in Settings. SYSTEM follows the device language. */
enum class LanguageMode {
    SYSTEM,
    ENGLISH,
    PERSIAN;

    /** BCP-47 tag; empty = keep the system locale. */
    val tag: String
        get() = when (this) {
            SYSTEM -> ""
            ENGLISH -> "en"
            PERSIAN -> "fa"
        }

    companion object {
        fun from(name: String?): LanguageMode = entries.firstOrNull { it.name == name } ?: SYSTEM
    }
}

/**
 * The two user-facing settings, persisted in the same SharedPreferences file as the view mode.
 *
 * [themeMode] and [language] are Compose snapshot state, so the theme updates as soon as the
 * option is tapped. The language is applied by [wrap] in `attachBaseContext` of the application
 * and of both activities (dependency-free per-app locale: minSdk 30, no AppCompat) and takes
 * effect when the activity is recreated. [wrap] also updates `Locale.getDefault()` so dates and
 * numbers match the chosen language even though they do not go through resources.
 */
object AppSettings {
    private const val PREFS = "lite_files"
    private const val KEY_THEME = "theme_mode"
    private const val KEY_LANGUAGE = "language"

    /** The device's locale, captured on the first [wrap] (before any override is applied). */
    private var systemDefault: Locale? = null

    var themeMode: ThemeMode by mutableStateOf(ThemeMode.SYSTEM)
        private set

    var language: LanguageMode by mutableStateOf(LanguageMode.SYSTEM)
        private set

    /** Reads the persisted values. Called once from [LiteFilesApp.onCreate]. */
    fun init(ctx: Context) {
        val p = prefs(ctx)
        themeMode = runCatching { ThemeMode.valueOf(p.getString(KEY_THEME, null) ?: "") }
            .getOrDefault(ThemeMode.SYSTEM)
        language = LanguageMode.from(p.getString(KEY_LANGUAGE, null))
    }

    fun setTheme(ctx: Context, mode: ThemeMode) {
        themeMode = mode
        prefs(ctx).edit().putString(KEY_THEME, mode.name).apply()
    }

    fun setLanguage(ctx: Context, mode: LanguageMode) {
        language = mode
        prefs(ctx).edit().putString(KEY_LANGUAGE, mode.name).apply()
    }

    /**
     * Returns [base] with the chosen language applied. SYSTEM (or no override) returns [base]
     * untouched, so the app always follows the device language until the user picks one.
     */
    fun wrap(base: Context): Context {
        val first = systemDefault ?: Locale.getDefault().also { systemDefault = it }
        val tag = LanguageMode.from(prefs(base).getString(KEY_LANGUAGE, null)).tag
        val locale = if (tag.isEmpty()) first else Locale.forLanguageTag(tag)
        if (Locale.getDefault() != locale) Locale.setDefault(locale)
        if (tag.isEmpty()) return base
        val config = Configuration(base.resources.configuration)
        config.setLocale(locale) // also sets the layout direction (RTL for Persian)
        return base.createConfigurationContext(config)
    }

    private fun prefs(ctx: Context) = ctx.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
}

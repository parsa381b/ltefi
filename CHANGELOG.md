# Changelog

All notable changes to Lite Files are recorded here, newest first.
Format: [Keep a Changelog](https://keepachangelog.com/) · Versioning: [SemVer](https://semver.org/)
(`versionName` and `versionCode` in `app/build.gradle.kts` must match the top entry).

Categories: **Added** (new features) · **Changed** (behavior/config changes) · **Fixed** (bug fixes) · **Removed** · **Docs** · **Build/CI**

## [Unreleased]
_Nothing yet. Add new changes here as they are made, then move them under a new version heading when releasing._

## [1.7.0] - 2026-10-02
Persian localization and a Settings screen with a theme (light/dark/system) picker.

### Added
- **Persian (فارسی) localization:** every user-facing string moved out of the Kotlin code into `res/values/strings.xml` and translated in `res/values-fa/strings.xml` (165 strings/plurals), including the permission screen, home, browser, dialogs, operation messages, Details, all viewers, toasts and icon content descriptions. The app follows the device language by default.
- **In-app language switcher** (Settings → Language: System default / English / فارسی). The locale is applied in `attachBaseContext` of the application and of both activities (`AppSettings.wrap`, no AppCompat needed on minSdk 30) and `Locale.getDefault()` is kept in sync, so dates and numbers follow the chosen language (Persian digits; the calendar is the one ICU picks for that locale). Picking a language recreates the activity.
- **Settings screen** (gear icon on the home top bar, and More → Settings in the browser) with two sections: Theme and Language.
- **Theme setting** (Settings → Theme: Follow system / Light / Dark), persisted in the existing `lite_files` SharedPreferences and read as Compose snapshot state, so switching applies immediately; "Follow system" keeps the old behaviour.
- New `AppSettings` (settings state + locale wrapper), `LiteFilesApp` (application class registered in the manifest) and `ui/SettingsDialog`.

### Changed
- All UI text is now resolved through string resources (`stringResource` / `pluralStringResource` in composables, `Context.getString` in the ViewModel, `FileRepository` and toasts); counts use `<plurals>` so quantities are grammatical in both languages.
- `Category`, `SortBy` and `Shortcut` carry a `labelRes` instead of a hardcoded English label; `FileRepository.describe()` and `transfer()` take a `Context` to localize the type and "Preparing…" strings.
- `LiteFilesTheme` now reads the Theme setting (`isAppDarkTheme()`), which the text editor's syntax highlighter uses too instead of `isSystemInDarkTheme()`.
- App version bumped to 1.7.0 (`versionCode` 9).

### Docs
- `README.md` and `PROJECT_CONTEXT.md` updated (features, file map, design decisions 11/29/30, conventions, limitations).

## [1.6.0] - 2026-10-01
Built-in viewers: tapping a file now opens it inside the app instead of sending you to another app.

### Added
- **Image viewer:** JPEG, PNG, GIF, WebP (animated GIF/WebP play), BMP and, on devices that support them, HEIC/HEIF/AVIF. Swipe left/right through the other images in the same list (the Images category order, or the folder sorted by name), pinch to zoom, drag to pan, double-tap to zoom, tap to hide the bar. Huge photos are decoded at up to 2560 px.
- **Video player:** MP4, 3GP, WebM, MKV, MOV, AVI and other formats **as far as the device's own codecs allow** (the Android media stack is used, so AVI and some MOV/MKV files may not play). Play/pause, seek bar, back/forward 10 s, previous/next video, auto-continue, auto-hiding controls with full-screen. If a file can't play, the screen offers "Open with another app".
- **Audio player:** MP3, WAV, FLAC, OGG, AAC/M4A, Opus, AMR, MIDI. Cover art (embedded or folder art), seek bar, previous/next/±10 s, continues with the next track, respects other apps' audio (pauses when another app takes over). It pauses when you leave the screen: there is no background playback.
- **PDF viewer:** pages are drawn on demand, fit to the screen width, with a "Page x / n" indicator and a zoom toggle (fit width / 2x with sideways scrolling). Password-protected PDFs are reported (and can be opened in another app).
- **Text editor with syntax highlighting:** opens text and code files (txt, md, json, xml/html, css, js/ts, kt, java, py, c/cpp/cs, go, rs, sh, sql, yaml/toml/ini, logs and more). Highlighting for Kotlin, Java/Gradle, JavaScript/TypeScript, Python, C/C++, C#, Go, Rust, Swift/Dart/PHP, Ruby, shell/batch, SQL, JSON, XML/HTML, CSS, YAML/TOML, INI, Markdown. Line numbers, optional word wrap, Save button, and a "Save / Discard / Cancel" prompt when leaving with unsaved changes. Files must be UTF-8 text up to 1 MB; highlighting switches off above 200,000 characters.
- Every viewer has a menu with **Open with…** and **Share**.
- New permission-free `viewer` package: `ViewerActivity`, `ViewerRouter` (type detection, playlists), `ViewerCommon`, `ImageViewer`, `VideoPlayer`, `AudioPlayer`, `PdfViewer`, `TextEditor`, `SyntaxHighlighter`. No libraries were added; the APK grows only by the new code.

### Changed
- `openFile()` now routes: APK to the installer, images/video/audio/PDF/text to the built-in viewers, everything else to another app. The old "open with another app" path is `openExternal()`.
- `.ts` files are opened as text when the content looks like text (TypeScript) and as video otherwise (MPEG-TS).
- SVG, Office documents, archives and other types still open in another app.
- Manifest: new non-exported `ViewerActivity` that handles configuration changes itself, so video position and unsaved edits survive rotation.
- App version bumped to 1.6.0 (`versionCode` 8).

### Docs
- `README.md` and `PROJECT_CONTEXT.md` updated (features, file map, design decisions, limitations).

## [1.5.0] - 2026-10-01
Thumbnails for audio (cover art) and APKs (app icons).

### Added
- **Audio cover art:** songs show their embedded album art, or a `cover.jpg`/`folder.jpg`-style image from the same folder, in both list and grid view. Songs without any art keep the music-note icon.
- **APK icons:** `.apk` files show the app's launcher icon (masked to your device's icon shape, shown whole with a little padding). APKs that can't be read keep the Android icon.
- Both use the same loader as photos and videos: decoded at 256 px only for what is on screen, three at a time, cancelled when you scroll past, cached in memory, failures remembered. No new library.

### Changed
- `Thumbnails.load()` now takes the file `Kind` (and an application `Context`) instead of a video flag; `FileThumb` decides per kind whether to crop (photos, video, audio) or fit (APK icons).
- App version bumped to 1.5.0 (`versionCode` 7).

### Docs
- `README.md` and `PROJECT_CONTEXT.md` updated (thumbnail design decision and limitations).

## [1.4.0] - 2026-10-01
"Show in folder" for category files, and installing APKs from inside the app.

### Added
- **Show in folder:** in a category (Images, Videos, Audio, Documents, APKs), select one file and choose More → Show in folder. The app opens the file's folder, scrolls to the file and selects it so you can see it. (The menu item is greyed out unless exactly one file is selected; Back from there goes to the parent folder, not back to the category.) If the file is a dot-file, "Show hidden files" is switched on automatically so it can be found.
- **Install APKs:** tapping an `.apk` (in a folder or in the APKs category) now opens the system installer. The first time, Android needs "Install unknown apps" allowed for Lite Files: the app opens that settings page and asks you to tap the APK again.
- New permission `REQUEST_INSTALL_PACKAGES` in the manifest.

### Changed
- `openFile()` routes `.apk` files to a new `installApk()` (installer via FileProvider + `ACTION_VIEW`) instead of the generic "open with" path.
- `BrowserState` gains `reveal`; `BrowserViewModel` gains `showInFolder()` and `finishReveal()`; the file list scrolls to and selects the revealed file once its folder has loaded.
- App version bumped to 1.4.0 (`versionCode` 6).

### Docs
- `README.md` and `PROJECT_CONTEXT.md` updated (permissions, features, design decisions, limitations).

## [1.3.0] - 2026-10-01
List/grid view with image and video thumbnails, and a Recycle bin instead of permanent delete.

### Added
- **List/grid toggle** in the top bar (works in folders, categories and the Recycle bin). The choice is remembered between launches, and your scroll position is kept when switching.
- **Thumbnails for images and videos** in both views (videos get a play badge); other file types keep their colored type icon. Thumbnails are decoded on demand at 256 px, only for what is on screen, three at a time, cancelled when you scroll past, and cached in memory (about 10% of the heap, 8-40 MB). Files that can't be decoded fall back to the icon. No image library was added.
- **Recycle bin:** Delete now moves items to a hidden `.LiteFilesTrash` folder on the same storage volume (instant, no copying), hidden from gallery and music apps.
  - Home screen card "Recycle bin" shows how many items it holds.
  - Inside the bin: select items, then **Restore** (back to the original folder; folders are recreated, name conflicts get `(1)`) or **Delete** (permanent). Top-bar menu has **Empty Recycle bin**.
  - Items are removed automatically after **30 days**.
  - Deleting something that is already inside a bin (for example via "Show hidden files") deletes it permanently, and the confirmation says so.
- New files: `ui/Thumbnails.kt` (thumbnail loader + `FileThumb`).

### Changed
- The delete confirmation now reads "Move N item(s) to Recycle bin?" and the snackbar confirms where they went.
- `FileRepository.delete()` replaced by `moveToTrash()`, `restore()`, `deleteForever()`, `emptyBin()`, `purgeExpired()`, `trashCount()` and `trashItems()`.
- `BrowserState` gains `viewGrid`, `trash` and `trashCount`; `BrowserViewModel` gains `toggleView()`, `openTrash()`, `restore()`, `deleteForever()` and `emptyBin()`. The view mode is stored in SharedPreferences.
- `FileRow` shows thumbnails and takes a `deleted` flag (bin rows say "Deleted <date>"); new `FileTile` for grid cells.
- The top bar now has four icons (search, view toggle, sort, more).
- Deleted files keep using storage until the bin is emptied or they expire.
- App version bumped to 1.3.0 (`versionCode` 5).

### Docs
- `README.md` and `PROJECT_CONTEXT.md` updated (features, file map, design decisions, limitations).

## [1.2.1] - 2026-09-30
CI and dependency housekeeping after the first successful GitHub build. No app behavior changes.

### Build/CI
- Workflow actions updated to `actions/checkout@v7`, `actions/setup-java@v6`, `gradle/actions/setup-gradle@v5` and `actions/upload-artifact@v7`; this clears the "Node.js 20 is deprecated" and "setup-java v4 is deprecated" warnings. `softprops/action-gh-release` stays on v2 (it only runs on tag builds, so a bump could not be tested by a normal build).
- Runner pinned to `ubuntu-24.04` instead of `ubuntu-latest`, which GitHub moves to Ubuntu 26 on 2026-10-19.
- Dependabot: GitHub Actions updates are now grouped into one monthly PR; Gradle updates are monthly with at most 3 open PRs. Ignored for now: AGP major (9.x needs a migration), Gradle wrapper major (9.x needs AGP 9), and `core-ktx` minor/major bumps (1.19.1 failed CI). These two changes replace the 10 separate PRs from the first run, which were closed unmerged.
- App version bumped to 1.2.1 (`versionCode` 4).

### Docs
- `README.md` and `PROJECT_CONTEXT.md`: status updated (built by CI and installed on a device), dependency-update policy documented.

## [1.2.0] - 2026-09-30
Copy/move now show live progress with a Cancel button, and selections have a Details dialog.

### Added
- **Copy/move progress dialog:** progress bar, `x MB of y GB · n of m items`, and the name of the file currently being copied. Updates about 10 times per second.
- **Cancel button** for copy and move. Cancelling stops within one 2 MB step, deletes the half-copied item, leaves its source untouched, and keeps items that already finished. The snackbar reports `Cancelled · n of m done`.
- **Details dialog** (selection bar → More → Details) for one or many items:
  - single file: name, type, location, modified date, size (formatted and exact bytes);
  - single folder: the same plus **Items** (direct children) and **Contains** (files and folders, recursive), counted live with a progress bar and "…" until finished;
  - multiple items: how many files/folders are selected, total size, and the shared location when all items are in the same folder.
- Location and other values in Details can be long-pressed to select and copy.
- New files: `data/Transfer.kt` (`Progress`, `TransferStats`), `data/Details.kt` (`Details`, `ScanResult`), `ui/Dialogs.kt` (`OperationDialog`, `DetailsDialog`).

### Changed
- **Rename moved into the new "More" menu** of the selection bar (it is only enabled when exactly one item is selected) to keep the bar uncluttered.
- `FileRepository.transfer()` now takes a `TransferStats` and a progress callback and is cancellable. It works in passes: validate and instant same-volume rename for moves, size what still needs copying, then copy in 2 MB `transferTo` steps.
- Cancelling a move keeps the items already moved; the clipboard is cleared after any finished or cancelled move.
- `BrowserState.busy` (a String) replaced by `op` (`Op`: label, cancellable, cancelling, progress) and a new `details` field; `BusyDialog` replaced by `OperationDialog`. Delete, rename and new-folder still show a plain spinner (no cancel).
- `FileRepository` gains `describe()` and `scan()`; `scan()` counts with one stat per entry and never follows symlinks.
- App version bumped to 1.2.0 (`versionCode` 3).

### Removed
- Private `copyTree` helper (superseded by the progress-aware copy).

### Docs
- `README.md` and `PROJECT_CONTEXT.md` updated (features, file map, architecture, decisions, limitations).

## [1.1.0] - 2026-09-30
Adds the Samsung-style Categories view to the home screen.

### Added
- **Categories section on the home screen:** Images, Videos, Audio, Documents, APKs, shown as colored tiles above Shortcuts.
- Opening a category lists every matching file across all mounted storage volumes (internal + SD/USB) in one flat, scrollable list.
- Category lists default to newest-first; your folder sort order is restored when you leave the category.
- Each category row shows the containing folder name in its subtitle (`Camera · date · size`).
- Selection, rename, delete, share, copy/move (cut/copy then open a folder and paste), search and sort all work inside categories.
- Back from a category goes to the home screen (after clearing selection/search).
- New files: `data/Category.kt` (category definitions).

### Changed
- `FileRepository` gains `category()`, which reads the MediaStore index (one indexed query, no disk walk) and drops stale entries whose file no longer exists.
- `BrowserState` has a new `category` field; `BrowserViewModel` gains `openCategory()`; `refresh()`, `goUp()` and `onResume()` are category-aware.
- `BrowserScreen`/`FileList`/top bar accept a null folder when showing a category: no path subtitle, no "New folder" or "Show hidden files" menu items, and the paste bar says "Open a folder to paste" instead of offering a paste button.
- `HomeScreen` rewritten around a shared `HomeTile` used by categories and shortcuts.
- `FileRow` takes a new `showFolder` parameter.
- App version bumped to 1.1.0 (`versionCode` 2).

### Docs
- `README.md` and `PROJECT_CONTEXT.md` updated for categories (architecture, file map, design decision, limitations).

## [1.0.0] - 2026-09-29
Initial version (written without being compiled; first real build may need small compile fixes).

### Added
- Home screen with shortcuts (Downloads, DCIM, Pictures, Documents, Music, Movies) and storage volume cards with usage bars.
- Folder browsing with fast NIO directory listing, cancellable when navigating away.
- Sorting by name/date/size (asc/desc, folders first), in-folder search, hidden-files toggle, refresh.
- Multi-select (long-press) with rename, delete (with confirmation), copy, move (paste bar), share, new folder.
- Open files with external apps via FileProvider.
- Scroll-position restore when navigating back up.
- All-files-access permission screen; light/dark Samsung-style theme; adaptive launcher icon.

### Build/CI
- GitHub Actions workflow builds a release APK, uploads it as an artifact, and attaches it to GitHub Releases on `v*` tags.
- Optional release signing via `KEYSTORE_*` secrets (debug-key signing otherwise); Dependabot for Gradle and Actions.
- R8 minification and resource shrinking; AGP 8.13.0, Kotlin 2.2.20, Compose BOM 2025.09.00, minSdk 30, target/compile SDK 36.

### Docs
- `README.md` (setup, APK-without-Android-Studio guide) and `PROJECT_CONTEXT.md` (architecture and decisions).

# Changelog

All notable changes to Lite Files are recorded here, newest first.
Format: [Keep a Changelog](https://keepachangelog.com/) · Versioning: [SemVer](https://semver.org/)
(`versionName` and `versionCode` in `app/build.gradle.kts` must match the top entry).

Categories: **Added** (new features) · **Changed** (behavior/config changes) · **Fixed** (bug fixes) · **Removed** · **Docs** · **Build/CI**

## [Unreleased]
_Nothing yet. Add new changes here as they are made, then move them under a new version heading when releasing._

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

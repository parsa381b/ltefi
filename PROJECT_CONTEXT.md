# PROJECT_CONTEXT: Lite Files

> Read this file first when picking the project back up in a new chat. It should be enough to work on the app without re-reading every source file.
> Keep it updated whenever architecture or decisions change.

## What it is
Android file manager (Samsung "My Files" look and feel), Kotlin + Jetpack Compose (Material 3). Goals in priority order:
fast folder loading, low memory, no lag on large directories, small APK. Single module, single activity, no DI, no DB, no navigation library, no image library.

**Features:** home screen (categories: Images/Videos/Audio/Documents/APKs, shortcuts, storage volumes with usage bars), folder browsing, sort (name/date/size, asc/desc, folders first),
in-folder search, hidden-file toggle, multi-select (long-press), rename, delete, copy, move (clipboard + "paste here" bar, live progress + cancel), details dialog (size, path, modified, folder item counts), share (files only), open with external app, new folder, scroll-position restore when going up, dark mode.

**Status:** initial version written without being compiled or run on a device (no SDK in the authoring environment). First thing to do with a real build: fix any compile errors, then test on a device with a very large folder.

## Stack and versions (see `gradle/libs.versions.toml`)
- AGP 8.13.0, Kotlin 2.2.20 (Compose compiler plugin `org.jetbrains.kotlin.plugin.compose`), Gradle 8.14.3, JDK 17
- Compose BOM 2025.09.00 (Material 3, foundation, `material-icons-extended`, stripped by R8), activity-compose 1.11.0, lifecycle 2.9.4, core-ktx 1.17.0
- compileSdk/targetSdk 36, **minSdk 30**, package `com.litefiles.app`
- Only 8 dependencies; no Coil/Glide/Hilt/Room/Navigation

## File map
```
.github/workflows/build.yml      CI: installs Gradle, assembleRelease, uploads APK artifact, attaches to Release on v* tags
.github/dependabot.yml           weekly updates for Gradle + Actions
gradle/libs.versions.toml        version catalog (single source of dependency versions)
gradle/wrapper/gradle-wrapper.properties   (wrapper jar/scripts NOT committed; run `gradle wrapper` locally if wanted)
app/build.gradle.kts             R8 + resource shrink, optional env-var signing, packaging excludes
CHANGELOG.md                     per-version record of every change (see "Changelog policy")
app/src/main/AndroidManifest.xml MANAGE_EXTERNAL_STORAGE, FileProvider
app/src/main/res/xml/file_paths.xml   FileProvider root-path (all storage)
app/src/main/java/com/litefiles/app/
  MainActivity.kt                edge-to-edge, hosts App(vm), calls vm.onResume()
  data/Category.kt               Category enum (label + Kind for icon/color)
  data/Transfer.kt               Progress + TransferStats (copy/move progress and tally)
  data/Details.kt                Details + ScanResult (Details dialog model)
  data/FileItem.kt               tiny immutable entry + Kind enum + extension->Kind map
  data/FileRepository.kt         ALL disk I/O (list, sort, category via MediaStore, volumes, shortcuts, delete, rename, createFolder, transfer, describe, scan)
  ui/BrowserViewModel.kt         BrowserState + Clipboard + BrowserViewModel (single StateFlow)
  ui/App.kt                      root: permission screen / home / browser, snackbar, busy dialog
  ui/HomeScreen.kt               categories + shortcuts tile grid + storage cards
  ui/BrowserScreen.kt            top bars (normal/selection/search), bottom bars (selection actions / paste), FileList
  ui/Components.kt               FileRow, NameDialog, ConfirmDialog
  ui/Dialogs.kt                  OperationDialog (progress + cancel), DetailsDialog
  ui/FileIcons.kt                Kind -> icon/color, shortcut icons
  ui/Theme.kt                    static light/dark palettes
  util/Intents.kt                open file, share files, request All-files-access
```

## Architecture
MVVM-lite, unidirectional data flow:

```
Compose UI  --events-->  BrowserViewModel  --suspend calls-->  FileRepository (Dispatchers.IO)
     ^                        |
     +---- StateFlow<BrowserState> (collectAsStateWithLifecycle)
```
- **One `BrowserState`** (immutable data class): permission, volumes, shortcuts, `dir` and `category` (both null = home), `items` (already filtered), loading/error, sort, hidden flag, `query` (null = search closed), `selected` (set of paths), `clipboard`, `op` (running operation + progress), `details`, one-shot `message`.
- **Navigation is state, not a NavHost:** `dir == null && category == null` -> Home, else Browser (same screen for folders and categories; `dir` is null in category mode). `goUp()` handles Back in priority order: clear selection -> close search -> category goes home -> parent folder -> volume root goes home -> (home) system exits.
- **VM holds two lists:** private `all` (full sorted listing) and `state.items` (filtered by query). Filtering/sorting of large lists run on `Dispatchers.Default`.
- **Simple operations** (`delete/rename/newFolder`) go through `runOp`: spinner dialog (`op`), run on IO, snackbar message, refresh.
- **Copy/move (`paste`)** run in `opJob` with their own flow: `FileRepository.transfer` reports `Progress` via a callback into `state.op`, and `cancelOp()` cancels the job. Cancellation is intentionally caught in the VM (not rethrown) so the dialog closes, a "Cancelled · n of m done" message shows and the folder refreshes; the tally lives in a caller-owned `TransferStats` because a cancelled suspend call can't return a result.
- **Details**: `showDetails()` gets a cheap `describe()` immediately, then `scan()` streams running totals into `state.details` until done; `closeDetails()` cancels the scan.
- **Copy/move** use an in-VM `Clipboard(paths, move)`; user navigates to the destination and taps "Copy here/Move here". Name conflicts auto-rename to `name (1).ext`. Folder-into-itself is blocked.

## Key design decisions (and why)
1. **Compose + LazyColumn** rather than RecyclerView: modern, less code; with R8 the APK cost is a few MB. If APK size ever becomes critical, the alternative is Views + RecyclerView.
2. **minSdk 30:** the whole storage story is "All files access" (`MANAGE_EXTERNAL_STORAGE`); avoids legacy READ/WRITE permission paths and lets us use `StorageVolume.directory`. Trade-off: no Android 8-10.
3. **Direct `java.io/java.nio` file access, not SAF/MediaStore:** far faster and simpler for a file manager. Trade-off: cannot be published on Play without a policy exception.
4. **`Files.newDirectoryStream` + `readAttributes`:** one stat per entry (vs three with `File.isDirectory/length/lastModified`). Listing checks coroutine cancellation per entry.
5. **`FileItem` is a plain class with 5 fields**, `@Immutable`; `kind`/subtitle/icon are computed only for composed rows via `remember`. No folder child counts (would cost a listing per folder), no thumbnails (would need an image lib + cache).
6. **Stable keys (`path`) and `contentType`** in `LazyColumn`; row lambdas are hoisted and stable (via `rememberUpdatedState`) so selection changes recompose only the rows that changed.
7. **Scroll restore:** saved into `vm.scrollPositions[path]` when opening a subfolder, consumed once when the parent is shown again (applied after items load).
8. **Copy via `FileChannel.transferTo`**, move via `renameTo` with copy+delete fallback across volumes; partial copies are cleaned up on failure.
9. **Static theme, not dynamic color,** to match Samsung's neutral look; one-line swap documented in `Theme.kt`.
10. **CI without Gradle wrapper jar:** `gradle/actions/setup-gradle` installs Gradle 8.14.3 so the repo has no binary files. Release is signed with the debug key unless keystore env vars/secrets are supplied.
11. **UI strings are hardcoded English** (only `app_name` is a resource) to keep things small. Extract to `strings.xml` if localization is needed.

12. **Categories read the MediaStore index** (`MediaStore.Files`, one query, all volumes) instead of crawling the disk: instant even with 100k files. Images/Videos/Audio use `media_type`; Documents use a MIME allow-list (+ `text/*`); APKs match the APK MIME or `.apk` name. Entries are verified with one `File.isFile` stat each to hide stale index rows. Uses the deprecated-but-working `DATA` column (valid because we hold All-files access).
13. **Category sort is temporary:** entering a category saves the folder sort (`savedSort`) and switches to date-descending; leaving restores it. Sort changes made inside a category are not remembered.
14. **Paste needs a folder:** in a category there is no destination, so the paste bar only explains "Open a folder to paste" until the user navigates into one (clipboard survives navigation).

15. **Progress is byte-accurate only for real copies:** moves on the same volume are an instant `rename` (counted as done immediately); everything else is pre-sized (`treeSize`) so the bar reflects bytes. The pre-size walk costs one listing of the source tree. Updates are throttled to ~10/s so progress never floods the UI.
16. **Cancel granularity = one 2 MB chunk** (`CHUNK_BYTES`). The partially copied item is deleted; its source is never touched until its copy fully succeeded (for moves the source is deleted only after that).
17. **Details scan never follows symlinks** (loop-safe, one stat per entry) and runs iteratively, so deep trees can't overflow the stack.

## Known limitations / ideas for next steps
- Categories depend on MediaStore: freshly created/renamed files may appear after a short indexing delay, and folders with a `.nomedia` file are excluded. No per-category counts are shown on the tiles.
- Documents is a fixed MIME list (PDF, Office, OpenDocument, RTF, EPUB, `text/*`); other types (e.g. `.md`, `.json` with unknown MIME) may be missing.
- Not yet compiled or tested on device (see Status).
- Only copy/move have progress and cancel; delete, rename and new-folder show an indeterminate spinner. Cancelling a move across volumes leaves already-moved items at the destination.
- Share works for files only (Android can't share folders); APK install-from-manager not supported (needs `REQUEST_INSTALL_PACKAGES`).
- No recycle bin, zip/unzip, list/grid toggle, thumbnails, or recursive search.
- Listing loads all entries before showing (fine for ~100k); progressive/paged emission is possible if needed.
- Possible perf work: Baseline Profile for startup, `Modifier.Node`-level row tuning if profiling shows need.
- Handles only mounted volumes reported by `StorageManager`; USB OTG works if mounted as a volume with a `directory`.

## Conventions
- All disk work in `FileRepository` on `Dispatchers.IO`; UI never touches `java.io` except building `File(path)` for intents.
- Add dependencies only via `libs.versions.toml`; justify each one against APK size and startup cost.
- State changes only through `BrowserViewModel`; composables receive `BrowserState` and call VM methods.

## Changelog policy (required for every change)
- **Every change to the project gets an entry in `CHANGELOG.md`**, no exceptions (code, config, CI, docs).
- New work goes under `## [Unreleased]`; when a batch is finished, move it under a new version heading `## [x.y.z] - YYYY-MM-DD` and bump `versionName`/`versionCode` in `app/build.gradle.kts` to match.
- Each version lists what changed, grouped as Added / Changed / Fixed / Removed / Docs / Build/CI, one clear line per change.
- SemVer: patch = fixes/docs, minor = new features, major = breaking changes (e.g. min SDK raise).
- If a change affects architecture or a design decision, also update the relevant section of this file.
- Current version: **1.2.0**

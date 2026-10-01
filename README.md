# Lite Files

A small, fast Android file manager in the style of Samsung's My Files, written in Kotlin with Jetpack Compose.
No third-party runtime libraries: only AndroidX + Compose.

**Features:** home screen with categories (Images, Videos, Audio, Documents, APKs), storage overview and shortcuts (Downloads, DCIM, Pictures, …) · folder browsing · sort by name/date/size ·
search within a folder · show/hide hidden files · multi-select · rename · delete · copy · move (with live progress and cancel) · share · details (size, path, date, folder item count) · open with other apps ·
new folder · grid or list view with thumbnails (photos, video frames, album art, APK icons) · Recycle bin with restore (auto-empties after 30 days) · built-in viewers for images, video, audio, PDF and text/code (with syntax highlighting) · dark mode.

## Why it's fast and small

| Goal | How |
|------|-----|
| Fast folder loading | `java.nio` directory stream, one `stat` per entry, sorted in place on `Dispatchers.IO`; listing is cancelled the moment you navigate away |
| Instant categories | Categories query the MediaStore index in a single call instead of crawling the disk |
| Smooth thumbnails | Decoded on demand at 256 px for visible items only, 3 at a time, LRU-capped (≤ 40 MB), cancelled when scrolled away |
| Low memory | Each entry is a 5-field object (no `File`, `Uri`, thumbnails, or icons cached); no image-loading library |
| No lag on huge folders | `LazyColumn` with stable keys + `contentType`; only visible rows are composed; search/sort run off the main thread |
| Small APK | R8 minify + resource shrinking, no extra libraries, vector-only icons, dependency metadata stripped |
| Fast copy | `FileChannel.transferTo` (kernel-side copy); same-volume moves are an instant `rename`; progress updates and Cancel work in 2 MB steps |

## Get an APK without Android Studio (GitHub Actions)

1. Create a new GitHub repository and push this project (see below).
2. Open the repo's **Actions** tab → **Build APK** → the run for your push (or click **Run workflow**).
3. When it finishes (~3–5 min), download the **LiteFiles-apk** artifact from the run page, unzip it, and install `app-release.apk` on your phone.
4. **Releases:** push a tag like `git tag v1.0.0 && git push --tags` and the workflow also attaches the APK to a GitHub Release.

```bash
git init
git add .
git commit -m "Initial commit"
git branch -M main
git remote add origin https://github.com/<you>/<repo>.git
git push -u origin main
```

**Signing.** With no configuration the release APK is signed with the debug key so it installs directly (fine for personal use).
For a proper release key, add these repository secrets (Settings → Secrets and variables → Actions):
`KEYSTORE_BASE64` (`base64 -w0 release.jks`), `KEYSTORE_PASSWORD`, `KEY_ALIAS`, `KEY_PASSWORD`.
Keep using the same key for updates: Android only updates an app when the signature matches.

## Build locally

Requirements: JDK 17+, Android SDK (platform 36). Android Studio is optional.

```bash
# one-time, if you have Gradle installed (generates gradlew + wrapper jar, then commit them):
gradle wrapper --gradle-version 8.14.3

./gradlew :app:assembleRelease     # -> app/build/outputs/apk/release/app-release.apk
./gradlew :app:installRelease      # install on a connected device
```

Opening the folder in Android Studio also works; it syncs Gradle automatically.
The workflow does not need the wrapper, so the repo builds on GitHub as-is.

## First run

Android 11+ requires the special **All files access** permission for file managers. The app shows a screen that opens the
system settings page; enable it for *Lite Files* and come back.
This permission is not accepted on Google Play without a policy exception, so distribute via GitHub / sideloading.

## Customizing

- **Package name / app id:** change `namespace` and `applicationId` in `app/build.gradle.kts` and move the `com/litefiles/app` source folders.
- **App name:** `app/src/main/res/values/strings.xml`.
- **Colors:** `ui/Theme.kt` (static palette; swap in `dynamicLightColorScheme` for Material You).
- **Dependency versions:** `gradle/libs.versions.toml` (Dependabot is pre-configured: one grouped monthly PR for Actions, monthly Gradle PRs; see `.github/dependabot.yml` for what is deliberately ignored).

## Built-in viewers

Tapping a file opens it inside the app:

| Type | Viewer |
|------|--------|
| Images (JPEG, PNG, GIF, WebP, BMP, HEIC/AVIF where supported) | Swipe between images, pinch/double-tap zoom, animated GIF/WebP |
| Video (MP4, 3GP, WebM, MKV, MOV, AVI, …) | Player with seek bar, ±10 s, next/previous |
| Audio (MP3, WAV, FLAC, OGG, AAC, Opus, …) | Player with cover art and track list navigation |
| PDF | Scrollable pages, fit-width or 2x zoom |
| Text and code (txt, md, json, xml, html, css, js/ts, kt, java, py, c/cpp, go, rs, sh, sql, yaml, …) | Editor with syntax highlighting, line numbers, word wrap, save |

The viewers use Android's built-in media stack, so no libraries were added and the APK stays small. The catch: video and audio formats are
limited to what your phone's codecs support (AVI in particular often won't play). Every viewer has **More → Open with…** to hand the file
to another app. Audio stops when you leave the player (no background playback). The text editor handles UTF-8 files up to 1 MB.

## Installing APKs and "Show in folder"

Tap an `.apk` to install it with the system installer. The first time, Android asks you to allow "Install unknown apps" for Lite Files;
enable it and tap the APK again. In a category (Images, Videos, …), select a file and use **More → Show in folder** to jump to where it lives.

## Recycle bin

Deleting moves files to a hidden `.LiteFilesTrash` folder on the same storage (instant, no copying) instead of erasing them.
Open **Recycle bin** on the home screen to restore or permanently delete items, or use its menu to empty it.
Items are removed automatically after 30 days. Files deleted by other apps do not go there, and deleted files keep using space until the bin is emptied.

## Project docs

See [`CHANGELOG.md`](CHANGELOG.md) for what changed in each version, and [`PROJECT_CONTEXT.md`](PROJECT_CONTEXT.md) for architecture, file map, and design decisions.

## License

MIT

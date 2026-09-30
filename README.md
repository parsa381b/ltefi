# Lite Files

A small, fast Android file manager in the style of Samsung's My Files, written in Kotlin with Jetpack Compose.
No third-party runtime libraries: only AndroidX + Compose.

**Features:** home screen with categories (Images, Videos, Audio, Documents, APKs), storage overview and shortcuts (Downloads, DCIM, Pictures, …) · folder browsing · sort by name/date/size ·
search within a folder · show/hide hidden files · multi-select · rename · delete · copy · move (with live progress and cancel) · share · details (size, path, date, folder item count) · open with other apps ·
new folder · dark mode.

## Why it's fast and small

| Goal | How |
|------|-----|
| Fast folder loading | `java.nio` directory stream, one `stat` per entry, sorted in place on `Dispatchers.IO`; listing is cancelled the moment you navigate away |
| Instant categories | Categories query the MediaStore index in a single call instead of crawling the disk |
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

## Project docs

See [`CHANGELOG.md`](CHANGELOG.md) for what changed in each version, and [`PROJECT_CONTEXT.md`](PROJECT_CONTEXT.md) for architecture, file map, and design decisions.

## License

MIT

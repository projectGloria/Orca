# Orca

Native Android 14+ video and audio downloader. Kotlin, Compose Material 3, Room, DataStore, Media3 and on-device yt-dlp/FFmpeg. No backend, login, analytics or cookies.

## Build

Install JDK 17 and Android SDK platform 35 with build tools 35. Set `JAVA_HOME` and `ANDROID_HOME`, then run:

```powershell
.\gradlew.bat testDebugUnitTest lintDebug assembleDebug
.\gradlew.bat connectedDebugAndroidTest
```

The Gradle wrapper is pinned to 8.11.1 with a SHA-256 checksum. Maven dependencies require network access on first build. The app produces separate ARM64 and x86_64 APKs in `app/build/outputs/apk/`. HTTP is permitted for direct media sources; prefer HTTPS links.

The build patches five WebP libraries in the FFmpeg runtime to support 16 KB memory pages. This is automatic and leaves the original Maven artifacts unchanged. See [native dependency provenance](vendor/webp/README.md).

## Personal signing

The personal signing key must stay outside this repository. Create an RSA key with `keytool`, then put its configuration in the ignored `signing.properties`:

```properties
storeFile=C:/Users/you/.android/orca/orca-personal.jks
storePassword=YOUR_PASSWORD
keyAlias=orca
keyPassword=YOUR_PASSWORD
```

Run `./gradlew assembleRelease`. Keep the key and passwords safe: future updates must use the same key. No signing credentials are committed. Install the ARM64 release APK on a phone with `adb install -r PATH_TO_APK`; x86_64 builds target emulators.

## Automatic builds and GitHub releases

GitHub Actions builds and tests every push to `main`/`master` and every pull request. Debug APKs and test/lint reports are attached to the workflow run. Debug APKs use a different certificate; use the signed APKs on the **Releases** page for your phone.

To ship an update, increase **both** values in `version.properties` (`versionName` is stable `x.y.z`; `versionCode` must always increase), commit and push to `main`. The release workflow tests and builds both signed architectures, checks signatures and native alignment, creates `v<versionName>`, and publishes a GitHub release automatically. Pushing the matching `v*` tag, publishing a GitHub release for that tag, or manually running **Signed release** also works. The tag's source must contain the matching version file. Already completed releases are never overwritten; corrections need a new version.

The repository needs these encrypted **Actions secrets**, using the same key as the installed personal APK:

| Secret | Value |
| --- | --- |
| `ORCA_KEYSTORE_BASE64` | Base64 contents of the personal `.jks` signing key |
| `ORCA_STORE_PASSWORD` | Keystore password |
| `ORCA_KEY_ALIAS` | Signing alias (`orca` for the original personal key) |
| `ORCA_KEY_PASSWORD` | Key password |

Signing is restricted to the release workflow; pull requests never receive signing secrets. Missing secrets stop a release rather than publishing a differently signed APK. The workflow removes temporary signing files before uploading artifacts. Keep the original local key backup as well as the encrypted GitHub secrets.

Release assets are `Orca-<version>-arm64.apk`, `Orca-<version>-x86_64.apk`, `SHA256SUMS.txt`, and `orca-update.json`. The update manifest is uploaded last, so the app cannot offer a partially uploaded release. `scripts/release.py` generates the manifest and hashes from the same version file used by Android's `BuildConfig`.

## Check for app updates

Open **Home → Settings → App updates → Check for updates**. Orca checks the latest stable public release of [projectGloria/Orca](https://github.com/projectGloria/Orca/releases), compares Android version codes, and selects the APK matching the device's architecture. Draft and prerelease builds are excluded, and older versions are never offered as updates.

When an update is available, **Download update** opens its official GitHub APK link in your browser. Open the downloaded APK and approve Android's installation prompt. Existing downloads and settings are retained when the signing key matches. Orca never silently downloads or installs an application update. **Check for engine update** remains a separate action for yt-dlp.

## Behavior

Paste or share a public link. Shared links fill the field and never download automatically. Download starts best available video by default. Options applies to one request; Settings can enable the configuration sheet for every request. Playlist-only URLs always open a picker. A YouTube watch URL with playlist context downloads the video unless you choose the separate playlist action.

Best video uses `bestvideo*+bestaudio/best` with no cap or re-encoding, including combined streams when they offer the best video. Resolution choices cap stream height, including portrait formats, with the engine choosing streams within that cap. Audio uses `bestaudio/best` and forces FFmpeg audio stream copying, including when yt-dlp would otherwise choose an encoding fallback. An incompatible audio/container combination fails instead of silently transcoding. Source quality takes priority over device playback compatibility.

Two Android user-initiated transfer jobs drain a persisted queue. Jobs require a user action while the app is visible and display notifications. Temporary streams and partial files live in private app storage. On restart, unfinished requests become interrupted and require an explicit Resume. Resume re-extracts media URLs and lets yt-dlp resume compatible partial streams. A source that changes its streams may require a fresh transfer.

Completed files publish through MediaStore into Movies/Orca or Music/Orca. A unique request suffix prevents filename collisions. Publication records its URI and staging file before copying; retry reuses the staging file and deterministic MediaStore name. Files remain available after uninstall. Private staging files do not.

Library entries can play, share, open the source, retry or be deleted with confirmation. External media apps receive temporary read permission. Missing files produce a short actionable message. Manual engine updates are restricted to idle transfers/inspections and retain a recovery backup of the working engine.

## Validation

See [VALIDATION.md](VALIDATION.md) for actual checks and limitations. Public-source compatibility depends on yt-dlp extractors, source availability, network location and source restrictions. An implemented extractor is not evidence that a platform works on a particular network.

See [THIRD_PARTY_NOTICES.md](THIRD_PARTY_NOTICES.md) for upstream components and licenses.

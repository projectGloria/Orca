# Orca — Android video downloader

## Summary

Build a native Android app in the empty Orca repository, delivered as a personal installable APK for Android 14+. Use a clean dark interface with ocean-blue accents.

The first release supports public video links, audio-only downloads, playlists, background downloading, built-in playback, and file sharing. Prioritize YouTube, Instagram, TikTok, X, Reddit, and Facebook for real-world testing.

Use yt-dlp for broad source support. Website availability remains dependent on its extractors and source restrictions; universal support cannot be guaranteed. [Source support](https://github.com/yt-dlp/yt-dlp/blob/master/supportedsites.md).

## User experience

- **Home:** Orca title, link field, Paste, Options, and Download. Accept links shared from other Android apps, including links embedded in shared text. Receiving a link fills the field without starting a download.
- **Immediate downloading:** Download defaults to **Video · Best available**. Settings includes **Ask before downloading**, initially off. Options opens the same configuration sheet for one download without changing saved defaults.
- **Simple options:** Thumbnail, title, Video/Audio selector, and quality choices showing Best plus available resolutions. Show estimated size when known. Hide format IDs, codecs, bitrates, and command-line options.
- **Playlists:** Always show an item picker with Select all and a clearly labeled download count. A video URL containing playlist context defaults to that video; offer a separate playlist action.
- **Progress:** Show thumbnail, title, progress bar, percentage when measurable, and Cancel. Distinguish Preparing, Queued, Downloading, Finishing, Completed, and Failed. Keep the link field usable while downloads run.
- **Downloads:** Newest-first library with thumbnails, titles, duration, size, search, and All/Video/Audio filters. Tap to play; provide a visible Share button. Overflow offers original link and deletion with confirmation.
- **Player:** Built-in playback with seeking and fullscreen video. Offer Open with another app when the device cannot decode a downloaded format.
- **Navigation:** Home and Downloads destinations; Settings accessible from Home. Support large text, screen-reader labels, and accessible touch targets.

## Implementation

- Use Kotlin, Jetpack Compose, Material 3, coroutines, Room for downloads and queue state, DataStore for preferences, and Media3 for playback. Start with minimum SDK 34 and compile/target SDK 35, matching the installed SDK.
- Integrate `youtubedl-android` and FFmpeg, initially using documented version `0.18.1`. Validate bundled Python, JavaScript runtime, extraction, and merging before building the complete UI. Include dependency notices. [Android engine](https://github.com/yausername/youtubedl-android).
- Keep extraction behind a `DownloadEngine` interface covering metadata inspection, playlist listing, downloading, progress, and cancellation. Use a repository to coordinate persisted requests, background execution, and library entries. No backend or public API is needed.
- Best quality selects the engine’s best available video and audio without a resolution cap or lossy re-encoding. Merge streams into a suitable container automatically. Audio-only preserves the best source audio. Resolution choices select the best available streams within the chosen limit. [Format selection](https://github.com/yt-dlp/yt-dlp#format-selection).
- Use Android user-initiated data-transfer jobs with progress notifications. Process a persisted queue with at most two simultaneous downloads. Schedule work while the app is visible and handle system stop callbacks. [Android background transfers](https://developer.android.com/develop/background-work/background-tasks/uidt).
- Preserve recoverable partial downloads after interruptions. Retry re-extracts expired media URLs and resumes where supported. After force-stop or reboot, reconcile unfinished items and offer Resume when the app reopens.
- Download and merge in app-controlled staging storage. Publish completed videos to `Movies/Orca` and audio to `Music/Orca` through MediaStore. Mark completion only after publication succeeds; use unique filenames and recover interrupted publication without duplicate library entries.
- Share files through content URIs with temporary read permission. Keep downloaded media available outside Orca and after uninstall.
- Handle invalid links, unavailable/private content, unsupported sources, network loss, insufficient storage, extraction failures, and missing local files with short actionable messages. Keep technical details behind an expandable error view.
- Provide a manual engine-update action in Settings, showing the installed version. Run updates only while downloads are idle; preserve the working engine if an update fails.

## Validation and delivery

- Test quality selection, separate audio/video merging, portrait resolutions, unknown sizes, audio-only sources, and filename collisions.
- Test options enabled/disabled, one-time overrides, shared links, playlist selection, unavailable playlist entries, and independent item retries.
- Verify background operation with the screen locked, network interruption, cancellation, process termination, and app restart.
- Verify publication, playback, external-player fallback, sharing, deletion, and missing-file handling.
- Run Android lint, unit tests, and UI/integration tests on Android 14 and the available Android 15 emulator. Check native libraries on a 16 KB page-size environment.
- Exercise accessible public samples from all six priority platforms and direct media links. Record platform restrictions separately from app defects; do not label unverified sources as working.
- Deliver source, build instructions, and a signed ARM64 personal APK. Keep its signing key outside version control for compatible future updates. Provide an x86_64 test build for the emulator. Physical-phone validation remains outstanding until a device is available.

## Defaults and boundaries

- App name: **Orca**; application ID: `io.github.nihil.orca`; interface language: English.
- Downloads run entirely on the phone, over Wi-Fi or mobile data.
- No account login, cookies, subtitles, live-stream recording, custom commands, cloud service, analytics, or Play Store publication in this release.
- Account-required and DRM-protected media receive clear unsupported/access-required results.
- Preserve maximum source quality even when an external player is needed; never silently downgrade or transcode.

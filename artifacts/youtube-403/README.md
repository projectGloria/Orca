# YouTube HTTP 403 investigation — 2 November 2026

Reported URL: `https://www.youtube.com/watch?v=Dm_n9_XA-mA`.

The user confirmed that **Check for engine update** resolved the failed download on their device. Their original engine version was not supplied; the exact server-side rejection cause was not established.

The Android emulator live probe updated yt-dlp from `2025.11.12` to `2026.10.26`, inspected “Why So Friendly #cheetah #cat #wildlife”, and downloaded the complete 60-second video: **39,995,826 bytes, WebM**. See `android-updated.txt`. This is a sample-specific result on the test network.

The preliminary bundled-engine CLI probe downloaded small audio/video samples without reproducing the 403. Its merge failed because that standalone diagnostic command omitted FFmpeg's runtime library paths; the full app-engine probe above uses the library's proper environment and completed successfully.

The error classifier now gives plain HTTP 403 failures their own message recommending an engine update, while retaining separate explicit authentication and region/network messages. A regression unit test covers that distinction. The opt-in source probe accepts `-e sourceUrl URL` for reproducing individual reports.

Validation: `testDebugUnitTest`, `assembleDebug`, `assembleDebugAndroidTest`, and `lintDebug` passed. The three `EngineSmokeTest` instrumentation tests passed, and the opt-in source probe recorded the successful full download above.

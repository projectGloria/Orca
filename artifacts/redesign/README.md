# Orca redesign — 2 November 2026

A native Compose redesign with warm charcoal surfaces, warm white text and a lime accent. The shared design system covers Home, the link composer, navigation, the media library, transfer cards, settings, download/playlist sheets, dialogs and playback chrome.

## Visual captures

Captured with Android instrumentation on the Android 14 x86_64 emulator. Library entries are temporary local test fixtures; no media is fetched for these captures.

| Surface | Capture |
| --- | --- |
| Home | [Home](home.png) |
| Empty library | [Empty state](library-empty.png) |
| Populated library | [Media cards](library.png) |
| Settings | [Settings](settings.png) |
| Download options | [Options](options.png) |
| Playlist selection | [Playlist](playlist.png) |
| Options at 150% text | [Large text](options-large-text.png) |

## Design system

See `app/src/main/java/io/github/nihil/orca/ui/README.md` for component and token ownership. Visual constants live in theme, component metrics or screen metrics. Main screens use a shared bounded column and reusable action, panel, heading and empty-state components.

Measured contrast ratios: main text/background 16.46:1, secondary text/panel 7.44:1, primary button text/fill 11.73:1. These measurements cover those color pairs, not a complete accessibility audit.

## Validation

- `testDebugUnitTest`: 17 tests passed.
- `connectedDebugAndroidTest`: all 12 selected UI tests passed on Android 14, including transfer completion with the screen locked, playlist selection, quality/audio overrides, shared-link input, update dialogs, filter reset, Back navigation and the pinned options action at 150% text size. See [device results](ui-tests.xml).
- `lintDebug`: 0 errors, 24 warnings.
- `assembleDebug` and `assembleRelease`: both architectures built successfully.
- Signed ARM64 APK verified with APK Signature Scheme v2: [local installer](Orca-redesign-arm64.apk).
- `git diff --check`: passed.

The UI tests grant their required notification permission before launching the activity. Captures wait for Android window transitions and are collected through Gradle’s additional-test-output directory. Media fixtures have deterministic ordering and are removed after the test.

ARM64 APK SHA-256: `dd1c5f5004e389709e3689b582e6e8b45ebfe8f254a4926bbf2f84a65e726a86`.

This is a local build of the redesign; no GitHub release was published.

Physical-device validation, landscape playback and screen-reader traversal were not performed. Existing download selection, storage, queue and updater behavior remain in place.

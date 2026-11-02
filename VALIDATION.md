# Orca validation — 2 November 2026

## Build and signing

Built on Windows with JDK 17, Gradle 8.11.1, Kotlin 2.1.20, AGP 8.9.1 and Android SDK 35. Minimum Android version is 14 (API 34); target API is 35.

`assembleDebug`, `assembleDebugAndroidTest`, `assembleRelease`, `testDebugUnitTest` and `lintDebug` succeeded. The 10 JVM tests cover unrestricted best-quality selection, audio selection, resolution caps, portrait labels, embedded shared links, playlist-context routing, Unicode filenames and error classification. Lint reports zero errors and 23 warnings, primarily concerning newer dependency versions, KTX suggestions and Android backup configuration. See [lint output](artifacts/lint-results.txt) and [unit results](artifacts/unit-tests.xml).

Both personal release APKs verify with APK Signature Scheme v2. The signing certificate SHA-256 is `648016e23858949ef71fac42584cb455a8525d9a786efb5b146b539d66756beb`. The key and its configuration are stored outside this repository at `%USERPROFILE%/.android/orca/`. The local `signing.properties` is ignored by Git. Keep that directory for compatible future updates.

* [ARM64 personal APK](artifacts/Orca-0.1.0-arm64.apk)
* [x86_64 personal test APK](artifacts/Orca-0.1.0-x86_64.apk)
* [SHA-256 checksums](artifacts/SHA256SUMS.txt)
* [ARM64 signature verification](artifacts/signature-arm64.txt), [x86_64 verification](artifacts/signature-x86_64.txt)

## Emulator checks

The 14-test suite reports `OK` on Android 14 (debug build) and Android 15 with actual 16 KB pages (signed x86_64 release APK). Two opt-in entry points are skipped in normal runs, with source probing and process recovery executed separately. The release test runner was signed with the same personal key as the delivered APK. Force-stop recovery also passed against that release build.

* [Android 14 instrumentation output](artifacts/tests-android14.txt)
* [Android 15 / 16 KB instrumentation output](artifacts/tests-android15-16k.txt)
* [Force-stop recovery output](artifacts/process-recovery.txt)
* [Home screenshot](artifacts/home.png)

The tests exercise bundled Python/yt-dlp execution, QuickJS arithmetic, separate DASH video/audio merging, original-audio extraction, output stream validation with Android's media retriever, MediaStore publication, deterministic URI recovery, same-title filename collisions, deletion and missing-file detection. The DASH fixture is generated media and served over loopback, so these tests do not rely on third-party websites.

UI and repository checks cover Home/Downloads/Settings navigation, playlist count/selection and disabled unavailable entries, generic playlist detection before queueing, a one-time audio override, shared text filling the field without starting a transfer, saved Ask-before-download preferences, cancellation followed by independent Resume, and a real UIDT transfer completing after Home is pressed and the screen is locked.

The separate process test seeds a persisted in-progress request and partial file, force-stops the app, relaunches it, and verifies the request becomes Interrupted while its partial file remains intact. It does not claim a complete large-file network-resume stress test.

Source probes and the two-phase process test are opt-in; the normal suite skips those two entry points. The process test is run separately with `scripts/test-process-recovery.ps1`.

## 16 KB runtime correction

The original FFmpeg runtime in youtubedl-android 0.18.1 crashes on the 16 KB emulator because five bundled WebP libraries have 4 KB ELF alignment. Orca's build replaces only those libraries with ABI-compatible 16 KB Termux builds. Both runtime archives preserve all 55 Unix symlinks; losing that metadata breaks FFmpeg's versioned library aliases.

The packaged ARM64 APK contains 217 inspected ELF files and x86_64 contains 219; all have at least 16 KB PT_LOAD alignment. Cold-install integration tests verify that the packaged archive extracts into a working runtime. See [ARM64 audit](artifacts/native-alignment-arm64.txt), [x86_64 audit](artifacts/native-alignment-x86_64.txt), and [provenance, upstream source, package hashes and licenses](vendor/webp/README.md).

## Live source samples

These are sample-specific outcomes on this machine's network, not platform-wide guarantees. The manual engine update succeeded from bundled yt-dlp `2025.11.12` to `2026.10.26` before these probes. [Complete probe output](artifacts/source-probes.txt), [second X sample](artifacts/source-probe-x.txt).

| Source | Observed outcome |
| --- | --- |
| YouTube | “Me at the zoo” inspected and downloaded; 474,489-byte WebM. Available source heights were 240 and 144. |
| Instagram | Public reel inspected and downloaded; 1,949,801-byte MP4. |
| TikTok | Failed: source explicitly blocked the IP address. |
| X | First sample contained no downloadable video; alternate failed with HTTP 500 / Domain Not Found. Working X support remains unverified. |
| Reddit | Failed: source required account authentication, outside this release's boundaries. |
| Facebook | Failed: extractor could not parse the response. This is an unresolved compatibility limitation, not a verified account restriction. |
| Direct HTTPS MP4 | Inspected and downloaded; 2,848,208 bytes. |

The probe recorder itself finishing successfully means the observations were recorded; it does not turn failed sources into successful tests. No login, cookies or DRM bypass is implemented.

## Remaining practical validation

Physical ARM64-phone validation remains outstanding. The signed ARM64 artifact has been built, signature-checked and statically audited; emulator runtime checks use x86_64. Real OEM background policies, extended network outages with large files, reboot during publication, true disk exhaustion, a killed engine update, screen-reader traversal and external-player/share-recipient handoff still need device-level validation. Best/source quality is preserved even when a device needs an external player.

## Version 0.1.1: automatic releases and app update checks

The build now reads Android versionName/versionCode from `version.properties`. CI builds every push and pull request; signed release publication runs on a version bump, matching version tag, published release, or manual dispatch. Existing signing credentials are stored in encrypted Actions secrets, retaining the original personal signing identity.

Local verification passed 17 JVM tests (including seven update-selection and validation tests), five Python release-helper tests, Android lint, signed release assembly, and actionlint workflow validation. New cases cover ABI selection, version-code comparisons, wrong app/tag/host rejection, incomplete uploads, prerelease exclusion, monotonic release codes and immutable release assets. [Updater unit results](artifacts/app-updates-unit-tests.xml).

The baseline emulator and source-probe records above describe version 0.1.0. Current release APKs are published on [GitHub Releases](https://github.com/projectGloria/Orca/releases); live build results appear in [Actions](https://github.com/projectGloria/Orca/actions). App updates use a public GitHub metadata check and a user-approved browser download; Android verifies the APK signing identity during installation.

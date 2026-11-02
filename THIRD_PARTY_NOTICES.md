# Third-party notices

Orca includes or depends on these upstream components. Their source and license texts are linked below. This is a personal build, not a Play Store distribution.

* youtubedl-android 0.18.1 — MIT. Copyright its contributors. [Source and license](https://github.com/yausername/youtubedl-android/tree/0.18.1).
* yt-dlp — Unlicense, with separately licensed bundled dependencies. [Source and notices](https://github.com/yt-dlp/yt-dlp/blob/master/THIRD_PARTY_LICENSES.txt), [license](https://github.com/yt-dlp/yt-dlp/blob/master/LICENSE).
* Python — PSF license and included third-party licenses. [License](https://docs.python.org/3/license.html).
* QuickJS — MIT, Copyright Fabrice Bellard and Charlie Gordon. [Source and license](https://github.com/bellard/quickjs/blob/master/LICENSE).
* FFmpeg 7.1.1 — bundled build enables GPL and version 3, including x264/x265. Native binaries are supplied by youtubedl-android. [Build instructions](https://github.com/yausername/youtubedl-android/blob/0.18.1/BUILD_FFMPEG.md), [legal information](https://ffmpeg.org/legal.html), [source](https://ffmpeg.org/download.html).
* WebP/libsharpyuv 1.6.0 — BSD license and patent grant. Orca replaces five native WebP libraries per supported ABI with 16 KB-aligned Termux builds. See [provenance and checksums](vendor/webp/README.md). [Source and license](https://github.com/webmproject/libwebp/tree/v1.6.0).
* AndroidX (Compose, Activity, Lifecycle, Room, DataStore, Media3) — Apache 2.0. [Source](https://android.googlesource.com/platform/frameworks/support/), [license](https://www.apache.org/licenses/LICENSE-2.0).
* Kotlin and kotlinx.coroutines — Apache 2.0. [Source and license](https://github.com/JetBrains/kotlin/blob/master/license/LICENSE.txt).
* Coil 2.7.0 — Apache 2.0. [Source and license](https://github.com/coil-kt/coil/tree/2.7.0).
* Jackson — Apache 2.0. [Source](https://github.com/FasterXML/jackson).
* Apache Commons IO — Apache 2.0. [Source and notices](https://commons.apache.org/proper/commons-io/).

Engine updates may change yt-dlp and its included dependencies independently of Orca. The full dependency graph can be obtained with `./gradlew :app:dependencies`. Gradle packaging excludes duplicate META-INF license resources; notices are included as application assets instead.

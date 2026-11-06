**Orca Android app — technical analysis**

Reviewed 2–3 November 2026 (Europe/Istanbul). Source commit: 9701c9511ef451f065b5c7200f8877244b6aa474. App version: 0.1.1, versionCode 2. This review covers the current workspace, including the Compose redesign and diagnostics changes.

**Assessment**

Orca implements a working downloader with a clear product boundary, persistent queue, original-quality format selection, MediaStore publication, and a coherent native interface. Fresh builds, ordinary workflow tests, a cold install on Android 15 with 16 KB pages, and signed APK verification pass.

The strongest weaknesses are recovery and lifecycle correctness. Seven targeted probes reproduced seven defects: two separate engine-backup failures, a cancellation/start race, cancellation during publication, background playback, lost options after recreation, and lost library search after navigating to Settings. An independent ELF scan also established that the existing native checker omits 41 libraries per ABI, although all omitted libraries currently pass alignment requirements.

This is a broad source and runtime review, not a guarantee of zero remaining defects. Physical ARM64 devices, hostile-input fuzzing, every supported website, and prolonged adverse-network/storage conditions were not exhaustively tested. Confidence and limitations are stated below rather than inferred from passing tests.

**1. Confirmed findings, ordered by priority**

P1 means fix before relying on the affected recovery guarantee. P2 means a material correctness, lifecycle, or validation problem. P3 means a lower-impact usability problem. These priorities are engineering judgments, not CVSS security scores.

| ID | Priority | Finding | Evidence |
| --- | --- | --- | --- |
| F01 | P1 | Engine update deletes its own recovery backup | Actual bundled updater, isolated local HTTP fixture |
| F02 | P1 | A partial backup can replace a healthy engine at startup | Isolated filesystem probe |
| F03 | P2 | Cancellation can commit before the download nevertheless starts | Deterministic repository/engine handoff probe |
| F04 | P2 | Cancellation during publication leaves a public file marked Cancelled | Real MediaStore, 128 MiB staging-file probe |
| F05 | P2 | Playback continues after the activity stops | Actual PlayerView/ExoPlayer lifecycle probe |
| F06 | P2 | Activity recreation silently resets download choices | Compose UI probe |
| F07 | P3 | Settings round trip discards library search | Compose UI probe |
| F08 | P2 | Native alignment checker skips versioned shared libraries | Independent scan by ELF magic bytes |

The seven regression probes deliberately assert the defective current behavior. Their passing result demonstrates reproduction; it does not mean these behaviors are acceptable. Source: [AuditRegressionProbe.kt](<C:/Users/Nihil/Desktop/Gloria Repoz/Orca/artifacts/audit/AuditRegressionProbe.kt>). Result: [seven-probe run](<C:/Users/Nihil/Desktop/Gloria Repoz/Orca/artifacts/audit/regression-probes.txt>). Probe source is retained outside the app's normal test tree.

**F01 — The engine backup is inside a directory the updater deletes**

Location: [Engine.kt:100](<C:/Users/Nihil/Desktop/Gloria Repoz/Orca/app/src/main/java/io/github/nihil/orca/Engine.kt:100>).

Orca copies the current engine into yt-dlp.orca-backup beside yt-dlp, then calls YoutubeDL.updateYoutubeDL. The pinned wrapper's installer deletes the entire yt-dlp directory before copying in the replacement. The backup is therefore gone during the part of installation where it is needed most. If post-install version verification fails, Orca's catch block attempts to restore a nonexistent backup and can mask the original failure.

I exercised the actual dependency with a loopback metadata/download server and an isolated Context storage directory. The replacement file was installed and the Orca backup no longer existed. The real installed engine was not replaced by this fixture.

The upstream behavior is directly visible in [YoutubeDLUpdater.kt, version 0.18.1](https://raw.githubusercontent.com/yausername/youtubedl-android/0.18.1/library/src/main/java/com/yausername/youtubedl_android/YoutubeDLUpdater.kt).

Fix: put recovery material outside the updater-owned directory. Preserve the old file until the new engine passes verification, and ensure rollback preserves the original exception. Also account for the wrapper's version preferences during rollback so the updater does not incorrectly consider a restored older binary current.

Regression acceptance: a failed install, a failed version check, and a killed process at every installation boundary all leave either a verified new engine or a working old/bundled engine.

**F02 — An incomplete backup is trusted as authoritative**

Locations: [Engine.kt:48](<C:/Users/Nihil/Desktop/Gloria Repoz/Orca/app/src/main/java/io/github/nihil/orca/Engine.kt:48>) and [Engine.kt:104](<C:/Users/Nihil/Desktop/Gloria Repoz/Orca/app/src/main/java/io/github/nihil/orca/Engine.kt:104>).

The backup is written directly to its final name with copyTo. On the next initialization, merely finding that filename causes it to overwrite the engine and then be deleted. A process kill or storage error during backup creation can leave a truncated file. That damaged file can subsequently replace a perfectly healthy executable. Backup creation also occurs outside the update try/catch.

An isolated probe seeded a healthy file and a partial backup, invoked Orca's initialization path, and confirmed that the partial contents replaced the healthy file. This tests the restoration logic, not a randomly timed power-loss simulation.

Fix: write a temporary backup outside the updater's directory, validate it, flush it as needed, and atomically rename it into the committed backup location. Use a recovery marker with explicit states and validate before restoring. Keep a fallback to the bundled engine.

F01 and F02 require separate corrections: moving the backup alone does not make backup creation atomic.

**F03 — A cancelled request can still begin its full transfer**

Location: [Downloads.kt:103](<C:/Users/Nihil/Desktop/Gloria Repoz/Orca/app/src/main/java/io/github/nihil/orca/Downloads.kt:103>).

After inspection, run checks/persists the active state and emits a notification. It then calls engine.download without coordinating process registration with cancellation. If Cancel arrives after the last state check but before the engine registers the process, cancel records CANCELLED and tries to kill a process that does not exist yet. The worker then starts it.

The deterministic probe cancelled in the notification callback at exactly this boundary. The final row stayed CANCELLED, yet the fake engine's download method was invoked. With the real engine, this window can consume bandwidth and storage after the user has cancelled. Later state checks prevent ordinary completion, but do not prevent that wasted work.

There is a related static problem: inspect and playlist processes use random IDs, while cancel only knows the download UUID. A cancellation while a worker is inspecting does not target that inspection process.

Fix: make process ownership and cancellation one protocol. Register a cancellable operation before launch, check cancellation during startup, and kill a newly registered process if cancellation won the race. Propagate ownership through inspection, download, and postprocessing; another pre-call state check alone still leaves a race.

Regression acceptance: cancel at inspection, launch, stream download, merge, and publication boundaries; verify process termination as well as Room state.

**F04 — Publication ignores cancellation while copying**

Locations: [Downloads.kt:118](<C:/Users/Nihil/Desktop/Gloria Repoz/Orca/app/src/main/java/io/github/nihil/orca/Downloads.kt:118>) and [Storage.kt:68](<C:/Users/Nihil/Desktop/Gloria Repoz/Orca/app/src/main/java/io/github/nihil/orca/Storage.kt:68>).

After recording the MediaStore URI, publication performs a blocking stream copy and clears IS_PENDING without checking cancellation. Cancellation sets the database row to CANCELLED, but publication still exposes the finished file to other apps. The final saveActive rejects the COMPLETED transition, so the public file and the application's status disagree. The private staging copy also remains.

The runtime probe cancelled after the URI was recorded while copying a 128 MiB file. It verified all three conditions: the row was CANCELLED, the MediaStore row had IS_PENDING=0, and the private staging file remained.

Fix: choose and implement one consistent policy. Either make final publication an explicit completion boundary where cancellation is no longer offered, or use a cooperative copy and keep/delete pending output on cancellation. Serialize the publish/complete decision with the request state. A retry that overwrites an already public URI should also restore pending status before modifying its contents.

Regression acceptance: cancellation never results in an unexplained publicly visible file whose sole library entry is Cancelled; retries neither duplicate nor expose incomplete files.

**F05 — The player outlives the visible activity**

Location: [MainActivity.kt:394](<C:/Users/Nihil/Desktop/Gloria Repoz/Orca/app/src/main/java/io/github/nihil/orca/MainActivity.kt:394>).

PlayerDialog creates ExoPlayer and releases it only when its composition is disposed. Stopping the activity does not dispose that composition. The runtime probe opened real local media, kept playback active, moved the activity to CREATED (therefore stopping it), and verified that player.isPlaying remained true.

Consequences include unexpected background audio and potentially simultaneous playback when opening an external player. There is no MediaSessionService or background playback notification establishing an intentional background-player contract. Audio-focus and headset-disconnect handling are also not configured explicitly in this player; those interruptions need separate tests.

Fix: bind foreground playback to lifecycle start/stop and preserve position deliberately. If background audio is a desired feature, implement it as a service-backed media session with appropriate controls. Android documents lifecycle release for activity-hosted players in its [Media3 playback guidance](https://developer.android.com/media/implement/playback-app).

Regression acceptance: Home, lock, interruption, external-player handoff, and rotation produce the intended pause/resume behavior without overlapping playback.

**F06 — Rotation/recreation changes the user's requested download**

Location: [MainActivity.kt:290](<C:/Users/Nihil/Desktop/Gloria Repoz/Orca/app/src/main/java/io/github/nihil/orca/MainActivity.kt:290>).

The options sheet uses remember for audio, resolution, and selected playlist indexes. Its source media survives an activity recreation in the ViewModel, but its choices do not. Audio resets to Video, a resolution cap resets to Best, and a narrowed playlist selection resets to all available items.

The UI probe selected Audio, recreated the activity, and confirmed that the still-open sheet had switched back to Video. The playlist consequence follows the same initialization code and could cause substantially more downloads than the user intended if they confirm without noticing.

Fix: save the selection by stable request/item identifiers using a ViewModel or appropriate saveable state. Preserve user input across process recreation where practical; link/options data in the current ViewModel have no SavedStateHandle backing.

Regression acceptance: rotation and supported process restoration retain format, quality, and the exact playlist selection.

**F07 — Library context is lost when returning from Settings**

Location: [MainActivity.kt:214](<C:/Users/Nihil/Desktop/Gloria Repoz/Orca/app/src/main/java/io/github/nihil/orca/MainActivity.kt:214>).

Search and filter use rememberSaveable inside a conditionally rendered LibraryScreen. Showing Settings removes that screen from composition without a SaveableStateHolder. Returning reconstructs its state. The test entered a unique search term, opened Settings, returned, and verified the search was absent.

Fix: hoist destination state or preserve each destination's saveable state explicitly. Include search, filter, and scroll position. The existing navigation test returns to the library but does not assert preservation of a nonempty search.

**F08 — The native checker omits important ELF files**

Location: [check_native_alignment.py:12](<C:/Users/Nihil/Desktop/Gloria Repoz/Orca/scripts/check_native_alignment.py:12>).

The checker inspects filenames ending in .so, which misses versioned filenames such as libavcodec.so.61.19.101, libavformat.so.61.7.100, libxml2.so.16.0.6, and libvpx.so.11.0.1 inside the runtime archive.

| Architecture | Existing checker | Scan by ELF magic | Omitted | Below 16 KB |
| --- | ---: | ---: | ---: | ---: |
| ARM64 | 217 | 258 | 41 | 0 |
| x86_64 | 219 | 260 | 41 | 0 |

The current binaries pass. The defect is incomplete validation, which could allow a future incompatible runtime through release CI.

Fix: identify ELF files by their header after recursively opening the packaged runtime archives, regardless of filename suffix. Fail when no ELF files are found. Retain a cold-start runtime test in addition to static alignment checks.

Reproducible checker: [full_native_audit.py](<C:/Users/Nihil/Desktop/Gloria Repoz/Orca/artifacts/audit/full_native_audit.py>). Inventories: [ARM64](<C:/Users/Nihil/Desktop/Gloria Repoz/Orca/artifacts/audit/full-elf-arm64-v8a.json>) and [x86_64](<C:/Users/Nihil/Desktop/Gloria Repoz/Orca/artifacts/audit/full-elf-x86_64.json>).

**2. Additional source-level risks and limitations**

These deserve follow-up, but were not promoted to reproduced runtime findings.

| Area | Evidence and consequence | Suggested correction |
| --- | --- | --- |
| Main-thread job stop | onStopJob uses runBlocking for repository cancellation and database updates. Cancellation waits for claim and a synchronous process-kill path. Slow storage/lock contention can hold the main thread. An ANR was not induced. | Keep stop callbacks bounded; record stop state and terminate workers through coordinated asynchronous ownership. See [JobService](https://developer.android.com/reference/android/app/job/JobService). |
| Stop/claim ownership window | next marks PREPARING and adds inFlight before TransferService records current[jobId]. A stop at that seam may not know which item it must interrupt. The finally that releases inFlight is inside run, which may not yet have begun. | Make acquisition and cleanup belong to one worker scope with unconditional finalization; add a deterministic stop-at-claim test. |
| Repeated options confirmation | OrcaModel.enqueue has no submitting guard, and the sheet remains enabled until insertion/scheduling finishes. Multiple taps can create distinct UUID requests for the same selected items. | Disable synchronously on submission and make confirmation idempotent per sheet/request. |
| Partial playlist enqueue | Each row is inserted separately and scheduling occurs afterward. An insertion failure can leave a partially accepted list without scheduling it. | Insert a batch transactionally and provide explicit recovery if scheduling fails. |
| UIDT visibility | The UI performs potentially slow inspection before scheduling. If the user backgrounds the activity during that work, scheduling may occur outside Android's allowed visible state. The code catches rejection and marks queued work Interrupted. | Test this path and make the user-visible recovery intentional; UIDT eligibility is described in [Android guidance](https://developer.android.com/develop/background-work/background-tasks/uidt). |
| Unbounded library work | Every progress write can invalidate an observation of all rows. next and finishIfEmpty also load the entire table; filters run over all rows in the UI. | Use targeted queue queries and indexes, paged history, and separate summary/progress streams when scale requires it. |
| Unbounded playlist/diagnostic input | Engine output is collected into strings, parsed as a whole JSON object, and playlist entries are fully materialized. No playlist size limit or operation-wide deadline is present. | Bound inspection output and list size, support cancellation/pagination, and cap persisted diagnostics. |
| Link normalization | The regex finds an HTTP-like substring and strips trailing punctuation even when punctuation can legally be part of a URL. It does not fully validate hosts. Playlist entries use startsWith(\"http\"). | Parse and validate schemes/hosts consistently while preserving signed URL bytes. |
| Large-file storage demand | Separate streams, merged output, and MediaStore copy can coexist. No preflight free-space estimate is implemented. Failed/cancelled staging is intentionally retained without an automatic storage-management UI. | Estimate temporary storage needs and expose retained partial-file size/cleanup. |
| Missing-file UX | Play and Share check existence, but a missing completed item remains counted as saved and has no direct Retry action. | Offer a redownload action that preserves the source/options without requiring manual navigation. |
| Foreground inspections | Inspection and engine updates have no user cancel action. Socket timeout does not bound total retries, playlist enumeration, merging, or every upstream metadata request. | Add whole-operation cancellation/deadlines with process cleanup. |
| Version/status diagnostics | Coroutine cancellation is swallowed by the general action helper, unlike checkAppUpdate. Some message paths reuse old technical details; openLink failures are silently ignored. | Rethrow CancellationException and model message/details as one state value. |
| Future schema evolution | Room is version 1 with exportSchema=false, no migrations, and stringly typed states. This is not a current migration failure. | Export schema and add migration tests before changing persisted entities. |


**3. Architecture and execution flow**

The production application contains 14 Kotlin files and 1,294 physical lines, plus its manifest, resources, and notices. It is one Android application module. The compact size makes the control flow understandable, but many unrelated responsibilities are concentrated in MainActivity.kt and Downloads.kt.

| Component | Responsibility | Assessment |
| --- | --- | --- |
| [MainActivity.kt](<C:/Users/Nihil/Desktop/Gloria Repoz/Orca/app/src/main/java/io/github/nihil/orca/MainActivity.kt>) | Activity entry, shared-text intake, ViewModel, navigation, library/options/settings, playback and sharing | Feature-complete for the stated scope; lifecycle and screen-state ownership need improvement |
| [Downloads.kt](<C:/Users/Nihil/Desktop/Gloria Repoz/Orca/app/src/main/java/io/github/nihil/orca/Downloads.kt>) | Application setup, queue orchestration, update exclusion, JobService, notifications, cancel receiver, error classification | Persistence is sound in ordinary paths; operation ownership is fragmented across mutexes, sets, job maps and database strings |
| [Engine.kt](<C:/Users/Nihil/Desktop/Gloria Repoz/Orca/app/src/main/java/io/github/nihil/orca/Engine.kt>) | URL parsing, format selection, extractor adapter, output receipt, engine initialization/update | Useful DownloadEngine seam; cancellation/update guarantees do not match the implementation |
| [Storage.kt](<C:/Users/Nihil/Desktop/Gloria Repoz/Orca/app/src/main/java/io/github/nihil/orca/Storage.kt>) | Room entities/DAO, DataStore preference, MediaStore publication/deletion, filenames | Good scoped-storage approach; cross-store completion is not atomic |
| [AppUpdates.kt](<C:/Users/Nihil/Desktop/Gloria Repoz/Orca/app/src/main/java/io/github/nihil/orca/AppUpdates.kt>) | Public GitHub metadata retrieval and compatible APK selection | Conservative manual update flow with useful validation |
| [UI directory](<C:/Users/Nihil/Desktop/Gloria Repoz/Orca/app/src/main/java/io/github/nihil/orca/ui/README.md>) | Theme tokens, shared surfaces/buttons, metrics and home layout | Consistent design ownership; maintain this structure when splitting the larger activity file |
| [Build configuration](<C:/Users/Nihil/Desktop/Gloria Repoz/Orca/app/build.gradle.kts>) | ABI splits, signing, dependencies and native-runtime patch | Functional and pinned; reproducibility/security maintenance can be strengthened |
| [Release helper](<C:/Users/Nihil/Desktop/Gloria Repoz/Orca/scripts/release.py>) | Version validation, hashes, manifest and publication | Good readiness marker and immutable-complete-release policy; additional failure-path tests warranted |

~~~mermaid
flowchart TD
    A[Paste or Android share] --> B[LinkParser and OrcaModel]
    B --> C[yt-dlp inspection]
    C --> D{Playlist or options required?}
    D -->|Yes| E[Options and item selection]
    D -->|No| F[Persist Room requests]
    E --> F
    F --> G[Two user-initiated JobScheduler jobs]
    G --> H[Claim oldest queued item]
    H --> I[Reinspect and download through yt-dlp]
    I --> J[FFmpeg merge or audio stream copy]
    J --> K[Private staging file]
    K --> L[Record MediaStore URI and copy]
    L --> M[Clear pending flag and save Completed]
    M --> N[Play / share / delete]
    O[Manual engine update] --> P[Repository idle gate]
    P --> Q[Backup and upstream updater]
    R[Manual app update check] --> S[GitHub metadata and ABI selection]
    S --> T[User opens browser download]
~~~

The diagram describes the intended ordinary sequence. F01–F06 identify places where interruption or lifecycle behavior differs from that sequence.

The persistent state path is QUEUED → PREPARING → DOWNLOADING/FINISHING → COMPLETED. A failure becomes FAILED; explicit cancellation becomes CANCELLED; startup reconciles unfinished rows into INTERRUPTED. Resume requeues the same UUID. Failed/cancelled/interrupted work retains private files so compatible partial streams can be reused.

Two JobScheduler IDs (701 and 702) each drain the shared queue. The claim mutex avoids ordinary duplicate worker claims. A gate and operation counter prevent an engine update from running concurrently with repository-mediated inspection/download operations. Resume rejects requests that remain in the inFlight set.

These safeguards are useful, but are not a complete state machine: Room transitions, subprocess startup, publication, JobService ownership, and cancellation are separate actions. The targeted races demonstrate why each boundary needs an explicit owner and completion rule.

**4. Functional behavior and product boundaries**

| Capability | Actual implementation |
| --- | --- |
| Input | First HTTP/HTTPS-looking link from typed/pasted/shared text; sharing fills the field without starting work |
| Default transfer | Best available video; inspection occurs before queueing and again in the worker |
| Video selection | bestvideo*+bestaudio/best; capped variant limits height in both merged and combined fallbacks |
| Audio | bestaudio/best plus extraction with explicit FFmpeg audio stream copy; incompatible output can fail rather than transcode |
| Portrait quality | Labels retain actual pixel height and identify portrait orientation |
| Playlists | YouTube playlist-only routing plus generic extractor detection; selectable available entries and a count |
| Playlist context | A watch URL with list context remains a single-video request unless the separate playlist action is chosen |
| Background transfer | Android 14+ user-initiated jobs with progress notifications and network requirement |
| Recovery | Persisted request state and private partial files; explicit Resume after process restart |
| Public storage | Videos in Movies/Orca, audio in Music/Orca; Unicode-safe title plus request UUID |
| Library | Newest-first title search, media-type filters, player, share, original link, confirmed deletion |
| Preferences | One persistent Boolean: ask before downloading |
| Engine update | Manual, separate from application update; current recovery defects described above |
| App update | Manual GitHub check, versionCode comparison, ABI-specific browser download |
| Excluded features | Login/imported cookies, DRM bypass, subtitle download, arbitrary commands, live-stream recording, cloud sync and Play Store delivery |

Source quality takes precedence over playback compatibility. There is no universal promise that every selected codec plays on the device. The external-player affordance is therefore relevant, although handoff/lifecycle behavior needs correction.

Quality estimates are not final transfer sizes: a format's video-only filesize may omit audio, and many sources do not provide a usable estimate. The selector unit tests confirm strings and basic routing; they do not establish optimal selection for every extractor.

Flat playlist entries normally do not include the full format list, so the playlist quality menu derives from limited first-entry metadata and will commonly offer only Best. A playlist may contain inaccessible items whose restrictions are discovered only during full extraction.

**5. Storage, data retention and privacy**

Orca stores request URLs, titles, thumbnail URLs, durations, choices, timestamps, progress, publication URIs, staging paths, sizes, MIME types, and technical errors in Room. Failed error details can contain source URLs and signed query parameters returned in engine diagnostics. They remain local in the app's private database; no remote telemetry endpoint appears in application source.

The staging directory is keyed by UUID. Output media names are sanitized and bounded by code points, preserving Unicode without splitting surrogate pairs. Public filenames include the UUID, reducing ordinary collisions. The MediaPublisher records a deterministic name and queries it on retry, addressing a process death between MediaStore insertion and Room persistence.

A normal successful download publishes before marking completion, then removes staging. This is substantially safer than treating engine exit alone as completion. However, publication is a sequence of filesystem, provider and Room operations rather than one transaction, so F04 and crash-boundary tests matter.

Deleting a library entry deletes its known MediaStore URI, any matching deterministic request filename, private staging and the database row. Destructive UI confirmation is present. It does not silently delete unrelated history. Missing-file checks are defensive but do not repair the saved-file count.

Public output intentionally survives uninstall. The private library database, settings, partial files and extracted runtime are not designed as portable backups. Reinstall does not scan Movies/Orca or Music/Orca to reconstruct the library.

Privacy boundaries:

- No application backend, analytics SDK, advertising SDK, login UI or custom WebView was found in the reviewed source/dependency graph.
- Download sites/CDNs still receive ordinary network requests, including the user's IP address and source requests.
- Coil can request remote thumbnail URLs when library cards are shown, so browsing previously saved entries is not necessarily network-free.
- Manual app updates contact GitHub; manual engine updates contact upstream release services.
- HTTP sources are deliberately enabled application-wide, so those requests do not gain HTTPS confidentiality.
- No broad media/storage read permission or application-installer permission is declared.
- allowBackup=false is present, but explicit Android 12+ data-extraction rules are absent. Device-to-device behavior can differ by manufacturer; review the [platform backup behavior](https://developer.android.com/about/versions/12/behavior-changes-12) before describing all forms of data transfer as disabled.

This review did not perform packet capture of every dependency or prove absence of all network activity inside bundled native/Python code.

**6. Security surface and updates**

The merged release manifest was inspected, not just the source manifest. It includes the launcher/share activity as exported, the transfer service and cancel receiver as non-exported, a non-exported AndroidX startup provider, a non-exported Room invalidation service, and an AndroidX profile-install receiver protected by android.permission.DUMP. The application-specific dynamic receiver permission is signature-protected. No debuggable flag appears in the merged release application.

Shared input fills UI state and does not automatically download. There is no app-level shell command interpolation of user URLs. Native work is delegated to the pinned wrapper, which constructs a subprocess argument list. That reduces ordinary shell-injection risk but does not prove the external extractors/parsers safe against hostile content.

The app updater has positive controls:

- Public HTTPS release metadata; no embedded GitHub credential.
- Connection/read timeouts and a 1 MiB metadata-response bound.
- Checks for schema, application ID, stable release status, tag/version consistency, positive version code, supported ABI and exact expected APK name.
- Release-download URLs restricted to HTTPS github.com and the Orca release path, rejecting userinfo, ports, query strings and fragments.
- No offered downgrade by versionCode and no automatic installation.
- Incomplete release manifests/assets produce a preparation state rather than an install prompt.

Limitations: metadata SHA-256/size fields are produced by release tooling but not verified in the app's browser-based download flow. The app is validating the download destination, not cryptographically validating downloaded APK bytes itself. Signature enforcement is deferred to Android installation. Redirect behavior and malformed/deep JSON were not fuzzed. The old Jackson parser merits dependency maintenance; its age alone is not evidence of an exploitable vulnerability here.

Engine updating has a separate trust model: it downloads executable Python code through the upstream updater and is not an Android APK update. The local wrapper does not independently verify an engine artifact signature/hash. Backup correctness, verified fallback and upstream dependency maintenance are consequently important.

**7. Dependencies, packaging and release engineering**

Pinned build inputs include Gradle 8.11.1 (wrapper distribution checksum present), AGP 8.9.1, Kotlin 2.1.20, KSP 2.1.20-1.0.32 and JDK 17. Minimum API is 34; compile/target API is 35. APKs are split for arm64-v8a and x86_64; there is no universal or 32-bit build.

The resolved release graph includes Compose BOM 2025.04.01, Activity 1.10.1, Lifecycle 2.9.0, Room 2.7.1, DataStore 1.1.4, Media3 1.6.1, Coil 2.7.0, youtubedl-android 0.18.1 and Jackson 2.11.1. Transitive dependencies include OkHttp 4.12.0, coroutines 1.8.1, Commons IO 2.5 and runtime Commons Compress 1.12. The Gradle patching script's Commons Compress 1.26.1 is a separate build-time dependency. Evidence: [resolved graph and release build](<C:/Users/Nihil/Desktop/Gloria Repoz/Orca/artifacts/audit/release-build-and-dependencies.txt>).

No dependency lockfiles or Gradle dependency-verification metadata are committed. Direct versions and action SHA pins are useful, but do not by themselves provide a complete independently verified software supply chain. No comprehensive CVE/SBOM scan was performed; this report does not equate old version numbers with demonstrated exploitation.

The build substitutes five WebP libraries per ABI in a generated FFmpeg AAR, retaining nested ZIP Unix modes. Vendor documentation identifies Termux package versions, package hashes and source/license links. An independent scan found all 518 ELF files across both release APKs have PT_LOAD alignment of at least 16 KB. The cold Android 15 runtime tests complement this static result.

Fresh signed release outputs:

| ABI | APK bytes | Approximate MiB | Signature |
| --- | ---: | ---: | --- |
| ARM64 | 107,315,950 | 102.3 | APK Signature Scheme v2 verified |
| x86_64 | 110,296,918 | 105.2 | APK Signature Scheme v2 verified |

Both use certificate SHA-256 648016e23858949ef71fac42584cb455a8525d9a786efb5b146b539d66756beb, matching the recorded personal signing identity. Signing credentials were neither printed nor added to the report. [Certificate verification](<C:/Users/Nihil/Desktop/Gloria Repoz/Orca/artifacts/audit/signature-arm64.txt>) and [APK hashes](<C:/Users/Nihil/Desktop/Gloria Repoz/Orca/artifacts/audit/release-sha256.txt>) are retained.

R8/minification and resource shrinking are not enabled. The embedded interpreter/native runtime dominates functionality and installation footprint, while the unminified app/dependency code contributes additional size. Actual installed/extracted storage and startup memory were not profiled. Two simultaneous engines and FFmpeg operations should be measured on a memory-constrained physical phone.

CI uses pinned action revisions and read-only repository permission for ordinary builds. Signing secrets are restricted to the release workflow. The release flow tests/builds, verifies APK signatures and native alignment, creates hashes and update metadata, uploads APKs/checksums, then uploads the readiness manifest last. Completed releases are not overwritten by the helper. Those are good choices.

Release gaps:

- Neither workflow runs emulator instrumentation, so the highest-risk storage/queue/lifecycle behaviors are not protected by CI.
- F08 means the current native release gate is incomplete.
- Signature verification checks validity, but does not explicitly assert the expected production certificate fingerprint; an incorrectly configured but valid key can generate incompatible updates.
- Existing tag-to-source commit correspondence is not explicitly checked by the helper when publishing an incomplete/preexisting tag.
- A rerun that finds the manifest immediately considers the release complete; publication failure after manifest upload but before draft publication deserves an explicit recovery test.
- The helper checks up to 100 releases and stops at the first prior stable manifest; historical irregularities and pagination are not covered.
- The Windows process-recovery helper assumes one adb device and an installed instrumentation runner. After Gradle removed that runner, the first audit invocation failed at setup; reinstalling it allowed both phases to pass. This was a harness prerequisite, not an app recovery failure.

Notices and license texts are packaged, including FFmpeg GPL/LGPL, Python, QuickJS, yt-dlp and WebP. There is no root license file licensing Orca's own source, and the app's notice UI only shows a summary without opening the bundled full texts. Complete distribution/license compliance was not assessed. Preserve exact native build/source provenance and review redistribution obligations before expanding distribution; the authoritative FFmpeg information is its [legal page](https://ffmpeg.org/legal.html).

**8. Interface and accessibility review**

The interface has a consistent charcoal/lime palette, semantic Material colors, shared typography/spacing/shapes, reusable action buttons and bounded content width. Primary actions use at least 48 dp targets (56 dp for the main action), icon-only buttons have useful labels, media titles wrap to two lines, unavailable playlist entries are disabled, and deletion requires confirmation.

Fresh screenshots of Home, empty/populated library, Settings, download options and playlist selection were reviewed. No obvious clipping of the main controls was visible at the captured phone dimensions. The options confirmation remains visible in the existing enlarged-density test. Captures are fixtures, not proof of every real-content/device layout.

| Surface | Capture |
| --- | --- |
| Home | [home.png](<C:/Users/Nihil/Desktop/Gloria Repoz/Orca/artifacts/audit/screenshots/home.png>) |
| Library | [library.png](<C:/Users/Nihil/Desktop/Gloria Repoz/Orca/artifacts/audit/screenshots/library.png>) |
| Empty library | [library-empty.png](<C:/Users/Nihil/Desktop/Gloria Repoz/Orca/artifacts/audit/screenshots/library-empty.png>) |
| Settings | [settings.png](<C:/Users/Nihil/Desktop/Gloria Repoz/Orca/artifacts/audit/screenshots/settings.png>) |
| Options | [options.png](<C:/Users/Nihil/Desktop/Gloria Repoz/Orca/artifacts/audit/screenshots/options.png>) |
| Playlist | [playlist.png](<C:/Users/Nihil/Desktop/Gloria Repoz/Orca/artifacts/audit/screenshots/playlist.png>) |
| Enlarged-density fixture | [options-large-text.png](<C:/Users/Nihil/Desktop/Gloria Repoz/Orca/artifacts/audit/screenshots/options-large-text.png>) |

The strongest interface issues are F05–F07. Other limitations are English strings embedded in Kotlin, no light-theme choice, no measured TalkBack traversal, no tested switch-access/keyboard workflow, and no comprehensive 200% font-scale, landscape, small-screen, tablet or foldable matrix. supportsRtl=true is not proof of localized/RTL readiness.

The player uses a fixed 16:9 default area; fullscreen changes layout weight rather than establishing a complete orientation/immersive-mode policy. Portrait, audio-only and landscape playback deserve explicit device tests. Current playback has no saved position across activity recreation.

Normal permission flow also deserves attention: notification permission is launched from onCreate and the callback is ignored. Existing UI tests grant it up front, so they do not validate denial, rationale, or later settings recovery.

**9. Fresh verification and evidence**

| Check | Result | Scope/qualification |
| --- | --- | --- |
| JVM tests | 18 passed | Explicitly reran the test task; 7 app-update and 11 selection/error tests |
| Python release-helper tests | 5 passed | Version, packaging, monotonic code and immutable-release cases |
| Android lint | 0 errors, 24 warnings | Current report; mostly dependency-version/KTX suggestions plus extraction-rule warning |
| Debug APK and test APK builds | Passed | Both ABIs built; baseline tasks initially reused valid Gradle outputs |
| Release assembly | Passed | Fresh changed release tasks executed; no release was published |
| Android 14 instrumentation | 16 passed, 3 opt-in cases skipped | 19 XML test cases; existing install used an already updated engine |
| Android 15, actual 16 KB pages | 16 passed, 3 opt-in cases skipped | Fresh debug install in a disposable read-only AVD instance; exercised bundled engine/runtime |
| Audit regression probes | 7 reproduced defects | Intentional assertions of current bad behavior |
| Separate process recovery | Seed and verify passed | Real force-stop/relaunch; checks persisted interruption and retained fixture bytes |
| Live app-update check | Passed | Current install plus simulated older installed code; metadata only, no APK installation |
| Release signatures | Both verified | Expected certificate identity matched local recorded fingerprint |
| Native checker supplied by repo | Passed 217/219 files | Incomplete filename filter, F08 |
| Independent native scan | Passed 258/260 files | Includes all 41 omitted ELF files per ABI |
| Working tree | Production code unchanged | New analysis/evidence under artifacts/audit only |

ADB's AndroidJUnitRunner displays “OK (19 tests)” for the Android 15 run even though the three opt-in test methods were skipped. Similarly, Gradle's progress output briefly reported 22 due to skip reporting. The XML has 19 test cases: 16 executed normally and 3 skipped. This report uses the actual test-case accounting.

Evidence:

- [Baseline build/test log](<C:/Users/Nihil/Desktop/Gloria Repoz/Orca/artifacts/audit/baseline-build.txt>)
- [JVM selection results](<C:/Users/Nihil/Desktop/Gloria Repoz/Orca/artifacts/audit/baseline-results/TEST-io.github.nihil.orca.SelectionTest.xml>)
- [JVM update results](<C:/Users/Nihil/Desktop/Gloria Repoz/Orca/artifacts/audit/baseline-results/TEST-io.github.nihil.orca.AppUpdatesTest.xml>)
- [Android 14 results](<C:/Users/Nihil/Desktop/Gloria Repoz/Orca/artifacts/audit/baseline-results/TEST-orca34(AVD) - 14-_app-.xml>)
- [Android 15 / 16 KB run](<C:/Users/Nihil/Desktop/Gloria Repoz/Orca/artifacts/audit/android35-16k.txt>)
- [Process recovery](<C:/Users/Nihil/Desktop/Gloria Repoz/Orca/artifacts/audit/process-recovery.txt>)
- [Live app-update check](<C:/Users/Nihil/Desktop/Gloria Repoz/Orca/artifacts/audit/live-app-update.txt>)
- [Lint report](<C:/Users/Nihil/Desktop/Gloria Repoz/Orca/artifacts/audit/lint-results-debug.txt>)
- [Merged release manifest](<C:/Users/Nihil/Desktop/Gloria Repoz/Orca/artifacts/audit/merged-release-manifest.xml>)

The existing tests establish ordinary extraction/merge/audio operation, deterministic publication/collisions, navigation, basic preferences/options, share-intent intake, a small download with the screen locked, cancellation/resume, and basic reconciliation. They do not establish large-file resume correctness, packet-level audio identity, all codecs, reliable OEM background execution, or robust power-loss behavior.

The existing OptionsUiTest asserts the chosen audio option and selector; the engine smoke test asserts an audio stream without video. Neither by itself compares encoded source/output audio packets. The copy command is strong source evidence for intended lossless behavior, but broader source/container combinations remain untested.

**10. Source compatibility evidence**

The repository's older validation artifacts report sample-specific success for YouTube, Instagram and direct HTTPS MP4; TikTok IP blocking, an X failure, Reddit authentication requirements, and Facebook extraction failure. A later recorded YouTube case succeeded after updating yt-dlp. These are useful historical observations, not universal platform certification.

The audit did not rerun every public-source sample or update the user's installed engine. Core transfer tests used local generated DASH fixtures. The fresh external-network test was the application-update metadata flow.

An extractor being present is not evidence that every public link works from every region or IP. Current failures should retain the exact source, engine version, device/runtime, network conditions and sanitized diagnostic output. Test success should be attributed to that sample.

**11. Recommended order of work**

1. Repair both engine-backup problems together. Add atomic backup/restore, verified fallback and install-boundary failure injection before further reliance on manual engine updates.
2. Define one cancellation/worker-ownership protocol spanning inspection, launch, merge, publication and JobService stop. Promote the audit probes into normal regression tests with assertions changed to the desired behavior.
3. Bind playback to lifecycle; preserve option selection and library context; prevent duplicate confirmation while enqueueing.
4. Replace the filename-filtered native checker, add device instrumentation to CI, and assert the expected signing identity during release verification.
5. Test realistic adversity on a physical ARM64 device: multi-gigabyte download, network loss/reconnect, dual transfers, disk pressure, background policies, external sharing/player handoff and killed update/publication.
6. Address scalability and maintenance: paged library queries, bounded playlist inspection, retained-file management, schema export/migrations, dependency review and notice access.

No application fixes, commits, pushes or release publications were made as part of this analysis. The temporary audit test file was removed from app/src/androidTest after execution; its reusable copy and evidence remain under artifacts/audit.




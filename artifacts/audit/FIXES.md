# Audit remediation

This document tracks remediation of the eight reproduced findings in `ANDROID_APP_ANALYSIS.md`. The original report and probe results describe the pre-fix code and are retained as historical evidence.

| Finding | Correction | Regression coverage |
| --- | --- | --- |
| F01: updater deletes recovery backup | Store the snapshot outside the updater-owned directory. Verify the updated engine before committing; restore the snapshot and reset version metadata on failure. | Real bundled updater against a local HTTP fixture; backup unit tests. |
| F02: incomplete backup replaces healthy engine | Commit a checksummed ZIP snapshot by atomic rename. Validate before restoring through a second atomic rename. Ignore legacy partial backups and verify startup, falling back to the bundled engine if necessary. | Partial/corrupt snapshots, repeatable recovery, legacy backup regression. |
| F03: cancellation before launch still starts work | Register a cancellable child job before execution, check cancellation at handoff, and interrupt blocking engine calls, including inspections. Keep ownership until cleanup finishes. | Deterministic handoff cancellation and stalled inspection cancellation. |
| F04: cancellation publishes a file | Copy in checked chunks while the MediaStore row stays pending. Serialize the final publication/completion decision against cancellation. Preserve staging for retry. | Real 128 MiB MediaStore copy cancellation, pending-state assertion, then successful resume into the same URI. |
| F05: playback continues after activity stops | Pause and stop on lifecycle stop, prepare paused on restart, release on disposal; handle audio focus and noisy-route events. | Real media playback followed by activity stop. |
| F06: recreation resets download choices | Save audio, quality, and selected playlist entries against a stable options-request ID. | Activity recreation with audio and playlist/quality selections. |
| F07: Settings discards library search | Retain destination state with a saveable state holder. | Library search through a Settings round trip. |
| F08: alignment checker skips versioned ELF files | Inspect ELF magic bytes regardless of filename, including nested runtime archives; fail an empty scan. | Synthetic versioned/extensionless ELF tests and both release APK scans. |

Related corrections include transactional playlist insertion, a synchronous submission guard, cancellation-safe engine-operation bookkeeping, targeted queue queries, capped stored diagnostics, and asynchronous JobService stop handling. A deterministic stop-during-claim test checks that reservations are released and interrupted items remain recoverable.

Validation results and remaining limits will be recorded after the final build and emulator runs.

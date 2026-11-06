# Design-token verification

Captured on the Android 14 `orca34` emulator after applying the token extraction.

| Surface | Normal text | 130% text |
| --- | --- | --- |
| Home | [Screenshot](home-1.0.png) | [Screenshot](home-1.3.png) |
| Library (empty) | [Screenshot](library-1.0.png) | [Screenshot](library-1.3.png) |
| Settings | [Screenshot](settings-1.0.png) | [Screenshot](settings-1.3.png) |

Visual inspection confirmed wrapping and visible primary controls on these surfaces at both scales. Settings remains a scrolling list. These captures do not establish accessibility compliance or cover every media/player state. The emulator font-scale setting was restored after capture.

Token ownership:

- `app/src/main/java/io/github/nihil/orca/ui/theme`: colors, typography, spacing, layout aliases, shapes.
- `app/src/main/java/io/github/nihil/orca/ui/components`: shared buttons and component metrics.
- `app/src/main/java/io/github/nihil/orca/ui/ScreenMetrics.kt`: screen-specific geometry.
- `app/src/main/res/values/colors.xml`: app/window primary and background, plus separate launcher artwork colors.

Prefer semantic theme roles in screen code. Add reusable rhythm to `OrcaSpacing`; keep exceptional geometry in its component or screen metrics. Typography roles retain sp units and system scaling.

Validation completed:

- `testDebugUnitTest`: 17 tests, zero failures.
- `lintDebug`: zero errors, 24 warnings.
- `assembleDebug` and `assembleDebugAndroidTest`: passed.
- Five existing UI tests at normal text size: [console result](ui-tests-normal.txt).
- Six existing UI tests at 130% text size: [results](ui-tests-130-percent.xml). These cover options selection and quality, the shared confirmation action, update controls/dialog dismissal, navigation, and shared text filling the link without downloading.
- Source scan: zero inline dp/sp dimensions, raw UI colors, or font overrides in MainActivity.kt.
- `git diff --check`: passed.

The first enlarged-text automation attempts were blocked by the POST_NOTIFICATIONS permission dialog following test installation. Granting that permission on the emulator allowed all six tests to pass. No application change was needed. The font-scale setting was restored to its original unset value afterwards.

Player playback, populated-library card visuals, physical devices, and font scales above 130% were not visually reviewed in this change. Existing player/card geometry was retained, and the player message padding, background, and aspect ratio now use named tokens/metrics.

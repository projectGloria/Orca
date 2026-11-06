# Orca design-token audit

Audited 2026-11-02 using `frontend-ui-standards`. Scope: all five Kotlin files and all XML resources under `app/src/main`. App-owned visual declarations are in `MainActivity.kt`, `res/values/styles.xml`, and `res/drawable/ic_orca.xml`. Generated output, dependencies, and test fixtures are excluded.

This inventory records the source before token extraction. The proposal has since been implemented; see the implementation record below. Line references in the inventory refer to the original audited snapshot. Counts are literal occurrences, including multiple occurrences on a line.

## Findings

1. **Spacing is the main extraction opportunity.** There are 42 inline spacing literals across 10 distinct values. Home and Library use 20dp page insets; Settings and Options use 24dp. This may be deliberate, but the distinction is unnamed. Repeated button icon gaps, card padding, and section spacing have no shared metrics.
2. **Color tokens exist partially.** Seven Compose colors are defined centrally in `OrcaTheme`, which is already an appropriate use of literals. However, seven UI usages reference `Ocean` directly rather than `MaterialTheme.colorScheme.primary`. Compose primary (`#48BDEE`), platform accent (`#39B8ED`), and icon accent (`#45BEF0`) differ. Treat the icon palette as artwork; decide explicitly whether the platform accent should match Compose.
3. **No hardcoded font sizes were found.** There are zero `.sp` literals or `fontSize` assignments in app-owned UI. There are 28 explicit references to Material typography roles, and other text inherits component/local styles. `OrcaTheme` does not supply custom typography. Preserve this foundation; five local weight overrides can become named semantic styles.
4. **Keep geometry separate from spacing.** An additional 16 inline dp literals describe shapes, icon sizes, buttons, thumbnails, and a list height. These belong in shape tokens or component/screen metrics, rather than a global spacing scale.

## Color inventory

| Declaration | Current value | Location | Proposed treatment |
| --- | --- | --- | --- |
| `Ocean` / primary | `#48BDEE` | MainActivity.kt:136, 138 | Theme primary; consumers use `colorScheme.primary` |
| onPrimary | `#002C40` | MainActivity.kt:138 | Keep semantic role |
| secondary | `#8FD3EC` | MainActivity.kt:138 | Keep semantic role |
| background | `#080F18` | MainActivity.kt:138 | Shared background resource/token |
| surface | `#101C2A` | MainActivity.kt:138 | Keep semantic role |
| surfaceVariant | `#192A3B` | MainActivity.kt:138 | Keep current role during extraction |
| onSurface | `#E4EDF7` | MainActivity.kt:138 | Keep semantic role |
| Player surface | `Color.Black` | MainActivity.kt:313 | `OrcaPlayerColors.background = #000000` |
| System bars | `android.graphics.Color.TRANSPARENT`, twice | MainActivity.kt:63 | Intentional edge-to-edge configuration; keep at platform boundary |
| Platform accent | `#39B8ED` | styles.xml:1 | Propose `@color/orca_primary = #48BDEE` |
| Platform background, window background, splash background | `#080F18`, three times | styles.xml:1 | All reference `@color/orca_background` |
| Launcher background | `#0B2539` | ic_orca.xml:1 | Named artwork palette, retain |
| Launcher accent | `#45BEF0` | ic_orca.xml:1 | Named artwork palette, retain |
| Launcher highlight | `#E6F7FF` | ic_orca.xml:1 | Named artwork palette, retain |
| Launcher detail | `#080F18` | ic_orca.xml:1 | Artwork detail; same current value as app background |

Direct `Ocean` consumers: MainActivity.kt:170, 181, 211, 223, 288, 290, 291. These should use the theme role so theme substitution affects every consumer.

The scheme leaves other roles at Material defaults, including `onSurfaceVariant`, outline, error, and surface-container roles. Several UI components consume those defaults. Extracting the existing seven values will not define a complete custom palette; retain the defaults initially and assess those surfaces visually before changing them.

## Complete spacing inventory

All locations in this table are in `app/src/main/java/io/github/nihil/orca/MainActivity.kt`. `×2` means two occurrences on that source line. Radii and geometry are excluded here and listed separately.

| Value | Count | Lines | Current role |
| --- | ---: | --- | --- |
| 4dp | 2 | 181, 227 | Section label/body and title/metadata gap |
| 6dp | 3 | 173, 174, 240 | Small action icon/label gap |
| 8dp | 11 | 168, 172, 176, 210, 211, 238, 277, 290 ×2, 291, 317 | Text, chip, button, and row spacing |
| 10dp | 1 | 220 | Download card vertical gap |
| 12dp | 8 | 169, 208, 221, 255, 257, 258, 290, 291 | Card/sheet content, rows, list, settings action gap |
| 14dp | 1 | 168 | Home hero top offset |
| 16dp | 4 | 167, 211, 220, 316 | Home item gap, empty-state gap, download card/player padding |
| 20dp | 8 | 167, 169, 208, 287, 290, 291, 292 ×2 | Page/card inset and settings section gaps |
| 24dp | 3 | 255 ×2, 287 | Sheet horizontal/bottom inset and settings inset |
| 60dp | 1 | 211 | Empty-library vertical inset |
| **Total** | **42** | | |

## Other dimensions and shape literals

| Role | Value | MainActivity.kt lines | Proposed owner |
| --- | --- | --- | --- |
| Link card radius | 24dp | 169 | `OrcaShapes.featureCard` |
| Link input radius | 16dp | 171 | `OrcaShapes.input` |
| Search input radius | 20dp | 209 | `OrcaShapes.searchInput` |
| Download card radius | 20dp | 220 | `OrcaShapes.downloadCard` |
| Thumbnail radius | 10dp | 222 | `DownloadCardMetrics.thumbnailRadius` |
| Small action icon | 18dp, three occurrences | 173, 174, 240 | `ActionButtonMetrics.smallIconSize` |
| Empty state icon | 48dp | 211 | `LibraryLayout.emptyIconSize` |
| Primary action min height | 52dp, two occurrences | 176, 281 | `PrimaryActionMetrics.minHeight` |
| Download thumbnail | 76 × 64dp | 222 | `DownloadCardMetrics.thumbnailSize` |
| Options preview | 88 × 60dp | 257 | `OptionsSheetMetrics.previewSize` |
| Playlist list max height | 280dp | 273 | `OptionsSheetMetrics.playlistMaxHeight` |

The launcher vector also declares 48dp width and height (ic_orca.xml:1). Keep its viewport/path coordinates as asset geometry. Player video aspect ratio `16f / 9f` (MainActivity.kt:315) can be named `PlayerMetrics.defaultAspectRatio`; weights, progress ratios, media qualities, and state indices are not spacing tokens.

## Typography inventory and proposal

The following sizes are **proposed explicit tokens**, not font-size literals found in the app. Use sp and retain system font scaling. A separate family is unnecessary: keep the system sans family (also declared as `sans` in styles.xml:1).

| Existing Material role | Explicit usages | Proposed size / line height | Base weight |
| --- | ---: | --- | --- |
| headlineLarge | 1 | 32 / 40sp | Normal |
| titleLarge | 2 | 22 / 28sp | Normal |
| titleMedium | 4 | 16 / 24sp | Medium |
| bodyMedium | 1 | 14 / 20sp | Normal |
| bodySmall | 14 | 12 / 16sp | Normal |
| labelMedium | 6 | 12 / 16sp | Medium |

These explicit role references do not cover all text: buttons, navigation, app bars, dialogs, and unstyled `Text` use inherited Material styles. Keep the full default `Typography()` and override only roles the product intentionally owns; do not replace inherited styles with one universal size.

Move the five local weights into semantic aliases:

| Alias | Definition | Current override |
| --- | --- | --- |
| `OrcaTextStyles.appBarTitle` | titleLarge + Bold | MainActivity.kt:163 |
| `OrcaTextStyles.hero` | headlineLarge + Bold | MainActivity.kt:168 |
| `OrcaTextStyles.sectionTitle` | titleLarge + SemiBold | MainActivity.kt:183 |
| `OrcaTextStyles.downloadTitle` | bodyLarge + SemiBold | MainActivity.kt:226; currently inherits size |
| `OrcaTextStyles.sheetTitle` | titleLarge + Bold | MainActivity.kt:256 |

The download title's proposed explicit bodyLarge role (16 / 24sp) should be checked against its inherited appearance before adoption. Typography extraction should preserve the current hierarchy and wrapping.

## Proposed token architecture

Use `ui/theme/Color.kt`, `Type.kt`, `Spacing.kt`, and `Shape.kt`, with `OrcaTheme` providing the Material color scheme and typography. These are proposed paths, not existing files.

- **Colors:** preserve the seven existing Compose role values listed above. Define app primary/background in Android color resources and read those resources in Compose, so startup windows and Compose share the same source. Give player black its own semantic token. Keep the launcher palette separate.
- **Spacing primitives:** `xs = 4.dp`, `sm = 8.dp`, `md = 12.dp`, `lg = 16.dp`, `xl = 20.dp`, `xxl = 24.dp`. These cover the regular rhythm already present. Do not automatically round 6, 10, 14, or 60dp to the nearest primitive.
- **Layout aliases:** `pageInset = spacing.xl`, `settingsInset = spacing.xxl`, `sheetInset = spacing.xxl`, `homeItemGap = spacing.lg`, `libraryItemGap = spacing.md`, `settingsSectionGap = spacing.xl`. Preserve the existing 20/24dp distinction initially; unify it only as an intentional visual change.
- **Shapes:** retain feature card 24dp, download card/search input 20dp, input 16dp. Shared numeric values may reference one primitive while keeping distinct semantic names.
- **Component metrics:** `ActionButtonMetrics.iconLabelGap = 6.dp`, `smallIconSize = 18.dp`; `PrimaryActionMetrics.minHeight = 52.dp`, `iconLabelGap = spacing.sm`; `DownloadCardMetrics.padding = spacing.lg`, `contentGap = 10.dp`, `rowGap = spacing.md`, with thumbnail geometry from the table.
- **Screen metrics:** `HomeLayout.heroTopGap = 14.dp`; `LibraryLayout.emptyVerticalInset = 60.dp`, `emptyIconSize = 48.dp`; `OptionsSheetMetrics.previewSize = 88 × 60.dp`, `playlistMaxHeight = 280.dp`, `itemVerticalPadding = spacing.sm`.

Keeping unusual dimensions in named local metrics preserves the current appearance without growing a global spacing scale around one-off geometry.

## Suggested adoption order

1. Extract the existing theme and share primary/background resources with the Android window theme. Replace direct `Ocean` consumers with `colorScheme.primary`. Aligning platform accent to Compose primary is the one proposed palette change; review it explicitly.
2. Introduce the spacing primitives and semantic aliases, replacing literals without altering their values.
3. Extract shared primary-action and icon/label button variants. Download actions repeat the same 52dp minimum; Paste, Options, and Share repeat 18dp icons with 6dp gaps. Keep Play and engine-update icon gaps as a deliberate follow-up review because they currently have no explicit spacer (MainActivity.kt:239, 291).
4. Name card, sheet, and screen metrics and optionally make typography ownership explicit. Material styles already solve the font-size problem.
5. After implementation, run the app build and existing UI checks, then visually inspect Home, Library, Settings, Options, and Player at normal and enlarged font scales. Check theme-default surfaces, long titles, button labels, and sheet scrolling. This audit alone does not establish visual or contrast compliance.

## Implementation record

Applied on 2026-11-02:

- `ui/theme/Color.kt`, `Type.kt`, `Spacing.kt`, and `Shape.kt` own theme values, the six-step spacing scale, layout aliases, and semantic text styles. `OrcaTheme` retains its existing entry point and now supplies these colors and typography.
- `res/values/colors.xml` supplies primary/background to both Compose and the Android window theme. Platform accent now matches `#48BDEE`. Launcher palette values remain unchanged and have dedicated artwork resources.
- `ui/components/Metrics.kt` and `ui/ScreenMetrics.kt` name the exceptional dimensions without rounding or changing the existing geometry.
- `ui/components/Buttons.kt` shares the primary Download button, outlined icon actions, and icon/label content. Play and engine-update now use the standard 8dp icon gap. Existing actions, enabled states, and the confirm-download test tag are retained.
- The six explicitly owned typography roles retain Material sizes and line heights; the remaining Material roles and font scaling are retained. The download title now explicitly uses bodyLarge with SemiBold.
- No inline dp/sp sizes, raw Compose colors, or font-weight overrides remain in `MainActivity.kt`. System bar transparency remains at the Android platform boundary, as intended.

Validation: debug app and test APK assembly passed; 17 unit tests passed; lint reported zero errors and 24 warnings. Five existing UI tests passed at normal text size, and six passed at 130% text size after granting notification permission in the emulator. Home, empty Library, and Settings screenshots were visually inspected at both sizes. See [verification records and limitations](design-tokens/README.md).

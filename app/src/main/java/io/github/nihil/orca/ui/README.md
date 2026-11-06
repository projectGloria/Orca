# Orca interface

The interface uses a warm charcoal palette, warm white text, and a lime primary accent. Color is reserved for actions, selection, progress, and status. Panels use a subtle border instead of shadows; headings establish hierarchy before containers do.

## Ownership

- `theme/Color.kt`: Material semantic colors, including containers, outlines, errors and inverse surfaces. Startup and launcher colors live in `res/values/colors.xml`.
- `theme/Type.kt`: scalable typography and semantic title roles.
- `theme/Spacing.kt`: shared rhythm, page insets, touch target and bounded content width.
- `theme/Shape.kt`: panel, input, button and Material shapes.
- `components/Metrics.kt` and `components/Surfaces.kt`: component geometry and reusable panels, headings, navigation, brand bar, information rows and empty states.
- `ScreenMetrics.kt`: options preview, playlist viewport and player geometry.
- `HomeContent.kt`: home composition, link composer and collection summary.

Use the Material color scheme for every surface, including dialogs and sheets. Prefer a shared component variant before adding a screen-specific implementation. Keep one-off geometry in a named metric. The main column and header share a bounded width; phones retain the same page inset.

## Behavior

Navigation selection and settings visibility survive recreation. Android Back returns from Settings to the previous tab, then from Downloads to Home. Search and media filtering are independent, with an explicit reset when a result is empty. Completed media thumbnails and titles form one playback target. Deletion requires confirmation. Long content titles may wrap to two lines; body copy wraps without clipping. Loading, unavailable, interrupted, failed and disabled states remain explicit.

The download engine, original-quality selection, queue, local storage and application updater keep their existing behavior.

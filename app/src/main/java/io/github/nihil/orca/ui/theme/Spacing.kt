package io.github.nihil.orca.ui.theme

import androidx.compose.ui.unit.dp

internal object OrcaSpacing {
    val xs = 4.dp
    val sm = 8.dp
    val md = 12.dp
    val lg = 16.dp
    val xl = 20.dp
    val xxl = 24.dp
    val xxxl = 32.dp
    val huge = 48.dp
}
internal object OrcaLayout {
    val pageInset = OrcaSpacing.xxl
    val settingsInset = pageInset
    val sheetInset = pageInset
    val contentMaxWidth = 680.dp
    val homeItemGap = OrcaSpacing.xxl
    val libraryItemGap = OrcaSpacing.lg
    val settingsSectionGap = OrcaSpacing.xxl
    val dividerWidth = 1.dp
    val touchTarget = 48.dp
}

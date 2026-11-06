package io.github.nihil.orca.ui.components

import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.unit.dp
import io.github.nihil.orca.ui.theme.OrcaSpacing

internal object ActionButtonMetrics {
    val smallIconSize = 18.dp
    val iconLabelGap = 6.dp
    val standardIconLabelGap = OrcaSpacing.sm
}

internal object PrimaryActionMetrics {
    val minHeight = 56.dp
    val iconLabelGap = OrcaSpacing.sm
}

internal object DownloadCardMetrics {
    val padding = OrcaSpacing.lg
    val contentGap = 12.dp
    val rowGap = OrcaSpacing.md
    val thumbnailWidth = 88.dp
    val thumbnailHeight = 76.dp
    val thumbnailRadius = RoundedCornerShape(12.dp)
}

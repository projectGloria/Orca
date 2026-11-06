package io.github.nihil.orca.ui.theme

import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Shapes

internal object OrcaShapes {
    val featureCard = RoundedCornerShape(OrcaSpacing.xxl)
    val downloadCard = RoundedCornerShape(OrcaSpacing.xl)
    val searchInput = RoundedCornerShape(OrcaSpacing.lg)
    val input = RoundedCornerShape(OrcaSpacing.lg)
    val button = RoundedCornerShape(OrcaSpacing.lg)
    val badge = RoundedCornerShape(OrcaSpacing.sm)
    val material = Shapes(small = badge, medium = input, large = downloadCard, extraLarge = featureCard)
}

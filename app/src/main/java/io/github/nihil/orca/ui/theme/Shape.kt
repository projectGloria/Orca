package io.github.nihil.orca.ui.theme

import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Shapes

internal object OrcaShapes {
    val featureCard = RoundedCornerShape(OrcaSpacing.xl)
    val downloadCard = RoundedCornerShape(OrcaSpacing.md)
    val searchInput = RoundedCornerShape(OrcaSpacing.md)
    val input = RoundedCornerShape(OrcaSpacing.md)
    val button = RoundedCornerShape(OrcaSpacing.md)
    val badge = RoundedCornerShape(OrcaSpacing.sm)
    val material = Shapes(small = badge, medium = input, large = downloadCard, extraLarge = featureCard)
}

package io.github.nihil.orca.ui.theme

import androidx.compose.material3.Typography
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.style.LineBreak
import androidx.compose.ui.text.style.TextDirection

private fun TextStyle.paragraph() = copy(lineBreak = LineBreak.Paragraph, textDirection = TextDirection.Content)

private val base = Typography()
internal val OrcaTypography = Typography(
    displayLarge = base.displayLarge.paragraph(), displayMedium = base.displayMedium.paragraph(),
    displaySmall = base.displaySmall.paragraph(), headlineLarge = base.headlineLarge.paragraph(),
    headlineMedium = base.headlineMedium.paragraph(), headlineSmall = base.headlineSmall.paragraph(),
    titleLarge = base.titleLarge.paragraph(), titleMedium = base.titleMedium.paragraph(),
    titleSmall = base.titleSmall.paragraph(), bodyLarge = base.bodyLarge.paragraph(),
    bodyMedium = base.bodyMedium.paragraph(), bodySmall = base.bodySmall.paragraph(),
    labelLarge = base.labelLarge.paragraph(), labelMedium = base.labelMedium.paragraph(),
    labelSmall = base.labelSmall.paragraph(),
)

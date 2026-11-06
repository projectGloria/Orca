package io.github.nihil.orca.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Typography
import androidx.compose.runtime.Composable
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp

private val base = Typography()
internal val OrcaTypography = base.copy(
    headlineLarge = base.headlineLarge.copy(fontSize = 44.sp, lineHeight = 48.sp, letterSpacing = (-1.8).sp, fontWeight = FontWeight.SemiBold),
    headlineMedium = base.headlineMedium.copy(fontSize = 32.sp, lineHeight = 38.sp, letterSpacing = (-0.8).sp, fontWeight = FontWeight.SemiBold),
    titleLarge = base.titleLarge.copy(fontSize = 22.sp, lineHeight = 28.sp, letterSpacing = (-0.4).sp),
    titleMedium = base.titleMedium.copy(fontSize = 16.sp, lineHeight = 24.sp, fontWeight = FontWeight.Medium),
    bodyLarge = base.bodyLarge.copy(fontSize = 16.sp, lineHeight = 24.sp),
    bodyMedium = base.bodyMedium.copy(fontSize = 14.sp, lineHeight = 21.sp),
    bodySmall = base.bodySmall.copy(fontSize = 12.sp, lineHeight = 18.sp),
    labelLarge = base.labelLarge.copy(fontSize = 14.sp, lineHeight = 20.sp, fontWeight = FontWeight.SemiBold),
    labelMedium = base.labelMedium.copy(fontSize = 11.sp, lineHeight = 16.sp, letterSpacing = 1.4.sp, fontWeight = FontWeight.SemiBold),
)
internal object OrcaTextStyles {
    val appBarTitle @Composable get() = MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.Bold, letterSpacing = (-0.7).sp)
    val hero @Composable get() = MaterialTheme.typography.headlineLarge
    val sectionTitle @Composable get() = MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.SemiBold)
    val downloadTitle @Composable get() = MaterialTheme.typography.titleMedium
    val sheetTitle @Composable get() = MaterialTheme.typography.headlineMedium
    val stat @Composable get() = MaterialTheme.typography.headlineMedium.copy(fontFamily = FontFamily.Monospace)
}

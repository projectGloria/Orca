package io.github.nihil.orca.ui.theme

import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.colorResource
import io.github.nihil.orca.R

@Composable
internal fun orcaColorScheme() = darkColorScheme(
    primary = colorResource(R.color.orca_primary), onPrimary = Color(0xFF062B33),
    primaryContainer = Color(0xFF143B48), onPrimaryContainer = Color(0xFFB1ECFA),
    secondary = Color(0xFFADC8DD), onSecondary = Color(0xFF163042),
    secondaryContainer = Color(0xFF233C50), onSecondaryContainer = Color(0xFFDAEBFA),
    tertiary = Color(0xFFC8BFFE), onTertiary = Color(0xFF30275D),
    tertiaryContainer = Color(0xFF463D71), onTertiaryContainer = Color(0xFFE7DEFF),
    background = colorResource(R.color.orca_background), onBackground = Color(0xFFEAF3FA),
    surface = Color(0xFF152431), onSurface = Color(0xFFEAF3FA),
    surfaceVariant = Color(0xFF253746), onSurfaceVariant = Color(0xFFB3C6D5),
    surfaceContainerLowest = Color(0xFF0B131D), surfaceContainerLow = Color(0xFF111E2A),
    surfaceContainer = Color(0xFF162633), surfaceContainerHigh = Color(0xFF1C2F3E),
    surfaceContainerHighest = Color(0xFF263B4B),
    outline = Color(0xFF7790A4), outlineVariant = Color(0xFF354D60),
    error = Color(0xFFFFB4A8), onError = Color(0xFF571E17),
    errorContainer = Color(0xFF43251F), onErrorContainer = Color(0xFFFFDAD3),
    inverseSurface = Color(0xFFEAF3FA), inverseOnSurface = Color(0xFF152431),
    inversePrimary = Color(0xFF006476),
)

internal object OrcaPlayerColors { val background = Color.Black }

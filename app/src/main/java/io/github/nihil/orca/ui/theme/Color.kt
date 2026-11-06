package io.github.nihil.orca.ui.theme

import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.colorResource
import io.github.nihil.orca.R

// A single neutral palette keeps sheets, dialogs and native chrome in the same system.
@Composable
internal fun orcaColorScheme() = darkColorScheme(
    primary = colorResource(R.color.orca_primary), onPrimary = Color(0xFF202710),
    primaryContainer = Color(0xFF303B21), onPrimaryContainer = Color(0xFFD9F4A7),
    secondary = Color(0xFFC6D4B3), onSecondary = Color(0xFF202710),
    secondaryContainer = Color(0xFF30372A), onSecondaryContainer = Color(0xFFE2ECCF),
    tertiary = Color(0xFFAACFC6),
    background = colorResource(R.color.orca_background), onBackground = Color(0xFFF1F2E9),
    surface = Color(0xFF191C19), onSurface = Color(0xFFF1F2E9),
    surfaceVariant = Color(0xFF262B25), onSurfaceVariant = Color(0xFFA5ADA0),
    surfaceContainerLowest = Color(0xFF10120F), surfaceContainerLow = Color(0xFF191C19),
    surfaceContainer = Color(0xFF1E221D), surfaceContainerHigh = Color(0xFF262B25),
    surfaceContainerHighest = Color(0xFF30362E),
    outline = Color(0xFF70796A), outlineVariant = Color(0xFF333A30),
    error = Color(0xFFFFB4A8), onError = Color(0xFF571E17),
    errorContainer = Color(0xFF43251F), onErrorContainer = Color(0xFFFFDAD3),
    inverseSurface = Color(0xFFF1F2E9), inverseOnSurface = Color(0xFF191C19),
)

internal object OrcaPlayerColors { val background = Color.Black }

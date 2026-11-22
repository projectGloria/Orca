package io.github.nihil.orca.ui.theme

import android.content.Context
import androidx.compose.material3.ColorScheme
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color

/** Material You scheme generated from the device wallpaper (Orca's minSdk always supports it). */
internal fun orcaColorScheme(context: Context, dark: Boolean): ColorScheme =
    if (dark) dynamicDarkColorScheme(context) else dynamicLightColorScheme(context)

/** Material 3 "fixed" roles: identical in light and dark themes. Compose Material3 stable has no API for them yet. */
@Immutable
internal data class FixedColorRoles(
    val primaryFixed: Color, val primaryFixedDim: Color, val onPrimaryFixed: Color, val onPrimaryFixedVariant: Color,
    val secondaryFixed: Color, val secondaryFixedDim: Color, val onSecondaryFixed: Color, val onSecondaryFixedVariant: Color,
    val tertiaryFixed: Color, val tertiaryFixedDim: Color, val onTertiaryFixed: Color, val onTertiaryFixedVariant: Color,
) {
    companion object {
        /** Tones 90/80/10/30 of the system accent palettes. */
        fun fromSystem(context: Context): FixedColorRoles {
            fun c(id: Int) = Color(context.getColor(id))
            return FixedColorRoles(
                c(android.R.color.system_accent1_100), c(android.R.color.system_accent1_200),
                c(android.R.color.system_accent1_900), c(android.R.color.system_accent1_700),
                c(android.R.color.system_accent2_100), c(android.R.color.system_accent2_200),
                c(android.R.color.system_accent2_900), c(android.R.color.system_accent2_700),
                c(android.R.color.system_accent3_100), c(android.R.color.system_accent3_200),
                c(android.R.color.system_accent3_900), c(android.R.color.system_accent3_700),
            )
        }
        val Unspecified = Color.Unspecified.let { u -> FixedColorRoles(u, u, u, u, u, u, u, u, u, u, u, u) }
    }
}

internal val LocalFixedColorRoles = staticCompositionLocalOf { FixedColorRoles.Unspecified }
internal val LocalDarkTheme = staticCompositionLocalOf { false }

internal object OrcaColors {
    /** Translucent scrim behind labels drawn over thumbnails. */
    val label = Color.Black.copy(alpha = 0.68f)
    val successDark = Color(0xFF7CDA97)
    val successLight = Color(0xFF1C6D37)
}

internal object OrcaPlayerColors { val background = Color.Black }

package io.github.nihil.orca.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.LocalTextStyle
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ProvideTextStyle
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.LineBreak
import androidx.compose.ui.text.style.TextDirection

@Composable
internal fun OrcaDynamicTheme(darkTheme: Boolean = isSystemInDarkTheme(), content: @Composable () -> Unit) {
    val context = LocalContext.current
    val configuration = LocalConfiguration.current
    val scheme = remember(darkTheme, configuration) { orcaColorScheme(context, darkTheme) }
    val fixed = remember(configuration) { FixedColorRoles.fromSystem(context) }
    MaterialTheme(colorScheme = scheme, typography = OrcaTypography, shapes = OrcaShapes.material) {
        CompositionLocalProvider(LocalFixedColorRoles provides fixed, LocalDarkTheme provides darkTheme) {
            ProvideTextStyle(LocalTextStyle.current.copy(lineBreak = LineBreak.Paragraph, textDirection = TextDirection.Content), content)
        }
    }
}

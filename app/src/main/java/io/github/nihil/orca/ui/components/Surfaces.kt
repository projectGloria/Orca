package io.github.nihil.orca.ui.components

import androidx.compose.foundation.Image
import androidx.compose.ui.res.painterResource
import io.github.nihil.orca.R
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.SideEffect
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.DialogWindowProvider
import androidx.core.view.WindowCompat
import io.github.nihil.orca.ui.theme.*

internal object ChromeMetrics {
    val brandSize = 32.dp
    val emptyMark = 56.dp
    val emptyIcon = 26.dp
}

@Composable internal fun DarkDialogSystemBars() {
    val view = LocalView.current
    val window = (view.parent as? DialogWindowProvider)?.window ?: return
    SideEffect {
        window.isNavigationBarContrastEnforced = false
        WindowCompat.getInsetsController(window, view).apply {
            isAppearanceLightStatusBars = false
            isAppearanceLightNavigationBars = false
        }
    }
}

@Composable internal fun OrcaPanel(modifier: Modifier = Modifier, content: @Composable ColumnScope.() -> Unit) {
    Surface(modifier.fillMaxWidth(), shape = OrcaShapes.featureCard,
        color = MaterialTheme.colorScheme.surface, border = BorderStroke(OrcaLayout.dividerWidth, MaterialTheme.colorScheme.outlineVariant)) {
        Column(Modifier.padding(OrcaSpacing.xl), verticalArrangement = Arrangement.spacedBy(OrcaSpacing.lg), content = content)
    }
}

@Composable internal fun SectionHeading(title: String) {
    Text(title, Modifier.semantics { heading() }, style = OrcaTextStyles.sectionTitle)
}

@Composable internal fun OrcaTopBar(settings: Boolean, back: () -> Unit) {
    Surface(color = MaterialTheme.colorScheme.surfaceContainerLow) {
        Box(Modifier.fillMaxWidth().statusBarsPadding(), contentAlignment = Alignment.Center) {
            Row(
                Modifier.widthIn(max = OrcaLayout.contentMaxWidth).fillMaxWidth()
                    .heightIn(min = OrcaLayout.touchTarget)
                    .padding(horizontal = OrcaLayout.pageInset, vertical = OrcaSpacing.sm),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(OrcaSpacing.md),
            ) {
                if (settings) {
                    IconButton(onClick = back) { Icon(Icons.AutoMirrored.Filled.ArrowBack, "Back") }
                } else {
                    Image(painterResource(R.drawable.ic_orca), null, Modifier.size(ChromeMetrics.brandSize))
                }
                Text(if (settings) "Settings" else "Orca", style = OrcaTextStyles.appBarTitle)
            }
        }
    }
}

@Composable internal fun OrcaNavigation(destination: Int, select: (Int) -> Unit) {
    val destinations = listOf(
        Triple("Home", Icons.Default.AddLink, Icons.Default.AddLink),
        Triple("Downloads", Icons.Default.Folder, Icons.Default.FolderOpen),
        Triple("Settings", Icons.Default.Settings, Icons.Default.Settings),
    )
    Surface(color = MaterialTheme.colorScheme.surfaceContainerLow) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
            NavigationBar(
                modifier = Modifier.widthIn(max = OrcaLayout.contentMaxWidth),
                containerColor = MaterialTheme.colorScheme.surfaceContainerLow,
                tonalElevation = 0.dp,
            ) {
                destinations.forEachIndexed { index, (label, idle, selected) ->
                    val active = destination == index
                    NavigationBarItem(
                        selected = active, onClick = { select(index) },
                        icon = { Icon(if (active) selected else idle, null) },
                        label = { Text(label) },
                        colors = NavigationBarItemDefaults.colors(
                            indicatorColor = MaterialTheme.colorScheme.primaryContainer,
                            selectedIconColor = MaterialTheme.colorScheme.onPrimaryContainer,
                            selectedTextColor = MaterialTheme.colorScheme.primary,
                        ),
                    )
                }
            }
        }
    }
}

@Composable internal fun EmptyLibrary(title: String, description: String, icon: ImageVector = Icons.Default.VideoLibrary, action: (@Composable () -> Unit)? = null) {
    Column(Modifier.fillMaxWidth().padding(vertical = OrcaSpacing.xxxl), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(OrcaSpacing.lg)) {
        Box(Modifier.size(ChromeMetrics.emptyMark).background(MaterialTheme.colorScheme.surfaceVariant, CircleShape), contentAlignment = Alignment.Center) {
            Icon(icon, null, Modifier.size(ChromeMetrics.emptyIcon), tint = MaterialTheme.colorScheme.primary)
        }
        Text(title, style = MaterialTheme.typography.titleLarge, textAlign = TextAlign.Center)
        Text(description, color = MaterialTheme.colorScheme.onSurfaceVariant, textAlign = TextAlign.Center, style = MaterialTheme.typography.bodyMedium)
        action?.invoke()
    }
}

@Composable internal fun InfoLine(icon: ImageVector, title: String, detail: String) {
    Row(horizontalArrangement = Arrangement.spacedBy(OrcaSpacing.md), verticalAlignment = Alignment.Top) {
        Icon(icon, null, tint = MaterialTheme.colorScheme.primary)
        Column(verticalArrangement = Arrangement.spacedBy(OrcaSpacing.xs)) {
            Text(title, style = MaterialTheme.typography.titleMedium)
            Text(detail, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

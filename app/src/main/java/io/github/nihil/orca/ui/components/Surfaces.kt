package io.github.nihil.orca.ui.components

import androidx.compose.foundation.Image
import androidx.compose.ui.res.painterResource
import io.github.nihil.orca.R
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import io.github.nihil.orca.ui.theme.*

internal object ChromeMetrics {
    val brandSize = 38.dp
    val navigationIcon = 22.dp
    val navigationWidth = 360.dp
    val emptyMark = 80.dp
    val emptyIcon = 34.dp
}

@Composable internal fun OrcaPanel(modifier: Modifier = Modifier, content: @Composable ColumnScope.() -> Unit) {
    Surface(modifier.fillMaxWidth(), shape = OrcaShapes.featureCard,
        color = MaterialTheme.colorScheme.surface, border = BorderStroke(OrcaLayout.dividerWidth, MaterialTheme.colorScheme.outlineVariant)) {
        Column(Modifier.padding(OrcaSpacing.xl), verticalArrangement = Arrangement.spacedBy(OrcaSpacing.lg), content = content)
    }
}

@Composable internal fun SectionHeading(title: String, detail: String? = null) {
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(OrcaSpacing.sm)) {
        Text(title, Modifier.weight(1f), style = OrcaTextStyles.sectionTitle)
        if (detail != null) Text(detail, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

@Composable internal fun Eyebrow(text: String) {
    Text(text, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.primary)
}

@Composable internal fun OrcaTopBar(settings: Boolean, back: () -> Unit, openSettings: () -> Unit) {
    Box(Modifier.fillMaxWidth(), contentAlignment = Alignment.TopCenter) {
        Row(Modifier.widthIn(max = OrcaLayout.contentMaxWidth).fillMaxWidth().statusBarsPadding().padding(horizontal = OrcaLayout.pageInset, vertical = OrcaSpacing.md),
            verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(OrcaSpacing.md)) {
            if (settings) IconButton(onClick = back) { Icon(Icons.AutoMirrored.Filled.ArrowBack, "Back") }
            else Surface(shape = OrcaShapes.badge, color = MaterialTheme.colorScheme.primary) {
                Box(Modifier.size(ChromeMetrics.brandSize), contentAlignment = Alignment.Center) {
                    Image(painterResource(R.drawable.ic_orca), null, Modifier.fillMaxSize())
                }
            }
            Text(if (settings) "Settings" else "orca", Modifier.weight(1f), style = OrcaTextStyles.appBarTitle)
            if (!settings) IconButton(onClick = openSettings) { Icon(Icons.Default.Tune, "Settings", tint = MaterialTheme.colorScheme.onSurfaceVariant) }
        }
    }
}

@Composable internal fun OrcaNavigation(destination: Int, select: (Int) -> Unit) {
    Surface(color = MaterialTheme.colorScheme.background) {
        Column(Modifier.fillMaxWidth().navigationBarsPadding(), horizontalAlignment = Alignment.CenterHorizontally) {
            HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
            Row(Modifier.widthIn(max = ChromeMetrics.navigationWidth).fillMaxWidth().padding(horizontal = OrcaSpacing.xxl, vertical = OrcaSpacing.md), horizontalArrangement = Arrangement.spacedBy(OrcaSpacing.md)) {
                listOf("Home" to Icons.Default.Waves, "Downloads" to Icons.Default.VideoLibrary).forEachIndexed { index, (label, icon) ->
                    val active = destination == index
                    val foreground = if (active) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant
                    Row(Modifier.weight(1f).background(if (active) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.background, OrcaShapes.button)
                        .selectable(selected = active, role = Role.Tab, onClick = { select(index) })
                        .heightIn(min = OrcaLayout.touchTarget).padding(horizontal = OrcaSpacing.md, vertical = OrcaSpacing.sm),
                        verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(OrcaSpacing.sm, Alignment.CenterHorizontally)) {
                        Icon(icon, null, Modifier.size(ChromeMetrics.navigationIcon), tint = foreground)
                        Text(label, color = foreground, style = MaterialTheme.typography.labelLarge)
                    }
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

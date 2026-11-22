// Header, drawer and sheet chrome adapted from Seal (https://github.com/JunkFood02/Seal), GPL-3.0-or-later.
package io.github.nihil.orca.ui.components

import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Menu
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.SideEffect
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.DialogWindowProvider
import androidx.core.view.WindowCompat
import io.github.nihil.orca.R
import io.github.nihil.orca.ui.theme.LocalDarkTheme
import io.github.nihil.orca.ui.theme.OrcaLayout

/** Keeps a dialog window's system bar icons readable against the current theme. */
@Composable
internal fun DialogSystemBars(dark: Boolean = LocalDarkTheme.current) {
    val view = LocalView.current
    val window = (view.parent as? DialogWindowProvider)?.window ?: return
    SideEffect {
        window.isNavigationBarContrastEnforced = false
        WindowCompat.getInsetsController(window, view).apply {
            isAppearanceLightStatusBars = !dark
            isAppearanceLightNavigationBars = !dark
        }
    }
}

/** Compact page header with a navigation-drawer button. */
@Composable
internal fun OrcaHeader(title: String, openDrawer: () -> Unit, modifier: Modifier = Modifier, actions: @Composable RowScope.() -> Unit = {}) {
    Row(
        modifier.fillMaxWidth().height(OrcaLayout.headerHeight).padding(horizontal = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        IconButton(onClick = openDrawer) { Icon(Icons.Default.Menu, "Open navigation") }
        Spacer(Modifier.width(4.dp))
        Text(
            title, Modifier.weight(1f).semantics { heading() },
            style = MaterialTheme.typography.titleLarge.copy(fontSize = 20.sp, fontWeight = FontWeight.Medium),
        )
        actions()
    }
}

/** Orca's app artwork, clipped to a circle. */
@Composable
internal fun OrcaAvatar(modifier: Modifier = Modifier) {
    Image(
        painterResource(R.drawable.orca_artwork), null,
        modifier.clip(CircleShape), contentScale = ContentScale.Crop,
    )
}

internal data class DrawerDestination(val label: String, val icon: ImageVector, val selectedIcon: ImageVector)

@Composable
internal fun OrcaDrawerContent(destinations: List<DrawerDestination>, selected: Int, select: (Int) -> Unit) {
    ModalDrawerSheet(Modifier.widthIn(max = 320.dp)) {
        Column(Modifier.verticalScroll(rememberScrollState()).padding(horizontal = 12.dp)) {
            Spacer(Modifier.height(48.dp))
            Row(Modifier.padding(start = 16.dp, bottom = 24.dp), verticalAlignment = Alignment.CenterVertically) {
                OrcaAvatar(Modifier.size(40.dp))
                Spacer(Modifier.width(12.dp))
                Text("Orca", style = MaterialTheme.typography.titleLarge)
            }
            destinations.forEachIndexed { index, destination ->
                val active = index == selected
                NavigationDrawerItem(
                    label = { Text(destination.label, style = MaterialTheme.typography.labelLarge) },
                    icon = { Icon(if (active) destination.selectedIcon else destination.icon, null) },
                    selected = active,
                    onClick = { select(index) },
                )
            }
        }
    }
}

/** Icon-over-title header used at the top of bottom sheets. */
@Composable
internal fun SheetHeader(title: String, icon: ImageVector, modifier: Modifier = Modifier) {
    Column(modifier.fillMaxWidth(), horizontalAlignment = Alignment.CenterHorizontally) {
        Icon(icon, null, tint = MaterialTheme.colorScheme.secondary)
        Text(
            title, Modifier.padding(top = 16.dp, bottom = 8.dp).semantics { heading() },
            style = MaterialTheme.typography.headlineSmall, textAlign = TextAlign.Center,
        )
    }
}

/** Galaxy-style empty state: the download illustration, a title and an optional hint/action. */
@Composable
internal fun EmptyPlaceholder(title: String, description: String, modifier: Modifier = Modifier, action: (@Composable () -> Unit)? = null) {
    Column(
        modifier.fillMaxWidth().padding(horizontal = 32.dp, vertical = 48.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Image(downloadIllustration(), null, Modifier.widthIn(max = 240.dp).fillMaxWidth().aspectRatio(1f))
        Text(
            title, Modifier.padding(top = 36.dp), style = MaterialTheme.typography.titleLarge, textAlign = TextAlign.Center,
        )
        Text(
            description, Modifier.padding(top = 8.dp), style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant, textAlign = TextAlign.Center,
        )
        if (action != null) Box(Modifier.padding(top = 16.dp)) { action() }
    }
}

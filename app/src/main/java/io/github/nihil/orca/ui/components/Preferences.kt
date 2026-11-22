// Preference rows adapted from Seal (https://github.com/JunkFood02/Seal), GPL-3.0-or-later.
package io.github.nihil.orca.ui.components

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.selection.toggleable
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp

@Composable
internal fun PreferenceSubtitle(text: String, modifier: Modifier = Modifier) {
    Text(
        text,
        modifier.fillMaxWidth().padding(start = 16.dp, top = 24.dp, end = 16.dp, bottom = 8.dp).semantics { heading() },
        color = MaterialTheme.colorScheme.primary, style = MaterialTheme.typography.labelLarge,
    )
}

@Composable
private fun PreferenceRow(
    title: String,
    description: String?,
    icon: ImageVector?,
    enabled: Boolean,
    modifier: Modifier,
    trailing: (@Composable () -> Unit)?,
) {
    Row(
        modifier.fillMaxWidth().padding(horizontal = 8.dp, vertical = 16.dp).alpha(if (enabled) 1f else 0.38f),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (icon != null) {
            Icon(icon, null, Modifier.padding(start = 8.dp, end = 16.dp).size(24.dp), tint = MaterialTheme.colorScheme.onSurfaceVariant)
        } else {
            Spacer(Modifier.width(16.dp))
        }
        Column(Modifier.weight(1f).padding(end = 8.dp)) {
            Text(title, style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.onSurface)
            if (!description.isNullOrBlank()) Text(
                description, Modifier.padding(top = 2.dp), style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        trailing?.invoke()
    }
}

@Composable
internal fun PreferenceItem(
    title: String,
    description: String? = null,
    icon: ImageVector? = null,
    enabled: Boolean = true,
    onClick: (() -> Unit)? = null,
    trailing: (@Composable () -> Unit)? = null,
) {
    PreferenceRow(
        title, description, icon, enabled,
        if (onClick != null) Modifier.clickable(enabled = enabled, onClick = onClick) else Modifier,
        trailing,
    )
}

@Composable
internal fun PreferenceSwitch(
    title: String,
    description: String?,
    icon: ImageVector?,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
    enabled: Boolean = true,
) {
    PreferenceRow(
        title, description, icon, enabled,
        Modifier.toggleable(checked, enabled = enabled, role = Role.Switch, onValueChange = onCheckedChange),
    ) { Switch(checked, onCheckedChange = null, Modifier.padding(start = 12.dp, end = 6.dp), enabled = enabled) }
}

@Composable
internal fun PreferenceInfo(text: String, icon: ImageVector, modifier: Modifier = Modifier) {
    Column(modifier.fillMaxWidth().padding(16.dp)) {
        Icon(icon, null, tint = MaterialTheme.colorScheme.onSurfaceVariant)
        Text(
            text, Modifier.padding(top = 12.dp), style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

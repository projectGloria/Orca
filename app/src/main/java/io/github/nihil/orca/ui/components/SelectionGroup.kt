// Selection pills adapted from Seal (https://github.com/JunkFood02/Seal), GPL-3.0-or-later.
package io.github.nihil.orca.ui.components

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.spring
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ProvideTextStyle
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import io.github.nihil.orca.ui.theme.LocalFixedColorRoles

@Composable
internal fun SelectionGroupRow(modifier: Modifier = Modifier, content: @Composable RowScope.() -> Unit) {
    Row(modifier.selectableGroup(), horizontalArrangement = Arrangement.spacedBy(8.dp), content = content)
}

@Composable
internal fun SelectionGroupItem(
    selected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    content: @Composable RowScope.() -> Unit,
) {
    val fixed = LocalFixedColorRoles.current
    val scheme = MaterialTheme.colorScheme
    val corner by animateDpAsState(
        if (selected) 28.dp else 12.dp, spring(stiffness = Spring.StiffnessMediumLow), label = "itemShape",
    )
    val container by animateColorAsState(
        when {
            !enabled -> scheme.onSurface.copy(alpha = 0.12f)
            selected -> fixed.primaryFixed
            else -> scheme.surfaceContainer
        },
        label = "itemContainer",
    )
    val contentColor by animateColorAsState(
        when {
            !enabled -> scheme.onSurface.copy(alpha = 0.38f)
            selected -> fixed.onPrimaryFixed
            else -> scheme.onSurface
        },
        label = "itemContent",
    )
    Surface(
        selected = selected, onClick = onClick, modifier = modifier, enabled = enabled,
        shape = RoundedCornerShape(corner), color = container, contentColor = contentColor,
    ) {
        Row(
            Modifier.heightIn(min = 32.dp).widthIn(min = 56.dp).padding(horizontal = 16.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.Center,
        ) { ProvideTextStyle(MaterialTheme.typography.labelLarge) { content() } }
    }
}

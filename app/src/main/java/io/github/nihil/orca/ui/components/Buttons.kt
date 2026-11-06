package io.github.nihil.orca.ui.components

import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import io.github.nihil.orca.ui.theme.OrcaShapes
import io.github.nihil.orca.ui.theme.OrcaLayout

@Composable
internal fun OrcaPrimaryButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    icon: ImageVector? = null,
) {
    Button(onClick, modifier.fillMaxWidth().heightIn(min = PrimaryActionMetrics.minHeight), enabled = enabled, shape = OrcaShapes.button) {
        if (icon != null) {
            Icon(icon, contentDescription = null)
            Spacer(Modifier.width(PrimaryActionMetrics.iconLabelGap))
        }
        Text(text)
    }
}

@Composable
internal fun OrcaOutlinedActionButton(
    text: String,
    icon: ImageVector,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    smallIcon: Boolean = true,
) {
    OutlinedButton(onClick, modifier.heightIn(min = OrcaLayout.touchTarget), enabled = enabled, shape = OrcaShapes.button) {
        OrcaActionLabel(text, icon, smallIcon)
    }
}

@Composable
internal fun RowScope.OrcaActionLabel(text: String, icon: ImageVector, smallIcon: Boolean = false) {
    Icon(icon, contentDescription = null, modifier = if (smallIcon) Modifier.size(ActionButtonMetrics.smallIconSize) else Modifier)
    Spacer(Modifier.width(if (smallIcon) ActionButtonMetrics.iconLabelGap else ActionButtonMetrics.standardIconLabelGap))
    Text(text)
}

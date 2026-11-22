// Download cards, list rows and the action sheet adapted from Seal
// (https://github.com/JunkFood02/Seal), GPL-3.0-or-later.
package io.github.nihil.orca.ui

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.OpenInNew
import androidx.compose.material.icons.filled.*
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import io.github.nihil.orca.DownloadItem
import io.github.nihil.orca.DownloadRepository
import io.github.nihil.orca.duration
import io.github.nihil.orca.size
import io.github.nihil.orca.ui.theme.LocalDarkTheme
import io.github.nihil.orca.ui.theme.LocalFixedColorRoles
import io.github.nihil.orca.ui.theme.OrcaColors

internal val DownloadItem.completed get() = state == "COMPLETED"
internal val DownloadItem.active get() = state in DownloadRepository.activeStates
internal val DownloadItem.stopped get() = !completed && !active

internal fun DownloadItem.host(): String =
    runCatching { java.net.URI(url).host }.getOrNull()?.removePrefix("www.")?.takeIf { it.isNotBlank() } ?: url

internal fun DownloadItem.stateLabel(): String = when (state) {
    "QUEUED" -> "Queued"
    "PREPARING" -> "Preparing"
    "DOWNLOADING" -> if (progress >= 0) "%.1f %%".format(progress) else "Downloading"
    "FINISHING" -> "Finishing"
    "FAILED" -> "Failed"
    "CANCELLED" -> "Cancelled"
    "INTERRUPTED" -> "Interrupted"
    else -> "Completed"
}

private fun DownloadItem.metadata(separator: String) =
    listOf(size(bytes), duration(duration)).filter { it.isNotBlank() }.joinToString(separator)

@Composable
private fun Thumbnail(item: DownloadItem, modifier: Modifier) {
    Box(modifier.background(MaterialTheme.colorScheme.surfaceContainerHighest), contentAlignment = Alignment.Center) {
        Icon(
            if (item.audio) Icons.Outlined.Headphones else Icons.Outlined.Movie, null,
            tint = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        if (item.thumbnail.isNotBlank()) {
            AsyncImage(item.thumbnail, null, Modifier.fillMaxSize(), contentScale = ContentScale.Crop)
        }
    }
}

@Composable
private fun OverlayLabel(modifier: Modifier = Modifier, content: @Composable RowScope.() -> Unit) {
    Surface(modifier, shape = MaterialTheme.shapes.extraSmall, color = OrcaColors.label, contentColor = Color.White) {
        Row(Modifier.padding(horizontal = 4.dp, vertical = 2.dp), verticalAlignment = Alignment.CenterVertically) {
            ProvideTextStyle(MaterialTheme.typography.labelSmall) { content() }
        }
    }
}

@Composable
private fun StateIndicator(item: DownloadItem, modifier: Modifier) {
    if (item.completed) return
    OverlayLabel(modifier) {
        if (item.state == "FAILED") {
            Icon(Icons.Outlined.ErrorOutline, null, Modifier.size(12.dp))
            Spacer(Modifier.width(4.dp))
        }
        Text(item.stateLabel())
    }
}

@Composable
private fun InfoLabel(item: DownloadItem, modifier: Modifier) {
    val text = item.metadata("  ")
    if (text.isNotEmpty()) OverlayLabel(modifier) { Text(text) }
}

/** The round button drawn over a card's thumbnail. */
@Composable
private fun CardActionButton(item: DownloadItem, onAction: () -> Unit, modifier: Modifier) {
    val fixed = LocalFixedColorRoles.current
    val (icon, label) = when {
        item.completed -> Icons.Filled.PlayArrow to "Play ${item.title}"
        item.active -> Icons.Filled.Pause to "Cancel ${item.title}"
        item.state == "FAILED" -> Icons.Filled.RestartAlt to "Retry ${item.title}"
        else -> Icons.Filled.Download to "Resume ${item.title}"
    }
    Surface(
        onClick = onAction, modifier = modifier.size(64.dp), shape = CircleShape,
        color = fixed.onSecondaryFixed.copy(alpha = 0.68f), contentColor = fixed.secondaryFixed,
    ) {
        Box(contentAlignment = Alignment.Center) {
            if (item.active) {
                if (item.progress >= 0) CircularProgressIndicator(
                    progress = { (item.progress / 100f).coerceIn(0f, 1f) }, modifier = Modifier.fillMaxSize(),
                    color = fixed.secondaryFixed, trackColor = Color.Transparent, gapSize = 0.dp,
                ) else CircularProgressIndicator(Modifier.fillMaxSize(), color = fixed.secondaryFixed)
            }
            Icon(icon, label, Modifier.size(36.dp))
        }
    }
}

@Composable
internal fun MediaCard(
    item: DownloadItem,
    onClick: () -> Unit,
    onAction: () -> Unit,
    onMore: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val scheme = MaterialTheme.colorScheme
    Card(
        onClick = onClick, modifier = modifier.fillMaxWidth(), shape = MaterialTheme.shapes.medium,
        colors = CardDefaults.cardColors(
            containerColor = if (LocalDarkTheme.current) scheme.surfaceContainer else scheme.surfaceContainerLowest,
        ),
    ) {
        Box(Modifier.fillMaxWidth().aspectRatio(16f / 9f)) {
            Thumbnail(item, Modifier.fillMaxSize())
            StateIndicator(item, Modifier.align(Alignment.TopStart).padding(horizontal = 12.dp, vertical = 8.dp))
            CardActionButton(item, onAction, Modifier.align(Alignment.Center))
            InfoLabel(item, Modifier.align(Alignment.BottomEnd).padding(4.dp))
        }
        Row(verticalAlignment = Alignment.Top) {
            Column(Modifier.weight(1f).padding(start = 12.dp, top = 12.dp, bottom = 12.dp)) {
                Text(item.title, style = MaterialTheme.typography.titleSmall, maxLines = 2, overflow = TextOverflow.Ellipsis)
                Text(
                    item.host(), Modifier.padding(top = 3.dp), style = MaterialTheme.typography.bodySmall,
                    color = scheme.onSurfaceVariant, maxLines = 1, overflow = TextOverflow.Ellipsis,
                )
            }
            IconButton(onClick = onMore) { Icon(Icons.Default.MoreVert, "More actions for ${item.title}", Modifier.size(20.dp)) }
        }
    }
}

@Composable
private fun ListItemStateText(item: DownloadItem) {
    val dark = LocalDarkTheme.current
    val scheme = MaterialTheme.colorScheme
    val (icon, tint) = when {
        item.completed -> Icons.Filled.CheckCircle to if (dark) OrcaColors.successDark else OrcaColors.successLight
        item.state == "FAILED" -> Icons.Filled.Error to scheme.error
        item.stopped -> Icons.Filled.Cancel to scheme.onSurfaceVariant
        else -> null to scheme.primary
    }
    Row(Modifier.padding(top = 6.dp), verticalAlignment = Alignment.CenterVertically) {
        if (icon != null) {
            Icon(icon, null, Modifier.size(14.dp), tint = tint)
            Spacer(Modifier.width(4.dp))
        }
        Text(item.stateLabel(), style = MaterialTheme.typography.labelMedium, color = if (icon == null) tint else scheme.onSurfaceVariant)
    }
    if (item.active) {
        val progress = Modifier.padding(top = 6.dp).fillMaxWidth()
        if (item.progress >= 0) LinearProgressIndicator(progress = { (item.progress / 100f).coerceIn(0f, 1f) }, modifier = progress)
        else LinearProgressIndicator(progress)
    }
}

@Composable
internal fun MediaListItem(item: DownloadItem, onClick: () -> Unit, onMore: () -> Unit, modifier: Modifier = Modifier) {
    Row(
        modifier.fillMaxWidth().clickable(onClick = onClick).padding(start = 12.dp, top = 8.dp, bottom = 8.dp),
        verticalAlignment = Alignment.Top,
    ) {
        Box(Modifier.width(160.dp).aspectRatio(16f / 9f).clip(MaterialTheme.shapes.extraSmall)) {
            Thumbnail(item, Modifier.fillMaxSize())
            InfoLabel(item, Modifier.align(Alignment.BottomEnd).padding(4.dp))
        }
        Column(Modifier.weight(1f).padding(start = 12.dp)) {
            Text(item.title, style = MaterialTheme.typography.titleSmall, maxLines = 2, overflow = TextOverflow.Ellipsis)
            Text(
                item.host(), Modifier.padding(top = 3.dp), style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1, overflow = TextOverflow.Ellipsis,
            )
            ListItemStateText(item)
        }
        IconButton(onClick = onMore) { Icon(Icons.Default.MoreVert, "More actions for ${item.title}", Modifier.size(20.dp)) }
    }
}

internal class DownloadActions(
    val play: (DownloadItem) -> Unit,
    val share: (DownloadItem) -> Unit,
    val resume: (DownloadItem) -> Unit,
    val cancel: (DownloadItem) -> Unit,
    val details: (DownloadItem) -> Unit,
    val openLink: (DownloadItem) -> Unit,
    val copyLink: (DownloadItem) -> Unit,
    val delete: (DownloadItem) -> Unit,
)

/** The card's primary action: play a saved file, cancel a running one, or resume a stopped one. */
internal fun DownloadActions.primary(item: DownloadItem) = when {
    item.completed -> play(item)
    item.active -> cancel(item)
    else -> resume(item)
}

@Composable
private fun ActionSheetButton(
    label: String,
    icon: ImageVector,
    onClick: () -> Unit,
    container: Color,
    content: Color,
    outlined: Boolean = false,
) {
    Column(
        Modifier.widthIn(min = 88.dp).clip(MaterialTheme.shapes.large)
            .clickable(role = Role.Button, onClick = onClick).padding(8.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Box(
            Modifier.size(80.dp, 64.dp).clip(CircleShape).background(container)
                .then(if (outlined) Modifier.border(BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant), CircleShape) else Modifier),
            contentAlignment = Alignment.Center,
        ) { Icon(icon, null, Modifier.size(24.dp), tint = content) }
        Text(label, Modifier.padding(top = 8.dp), style = MaterialTheme.typography.labelMedium)
    }
}

@Composable
private fun InfoRow(icon: ImageVector, title: String, value: String) {
    if (value.isBlank()) return
    Row(
        Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 12.dp).semantics(mergeDescendants = true) {},
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(icon, null, Modifier.padding(end = 16.dp).size(24.dp), tint = MaterialTheme.colorScheme.onSurfaceVariant)
        Column {
            Text(title, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Text(value, style = MaterialTheme.typography.bodyMedium, maxLines = 4, overflow = TextOverflow.Ellipsis)
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun ActionSheet(item: DownloadItem, actions: DownloadActions, dismiss: () -> Unit) {
    val fixed = LocalFixedColorRoles.current
    val scheme = MaterialTheme.colorScheme
    fun run(action: (DownloadItem) -> Unit): () -> Unit = { dismiss(); action(item) }
    ModalBottomSheet(onDismissRequest = dismiss, sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)) {
        Column(Modifier.fillMaxWidth().padding(horizontal = 20.dp)) {
            Text(item.title, style = MaterialTheme.typography.titleMedium, maxLines = 3, overflow = TextOverflow.Ellipsis)
            Text(item.host(), Modifier.padding(top = 2.dp), style = MaterialTheme.typography.bodySmall, color = scheme.onSurfaceVariant)
            Text(item.stateLabel(), Modifier.padding(top = 4.dp), style = MaterialTheme.typography.labelMedium, color = scheme.primary)
        }
        LazyRow(
            Modifier.fillMaxWidth().padding(top = 12.dp, bottom = 24.dp),
            contentPadding = PaddingValues(horizontal = 12.dp),
        ) {
            if (item.completed) {
                item { ActionSheetButton("Play", Icons.Filled.PlayArrow, run(actions.play), fixed.primaryFixed, fixed.onPrimaryFixedVariant) }
                item { ActionSheetButton("Share", Icons.Filled.Share, run(actions.share), fixed.secondaryFixed, fixed.onSecondaryFixedVariant) }
            }
            if (item.active) item {
                ActionSheetButton("Cancel", Icons.Filled.Cancel, run(actions.cancel), scheme.surfaceContainerHighest, scheme.onSurface)
            }
            if (item.stopped) item {
                if (item.state == "FAILED") ActionSheetButton("Retry", Icons.Filled.RestartAlt, run(actions.resume), scheme.errorContainer, scheme.onErrorContainer)
                else ActionSheetButton("Resume", Icons.Filled.Download, run(actions.resume), fixed.tertiaryFixed, fixed.onTertiaryFixedVariant)
            }
            if (item.details.isNotBlank()) item {
                ActionSheetButton("Details", Icons.Outlined.Article, run(actions.details), fixed.secondaryFixed, fixed.onSecondaryFixedVariant)
            }
            item { ActionSheetButton("Copy link", Icons.Outlined.ContentCopy, run(actions.copyLink), Color.Transparent, scheme.onSurface, outlined = true) }
            item { ActionSheetButton("Original link", Icons.AutoMirrored.Filled.OpenInNew, run(actions.openLink), Color.Transparent, scheme.onSurface, outlined = true) }
            if (!item.active) item {
                ActionSheetButton("Delete", Icons.Outlined.Delete, run(actions.delete), Color.Transparent, scheme.error, outlined = true)
            }
        }
        HorizontalDivider()
        Text(
            "Media info", Modifier.padding(start = 16.dp, top = 24.dp, bottom = 8.dp),
            style = MaterialTheme.typography.titleMedium,
        )
        InfoRow(Icons.Outlined.Link, "Link", item.url)
        InfoRow(
            if (item.audio) Icons.Outlined.Headphones else Icons.Outlined.Movie, "Format",
            if (item.audio) "Audio file" else if (item.height > 0) "Video file · up to ${item.height}p" else "Video file",
        )
        InfoRow(Icons.Outlined.SdStorage, "Size", size(item.bytes))
        InfoRow(Icons.Outlined.Schedule, "Length", duration(item.duration))
        if (item.stopped) InfoRow(Icons.Outlined.ErrorOutline, "Status", item.error)
        Spacer(Modifier.height(28.dp))
    }
}

/** Number of saved videos and audio tracks, phrased like Seal's queue sub-header. */
internal fun mediaCountLabel(items: List<DownloadItem>): String {
    val audio = items.count { it.audio }
    val video = items.size - audio
    fun plural(count: Int, word: String) = "$count $word" + if (count == 1) "" else "s"
    return "${plural(video, "video")}, ${plural(audio, "audio track")}"
}

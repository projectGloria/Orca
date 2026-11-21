package io.github.nihil.orca

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.selection.toggleable
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.listSaver
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.state.ToggleableState
import coil.compose.AsyncImage
import io.github.nihil.orca.ui.OptionsSheetMetrics
import io.github.nihil.orca.ui.components.DarkDialogSystemBars
import io.github.nihil.orca.ui.components.OrcaPrimaryButton
import io.github.nihil.orca.ui.components.SectionHeading
import io.github.nihil.orca.ui.theme.*

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable internal fun OptionsSheet(
    state: OptionsState,
    dismiss: () -> Unit,
    download: (List<MediaInfo>, DownloadOptions) -> Unit,
    submitting: Boolean = false,
) {
    val available = remember(state.id) { state.media.indices.filter { state.media[it].available }.toSet() }
    var selected by rememberSaveable(state.id, stateSaver = listSaver<Set<Int>, Int>(
        save = { it.toList() }, restore = { it.toSet() },
    )) { mutableStateOf(available) }
    var audio by rememberSaveable(state.id) { mutableStateOf(false) }
    var height by rememberSaveable(state.id) { mutableIntStateOf(0) }
    val first = state.media.firstOrNull()
    val selection = when {
        selected.isEmpty() -> ToggleableState.Off
        selected.size == available.size -> ToggleableState.On
        else -> ToggleableState.Indeterminate
    }
    val submissionInProgress by rememberUpdatedState(submitting)

    ModalBottomSheet(
        onDismissRequest = dismiss,
        containerColor = MaterialTheme.colorScheme.surfaceContainer,
        sheetState = rememberModalBottomSheetState(
            skipPartiallyExpanded = true,
            confirmValueChange = { it != SheetValue.Hidden || !submissionInProgress },
        ),
    ) {
        DarkDialogSystemBars()
        Column(Modifier.fillMaxWidth().padding(horizontal = OrcaLayout.sheetInset)) {
            LazyColumn(
                Modifier.weight(1f, fill = false),
                contentPadding = PaddingValues(bottom = OrcaSpacing.lg),
                verticalArrangement = Arrangement.spacedBy(OrcaSpacing.md),
            ) {
                item {
                    Text(if (state.playlist) "Choose playlist items" else "Download options", style = OrcaTextStyles.sheetTitle)
                }
                if (!state.playlist && first != null) item { MediaPreview(first) }
                item {
                    SectionHeading("Format")
                    FlowRow(horizontalArrangement = Arrangement.spacedBy(OrcaSpacing.sm)) {
                        FilterChip(
                            !audio, { audio = false }, label = { Text("Video") }, enabled = !submitting,
                            leadingIcon = { Icon(Icons.Default.Videocam, null) },
                            modifier = Modifier.heightIn(min = OrcaLayout.touchTarget),
                        )
                        FilterChip(
                            audio, { audio = true }, label = { Text("Audio") }, enabled = !submitting,
                            leadingIcon = { Icon(Icons.Default.Headphones, null) },
                            modifier = Modifier.heightIn(min = OrcaLayout.touchTarget),
                        )
                    }
                }
                item {
                    if (audio) {
                        Text("Save the original audio stream.", style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant)
                    } else if (state.playlist && first?.qualities.isNullOrEmpty()) {
                        Text("Video quality · Best available for each item", style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant)
                    } else {
                        QualityMenu(first?.qualities.orEmpty(), height, !submitting) { height = it }
                    }
                }
                if (state.playlist) {
                    item {
                        HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
                        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                            TriStateCheckbox(
                                state = selection,
                                onClick = { selected = if (selection == ToggleableState.On) emptySet() else available },
                                enabled = available.isNotEmpty() && !submitting,
                                modifier = Modifier.semantics { contentDescription = "Select all available items" },
                            )
                            Text("Select all", Modifier.weight(1f), style = MaterialTheme.typography.titleMedium)
                            Text("${selected.size} of ${available.size}", style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                    }
                    if (state.media.isEmpty()) {
                        item { Text("No playlist entries were found.", style = MaterialTheme.typography.bodyMedium) }
                    }
                    items(state.media.indices.toList(), key = { it }) { index ->
                        val entry = state.media[index]
                        Row(
                            Modifier.fillMaxWidth().heightIn(min = OrcaLayout.touchTarget)
                                .semantics { contentDescription = "Select ${entry.title}" }
                                .toggleable(index in selected, enabled = entry.available && !submitting, role = Role.Checkbox) { checked ->
                                    selected = if (checked) selected + index else selected - index
                                },
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(OrcaSpacing.md),
                        ) {
                            Checkbox(index in selected, onCheckedChange = null, enabled = entry.available && !submitting)
                            Column(Modifier.padding(vertical = OrcaSpacing.xs)) {
                                Text(entry.title)
                                if (!entry.available) Text("Unavailable or access required", style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant)
                            }
                        }
                    }
                }
            }
            HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
            val button = when {
                submitting -> "Adding downloads…"
                state.playlist -> "Download ${selected.size} ${if (selected.size == 1) "item" else "items"}"
                else -> "Download"
            }
            OrcaPrimaryButton(
                button, { download(state.media.filterIndexed { index, _ -> index in selected }, DownloadOptions(audio, height)) },
                modifier = Modifier.padding(vertical = OrcaSpacing.sm).testTag("confirmDownload"),
                enabled = selected.isNotEmpty() && !submitting,
                icon = Icons.Default.FileDownload,
            )
        }
    }
}

@Composable private fun MediaPreview(media: MediaInfo) {
    Row(
        Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(OrcaSpacing.md),
    ) {
        Box(
            Modifier.size(OptionsSheetMetrics.previewWidth, OptionsSheetMetrics.previewHeight)
                .clip(OrcaShapes.badge).background(MaterialTheme.colorScheme.surfaceVariant),
            contentAlignment = Alignment.Center,
        ) {
            Icon(Icons.Default.VideoLibrary, null, tint = MaterialTheme.colorScheme.primary)
            if (media.thumbnail.isNotBlank()) AsyncImage(media.thumbnail, null, Modifier.fillMaxSize(), contentScale = ContentScale.Crop)
        }
        Column(Modifier.weight(1f)) {
            Text(media.title, style = MaterialTheme.typography.titleMedium)
            val metadata = listOf(duration(media.duration), media.bytes?.let { "About ${size(it)}" }.orEmpty())
                .filter { it.isNotBlank() }.joinToString(" · ")
            if (metadata.isNotEmpty()) Text(metadata, style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

@Composable private fun QualityMenu(qualities: List<Quality>, height: Int, enabled: Boolean, select: (Int) -> Unit) {
    var expanded by remember { mutableStateOf(false) }
    Box {
        OutlinedButton(
            onClick = { expanded = true }, enabled = enabled,
            modifier = Modifier.fillMaxWidth().heightIn(min = OrcaLayout.touchTarget),
        ) {
            Text(if (height == 0) "Best available" else "Up to ${height}p", Modifier.weight(1f))
            Icon(Icons.Default.ExpandMore, null)
        }
        DropdownMenu(expanded, { expanded = false }) {
            DropdownMenuItem(text = { Text("Best available") }, onClick = { select(0); expanded = false })
            qualities.forEach { quality ->
                val estimate = quality.bytes?.let { " · Video about ${size(it)}" }.orEmpty()
                DropdownMenuItem(text = { Text(quality.label + estimate) }, onClick = { select(quality.height); expanded = false })
            }
        }
    }
}

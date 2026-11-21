package io.github.nihil.orca.ui

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import io.github.nihil.orca.LinkParser
import io.github.nihil.orca.ui.components.*
import io.github.nihil.orca.ui.theme.*

@OptIn(ExperimentalLayoutApi::class)
@Composable internal fun LinkComposer(
    link: String,
    busy: Boolean,
    updatingEngine: Boolean,
    ask: Boolean,
    change: (String) -> Unit,
    paste: () -> Unit,
    options: () -> Unit,
    download: () -> Unit,
    playlist: () -> Unit,
) {
    val focus = LocalFocusManager.current
    val canSubmit = !busy && !updatingEngine && link.isNotBlank()
    val submit = { focus.clearFocus(); download() }

    Column(verticalArrangement = Arrangement.spacedBy(OrcaSpacing.xl)) {
        Column(verticalArrangement = Arrangement.spacedBy(OrcaSpacing.sm)) {
            Text("New download", style = OrcaTextStyles.hero)
            Text("Save a video, audio track, or playlist to your device.",
                style = MaterialTheme.typography.bodyLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        OrcaPanel {
            OutlinedTextField(
                value = link, onValueChange = change,
                label = { Text("Video or playlist link") }, placeholder = { Text("https://") },
                leadingIcon = { Icon(Icons.Default.Link, null) },
                trailingIcon = {
                    if (link.isNotEmpty()) IconButton(onClick = { change("") }, enabled = !busy) {
                        Icon(Icons.Default.Close, "Clear link")
                    }
                },
                modifier = Modifier.fillMaxWidth(), enabled = !busy, singleLine = true,
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Uri, imeAction = ImeAction.Go),
                keyboardActions = KeyboardActions(onGo = { if (canSubmit) submit() }),
                shape = OrcaShapes.input,
            )
            FlowRow(horizontalArrangement = Arrangement.spacedBy(OrcaSpacing.sm)) {
                OrcaOutlinedActionButton("Paste", Icons.Default.ContentPaste, paste, enabled = !busy)
                OrcaOutlinedActionButton("Options", Icons.Default.Tune, {
                    focus.clearFocus()
                    options()
                }, enabled = canSubmit)
            }
            if (LinkParser.hasPlaylist(link) && !LinkParser.isPlaylistOnly(link)) {
                TextButton(onClick = { focus.clearFocus(); playlist() }, enabled = canSubmit) {
                    Text("Choose from this playlist")
                }
            }
            HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
            Text(
                when {
                    busy -> "Checking link…"
                    updatingEngine -> "Download engine is updating…"
                    ask -> "Choose a format before saving."
                    else -> "Best available video by default"
                },
                Modifier.semantics { liveRegion = LiveRegionMode.Polite },
                style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            OrcaPrimaryButton("Download", submit, enabled = canSubmit, icon = Icons.Default.FileDownload)
            if (busy) LinearProgressIndicator(Modifier.fillMaxWidth())
        }
    }
}

@Composable internal fun CollectionSummary(saved: Int, active: Int, needsAttention: Int, openLibrary: () -> Unit) {
    Surface(
        onClick = openLibrary, shape = OrcaShapes.downloadCard,
        color = MaterialTheme.colorScheme.surfaceContainerHigh,
    ) {
        Row(
            Modifier.fillMaxWidth().heightIn(min = OrcaLayout.touchTarget).padding(OrcaSpacing.lg),
            horizontalArrangement = Arrangement.spacedBy(OrcaSpacing.md),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon(Icons.Default.FolderOpen, null, tint = MaterialTheme.colorScheme.primary)
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(OrcaSpacing.xs)) {
                Text("View downloads", style = MaterialTheme.typography.titleMedium)
                val counts = buildList {
                    add("$saved saved")
                    if (active > 0) add("$active in progress")
                    if (needsAttention > 0) add("$needsAttention to review")
                }
                Text(counts.joinToString(" · "), style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            Icon(Icons.AutoMirrored.Filled.ArrowForward, null, tint = MaterialTheme.colorScheme.primary)
        }
    }
}

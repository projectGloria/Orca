// New-download sheet styled after Seal's download dialog (https://github.com/JunkFood02/Seal), GPL-3.0-or-later.
package io.github.nihil.orca.ui

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.PlaylistAdd
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.outlined.ContentPaste
import androidx.compose.material.icons.outlined.DoneAll
import androidx.compose.material.icons.outlined.FileDownload
import androidx.compose.material.icons.outlined.Link
import androidx.compose.material.icons.outlined.Tune
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import io.github.nihil.orca.LinkParser
import io.github.nihil.orca.ui.components.*
import io.github.nihil.orca.ui.theme.OrcaSpacing

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
internal fun LinkSheet(
    link: String,
    busy: Boolean,
    updatingEngine: Boolean,
    ask: Boolean,
    change: (String) -> Unit,
    paste: () -> Unit,
    options: () -> Unit,
    download: () -> Unit,
    playlist: () -> Unit,
    dismiss: () -> Unit,
) {
    val focus = LocalFocusManager.current
    val canSubmit = !busy && !updatingEngine && link.isNotBlank()
    val submit = { focus.clearFocus(); download() }

    ModalBottomSheet(onDismissRequest = dismiss, sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)) {
        Column(
            Modifier.fillMaxWidth().padding(horizontal = OrcaSpacing.sheet).navigationBarsPadding().imePadding(),
        ) {
            SheetHeader("New download", Icons.Outlined.FileDownload)
            Text(
                "Save a video, audio track, or playlist to your device.",
                Modifier.fillMaxWidth().padding(bottom = 16.dp),
                style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = androidx.compose.ui.text.style.TextAlign.Center,
            )
            OutlinedTextField(
                value = link, onValueChange = change,
                label = { Text("Video or playlist link") }, placeholder = { Text("https://") },
                leadingIcon = { Icon(Icons.Outlined.Link, null) },
                trailingIcon = {
                    if (link.isNotEmpty()) IconButton(onClick = { change("") }, enabled = !busy) {
                        Icon(Icons.Default.Close, "Clear link")
                    }
                },
                modifier = Modifier.fillMaxWidth(), enabled = !busy, singleLine = true,
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Uri, imeAction = ImeAction.Go),
                keyboardActions = KeyboardActions(onGo = { if (canSubmit) submit() }),
            )
            FlowRow(Modifier.padding(top = 8.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                AssistChip(
                    onClick = paste, enabled = !busy, label = { Text("Paste") },
                    leadingIcon = { Icon(Icons.Outlined.ContentPaste, null, Modifier.size(AssistChipDefaults.IconSize)) },
                )
                if (LinkParser.hasPlaylist(link) && !LinkParser.isPlaylistOnly(link)) AssistChip(
                    onClick = { focus.clearFocus(); playlist() }, enabled = canSubmit,
                    label = { Text("Choose from this playlist") },
                    leadingIcon = { Icon(Icons.AutoMirrored.Outlined.PlaylistAdd, null, Modifier.size(AssistChipDefaults.IconSize)) },
                )
            }
            Text(
                when {
                    busy -> "Checking link…"
                    updatingEngine -> "Download engine is updating…"
                    ask -> "Choose a format before saving."
                    else -> "Best available video by default"
                },
                Modifier.padding(top = 8.dp).semantics { liveRegion = LiveRegionMode.Polite },
                style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            if (busy) LinearProgressIndicator(Modifier.fillMaxWidth().padding(top = 12.dp))
            Row(
                Modifier.fillMaxWidth().padding(top = 20.dp, bottom = 24.dp),
                horizontalArrangement = Arrangement.spacedBy(12.dp, androidx.compose.ui.Alignment.End),
            ) {
                OutlinedButtonWithIcon("Options", Icons.Outlined.Tune, { focus.clearFocus(); options() }, enabled = canSubmit)
                FilledButtonWithIcon("Download", Icons.Outlined.DoneAll, submit, enabled = canSubmit)
            }
        }
    }
}

package io.github.nihil.orca.ui

import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.foundation.text.KeyboardOptions
import io.github.nihil.orca.LinkParser
import io.github.nihil.orca.ui.components.*
import io.github.nihil.orca.ui.theme.*

@Composable internal fun HomeHero() {
    Column(Modifier.padding(top = OrcaSpacing.lg), verticalArrangement = Arrangement.spacedBy(OrcaSpacing.md)) {
        Eyebrow("YOUR OFFLINE COLLECTION")
        Text("Keep what\nmoves you.", style = OrcaTextStyles.hero)
        Text("Videos and audio you love.\nSaved in their original quality.", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

@Composable internal fun LinkComposer(link: String, busy: Boolean, change: (String) -> Unit, paste: () -> Unit, options: () -> Unit, download: () -> Unit, playlist: () -> Unit) {
    OrcaPanel {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(OrcaSpacing.sm)) {
            Icon(Icons.Default.AddLink, null, tint = MaterialTheme.colorScheme.primary)
            Text("Start with a link", style = MaterialTheme.typography.titleMedium)
        }
        OutlinedTextField(value = link, onValueChange = change, label = { Text("Video or playlist link") },
            placeholder = { Text("https://") }, modifier = Modifier.fillMaxWidth(), maxLines = 3,
            shape = OrcaShapes.input, keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Uri),
            colors = OutlinedTextFieldDefaults.colors(unfocusedContainerColor = MaterialTheme.colorScheme.background, unfocusedBorderColor = MaterialTheme.colorScheme.outlineVariant))
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(OrcaSpacing.sm)) {
            OrcaOutlinedActionButton("Paste", Icons.Default.ContentPaste, paste, Modifier.weight(1f))
            OrcaOutlinedActionButton("Options", Icons.Default.Tune, options, Modifier.weight(1f), enabled = !busy && link.isNotBlank())
        }
        OrcaPrimaryButton(if (busy) "Preparing…" else "Download", download, enabled = !busy && link.isNotBlank(), icon = Icons.Default.South)
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(OrcaSpacing.sm, Alignment.CenterHorizontally)) {
            Icon(Icons.Default.HighQuality, null, Modifier.size(HomeLayout.qualityIcon), tint = MaterialTheme.colorScheme.onSurfaceVariant)
            Text("Video · Best available", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        if (LinkParser.hasPlaylist(link) && !LinkParser.isPlaylistOnly(link)) TextButton(onClick = playlist, enabled = !busy) { Text("Choose from this playlist") }
        if (busy) LinearProgressIndicator(Modifier.fillMaxWidth())
    }
}

@Composable internal fun CollectionSummary(saved: Int, active: Int, openLibrary: () -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(OrcaSpacing.lg)) {
        SectionHeading("Your collection")
        Surface(onClick = openLibrary, shape = OrcaShapes.downloadCard, color = MaterialTheme.colorScheme.surface) {
            Row(Modifier.fillMaxWidth().padding(OrcaSpacing.xl), horizontalArrangement = Arrangement.spacedBy(OrcaSpacing.xl), verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(OrcaSpacing.xs)) {
                    Text(saved.toString().padStart(2, '0'), style = OrcaTextStyles.stat)
                    Text("Saved files", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(OrcaSpacing.xs)) {
                    Text(active.toString().padStart(2, '0'), style = OrcaTextStyles.stat)
                    Text("In progress", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                Icon(Icons.AutoMirrored.Filled.ArrowForward, "Open collection", tint = MaterialTheme.colorScheme.primary)
            }
        }
        InfoLine(Icons.Default.PhonelinkLock, "Yours. On your device.", "No account or cloud. Find your files in Movies/Orca or Music/Orca.")
    }
}

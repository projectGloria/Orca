package io.github.nihil.orca

import android.Manifest
import android.content.Intent
import android.content.ClipData
import android.content.ClipboardManager
import android.net.Uri
import android.os.Bundle
import android.os.Build
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.SystemBarStyle
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.OpenInNew
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.media3.common.MediaItem
import androidx.media3.common.PlaybackException
import androidx.media3.common.Player
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.ui.PlayerView
import coil.compose.AsyncImage
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import kotlinx.coroutines.CancellationException
import android.app.Application

class MainActivity : ComponentActivity() {
    private val model: OrcaModel by viewModels()
    private val permission = registerForActivityResult(ActivityResultContracts.RequestPermission()) { }
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge(statusBarStyle = SystemBarStyle.dark(android.graphics.Color.TRANSPARENT), navigationBarStyle = SystemBarStyle.dark(android.graphics.Color.TRANSPARENT))
        if (savedInstanceState == null) accept(intent)
        permission.launch(Manifest.permission.POST_NOTIFICATIONS)
        setContent { OrcaTheme { OrcaApp(model) } }
    }
    override fun onNewIntent(intent: Intent) { super.onNewIntent(intent); accept(intent) }
    private fun accept(intent: Intent) {
        if (intent.action == Intent.ACTION_SEND) LinkParser.fromText(intent.getStringExtra(Intent.EXTRA_TEXT).orEmpty())?.let { model.link.value = it }
    }
}

data class OptionsState(val media: List<MediaInfo>, val playlist: Boolean)
class OrcaModel(application: Application) : AndroidViewModel(application) {
    val repo = application.repository
    val link = MutableStateFlow("")
    val busy = MutableStateFlow(false)
    val message = MutableStateFlow<String?>(null)
    val details = MutableStateFlow("")
    val options = MutableStateFlow<OptionsState?>(null)
    val engineVersion = MutableStateFlow("Checking…")
    val checkingAppUpdate = MutableStateFlow(false)
    val appUpdateStatus = MutableStateFlow("")
    val availableAppRelease = MutableStateFlow<AppRelease?>(null)
    val items = repo.items.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())
    val ask = repo.preferences.ask.stateIn(viewModelScope, SharingStarted.Eagerly, false)
    init { viewModelScope.launch { engineVersion.value = runCatching { repo.engine.version() }.getOrElse { "Unavailable" } } }
    fun action(block: suspend () -> Unit) { viewModelScope.launch { try { block() } catch (e: Exception) { message.value = friendlyError(e); details.value = e.stackTraceToString() } } }
    fun start(configure: Boolean = false, playlist: Boolean = false) {
        if (busy.value) return
        val url = LinkParser.fromText(link.value)
        if (url == null) { message.value = "Paste a valid http or https link."; return }
        busy.value = true
        action {
            try {
                if (playlist || LinkParser.isPlaylistOnly(url)) options.value = OptionsState(repo.playlist(url), true)
                else {
                    val info = repo.inspect(url)
                    if (info.playlist) options.value = OptionsState(repo.playlist(url), true)
                    else if (configure || repo.preferences.ask.first()) options.value = OptionsState(listOf(info), false)
                    else repo.enqueue(listOf(info), DownloadOptions())
                }
            } finally { busy.value = false }
        }
    }
    fun enqueue(media: List<MediaInfo>, selection: DownloadOptions) { action { repo.enqueue(media, selection); options.value = null } }
    fun update() {
        if (busy.value) return
        busy.value = true
        action { try { engineVersion.value = repo.update(); message.value = "Engine is up to date."; details.value = "" } finally { busy.value = false } }
    }
    fun checkAppUpdate() {
        if (checkingAppUpdate.value) return
        checkingAppUpdate.value = true
        appUpdateStatus.value = "Checking GitHub releases…"
        viewModelScope.launch {
            try {
                when (val result = GitHubAppUpdates().check(BuildConfig.VERSION_CODE.toLong(), Build.SUPPORTED_ABIS.toList())) {
                    AppUpdateResult.Current -> appUpdateStatus.value = "You're up to date."
                    AppUpdateResult.NoRelease -> appUpdateStatus.value = "No app releases are available yet."
                    AppUpdateResult.Preparing -> appUpdateStatus.value = "The latest release is still being prepared. Try again shortly."
                    is AppUpdateResult.Available -> {
                        appUpdateStatus.value = "Version ${result.release.versionName} is available."
                        availableAppRelease.value = result.release
                    }
                }
            } catch (cancel: CancellationException) { throw cancel }
            catch (error: Exception) {
                appUpdateStatus.value = if (error is java.io.IOException) "Couldn't connect to GitHub. Check your connection and try again." else error.message ?: "Couldn't check for updates. Try again later."
            } finally { checkingAppUpdate.value = false }
        }
    }
}

private val Ocean = Color(0xFF48BDEE)
@Composable fun OrcaTheme(content: @Composable () -> Unit) {
    MaterialTheme(colorScheme = darkColorScheme(primary = Ocean, onPrimary = Color(0xFF002C40), secondary = Color(0xFF8FD3EC), background = Color(0xFF080F18), surface = Color(0xFF101C2A), surfaceVariant = Color(0xFF192A3B), onSurface = Color(0xFFE4EDF7)), content = content)
}
@OptIn(ExperimentalMaterial3Api::class)
@Composable fun OrcaApp(model: OrcaModel) {
    val items by model.items.collectAsStateWithLifecycle()
    val link by model.link.collectAsStateWithLifecycle()
    val busy by model.busy.collectAsStateWithLifecycle()
    val ask by model.ask.collectAsStateWithLifecycle()
    val options by model.options.collectAsStateWithLifecycle()
    val message by model.message.collectAsStateWithLifecycle()
    val details by model.details.collectAsStateWithLifecycle()
    val version by model.engineVersion.collectAsStateWithLifecycle()
    val checkingAppUpdate by model.checkingAppUpdate.collectAsStateWithLifecycle()
    val appUpdateStatus by model.appUpdateStatus.collectAsStateWithLifecycle()
    val appRelease by model.availableAppRelease.collectAsStateWithLifecycle()
    var destination by remember { mutableIntStateOf(0) }
    var settings by remember { mutableStateOf(false) }
    var playing by remember { mutableStateOf<DownloadItem?>(null) }
    var deleting by remember { mutableStateOf<DownloadItem?>(null) }
    var errorItem by remember { mutableStateOf<DownloadItem?>(null) }
    val context = LocalContext.current
    fun play(item: DownloadItem) {
        if (model.repo.publisher.exists(item.uri)) playing = item else { model.message.value = "File is missing. Download it again from the original link."; model.details.value = "" }
    }
    Scaffold(
        topBar = { CenterAlignedTopAppBar(title = { Text(if (settings) "Settings" else if (destination == 0) "ORCA" else "Downloads", fontWeight = FontWeight.Bold) }, navigationIcon = { if (settings) IconButton(onClick = { settings = false }) { Icon(Icons.AutoMirrored.Filled.ArrowBack, "Back") } }, actions = { if (!settings && destination == 0) IconButton(onClick = { settings = true }) { Icon(Icons.Default.Settings, "Settings") } }) },
        bottomBar = { if (!settings) NavigationBar { NavigationBarItem(selected = destination == 0, onClick = { destination = 0 }, icon = { Icon(Icons.Default.Waves, null) }, label = { Text("Home") }); NavigationBarItem(selected = destination == 1, onClick = { destination = 1 }, icon = { Icon(Icons.Default.VideoLibrary, null) }, label = { Text("Downloads") }) } }
    ) { padding ->
        if (settings) SettingsScreen(Modifier.padding(padding), ask, version, busy || items.any { it.state in DownloadRepository.activeStates }, { value -> model.action { model.repo.preferences.setAsk(value) } }, model::update, checkingAppUpdate, appUpdateStatus, model::checkAppUpdate)
        else if (destination == 0) LazyColumn(Modifier.fillMaxSize().padding(padding), contentPadding = PaddingValues(20.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
            item { Spacer(Modifier.height(14.dp)); Text("Your links.\nYour library.", style = MaterialTheme.typography.headlineLarge, fontWeight = FontWeight.Bold); Spacer(Modifier.height(8.dp)); Text("Save public videos and audio in their best available quality.", color = MaterialTheme.colorScheme.onSurfaceVariant) }
            item { Card(shape = RoundedCornerShape(24.dp)) { Column(Modifier.padding(20.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Text("ADD A LINK", color = Ocean, style = MaterialTheme.typography.labelMedium)
                OutlinedTextField(value = link, onValueChange = { model.link.value = it }, label = { Text("Video or playlist link") }, modifier = Modifier.fillMaxWidth(), maxLines = 4, shape = RoundedCornerShape(16.dp))
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedButton(onClick = { val clip = context.getSystemService(ClipboardManager::class.java).primaryClip; model.link.value = LinkParser.fromText(clip?.getItemAt(0)?.coerceToText(context)?.toString().orEmpty()).orEmpty() }, modifier = Modifier.weight(1f)) { Icon(Icons.Default.ContentPaste, null, Modifier.size(18.dp)); Spacer(Modifier.width(6.dp)); Text("Paste") }
                    OutlinedButton(onClick = { model.start(configure = true) }, enabled = !busy && link.isNotBlank(), modifier = Modifier.weight(1f)) { Icon(Icons.Default.Tune, null, Modifier.size(18.dp)); Spacer(Modifier.width(6.dp)); Text("Options") }
                }
                Button(onClick = { model.start() }, enabled = !busy && link.isNotBlank(), modifier = Modifier.fillMaxWidth().heightIn(min = 52.dp)) { Icon(Icons.Default.Download, null); Spacer(Modifier.width(8.dp)); Text(if (busy) "Preparing…" else "Download") }
                Text("Video · Best available", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                if (LinkParser.hasPlaylist(link) && !LinkParser.isPlaylistOnly(link)) TextButton(onClick = { model.start(playlist = true) }, enabled = !busy) { Text("Choose from this playlist") }
                if (busy) LinearProgressIndicator(Modifier.fillMaxWidth())
            } } }
            item { Text("ON YOUR DEVICE", color = Ocean, style = MaterialTheme.typography.labelMedium); Spacer(Modifier.height(4.dp)); Text("No account. No cloud. Files stay in Movies/Orca or Music/Orca.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant) }
            val transfers = items.filter { it.state != "COMPLETED" }
            if (transfers.isNotEmpty()) item { Text("Transfers", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.SemiBold) }
            items(transfers, key = { it.id }) { item -> DownloadCard(item, onPlay = { play(item) }, onShare = { share(context, item, model) }, onCancel = { model.action { model.repo.cancel(item.id) } }, onResume = { model.action { model.repo.resume(item) } }, onDelete = { deleting = item }, onError = { errorItem = item }, onLink = { openLink(context, item.url) }) }
        }
        else LibraryScreen(Modifier.padding(padding), items, { play(it) }, { share(context, it, model) }, { model.action { model.repo.cancel(it.id) } }, { model.action { model.repo.resume(it) } }, { deleting = it }, { errorItem = it }, { openLink(context, it.url) })
    }
    options?.let { data -> OptionsSheet(data, { model.options.value = null }, model::enqueue) }
    appRelease?.let { release ->
        AlertDialog(onDismissRequest = { model.availableAppRelease.value = null }, title = { Text("Orca ${release.versionName} is available") },
            text = { Column { Text("Installed: ${BuildConfig.VERSION_NAME}. Download the update from GitHub, then open the APK to install it. Android will ask you to approve the installation."); TextButton(onClick = { openLink(context, release.pageUrl) }) { Text("Release notes") } } },
            confirmButton = { TextButton(onClick = {
                model.availableAppRelease.value = null
                runCatching { context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(release.downloadUrl))) }.onFailure { model.appUpdateStatus.value = "No browser is available to download the update." }
            }) { Text("Download update") } },
            dismissButton = { TextButton(onClick = { model.availableAppRelease.value = null }) { Text("Not now") } })
    }
    if (message != null) ErrorDialog(message.orEmpty(), details, { model.message.value = null; model.details.value = "" })
    errorItem?.let { item -> ErrorDialog(item.error, item.details, { errorItem = null }) }
    deleting?.let { item -> AlertDialog(onDismissRequest = { deleting = null }, title = { Text("Delete download?") }, text = { Text("Delete “${item.title}” from Orca and device storage?") }, confirmButton = { TextButton(onClick = { model.action { model.repo.delete(item) }; deleting = null }) { Text("Delete") } }, dismissButton = { TextButton(onClick = { deleting = null }) { Text("Keep") } }) }
    playing?.let { item -> PlayerDialog(item, { playing = null }, { externalPlayer(context, item, model) }) }
}

@Composable private fun LibraryScreen(modifier: Modifier, downloads: List<DownloadItem>, onPlay: (DownloadItem) -> Unit, onShare: (DownloadItem) -> Unit, onCancel: (DownloadItem) -> Unit, onResume: (DownloadItem) -> Unit, onDelete: (DownloadItem) -> Unit, onError: (DownloadItem) -> Unit, onLink: (DownloadItem) -> Unit) {
    var search by remember { mutableStateOf("") }
    var filter by remember { mutableIntStateOf(0) }
    val visible = downloads.filter { it.title.contains(search, true) && (filter == 0 || (filter == 2) == it.audio) }
    LazyColumn(modifier.fillMaxSize(), contentPadding = PaddingValues(20.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        item { OutlinedTextField(search, { search = it }, modifier = Modifier.fillMaxWidth(), label = { Text("Search downloads") }, leadingIcon = { Icon(Icons.Default.Search, null) }, singleLine = true, shape = RoundedCornerShape(20.dp)) }
        item { Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) { listOf("All", "Video", "Audio").forEachIndexed { index, text -> FilterChip(selected = filter == index, onClick = { filter = index }, label = { Text(text) }) } } }
        if (visible.isEmpty()) item { Column(Modifier.fillMaxWidth().padding(vertical = 60.dp), horizontalAlignment = Alignment.CenterHorizontally) { Icon(Icons.Default.VideoLibrary, null, Modifier.size(48.dp), tint = Ocean); Spacer(Modifier.height(16.dp)); Text(if (search.isBlank()) "Your library starts here" else "No matching downloads", style = MaterialTheme.typography.titleMedium); Spacer(Modifier.height(8.dp)); Text("Saved media appears here.", color = MaterialTheme.colorScheme.onSurfaceVariant) } }
        items(visible, key = { it.id }) { item -> DownloadCard(item, { onPlay(item) }, { onShare(item) }, { onCancel(item) }, { onResume(item) }, { onDelete(item) }, { onError(item) }, { onLink(item) }) }
    }
}

@Composable private fun DownloadCard(item: DownloadItem, onPlay: () -> Unit, onShare: () -> Unit, onCancel: () -> Unit, onResume: () -> Unit, onDelete: () -> Unit, onError: () -> Unit, onLink: () -> Unit) {
    val completed = item.state == "COMPLETED"
    val active = item.state in DownloadRepository.activeStates
    var menu by remember { mutableStateOf(false) }
    Card(Modifier.fillMaxWidth(), shape = RoundedCornerShape(20.dp)) { Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            Box(Modifier.size(76.dp, 64.dp).background(MaterialTheme.colorScheme.surfaceVariant, RoundedCornerShape(10.dp)), contentAlignment = Alignment.Center) {
                if (item.thumbnail.isNotBlank()) AsyncImage(item.thumbnail, null, Modifier.fillMaxSize(), contentScale = ContentScale.Crop) else Icon(if (item.audio) Icons.Default.MusicNote else Icons.Default.PlayCircle, null, tint = Ocean)
            }
            Column(Modifier.weight(1f).then(if (completed) Modifier.clickable(onClick = onPlay) else Modifier)) {
                Text(item.title, fontWeight = FontWeight.SemiBold, maxLines = 2, overflow = TextOverflow.Ellipsis)
                Spacer(Modifier.height(4.dp))
                Text(listOf(if (item.audio) "Audio" else "Video", duration(item.duration), size(item.bytes)).filter { it.isNotBlank() }.joinToString(" · "), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            Box { IconButton(onClick = { menu = true }) { Icon(Icons.Default.MoreVert, "More actions for ${item.title}") }; DropdownMenu(menu, { menu = false }) {
                DropdownMenuItem(text = { Text("Original link") }, onClick = { menu = false; onLink() })
                if (!active) DropdownMenuItem(text = { Text("Delete") }, onClick = { menu = false; onDelete() })
            } }
        }
        if (active) {
            if (item.progress >= 0) LinearProgressIndicator(progress = { item.progress / 100f }, modifier = Modifier.fillMaxWidth()) else LinearProgressIndicator(Modifier.fillMaxWidth())
            Row(verticalAlignment = Alignment.CenterVertically) { Text(item.state.lowercase().replaceFirstChar { it.uppercase() } + if (item.progress >= 0) " · ${item.progress.toInt()}%" else "", Modifier.weight(1f), style = MaterialTheme.typography.bodySmall); TextButton(onClick = onCancel) { Text("Cancel") } }
        } else if (completed) Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            FilledTonalButton(onClick = onPlay, modifier = Modifier.weight(1f)) { Icon(Icons.Default.PlayArrow, null); Text("Play") }
            OutlinedButton(onClick = onShare, modifier = Modifier.weight(1f)) { Icon(Icons.Default.Share, null, Modifier.size(18.dp)); Spacer(Modifier.width(6.dp)); Text("Share") }
        } else {
            Text(item.error.ifBlank { item.state.lowercase().replaceFirstChar { it.uppercase() } }, style = MaterialTheme.typography.bodySmall)
            Row { TextButton(onClick = onResume) { Text(if (item.state == "FAILED") "Retry" else "Resume") }; if (item.details.isNotBlank()) TextButton(onClick = onError) { Text("Details") } }
        }
    } }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable internal fun OptionsSheet(state: OptionsState, dismiss: () -> Unit, download: (List<MediaInfo>, DownloadOptions) -> Unit) {
    var audio by remember { mutableStateOf(false) }
    var height by remember { mutableIntStateOf(0) }
    var selected by remember { mutableStateOf(state.media.indices.filter { state.media[it].available }.toSet()) }
    val first = state.media.firstOrNull()
    ModalBottomSheet(onDismissRequest = dismiss, sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)) {
        Column(Modifier.fillMaxWidth().verticalScroll(rememberScrollState()).padding(horizontal = 24.dp).padding(bottom = 24.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Text(if (state.playlist) "Choose playlist items" else "Download options", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
            if (!state.playlist && first != null) Row(horizontalArrangement = Arrangement.spacedBy(12.dp), verticalAlignment = Alignment.CenterVertically) { AsyncImage(first.thumbnail, null, Modifier.size(88.dp, 60.dp), contentScale = ContentScale.Crop); Column(Modifier.weight(1f)) { Text(first.title, maxLines = 2, overflow = TextOverflow.Ellipsis); Text(duration(first.duration) + (first.bytes?.let { " · About ${size(it)}" } ?: " · Size unknown"), style = MaterialTheme.typography.bodySmall) } }
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) { FilterChip(!audio, { audio = false }, label = { Text("Video") }); FilterChip(audio, { audio = true }, label = { Text("Audio") }) }
            if (!audio) {
                var qualityMenu by remember { mutableStateOf(false) }
                Box {
                    OutlinedButton(onClick = { qualityMenu = true }) { Text(if (height == 0) "Best available" else "Up to ${height}p"); Icon(Icons.Default.ArrowDropDown, null) }
                    DropdownMenu(qualityMenu, { qualityMenu = false }) {
                        DropdownMenuItem(text = { Text("Best available") }, onClick = { height = 0; qualityMenu = false })
                        first?.qualities?.forEach { quality -> DropdownMenuItem(text = { Text(quality.label + (quality.bytes?.let { " · Video about ${size(it)}" } ?: "")) }, onClick = { height = quality.height; qualityMenu = false }) }
                    }
                }
                if (state.playlist) Text("Best available for each selected item.", style = MaterialTheme.typography.bodySmall)
            } else Text("Best source audio · original quality", style = MaterialTheme.typography.bodySmall)
            if (state.playlist) {
                val available = state.media.indices.filter { state.media[it].available }.toSet()
                Row(verticalAlignment = Alignment.CenterVertically) { Checkbox(selected.containsAll(available) && available.isNotEmpty(), { selected = if (it) available else emptySet() }, modifier = Modifier.semantics { contentDescription = "Select all available items" }); Text("Select all (${available.size})") }
                LazyColumn(Modifier.fillMaxWidth().heightIn(max = 280.dp)) { items(state.media.indices.toList()) { index ->
                    val item = state.media[index]
                    Row(Modifier.fillMaxWidth().clickable(enabled = item.available) { selected = if (index in selected) selected - index else selected + index }, verticalAlignment = Alignment.CenterVertically) {
                        Checkbox(index in selected, { checked -> selected = if (checked) selected + index else selected - index }, enabled = item.available, modifier = Modifier.semantics { contentDescription = "Select ${item.title}" })
                        Column(Modifier.weight(1f).padding(vertical = 8.dp)) { Text(item.title, maxLines = 2, overflow = TextOverflow.Ellipsis); if (!item.available) Text("Unavailable or access required", style = MaterialTheme.typography.bodySmall) }
                    }
                } }
            }
            Button(onClick = { download(state.media.filterIndexed { index, _ -> index in selected }, DownloadOptions(audio, height)) }, enabled = selected.isNotEmpty(), modifier = Modifier.fillMaxWidth().heightIn(min = 52.dp).testTag("confirmDownload")) { Text(if (state.playlist) "Download ${selected.size} ${if (selected.size == 1) "item" else "items"}" else "Download") }
        }
    }
}

@Composable private fun SettingsScreen(modifier: Modifier, ask: Boolean, version: String, busy: Boolean, setAsk: (Boolean) -> Unit, update: () -> Unit, checkingAppUpdate: Boolean, appUpdateStatus: String, checkAppUpdate: () -> Unit) {
    LazyColumn(modifier.fillMaxSize(), contentPadding = PaddingValues(24.dp), verticalArrangement = Arrangement.spacedBy(20.dp)) {
        item { Text("DOWNLOADS", color = Ocean, style = MaterialTheme.typography.labelMedium) }
        item { Row(verticalAlignment = Alignment.CenterVertically) { Column(Modifier.weight(1f)) { Text("Ask before downloading", style = MaterialTheme.typography.titleMedium); Text("Choose audio and quality each time.", style = MaterialTheme.typography.bodySmall) }; Switch(ask, setAsk, modifier = Modifier.semantics { contentDescription = "Ask before downloading" }) } }
        item { HorizontalDivider(); Spacer(Modifier.height(20.dp)); Text("APP UPDATES", color = Ocean, style = MaterialTheme.typography.labelMedium); Spacer(Modifier.height(8.dp)); Text("Orca ${BuildConfig.VERSION_NAME}"); Text("Updates come from the official GitHub releases.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant); Spacer(Modifier.height(12.dp)); OutlinedButton(onClick = checkAppUpdate, enabled = !checkingAppUpdate) { Icon(Icons.Default.SystemUpdate, null); Spacer(Modifier.width(8.dp)); Text(if (checkingAppUpdate) "Checking…" else "Check for updates") }; if (appUpdateStatus.isNotBlank()) Text(appUpdateStatus, style = MaterialTheme.typography.bodySmall) }
        item { HorizontalDivider(); Spacer(Modifier.height(20.dp)); Text("DOWNLOAD ENGINE", color = Ocean, style = MaterialTheme.typography.labelMedium); Spacer(Modifier.height(8.dp)); Text("yt-dlp · $version"); Text("Source support changes over time. Updates require all transfers to be idle.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant); Spacer(Modifier.height(12.dp)); OutlinedButton(onClick = update, enabled = !busy) { Icon(Icons.Default.Refresh, null); Text("Check for engine update") } }
        item { HorizontalDivider(); Spacer(Modifier.height(20.dp)); Text("About Orca", style = MaterialTheme.typography.titleMedium); Text("Version ${BuildConfig.VERSION_NAME}\nFiles save to Movies/Orca and Music/Orca and remain after uninstall. Public, non-DRM media only. No login or cookies.", style = MaterialTheme.typography.bodyMedium); Spacer(Modifier.height(20.dp)); Text("Open source notices", style = MaterialTheme.typography.titleMedium); Text("yt-dlp — Unlicense\nyoutubedl-android — MIT\nFFmpeg — LGPL/GPL (bundled build)\nPython — PSF license\nQuickJS — MIT\nAndroidX, Kotlin, Coil — Apache 2.0\n\nFull notices and upstream source links are included in this app’s assets and the source repository.", style = MaterialTheme.typography.bodySmall) }
    }
}

@Composable private fun ErrorDialog(message: String, details: String, dismiss: () -> Unit) {
    var expanded by remember { mutableStateOf(false) }
    AlertDialog(onDismissRequest = dismiss, title = { Text("Orca") }, text = { LazyColumn { item { Text(message) }; if (details.isNotBlank()) item { TextButton(onClick = { expanded = !expanded }) { Text(if (expanded) "Hide technical details" else "Technical details") }; if (expanded) Text(details, style = MaterialTheme.typography.bodySmall) } } }, confirmButton = { TextButton(onClick = dismiss) { Text("OK") } })
}

@androidx.annotation.OptIn(androidx.media3.common.util.UnstableApi::class)
@Composable private fun PlayerDialog(item: DownloadItem, dismiss: () -> Unit, external: () -> Unit) {
    val context = LocalContext.current
    var fullscreen by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf(false) }
    val player = remember(item.uri) { ExoPlayer.Builder(context).build().apply { setMediaItem(MediaItem.fromUri(item.uri)); prepare(); playWhenReady = true } }
    DisposableEffect(player) {
        val listener = object : Player.Listener { override fun onPlayerError(e: PlaybackException) { error = true } }
        player.addListener(listener)
        onDispose { player.removeListener(listener); player.release() }
    }
    Dialog(onDismissRequest = dismiss, properties = DialogProperties(usePlatformDefaultWidth = false, decorFitsSystemWindows = false)) {
        Surface(Modifier.fillMaxSize(), color = Color.Black) { Column(Modifier.safeDrawingPadding()) {
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) { IconButton(onClick = dismiss) { Icon(Icons.AutoMirrored.Filled.ArrowBack, "Close player") }; Text(item.title, Modifier.weight(1f), maxLines = 1, overflow = TextOverflow.Ellipsis); IconButton(onClick = { fullscreen = !fullscreen }) { Icon(if (fullscreen) Icons.Default.FullscreenExit else Icons.Default.Fullscreen, "Toggle fullscreen") } }
            AndroidView(factory = { PlayerView(it).apply { this.player = player; useController = true; controllerShowTimeoutMs = 0 } }, modifier = if (fullscreen) Modifier.fillMaxWidth().weight(1f) else Modifier.fillMaxWidth().aspectRatio(16f / 9f))
            if (error) Text("Your device cannot play this format. Try another player.", Modifier.padding(16.dp))
            if (!fullscreen || error) TextButton(onClick = external, modifier = Modifier.align(Alignment.CenterHorizontally)) { Icon(Icons.AutoMirrored.Filled.OpenInNew, null); Spacer(Modifier.width(8.dp)); Text("Open with another app") }
        } }
    }
}
private fun share(context: android.content.Context, item: DownloadItem, model: OrcaModel) {
    if (!model.repo.publisher.exists(item.uri)) { model.message.value = "File is missing. Download it again from the original link."; return }
    val uri = Uri.parse(item.uri)
    runCatching { context.startActivity(Intent.createChooser(Intent(Intent.ACTION_SEND).apply { type = item.mime; putExtra(Intent.EXTRA_STREAM, uri); clipData = ClipData.newRawUri("Orca media", uri); addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION) }, "Share media")) }.onFailure { model.message.value = "No app is available to share this file." }
}
private fun externalPlayer(context: android.content.Context, item: DownloadItem, model: OrcaModel) {
    runCatching { context.startActivity(Intent.createChooser(Intent(Intent.ACTION_VIEW).setDataAndType(Uri.parse(item.uri), item.mime).addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION), "Open media")) }.onFailure { model.message.value = "Install a player that supports this media format." }
}
private fun openLink(context: android.content.Context, url: String) { runCatching { context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(url))) } }
fun size(bytes: Long): String = when { bytes <= 0 -> ""; bytes < 1024 * 1024 -> "${bytes / 1024} KB"; bytes < 1024L * 1024 * 1024 -> "%.1f MB".format(bytes / 1048576.0); else -> "%.2f GB".format(bytes / 1073741824.0) }
fun duration(seconds: Long): String = if (seconds <= 0) "" else if (seconds >= 3600) "%d:%02d:%02d".format(seconds / 3600, seconds / 60 % 60, seconds % 60) else "%d:%02d".format(seconds / 60, seconds % 60)

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
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.ui.draw.clip
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.saveable.rememberSaveableStateHolder
import androidx.compose.runtime.saveable.listSaver
import io.github.nihil.orca.ui.*
import io.github.nihil.orca.ui.components.*
import androidx.compose.foundation.clickable
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.OpenInNew
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.viewinterop.AndroidView
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
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
import io.github.nihil.orca.ui.OptionsSheetMetrics
import io.github.nihil.orca.ui.PlayerMetrics
import io.github.nihil.orca.ui.components.DownloadCardMetrics
import io.github.nihil.orca.ui.components.OrcaActionLabel
import io.github.nihil.orca.ui.components.OrcaOutlinedActionButton
import io.github.nihil.orca.ui.components.OrcaPrimaryButton
import io.github.nihil.orca.ui.theme.OrcaLayout
import io.github.nihil.orca.ui.theme.OrcaPlayerColors
import io.github.nihil.orca.ui.theme.OrcaShapes
import io.github.nihil.orca.ui.theme.OrcaSpacing
import io.github.nihil.orca.ui.theme.OrcaTextStyles
import io.github.nihil.orca.ui.theme.OrcaTypography
import io.github.nihil.orca.ui.theme.orcaColorScheme

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

data class OptionsState(val media: List<MediaInfo>, val playlist: Boolean, val id: String = java.util.UUID.randomUUID().toString())
class OrcaModel(application: Application) : AndroidViewModel(application) {
    val repo = application.repository
    val link = MutableStateFlow("")
    val busy = MutableStateFlow(false)
    val message = MutableStateFlow<String?>(null)
    val details = MutableStateFlow("")
    val options = MutableStateFlow<OptionsState?>(null)
    val submitting = MutableStateFlow(false)
    val engineVersion = MutableStateFlow("Checking…")
    val checkingAppUpdate = MutableStateFlow(false)
    val appUpdateStatus = MutableStateFlow("")
    val availableAppRelease = MutableStateFlow<AppRelease?>(null)
    val items = repo.items.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())
    val ask = repo.preferences.ask.stateIn(viewModelScope, SharingStarted.Eagerly, false)
    init { viewModelScope.launch {
        try { engineVersion.value = repo.version() }
        catch (cancel: CancellationException) { throw cancel }
        catch (_: Exception) { engineVersion.value = "Unavailable" }
    } }
    fun action(block: suspend () -> Unit) { viewModelScope.launch { try { block() } catch (cancel: CancellationException) { throw cancel } catch (e: Exception) { message.value = friendlyError(e); details.value = e.stackTraceToString().take(32768) } } }
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
    fun enqueue(media: List<MediaInfo>, selection: DownloadOptions) {
        if (!submitting.compareAndSet(false, true)) return
        action { try { repo.enqueue(media, selection); options.value = null } finally { submitting.value = false } }
    }
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

@Composable fun OrcaTheme(content: @Composable () -> Unit) {
    MaterialTheme(colorScheme = orcaColorScheme(), typography = OrcaTypography, shapes = OrcaShapes.material, content = content)
}
@OptIn(ExperimentalMaterial3Api::class)
@Composable fun OrcaApp(model: OrcaModel) {
    val items by model.items.collectAsStateWithLifecycle()
    val link by model.link.collectAsStateWithLifecycle()
    val busy by model.busy.collectAsStateWithLifecycle()
    val ask by model.ask.collectAsStateWithLifecycle()
    val options by model.options.collectAsStateWithLifecycle()
    val submitting by model.submitting.collectAsStateWithLifecycle()
    val message by model.message.collectAsStateWithLifecycle()
    val details by model.details.collectAsStateWithLifecycle()
    val version by model.engineVersion.collectAsStateWithLifecycle()
    val checkingAppUpdate by model.checkingAppUpdate.collectAsStateWithLifecycle()
    val appUpdateStatus by model.appUpdateStatus.collectAsStateWithLifecycle()
    val appRelease by model.availableAppRelease.collectAsStateWithLifecycle()
    var destination by rememberSaveable { mutableIntStateOf(0) }
    var settings by rememberSaveable { mutableStateOf(false) }
    var playing by remember { mutableStateOf<DownloadItem?>(null) }
    var deleting by remember { mutableStateOf<DownloadItem?>(null) }
    var errorItem by remember { mutableStateOf<DownloadItem?>(null) }
    val screenState = rememberSaveableStateHolder()
    val context = LocalContext.current
    fun play(item: DownloadItem) {
        if (model.repo.publisher.exists(item.uri)) playing = item else { model.message.value = "File is missing. Download it again from the original link."; model.details.value = "" }
    }
    BackHandler(enabled = settings || destination != 0) { if (settings) settings = false else destination = 0 }
    Scaffold(
        topBar = { OrcaTopBar(settings, { settings = false }, { settings = true }) },
        bottomBar = { if (!settings) OrcaNavigation(destination) { destination = it } }
    ) { padding ->
        Box(Modifier.fillMaxSize().padding(padding), contentAlignment = Alignment.TopCenter) {
            val screenModifier = Modifier.widthIn(max = OrcaLayout.contentMaxWidth).fillMaxSize()
            screenState.SaveableStateProvider(if (settings) "settings" else "destination-$destination") {
            if (settings) SettingsScreen(screenModifier, ask, version, busy || items.any { it.state in DownloadRepository.activeStates }, { value -> model.action { model.repo.preferences.setAsk(value) } }, model::update, checkingAppUpdate, appUpdateStatus, model::checkAppUpdate)
            else if (destination == 0) LazyColumn(screenModifier, contentPadding = PaddingValues(OrcaLayout.pageInset), verticalArrangement = Arrangement.spacedBy(OrcaLayout.homeItemGap)) {
                item { HomeHero() }
                item { LinkComposer(link, busy, { model.link.value = it }, {
                    val clip = context.getSystemService(ClipboardManager::class.java).primaryClip
                    model.link.value = LinkParser.fromText(clip?.getItemAt(0)?.coerceToText(context)?.toString().orEmpty()).orEmpty()
                }, { model.start(configure = true) }, { model.start() }, { model.start(playlist = true) }) }
                item { CollectionSummary(items.count { it.state == "COMPLETED" }, items.count { it.state in DownloadRepository.activeStates }) { destination = 1 } }
                val transfers = items.filter { it.state != "COMPLETED" }
                if (transfers.isNotEmpty()) item { SectionHeading("Transfers", "${transfers.size} items") }
                items(transfers, key = { it.id }) { item -> DownloadCard(item, onPlay = { play(item) }, onShare = { share(context, item, model) }, onCancel = { model.action { model.repo.cancel(item.id) } }, onResume = { model.action { model.repo.resume(item) } }, onDelete = { deleting = item }, onError = { errorItem = item }, onLink = { openLink(context, item.url) }) }
            }
            else LibraryScreen(screenModifier, items, { play(it) }, { share(context, it, model) }, { model.action { model.repo.cancel(it.id) } }, { model.action { model.repo.resume(it) } }, { deleting = it }, { errorItem = it }, { openLink(context, it.url) })
            }
        }
    }
    options?.let { data -> OptionsSheet(data, { if (!submitting) model.options.value = null }, model::enqueue, submitting) }
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
    deleting?.let { item -> AlertDialog(onDismissRequest = { deleting = null }, title = { Text("Delete download?") }, text = { Text("Delete “${item.title}” from Orca and device storage?") }, confirmButton = { TextButton(onClick = { model.action { model.repo.delete(item) }; deleting = null }) { Text("Delete", color = MaterialTheme.colorScheme.error) } }, dismissButton = { TextButton(onClick = { deleting = null }) { Text("Keep") } }) }
    playing?.let { item -> PlayerDialog(item, { playing = null }, { externalPlayer(context, item, model) }) }
}

@Composable private fun LibraryScreen(modifier: Modifier, downloads: List<DownloadItem>, onPlay: (DownloadItem) -> Unit, onShare: (DownloadItem) -> Unit, onCancel: (DownloadItem) -> Unit, onResume: (DownloadItem) -> Unit, onDelete: (DownloadItem) -> Unit, onError: (DownloadItem) -> Unit, onLink: (DownloadItem) -> Unit) {
    var search by rememberSaveable { mutableStateOf("") }
    var filter by rememberSaveable { mutableIntStateOf(0) }
    val visible = downloads.filter { it.title.contains(search, true) && (filter == 0 || (filter == 2) == it.audio) }
    LazyColumn(modifier.fillMaxSize(), contentPadding = PaddingValues(OrcaLayout.pageInset), verticalArrangement = Arrangement.spacedBy(OrcaLayout.libraryItemGap)) {
        item {
            Eyebrow("SAVED FOR YOU")
            Spacer(Modifier.height(OrcaSpacing.sm))
            Text("Your library", style = OrcaTextStyles.hero)
            Spacer(Modifier.height(OrcaSpacing.sm))
            Text("${downloads.count { it.state == "COMPLETED" }} saved files · Ready when you are", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Spacer(Modifier.height(OrcaSpacing.sm))
        }
        item { OutlinedTextField(search, { search = it }, modifier = Modifier.fillMaxWidth(), label = { Text("Search downloads") }, leadingIcon = { Icon(Icons.Default.Search, null) }, singleLine = true, shape = OrcaShapes.searchInput,
            colors = OutlinedTextFieldDefaults.colors(unfocusedContainerColor = MaterialTheme.colorScheme.surface, unfocusedBorderColor = MaterialTheme.colorScheme.outlineVariant),
            trailingIcon = { if (search.isNotEmpty()) IconButton(onClick = { search = "" }) { Icon(Icons.Default.Close, "Clear search") } }) }
        item { Row(horizontalArrangement = Arrangement.spacedBy(OrcaSpacing.sm)) { listOf("All", "Video", "Audio").forEachIndexed { index, text -> FilterChip(selected = filter == index, onClick = { filter = index }, label = { Text(text) }, leadingIcon = { if (filter == index) Icon(Icons.Default.Check, null, Modifier.size(ActionButtonMetrics.smallIconSize)) }, shape = OrcaShapes.button) } } }
        if (visible.isEmpty()) item { EmptyLibrary(
            if (search.isNotBlank()) "No matching downloads" else if (filter == 0) "Make room for your favorites" else "No ${if (filter == 1) "videos" else "audio"} yet",
            if (search.isNotBlank()) "Try a different title or clear your search." else "Save a link from Home and build a collection that goes wherever you do.",
            if (filter == 2) Icons.Default.Headphones else Icons.Default.VideoLibrary,
            action = if (search.isNotBlank() || filter != 0) { { TextButton(onClick = { search = ""; filter = 0 }) { Text("Reset filters") } } } else null
        ) }
        items(visible, key = { it.id }) { item -> DownloadCard(item, { onPlay(item) }, { onShare(item) }, { onCancel(item) }, { onResume(item) }, { onDelete(item) }, { onError(item) }, { onLink(item) }) }
    }
}

@Composable private fun DownloadCard(item: DownloadItem, onPlay: () -> Unit, onShare: () -> Unit, onCancel: () -> Unit, onResume: () -> Unit, onDelete: () -> Unit, onError: () -> Unit, onLink: () -> Unit) {
    val completed = item.state == "COMPLETED"
    val active = item.state in DownloadRepository.activeStates
    var menu by remember { mutableStateOf(false) }
    Card(Modifier.fillMaxWidth(), shape = OrcaShapes.downloadCard,
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        border = BorderStroke(OrcaLayout.dividerWidth, MaterialTheme.colorScheme.outlineVariant)) {
        Column(Modifier.padding(DownloadCardMetrics.padding), verticalArrangement = Arrangement.spacedBy(DownloadCardMetrics.contentGap)) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(DownloadCardMetrics.rowGap)) {
                Row(Modifier.weight(1f).then(if (completed) Modifier.clickable(onClickLabel = "Play ${item.title}", onClick = onPlay) else Modifier), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(DownloadCardMetrics.rowGap)) {
                    Box(Modifier.size(DownloadCardMetrics.thumbnailWidth, DownloadCardMetrics.thumbnailHeight).clip(DownloadCardMetrics.thumbnailRadius).background(MaterialTheme.colorScheme.surfaceVariant), contentAlignment = Alignment.Center) {
                        if (item.thumbnail.isNotBlank()) AsyncImage(item.thumbnail, null, Modifier.fillMaxSize(), contentScale = ContentScale.Crop)
                        else Icon(if (item.audio) Icons.Default.Headphones else Icons.Default.PlayCircle, null, tint = MaterialTheme.colorScheme.primary)
                    }
                    Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(OrcaSpacing.xs)) {
                        Text(item.title, style = OrcaTextStyles.downloadTitle, maxLines = 2, overflow = TextOverflow.Ellipsis)
                        Text(listOf(if (item.audio) "Audio" else "Video", duration(item.duration), size(item.bytes)).filter { it.isNotBlank() }.joinToString(" · "), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
                Box { IconButton(onClick = { menu = true }) { Icon(Icons.Default.MoreHoriz, "More actions for ${item.title}") }; DropdownMenu(menu, { menu = false }) {
                    DropdownMenuItem(text = { Text("Original link") }, leadingIcon = { Icon(Icons.Default.Link, null) }, onClick = { menu = false; onLink() })
                    if (!active) DropdownMenuItem(text = { Text("Delete", color = MaterialTheme.colorScheme.error) }, leadingIcon = { Icon(Icons.Default.DeleteOutline, null, tint = MaterialTheme.colorScheme.error) }, onClick = { menu = false; onDelete() })
                } }
            }
            if (active) {
                if (item.progress >= 0) LinearProgressIndicator(progress = { (item.progress / 100f).coerceIn(0f, 1f) }, modifier = Modifier.fillMaxWidth()) else LinearProgressIndicator(Modifier.fillMaxWidth())
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(item.state.lowercase().replaceFirstChar { it.uppercase() } + if (item.progress >= 0) " · ${item.progress.toInt()}%" else "", Modifier.weight(1f), color = MaterialTheme.colorScheme.primary, style = MaterialTheme.typography.bodySmall)
                    TextButton(onClick = onCancel) { Text("Cancel") }
                }
            } else if (completed) {
                Row(horizontalArrangement = Arrangement.spacedBy(OrcaSpacing.sm)) {
                    FilledTonalButton(onClick = onPlay, modifier = Modifier.weight(1f).heightIn(min = OrcaLayout.touchTarget), shape = OrcaShapes.button) { OrcaActionLabel("Play", Icons.Default.PlayArrow) }
                    OrcaOutlinedActionButton(text = "Share", icon = Icons.Default.Share, onClick = onShare, modifier = Modifier.weight(1f))
                }
            } else {
                Surface(color = if (item.state == "FAILED") MaterialTheme.colorScheme.errorContainer else MaterialTheme.colorScheme.surfaceVariant, shape = OrcaShapes.badge) {
                    Text(item.error.ifBlank { item.state.lowercase().replaceFirstChar { it.uppercase() } }, Modifier.padding(OrcaSpacing.md), style = MaterialTheme.typography.bodySmall)
                }
                Row {
                    TextButton(onClick = onResume) { OrcaActionLabel(if (item.state == "FAILED") "Retry" else "Resume", Icons.Default.Refresh) }
                    if (item.details.isNotBlank()) TextButton(onClick = onError) { Text("Details") }
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable internal fun OptionsSheet(state: OptionsState, dismiss: () -> Unit, download: (List<MediaInfo>, DownloadOptions) -> Unit, submitting: Boolean = false) {
    var audio by rememberSaveable(state.id) { mutableStateOf(false) }
    var height by rememberSaveable(state.id) { mutableIntStateOf(0) }
    var selected by rememberSaveable(state.id, stateSaver = listSaver<Set<Int>, Int>(save = { it.toList() }, restore = { it.toSet() })) { mutableStateOf(state.media.indices.filter { state.media[it].available }.toSet()) }
    val first = state.media.firstOrNull()
    ModalBottomSheet(onDismissRequest = dismiss, containerColor = MaterialTheme.colorScheme.surface, sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)) {
        Column(Modifier.fillMaxWidth().padding(horizontal = OrcaLayout.sheetInset).padding(bottom = OrcaLayout.sheetInset), verticalArrangement = Arrangement.spacedBy(OptionsSheetMetrics.contentGap)) {
            Column(Modifier.weight(1f, fill = false).verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(OptionsSheetMetrics.contentGap)) {
            Eyebrow(if (state.playlist) "BUILD YOUR COLLECTION" else "JUST THE WAY YOU LIKE IT")
            Text(if (state.playlist) "Choose playlist items" else "Download options", style = OrcaTextStyles.sheetTitle)
            Text(if (state.playlist) "Pick what you want to keep. Unavailable items are skipped." else "Choose your format. We’ll keep the original quality.", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            if (!state.playlist && first != null) Row(horizontalArrangement = Arrangement.spacedBy(OrcaSpacing.md), verticalAlignment = Alignment.CenterVertically) { AsyncImage(first.thumbnail, null, Modifier.size(OptionsSheetMetrics.previewWidth, OptionsSheetMetrics.previewHeight).clip(OrcaShapes.badge).background(MaterialTheme.colorScheme.surfaceVariant), contentScale = ContentScale.Crop); Column(Modifier.weight(1f)) { Text(first.title, maxLines = 2, overflow = TextOverflow.Ellipsis); Text(duration(first.duration) + (first.bytes?.let { " · About ${size(it)}" } ?: " · Size unknown"), style = MaterialTheme.typography.bodySmall) } }
            HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
            Eyebrow("FORMAT")
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(OrcaSpacing.md)) {
                FilterChip(!audio, { audio = false }, label = { Text("Video") }, leadingIcon = { Icon(Icons.Default.Videocam, null) }, modifier = Modifier.weight(1f).heightIn(min = OrcaLayout.touchTarget), shape = OrcaShapes.button)
                FilterChip(audio, { audio = true }, label = { Text("Audio") }, leadingIcon = { Icon(Icons.Default.Headphones, null) }, modifier = Modifier.weight(1f).heightIn(min = OrcaLayout.touchTarget), shape = OrcaShapes.button)
            }
            if (!audio) {
                var qualityMenu by remember { mutableStateOf(false) }
                Box {
                    OutlinedButton(onClick = { qualityMenu = true }, modifier = Modifier.fillMaxWidth().heightIn(min = OrcaLayout.touchTarget), shape = OrcaShapes.button) {
                        Icon(Icons.Default.HighQuality, null)
                        Spacer(Modifier.width(OrcaSpacing.sm))
                        Text(if (height == 0) "Best available" else "Up to ${height}p", Modifier.weight(1f))
                        Icon(Icons.Default.ExpandMore, null)
                    }
                    DropdownMenu(qualityMenu, { qualityMenu = false }) {
                        DropdownMenuItem(text = { Text("Best available") }, onClick = { height = 0; qualityMenu = false })
                        first?.qualities?.forEach { quality -> DropdownMenuItem(text = { Text(quality.label + (quality.bytes?.let { " · Video about ${size(it)}" } ?: "")) }, onClick = { height = quality.height; qualityMenu = false }) }
                    }
                }
                if (state.playlist) Text("Best available for each selected item.", style = MaterialTheme.typography.bodySmall)
            } else InfoLine(Icons.Default.GraphicEq, "Original audio", "Best source audio · original quality")
            if (state.playlist) {
                val available = state.media.indices.filter { state.media[it].available }.toSet()
                Row(verticalAlignment = Alignment.CenterVertically) { Checkbox(selected.containsAll(available) && available.isNotEmpty(), { selected = if (it) available else emptySet() }, modifier = Modifier.semantics { contentDescription = "Select all available items" }); Text("Select all (${available.size})") }
                LazyColumn(Modifier.fillMaxWidth().heightIn(max = OptionsSheetMetrics.playlistMaxHeight)) { items(state.media.indices.toList()) { index ->
                    val item = state.media[index]
                    Row(Modifier.fillMaxWidth().clickable(enabled = item.available) { selected = if (index in selected) selected - index else selected + index }, verticalAlignment = Alignment.CenterVertically) {
                        Checkbox(index in selected, { checked -> selected = if (checked) selected + index else selected - index }, enabled = item.available, modifier = Modifier.semantics { contentDescription = "Select ${item.title}" })
                        Column(Modifier.weight(1f).padding(vertical = OptionsSheetMetrics.itemVerticalPadding)) { Text(item.title, maxLines = 2, overflow = TextOverflow.Ellipsis); if (!item.available) Text("Unavailable or access required", style = MaterialTheme.typography.bodySmall) }
                    }
                } }
            }
            }
            OrcaPrimaryButton(text = if (submitting) "Adding downloads…" else if (state.playlist) "Download ${selected.size} ${if (selected.size == 1) "item" else "items"}" else "Download", onClick = { download(state.media.filterIndexed { index, _ -> index in selected }, DownloadOptions(audio, height)) }, enabled = selected.isNotEmpty() && !submitting, modifier = Modifier.testTag("confirmDownload"))
        }
    }
}

@Composable private fun SettingsScreen(modifier: Modifier, ask: Boolean, version: String, busy: Boolean, setAsk: (Boolean) -> Unit, update: () -> Unit, checkingAppUpdate: Boolean, appUpdateStatus: String, checkAppUpdate: () -> Unit) {
    LazyColumn(modifier.fillMaxSize(), contentPadding = PaddingValues(OrcaLayout.settingsInset), verticalArrangement = Arrangement.spacedBy(OrcaLayout.settingsSectionGap)) {
        item {
            Eyebrow("MAKE IT YOURS")
            Spacer(Modifier.height(OrcaSpacing.sm))
            Text("A little more you.", style = OrcaTextStyles.sheetTitle)
            Spacer(Modifier.height(OrcaSpacing.sm))
            Text("Your downloads, your preferences.", color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        item { OrcaPanel {
            Eyebrow("DOWNLOAD PREFERENCES")
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(OrcaSpacing.md)) {
                Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(OrcaSpacing.xs)) {
                    Text("Ask before downloading", style = MaterialTheme.typography.titleMedium)
                    Text("Choose audio and quality each time.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                Switch(ask, setAsk, modifier = Modifier.semantics { contentDescription = "Ask before downloading" })
            }
            HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
            InfoLine(Icons.Default.FolderOpen, "Saved on your device", "Video → Movies/Orca\nAudio → Music/Orca")
        } }
        item { OrcaPanel {
            Eyebrow("APP UPDATES")
            InfoLine(Icons.Default.SystemUpdate, "Orca ${BuildConfig.VERSION_NAME}", "Updates from the official GitHub releases.")
            OrcaOutlinedActionButton(text = if (checkingAppUpdate) "Checking…" else "Check for updates", icon = Icons.Default.Refresh, onClick = checkAppUpdate, enabled = !checkingAppUpdate, modifier = Modifier.fillMaxWidth())
            if (checkingAppUpdate) LinearProgressIndicator(Modifier.fillMaxWidth())
            if (appUpdateStatus.isNotBlank()) Text(appUpdateStatus, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        } }
        item { OrcaPanel {
            Eyebrow("DOWNLOAD ENGINE")
            InfoLine(Icons.Default.Memory, "yt-dlp · $version", "Keep source support up to date. Pause until all transfers finish before updating.")
            OrcaOutlinedActionButton(text = "Check for engine update", icon = Icons.Default.Refresh, onClick = update, enabled = !busy, modifier = Modifier.fillMaxWidth())
            if (busy) Text("Available when transfers and inspections are idle.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        } }
        item { OrcaPanel {
            Eyebrow("ABOUT ORCA")
            Text("Good media. No baggage.", style = OrcaTextStyles.sectionTitle)
            Text("An independent home for your favorite public videos and audio. No login, cookies or cloud. Your saved files stay on your device, even after uninstalling Orca.", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Text("Public, non-DRM media only.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
            var notices by rememberSaveable { mutableStateOf(false) }
            TextButton(onClick = { notices = !notices }) { Text("Open source notices"); Spacer(Modifier.width(OrcaSpacing.sm)); Icon(if (notices) Icons.Default.ExpandLess else Icons.Default.ExpandMore, null) }
            if (notices) Text("yt-dlp — Unlicense\nyoutubedl-android — MIT\nFFmpeg — LGPL/GPL (bundled build)\nPython — PSF license\nQuickJS — MIT\nAndroidX, Kotlin, Coil — Apache 2.0\n\nFull notices and upstream source links are included in this app’s assets and the source repository.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        } }
    }
}

@Composable private fun ErrorDialog(message: String, details: String, dismiss: () -> Unit) {
    var expanded by remember { mutableStateOf(false) }
    AlertDialog(onDismissRequest = dismiss, icon = { Icon(Icons.Default.Info, null) }, title = { Text("Orca") }, text = { LazyColumn { item { Text(message) }; if (details.isNotBlank()) item { TextButton(onClick = { expanded = !expanded }) { Text(if (expanded) "Hide technical details" else "Technical details") }; if (expanded) Text(details, style = MaterialTheme.typography.bodySmall) } } }, confirmButton = { TextButton(onClick = dismiss) { Text("OK") } })
}

@androidx.annotation.OptIn(androidx.media3.common.util.UnstableApi::class)
@Composable private fun PlayerDialog(item: DownloadItem, dismiss: () -> Unit, external: () -> Unit) {
    val context = LocalContext.current
    var fullscreen by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf(false) }
    val lifecycle = LocalLifecycleOwner.current.lifecycle
    val player = remember(item.uri) { ExoPlayer.Builder(context).build().apply {
        setAudioAttributes(androidx.media3.common.AudioAttributes.DEFAULT, true)
        setHandleAudioBecomingNoisy(true)
        setMediaItem(MediaItem.fromUri(item.uri)); prepare()
        playWhenReady = lifecycle.currentState.isAtLeast(Lifecycle.State.STARTED)
    } }
    DisposableEffect(player, lifecycle) {
        val listener = object : Player.Listener { override fun onPlayerError(e: PlaybackException) { error = true } }
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_STOP) {
                player.pause()
                player.stop() // Release decoder resources while keeping the selected media/position.
            }
            if (event == Lifecycle.Event.ON_START && player.playbackState == Player.STATE_IDLE) player.prepare()
        }
        player.addListener(listener)
        lifecycle.addObserver(observer)
        onDispose { lifecycle.removeObserver(observer); player.removeListener(listener); player.release() }
    }
    Dialog(onDismissRequest = dismiss, properties = DialogProperties(usePlatformDefaultWidth = false, decorFitsSystemWindows = false)) {
        Surface(Modifier.fillMaxSize(), color = OrcaPlayerColors.background) { Column(Modifier.safeDrawingPadding()) {
            Row(Modifier.fillMaxWidth().padding(horizontal = OrcaSpacing.sm, vertical = OrcaSpacing.md), verticalAlignment = Alignment.CenterVertically) {
                IconButton(onClick = dismiss) { Icon(Icons.AutoMirrored.Filled.ArrowBack, "Close player") }
                Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(OrcaSpacing.xs)) {
                    Eyebrow("NOW PLAYING")
                    Text(item.title, style = MaterialTheme.typography.titleMedium, maxLines = 2, overflow = TextOverflow.Ellipsis)
                }
                IconButton(onClick = { fullscreen = !fullscreen }) { Icon(if (fullscreen) Icons.Default.FullscreenExit else Icons.Default.Fullscreen, "Toggle fullscreen") }
            }
            AndroidView(factory = { PlayerView(it).apply { this.player = player; useController = true; controllerShowTimeoutMs = 0 } }, modifier = if (fullscreen) Modifier.fillMaxWidth().weight(1f) else Modifier.fillMaxWidth().aspectRatio(PlayerMetrics.defaultAspectRatio))
            if (!fullscreen) Column(Modifier.padding(OrcaLayout.pageInset), verticalArrangement = Arrangement.spacedBy(OrcaSpacing.md)) {
                Text(item.title, style = OrcaTextStyles.sectionTitle)
                Text(listOf(if (item.audio) "Original audio" else "Saved video", duration(item.duration), size(item.bytes)).filter { it.isNotBlank() }.joinToString(" · "), style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            if (error) Text("Your device cannot play this format. Try another player.", Modifier.padding(PlayerMetrics.messagePadding), color = MaterialTheme.colorScheme.error)
            if (!fullscreen || error) TextButton(onClick = { player.pause(); external() }, modifier = Modifier.align(Alignment.CenterHorizontally)) { OrcaActionLabel("Open with another app", Icons.AutoMirrored.Filled.OpenInNew) }
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

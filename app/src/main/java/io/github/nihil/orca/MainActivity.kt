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
import io.github.nihil.orca.ui.*
import io.github.nihil.orca.ui.components.*
import androidx.compose.foundation.clickable
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
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
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.viewinterop.AndroidView
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.SavedStateHandle
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
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.coroutines.launch
import kotlinx.coroutines.CancellationException
import android.app.Application
import io.github.nihil.orca.ui.components.DownloadCardMetrics
import io.github.nihil.orca.ui.components.OrcaActionLabel
import io.github.nihil.orca.ui.components.OrcaOutlinedActionButton
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
        if (intent.action == Intent.ACTION_SEND) LinkParser.fromText(intent.getStringExtra(Intent.EXTRA_TEXT).orEmpty())?.let { model.acceptShare(it) }
    }
}

data class OptionsState(val media: List<MediaInfo>, val playlist: Boolean, val id: String = java.util.UUID.randomUUID().toString())
class OrcaModel(application: Application, savedState: SavedStateHandle = SavedStateHandle()) : AndroidViewModel(application) {
    val repo = application.repository
    val link = savedState.getMutableStateFlow("draft_link", "")
    val sharedLinkEvent = MutableStateFlow<String?>(null)
    val queued = MutableSharedFlow<Int>(extraBufferCapacity = 1)
    val busy = MutableStateFlow(false)
    val updatingEngine = MutableStateFlow(false)
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
    fun acceptShare(url: String) {
        link.value = url
        sharedLinkEvent.value = java.util.UUID.randomUUID().toString()
    }
    fun action(block: suspend () -> Unit) { viewModelScope.launch { try { block() } catch (cancel: CancellationException) { throw cancel } catch (e: Exception) { message.value = friendlyError(e); details.value = e.stackTraceToString().take(32768) } } }
    fun start(configure: Boolean = false, playlist: Boolean = false) {
        if (busy.value || updatingEngine.value) return
        val url = LinkParser.fromText(link.value)
        if (url == null) { details.value = ""; message.value = "Paste a valid http or https link."; return }
        busy.value = true
        action {
            try {
                if (playlist || LinkParser.isPlaylistOnly(url)) options.value = OptionsState(repo.playlist(url), true)
                else {
                    val info = repo.inspect(url)
                    if (info.playlist) options.value = OptionsState(repo.playlist(url), true)
                    else if (configure || repo.preferences.ask.first()) options.value = OptionsState(listOf(info), false)
                    else { repo.enqueue(listOf(info), DownloadOptions()); queued.tryEmit(1) }
                }
            } finally { busy.value = false }
        }
    }
    fun enqueue(media: List<MediaInfo>, selection: DownloadOptions) {
        if (!submitting.compareAndSet(false, true)) return
        action { try { repo.enqueue(media, selection); options.value = null; queued.tryEmit(media.size) } finally { submitting.value = false } }
    }
    fun update() {
        if (busy.value || updatingEngine.value) return
        updatingEngine.value = true
        action {
            try {
                engineVersion.value = repo.update()
                details.value = ""
                message.value = "Engine is up to date."
            } finally { updatingEngine.value = false }
        }
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
    val updatingEngine by model.updatingEngine.collectAsStateWithLifecycle()
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
    var showTransfers by rememberSaveable { mutableStateOf(false) }
    var handledShare by rememberSaveable { mutableStateOf<String?>(null) }
    val sharedLinkEvent by model.sharedLinkEvent.collectAsStateWithLifecycle()
    val snackbar = remember { SnackbarHostState() }
    val screenState = rememberSaveableStateHolder()
    val context = LocalContext.current
    fun play(item: DownloadItem) = model.action {
        if (withContext(Dispatchers.IO) { model.repo.publisher.exists(item.uri) }) playing = item else { model.message.value = "File is missing. Download it again from the original link."; model.details.value = "" }
    }
    LaunchedEffect(sharedLinkEvent) {
        if (sharedLinkEvent != null && sharedLinkEvent != handledShare) {
            handledShare = sharedLinkEvent
            settings = false
            destination = 0
        }
    }
    LaunchedEffect(model) {
        model.queued.collectLatest { count ->
            val result = snackbar.showSnackbar(if (count == 1) "Download added" else "$count downloads added", "View", duration = SnackbarDuration.Short)
            if (result == SnackbarResult.ActionPerformed) {
                settings = false
                showTransfers = true
                destination = 1
            }
        }
    }
    BackHandler(enabled = settings || destination != 0) { if (settings) settings = false else destination = 0 }
    Scaffold(
        modifier = Modifier.imePadding(),
        snackbarHost = { SnackbarHost(snackbar) },
        topBar = { OrcaTopBar(settings) { settings = false } },
        bottomBar = {
            OrcaNavigation(if (settings) 2 else destination) { next ->
                settings = next == 2
                if (!settings) destination = next
            }
        }
    ) { padding ->
        Box(Modifier.fillMaxSize().padding(padding), contentAlignment = Alignment.TopCenter) {
            val screenModifier = Modifier.widthIn(max = OrcaLayout.contentMaxWidth).fillMaxSize()
            screenState.SaveableStateProvider(if (settings) "settings" else "destination-$destination") {
                if (settings) {
                    SettingsScreen(
                        screenModifier, ask, version,
                        busy = busy || updatingEngine || items.any { it.state in DownloadRepository.activeStates },
                        setAsk = { value -> model.action { model.repo.preferences.setAsk(value) } },
                        update = model::update,
                        checkingAppUpdate = checkingAppUpdate,
                        appUpdateStatus = appUpdateStatus,
                        checkAppUpdate = model::checkAppUpdate,
                    )
                } else if (destination == 0) LazyColumn(
                    screenModifier,
                    contentPadding = PaddingValues(OrcaLayout.pageInset),
                    verticalArrangement = Arrangement.spacedBy(OrcaLayout.homeItemGap),
                ) {
                    item {
                        LinkComposer(link, busy, updatingEngine, ask, { model.link.value = it }, {
                            val clip = context.getSystemService(ClipboardManager::class.java).primaryClip
                            val text = clip?.takeIf { it.itemCount > 0 }?.getItemAt(0)?.coerceToText(context)
                            val pasted = LinkParser.fromText(text?.toString().orEmpty())
                            if (pasted != null) model.link.value = pasted
                            else {
                                model.details.value = ""
                                model.message.value = "The clipboard doesn’t contain a valid link."
                            }
                        }, { model.start(configure = true) }, { model.start() }, { model.start(playlist = true) })
                    }
                    item {
                        CollectionSummary(
                            saved = items.count { it.state == "COMPLETED" },
                            active = items.count { it.state in DownloadRepository.activeStates },
                            needsAttention = items.count { it.state != "COMPLETED" && it.state !in DownloadRepository.activeStates },
                        ) {
                            showTransfers = items.any { it.state != "COMPLETED" }
                            destination = 1
                        }
                    }
                } else {
                    LibraryScreen(
                        screenModifier, items, showTransfers,
                        selectTransfers = { showTransfers = it },
                        onPlay = { play(it) },
                        onShare = { share(context, it, model) },
                        onCancel = { model.action { model.repo.cancel(it.id) } },
                        onResume = { model.action { model.repo.resume(it) } },
                        onDelete = { deleting = it },
                        onError = { errorItem = it },
                        onLink = { openLink(context, it.url) },
                        onNewDownload = { destination = 0 },
                    )
                }
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

@OptIn(ExperimentalLayoutApi::class)
@Composable private fun LibraryScreen(
    modifier: Modifier,
    downloads: List<DownloadItem>,
    showTransfers: Boolean,
    selectTransfers: (Boolean) -> Unit,
    onPlay: (DownloadItem) -> Unit,
    onShare: (DownloadItem) -> Unit,
    onCancel: (DownloadItem) -> Unit,
    onResume: (DownloadItem) -> Unit,
    onDelete: (DownloadItem) -> Unit,
    onError: (DownloadItem) -> Unit,
    onLink: (DownloadItem) -> Unit,
    onNewDownload: () -> Unit,
) {
    var search by rememberSaveable { mutableStateOf("") }
    var filter by rememberSaveable { mutableIntStateOf(0) }
    val focus = LocalFocusManager.current
    val keyboard = LocalSoftwareKeyboardController.current
    val listState = rememberLazyListState()
    val saved = downloads.filter { it.state == "COMPLETED" }
    val transfers = downloads.filter { it.state != "COMPLETED" }
        .sortedBy { it.state !in DownloadRepository.activeStates }
    val visible = saved.filter { it.title.contains(search.trim(), true) && (filter == 0 || (filter == 2) == it.audio) }
    val actions: @Composable (DownloadItem) -> Unit = { item ->
        DownloadCard(item, { onPlay(item) }, { onShare(item) }, { onCancel(item) },
            { onResume(item) }, { onDelete(item) }, { onError(item) }, { onLink(item) })
    }
    LazyColumn(modifier, state = listState, contentPadding = PaddingValues(OrcaLayout.pageInset),
        verticalArrangement = Arrangement.spacedBy(OrcaLayout.libraryItemGap)) {
        item { Text("Your library", style = OrcaTextStyles.hero) }
        item {
            FlowRow(horizontalArrangement = Arrangement.spacedBy(OrcaSpacing.sm)) {
                FilterChip(!showTransfers, { selectTransfers(false) }, label = { Text("Saved media (${saved.size})") },
                    modifier = Modifier.heightIn(min = OrcaLayout.touchTarget))
                FilterChip(showTransfers, { focus.clearFocus(); selectTransfers(true) }, label = { Text("Transfers (${transfers.size})") },
                    modifier = Modifier.heightIn(min = OrcaLayout.touchTarget))
            }
        }
        if (showTransfers) {
            if (transfers.isEmpty()) item {
                EmptyLibrary("No transfers", "New downloads and items you can retry appear here.", Icons.Default.FileDownload) {
                    TextButton(onClick = onNewDownload) { Text("Add a link") }
                }
            }
            items(transfers, key = { it.id }, contentType = { "transfer" }) { actions(it) }
            return@LazyColumn
        }
        item {
            OutlinedTextField(
                search, { search = it }, modifier = Modifier.fillMaxWidth(),
                label = { Text("Search downloads") }, leadingIcon = { Icon(Icons.Default.Search, null) },
                singleLine = true, shape = OrcaShapes.searchInput,
                trailingIcon = { if (search.isNotEmpty()) IconButton(onClick = { search = "" }) { Icon(Icons.Default.Close, "Clear search") } },
            )
        }
        item {
            FlowRow(horizontalArrangement = Arrangement.spacedBy(OrcaSpacing.sm)) {
                listOf("All", "Video", "Audio").forEachIndexed { index, label ->
                    FilterChip(filter == index, { filter = index }, label = { Text(label) },
                        shape = OrcaShapes.button, modifier = Modifier.heightIn(min = OrcaLayout.touchTarget))
                }
            }
        }
        val filtering = search.isNotBlank() || filter != 0
        if (visible.isEmpty() && filtering) item {
            Column(
                Modifier.fillMaxWidth().padding(vertical = OrcaSpacing.sm),
                verticalArrangement = Arrangement.spacedBy(OrcaSpacing.xs),
            ) {
                Text("No matching downloads", style = MaterialTheme.typography.titleMedium)
                Text("Try another title or change the media filter.", style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant)
                TextButton(onClick = {
                    focus.clearFocus()
                    keyboard?.hide()
                    search = ""
                    filter = 0
                    listState.requestScrollToItem(0)
                }) { Text("Reset filters") }
            }
        }
        if (visible.isEmpty() && !filtering) item {
            EmptyLibrary(
                "No saved media yet", "Add a link to save your first video or audio track.", Icons.Default.FolderOpen,
            ) { TextButton(onClick = onNewDownload) { Text("Add a link") } }
        }
        items(visible, key = { it.id }, contentType = { "saved" }) { actions(it) }
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
                        Icon(if (item.audio) Icons.Default.Headphones else Icons.Default.VideoLibrary, null,
                            tint = MaterialTheme.colorScheme.primary)
                        if (item.thumbnail.isNotBlank()) {
                            AsyncImage(item.thumbnail, null, Modifier.fillMaxSize(), contentScale = ContentScale.Crop)
                        }
                        if (completed) Surface(
                            modifier = Modifier.align(Alignment.BottomEnd),
                            color = MaterialTheme.colorScheme.primaryContainer,
                            shape = OrcaShapes.badge,
                        ) {
                            Icon(Icons.Default.PlayArrow, null, Modifier.size(OrcaSpacing.xl),
                                tint = MaterialTheme.colorScheme.onPrimaryContainer)
                        }
                    }
                    Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(OrcaSpacing.xs)) {
                        Text(item.title, style = OrcaTextStyles.downloadTitle, maxLines = 2, overflow = TextOverflow.Ellipsis)
                        Text(listOf(if (item.audio) "Audio" else "Video", duration(item.duration), size(item.bytes)).filter { it.isNotBlank() }.joinToString(" · "), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
                Box {
                    IconButton(onClick = { menu = true }) { Icon(Icons.Default.MoreVert, "More actions for ${item.title}") }
                    DropdownMenu(menu, { menu = false }) {
                        if (completed) DropdownMenuItem(text = { Text("Share") },
                            leadingIcon = { Icon(Icons.Default.Share, null) }, onClick = { menu = false; onShare() })
                        DropdownMenuItem(text = { Text("Original link") }, leadingIcon = { Icon(Icons.Default.Link, null) }, onClick = { menu = false; onLink() })
                        if (!active) DropdownMenuItem(text = { Text("Delete", color = MaterialTheme.colorScheme.error) }, leadingIcon = { Icon(Icons.Default.DeleteOutline, null, tint = MaterialTheme.colorScheme.error) }, onClick = { menu = false; onDelete() })
                    }
                }
            }
            if (active) {
                if (item.progress >= 0) LinearProgressIndicator(progress = { (item.progress / 100f).coerceIn(0f, 1f) }, modifier = Modifier.fillMaxWidth()) else LinearProgressIndicator(Modifier.fillMaxWidth())
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(item.state.lowercase().replaceFirstChar { it.uppercase() } + if (item.progress >= 0) " · ${item.progress.toInt()}%" else "", Modifier.weight(1f), color = MaterialTheme.colorScheme.primary, style = MaterialTheme.typography.bodySmall)
                    TextButton(onClick = onCancel) { Text("Cancel") }
                }
            } else if (!completed) {
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

@Composable private fun SettingsScreen(
    modifier: Modifier,
    ask: Boolean,
    version: String,
    busy: Boolean,
    setAsk: (Boolean) -> Unit,
    update: () -> Unit,
    checkingAppUpdate: Boolean,
    appUpdateStatus: String,
    checkAppUpdate: () -> Unit,
) {
    var notices by rememberSaveable { mutableStateOf(false) }
    LazyColumn(modifier, contentPadding = PaddingValues(OrcaLayout.settingsInset),
        verticalArrangement = Arrangement.spacedBy(OrcaLayout.settingsSectionGap)) {
        item {
            SectionHeading("Download preferences")
            Spacer(Modifier.height(OrcaSpacing.sm))
            OrcaPanel {
                Row(
                    Modifier.fillMaxWidth().heightIn(min = OrcaLayout.touchTarget)
                        .toggleable(ask, role = Role.Switch, onValueChange = setAsk),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(OrcaSpacing.md),
                ) {
                    Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(OrcaSpacing.xs)) {
                        Text("Ask before downloading", style = MaterialTheme.typography.titleMedium)
                        Text("Choose a format and quality for each link.", style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                    Switch(ask, onCheckedChange = null)
                }
                HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
                InfoLine(Icons.Default.FolderOpen, "Download folders", "Video: Movies/Orca\nAudio: Music/Orca")
            }
        }
        item {
            SectionHeading("Updates")
            Spacer(Modifier.height(OrcaSpacing.sm))
            OrcaPanel {
                InfoLine(Icons.Default.SystemUpdate, "Orca ${BuildConfig.VERSION_NAME}", "App releases from GitHub")
                OrcaOutlinedActionButton(
                    text = if (checkingAppUpdate) "Checking…" else "Check for updates", icon = Icons.Default.Refresh,
                    onClick = checkAppUpdate, enabled = !checkingAppUpdate,
                )
                if (checkingAppUpdate) LinearProgressIndicator(Modifier.fillMaxWidth())
                if (appUpdateStatus.isNotBlank()) Text(appUpdateStatus, Modifier.semantics { liveRegion = LiveRegionMode.Polite },
                    style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
                InfoLine(Icons.Default.Memory, "Download engine", "yt-dlp · $version")
                OrcaOutlinedActionButton("Check for engine update", Icons.Default.Refresh, update, enabled = !busy)
                if (busy) Text("Available when transfers and inspections are idle.", style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
        item {
            SectionHeading("About")
            Spacer(Modifier.height(OrcaSpacing.sm))
            OrcaPanel {
                Text("Orca saves files on your device for offline use.", style = MaterialTheme.typography.bodyMedium)
                TextButton(onClick = { notices = !notices }) {
                    Text(if (notices) "Hide open source notices" else "Open source notices")
                    Spacer(Modifier.width(OrcaSpacing.sm))
                    Icon(if (notices) Icons.Default.ExpandLess else Icons.Default.ExpandMore, null)
                }
                if (notices) Text(
                    "yt-dlp — Unlicense\nyoutubedl-android — MIT\nFFmpeg — LGPL/GPL\nPython — PSF License\n" +
                        "QuickJS — MIT\nAndroidX, Kotlin and Coil — Apache 2.0\n\nFull notices are included in the app assets.",
                    style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
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
    var error by remember(item.uri) { mutableStateOf(false) }
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
        DarkDialogSystemBars()
        Surface(
            Modifier.fillMaxSize(),
            color = OrcaPlayerColors.background,
            contentColor = MaterialTheme.colorScheme.onSurface,
        ) {
            Column(Modifier.safeDrawingPadding()) {
                Row(
                    Modifier.fillMaxWidth().padding(horizontal = OrcaSpacing.sm),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    IconButton(onClick = dismiss) { Icon(Icons.AutoMirrored.Filled.ArrowBack, "Close player") }
                    Text(item.title, Modifier.weight(1f), style = MaterialTheme.typography.titleMedium,
                        maxLines = 2, overflow = TextOverflow.Ellipsis)
                    IconButton(onClick = { fullscreen = !fullscreen }) {
                        Icon(if (fullscreen) Icons.Default.FullscreenExit else Icons.Default.Fullscreen,
                            if (fullscreen) "Exit fullscreen" else "Fullscreen")
                    }
                }
                AndroidView(
                    factory = { PlayerView(it).apply { this.player = player; useController = true; controllerShowTimeoutMs = 0 } },
                    modifier = Modifier.fillMaxWidth().weight(1f),
                )
                if (!fullscreen || error) Column(
                    Modifier.fillMaxWidth().padding(horizontal = OrcaLayout.pageInset),
                    verticalArrangement = Arrangement.spacedBy(OrcaSpacing.xs),
                ) {
                    if (!fullscreen) Text(
                        listOf(if (item.audio) "Audio" else "Video", duration(item.duration), size(item.bytes))
                            .filter { it.isNotBlank() }.joinToString(" · "),
                        style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    if (error) Text("This device couldn’t play the file.", color = MaterialTheme.colorScheme.error)
                    TextButton(onClick = { player.pause(); external() }) {
                        OrcaActionLabel("Open with another app", Icons.AutoMirrored.Filled.OpenInNew)
                    }
                }
            }
        }
    }
}
private fun share(context: android.content.Context, item: DownloadItem, model: OrcaModel) = model.action {
    if (!withContext(Dispatchers.IO) { model.repo.publisher.exists(item.uri) }) {
        model.details.value = ""
        model.message.value = "File is missing. Download it again from the original link."
        return@action
    }
    val uri = Uri.parse(item.uri)
    runCatching { context.startActivity(Intent.createChooser(Intent(Intent.ACTION_SEND).apply { type = item.mime; putExtra(Intent.EXTRA_STREAM, uri); clipData = ClipData.newRawUri("Orca media", uri); addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION) }, "Share media")) }.onFailure { model.message.value = "No app is available to share this file." }
}
private fun externalPlayer(context: android.content.Context, item: DownloadItem, model: OrcaModel) {
    runCatching { context.startActivity(Intent.createChooser(Intent(Intent.ACTION_VIEW).setDataAndType(Uri.parse(item.uri), item.mime).addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION), "Open media")) }.onFailure { model.message.value = "Install a player that supports this media format." }
}
private fun openLink(context: android.content.Context, url: String) { runCatching { context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(url))) } }
fun size(bytes: Long): String = when { bytes <= 0 -> ""; bytes < 1024 * 1024 -> "${bytes / 1024} KB"; bytes < 1024L * 1024 * 1024 -> "%.1f MB".format(bytes / 1048576.0); else -> "%.2f GB".format(bytes / 1073741824.0) }
fun duration(seconds: Long): String = if (seconds <= 0) "" else if (seconds >= 3600) "%d:%02d:%02d".format(seconds / 3600, seconds / 60 % 60, seconds % 60) else "%d:%02d".format(seconds / 60, seconds % 60)

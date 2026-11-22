package io.github.nihil.orca

import android.Manifest
import android.app.Application
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.OpenInNew
import androidx.compose.material.icons.automirrored.outlined.ViewList
import androidx.compose.material.icons.filled.*
import androidx.compose.material.icons.outlined.Delete
import androidx.compose.material.icons.outlined.Description
import androidx.compose.material.icons.outlined.Download
import androidx.compose.material.icons.outlined.FileDownload
import androidx.compose.material.icons.outlined.FolderOpen
import androidx.compose.material.icons.outlined.GridView
import androidx.compose.material.icons.outlined.Link
import androidx.compose.material.icons.outlined.Memory
import androidx.compose.material.icons.outlined.Search
import androidx.compose.material.icons.outlined.Settings
import androidx.compose.material.icons.outlined.SystemUpdate
import androidx.compose.material.icons.outlined.Tune
import androidx.compose.material.icons.outlined.VideoLibrary
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.saveable.rememberSaveableStateHolder
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import androidx.media3.common.MediaItem
import androidx.media3.common.PlaybackException
import androidx.media3.common.Player
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.ui.PlayerView
import io.github.nihil.orca.ui.*
import io.github.nihil.orca.ui.components.*
import io.github.nihil.orca.ui.theme.OrcaDynamicTheme
import io.github.nihil.orca.ui.theme.OrcaLayout
import io.github.nihil.orca.ui.theme.OrcaPlayerColors
import io.github.nihil.orca.ui.theme.OrcaSpacing
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class MainActivity : ComponentActivity() {
    private val model: OrcaModel by viewModels()
    private val permission = registerForActivityResult(ActivityResultContracts.RequestPermission()) { }
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
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

@Composable fun OrcaTheme(content: @Composable () -> Unit) = OrcaDynamicTheme(content = content)

private val destinations = listOf(
    DrawerDestination("Download queue", Icons.Outlined.Download, Icons.Filled.Download),
    DrawerDestination("Library", Icons.Outlined.VideoLibrary, Icons.Filled.VideoLibrary),
    DrawerDestination("Settings", Icons.Outlined.Settings, Icons.Filled.Settings),
)

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
    var previous by rememberSaveable { mutableIntStateOf(0) }
    var linkSheet by rememberSaveable { mutableStateOf(false) }
    var sheetItemId by rememberSaveable { mutableStateOf<String?>(null) }
    var playing by remember { mutableStateOf<DownloadItem?>(null) }
    var deleting by remember { mutableStateOf<DownloadItem?>(null) }
    var errorItem by remember { mutableStateOf<DownloadItem?>(null) }
    var handledShare by rememberSaveable { mutableStateOf<String?>(null) }
    val sharedLinkEvent by model.sharedLinkEvent.collectAsStateWithLifecycle()
    val snackbar = remember { SnackbarHostState() }
    val drawer = rememberDrawerState(DrawerValue.Closed)
    val scope = rememberCoroutineScope()
    val screenState = rememberSaveableStateHolder()
    val context = LocalContext.current
    fun play(item: DownloadItem) = model.action {
        if (withContext(Dispatchers.IO) { model.repo.publisher.exists(item.uri) }) playing = item else { model.message.value = "File is missing. Download it again from the original link."; model.details.value = "" }
    }
    fun navigate(next: Int) {
        scope.launch { drawer.close() }
        if (next == 2 && destination != 2) previous = destination
        destination = next
    }
    val openDrawer: () -> Unit = { scope.launch { drawer.open() } }
    val actions = DownloadActions(
        play = { play(it) },
        share = { share(context, it, model) },
        resume = { model.action { model.repo.resume(it) } },
        cancel = { model.action { model.repo.cancel(it.id) } },
        details = { errorItem = it },
        openLink = { openLink(context, it.url) },
        copyLink = { context.getSystemService(ClipboardManager::class.java).setPrimaryClip(ClipData.newPlainText("Link", it.url)) },
        delete = { deleting = it },
    )
    LaunchedEffect(sharedLinkEvent) {
        if (sharedLinkEvent != null && sharedLinkEvent != handledShare) {
            handledShare = sharedLinkEvent
            destination = 0
            linkSheet = true
        }
    }
    LaunchedEffect(options) { if (options != null) linkSheet = false }
    LaunchedEffect(model) {
        model.queued.collectLatest { count ->
            linkSheet = false
            val result = snackbar.showSnackbar(if (count == 1) "Download added" else "$count downloads added", "View", duration = SnackbarDuration.Short)
            if (result == SnackbarResult.ActionPerformed) destination = 0
        }
    }
    BackHandler(enabled = destination != 0 && !drawer.isOpen) { destination = if (destination == 2) previous else 0 }
    ModalNavigationDrawer(
        drawerContent = { OrcaDrawerContent(destinations, destination, ::navigate) },
        drawerState = drawer,
        gesturesEnabled = drawer.isOpen || destination != 2,
    ) {
        Scaffold(
            snackbarHost = { SnackbarHost(snackbar) },
            contentWindowInsets = if (destination == 2) WindowInsets(0) else ScaffoldDefaults.contentWindowInsets,
            floatingActionButton = {
                if (destination != 2) FloatingActionButton(onClick = { linkSheet = true }) {
                    Icon(Icons.Outlined.FileDownload, "New download")
                }
            },
        ) { padding ->
            Box(Modifier.fillMaxSize().padding(padding), contentAlignment = Alignment.TopCenter) {
                val screenModifier = if (destination == 2) Modifier.fillMaxSize() else Modifier.widthIn(max = OrcaLayout.contentMaxWidth).fillMaxSize()
                screenState.SaveableStateProvider("destination-$destination") {
                    when (destination) {
                        0 -> QueueScreen(screenModifier, items, openDrawer, actions, { sheetItemId = it.id }, { linkSheet = true })
                        1 -> LibraryScreen(screenModifier, items, openDrawer, { play(it) }, { sheetItemId = it.id }, { linkSheet = true })
                        else -> SettingsScreen(
                            screenModifier, ask, version,
                            busy = busy || updatingEngine || items.any { it.state in DownloadRepository.activeStates },
                            setAsk = { value -> model.action { model.repo.preferences.setAsk(value) } },
                            update = model::update,
                            checkingAppUpdate = checkingAppUpdate,
                            appUpdateStatus = appUpdateStatus,
                            checkAppUpdate = model::checkAppUpdate,
                            back = { destination = previous },
                        )
                    }
                }
            }
        }
    }
    if (linkSheet) LinkSheet(
        link, busy, updatingEngine, ask, { model.link.value = it },
        paste = {
            val clip = context.getSystemService(ClipboardManager::class.java).primaryClip
            val text = clip?.takeIf { it.itemCount > 0 }?.getItemAt(0)?.coerceToText(context)
            val pasted = LinkParser.fromText(text?.toString().orEmpty())
            if (pasted != null) model.link.value = pasted
            else {
                model.details.value = ""
                model.message.value = "The clipboard doesn’t contain a valid link."
            }
        },
        options = { model.start(configure = true) },
        download = { model.start() },
        playlist = { model.start(playlist = true) },
        dismiss = { linkSheet = false },
    )
    sheetItemId?.let { id ->
        val item = items.firstOrNull { it.id == id }
        if (item == null) SideEffect { sheetItemId = null } else ActionSheet(item, actions) { sheetItemId = null }
    }
    options?.let { data -> OptionsSheet(data, { if (!submitting) model.options.value = null }, model::enqueue, submitting) }
    appRelease?.let { release ->
        AlertDialog(onDismissRequest = { model.availableAppRelease.value = null }, icon = { OrcaAvatar(Modifier.size(40.dp)) }, title = { Text("Orca ${release.versionName} is available") },
            text = { Column { Text("Installed: ${BuildConfig.VERSION_NAME}. Download the update from GitHub, then open the APK to install it. Android will ask you to approve the installation."); TextButton(onClick = { openLink(context, release.pageUrl) }) { Text("Release notes") } } },
            confirmButton = { TextButton(onClick = {
                model.availableAppRelease.value = null
                runCatching { context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(release.downloadUrl))) }.onFailure { model.appUpdateStatus.value = "No browser is available to download the update." }
            }) { Text("Download update") } },
            dismissButton = { TextButton(onClick = { model.availableAppRelease.value = null }) { Text("Not now") } })
    }
    if (message != null) ErrorDialog(message.orEmpty(), details, { model.message.value = null; model.details.value = "" })
    errorItem?.let { item -> ErrorDialog(item.error, item.details, { errorItem = null }) }
    deleting?.let { item -> AlertDialog(onDismissRequest = { deleting = null }, icon = { Icon(Icons.Outlined.Delete, null) }, title = { Text("Delete download?") }, text = { Text("Delete “${item.title}” from Orca and device storage?") }, confirmButton = { TextButton(onClick = { model.action { model.repo.delete(item) }; deleting = null }) { Text("Delete", color = MaterialTheme.colorScheme.error) } }, dismissButton = { TextButton(onClick = { deleting = null }) { Text("Keep") } }) }
    playing?.let { item -> PlayerDialog(item, { playing = null }, { externalPlayer(context, item, model) }) }
}

private val queueFilters = listOf("All", "Downloading", "Stopped", "Finished")

@Composable private fun QueueScreen(
    modifier: Modifier,
    downloads: List<DownloadItem>,
    openDrawer: () -> Unit,
    actions: DownloadActions,
    showActions: (DownloadItem) -> Unit,
    newDownload: () -> Unit,
) {
    var filter by rememberSaveable { mutableIntStateOf(0) }
    var grid by rememberSaveable { mutableStateOf(true) }
    val visible = when (filter) {
        1 -> downloads.filter { it.active }
        2 -> downloads.filter { it.stopped }
        3 -> downloads.filter { it.completed }
        else -> downloads
    }
    Column(modifier) {
        OrcaHeader("Download queue", openDrawer) {
            if (downloads.isNotEmpty()) IconButton(onClick = { grid = !grid }) {
                Icon(
                    if (grid) Icons.AutoMirrored.Outlined.ViewList else Icons.Outlined.GridView,
                    if (grid) "Show as list" else "Show as grid",
                )
            }
        }
        if (downloads.isEmpty()) {
            EmptyPlaceholder(
                "No downloads yet", "Add a video or playlist link to start your first download.",
                Modifier.verticalScroll(rememberScrollState()),
            ) { FilledButtonWithIcon("Add a link", Icons.Outlined.Link, newDownload) }
            return@Column
        }
        SelectionGroupRow(Modifier.horizontalScroll(rememberScrollState()).padding(horizontal = OrcaLayout.pageInset, vertical = 4.dp)) {
            queueFilters.forEachIndexed { index, label ->
                SelectionGroupItem(filter == index, { filter = index }) { Text(label) }
            }
        }
        Text(
            mediaCountLabel(visible), Modifier.padding(horizontal = OrcaLayout.pageInset, vertical = 12.dp),
            style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        if (visible.isEmpty()) {
            EmptyPlaceholder("Nothing here", "No downloads match this filter.", Modifier.verticalScroll(rememberScrollState())) {
                TextButton(onClick = { filter = 0 }) { Text("Show all") }
            }
        } else if (grid) {
            LazyVerticalGrid(
                GridCells.Adaptive(OrcaLayout.gridMinCellWidth), Modifier.fillMaxSize(),
                contentPadding = PaddingValues(start = OrcaLayout.pageInset, end = OrcaLayout.pageInset, bottom = OrcaLayout.fabClearance + 16.dp),
                horizontalArrangement = Arrangement.spacedBy(16.dp), verticalArrangement = Arrangement.spacedBy(16.dp),
            ) {
                items(visible, key = { it.id }) { item ->
                    MediaCard(item, { showActions(item) }, { actions.primary(item) }, { showActions(item) })
                }
            }
        } else {
            LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(bottom = OrcaLayout.fabClearance + 16.dp)) {
                items(visible, key = { it.id }) { item -> MediaListItem(item, { showActions(item) }, { showActions(item) }) }
            }
        }
    }
}

@Composable private fun LibraryScreen(
    modifier: Modifier,
    downloads: List<DownloadItem>,
    openDrawer: () -> Unit,
    onPlay: (DownloadItem) -> Unit,
    showActions: (DownloadItem) -> Unit,
    newDownload: () -> Unit,
) {
    var search by rememberSaveable { mutableStateOf("") }
    var filter by rememberSaveable { mutableIntStateOf(0) }
    val focus = LocalFocusManager.current
    val keyboard = LocalSoftwareKeyboardController.current
    val listState = rememberLazyListState()
    val saved = downloads.filter { it.completed }
    val visible = saved.filter { it.title.contains(search.trim(), true) && (filter == 0 || (filter == 2) == it.audio) }
    val filtering = search.isNotBlank() || filter != 0
    Column(modifier) {
        OrcaHeader("Library", openDrawer)
        LazyColumn(Modifier.fillMaxSize(), state = listState, contentPadding = PaddingValues(bottom = OrcaLayout.fabClearance + 16.dp)) {
            item {
                OutlinedTextField(
                    search, { search = it },
                    modifier = Modifier.fillMaxWidth().padding(horizontal = OrcaLayout.pageInset),
                    label = { Text("Search downloads") },
                    leadingIcon = { Icon(Icons.Outlined.Search, null) },
                    singleLine = true, shape = CircleShape,
                    trailingIcon = { if (search.isNotEmpty()) IconButton(onClick = { search = "" }) { Icon(Icons.Default.Close, "Clear search") } },
                )
            }
            if (saved.isNotEmpty()) {
                item {
                    SelectionGroupRow(Modifier.padding(horizontal = OrcaLayout.pageInset, vertical = 12.dp)) {
                        listOf("All", "Video", "Audio").forEachIndexed { index, label ->
                            SelectionGroupItem(filter == index, { filter = index }) { Text(label) }
                        }
                    }
                }
                item {
                    Text(
                        mediaCountLabel(visible), Modifier.padding(horizontal = OrcaLayout.pageInset, vertical = 4.dp),
                        style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
            if (saved.isNotEmpty() && visible.isEmpty() && filtering) item {
                EmptyPlaceholder("No matching downloads", "Try another title or change the media filter.") {
                    TextButton(onClick = {
                        focus.clearFocus()
                        keyboard?.hide()
                        search = ""
                        filter = 0
                        listState.requestScrollToItem(0)
                    }) { Text("Reset filters") }
                }
            }
            if (saved.isEmpty()) item {
                EmptyPlaceholder("No saved media yet", "Finished downloads appear here, ready to play offline.") {
                    FilledButtonWithIcon("Add a link", Icons.Outlined.Link, newDownload)
                }
            }
            items(visible, key = { it.id }) { item -> MediaListItem(item, { onPlay(item) }, { showActions(item) }) }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
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
    back: () -> Unit,
) {
    var notices by rememberSaveable { mutableStateOf(false) }
    val scroll = TopAppBarDefaults.exitUntilCollapsedScrollBehavior(rememberTopAppBarState())
    Scaffold(
        modifier.nestedScroll(scroll.nestedScrollConnection),
        topBar = {
            LargeTopAppBar(
                title = { Text("Settings") },
                navigationIcon = { IconButton(onClick = back) { Icon(Icons.AutoMirrored.Filled.ArrowBack, "Back") } },
                scrollBehavior = scroll,
            )
        },
    ) { padding ->
        LazyColumn(Modifier.fillMaxSize(), contentPadding = padding) {
            item { PreferenceSubtitle("Downloads") }
            item {
                PreferenceSwitch(
                    "Ask before downloading", "Choose a format and quality for each link.", Icons.Outlined.Tune,
                    ask, setAsk,
                )
            }
            item { PreferenceItem("Download folders", "Video: Movies/Orca\nAudio: Music/Orca", Icons.Outlined.FolderOpen) }
            item { PreferenceSubtitle("Updates") }
            item {
                PreferenceItem(
                    "Check for updates", if (checkingAppUpdate) "Checking…" else "Orca ${BuildConfig.VERSION_NAME}",
                    Icons.Outlined.SystemUpdate, enabled = !checkingAppUpdate, onClick = checkAppUpdate,
                )
            }
            if (checkingAppUpdate) item { LinearProgressIndicator(Modifier.fillMaxWidth().padding(horizontal = 56.dp)) }
            if (appUpdateStatus.isNotBlank()) item {
                Text(
                    appUpdateStatus, Modifier.padding(start = 56.dp, end = 16.dp, bottom = 8.dp).semantics { liveRegion = LiveRegionMode.Polite },
                    style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            item {
                PreferenceItem(
                    "Check for engine update",
                    "yt-dlp · $version" + if (busy) "\nAvailable when transfers and inspections are idle." else "",
                    Icons.Outlined.Memory, enabled = !busy, onClick = update,
                )
            }
            item { PreferenceSubtitle("About") }
            item {
                Row(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 12.dp), verticalAlignment = Alignment.CenterVertically) {
                    OrcaAvatar(Modifier.size(56.dp))
                    Column(Modifier.padding(start = 16.dp)) {
                        Text("Orca", style = MaterialTheme.typography.titleLarge)
                        Text(
                            "Version ${BuildConfig.VERSION_NAME} · Saves files on your device for offline use.",
                            style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
            }
            item {
                PreferenceItem(
                    "Open source notices", if (notices) "Hide licenses" else "Licenses for Orca's components",
                    Icons.Outlined.Description, onClick = { notices = !notices },
                    trailing = { Icon(if (notices) Icons.Default.ExpandLess else Icons.Default.ExpandMore, null, Modifier.padding(end = 8.dp)) },
                )
            }
            if (notices) item {
                Text(
                    "Interface design adapted from Seal — GPL-3.0\n" +
                        "yt-dlp — Unlicense\nyoutubedl-android — MIT\nFFmpeg — LGPL/GPL\nPython — PSF License\n" +
                        "QuickJS — MIT\nAndroidX, Kotlin and Coil — Apache 2.0\n\nFull notices are included in the app assets.",
                    Modifier.padding(start = 56.dp, end = 16.dp, bottom = 16.dp),
                    style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            item { Spacer(Modifier.navigationBarsPadding().height(16.dp)) }
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
        DialogSystemBars(dark = true)
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
                    TextButtonWithIcon("Open with another app", Icons.AutoMirrored.Filled.OpenInNew, { player.pause(); external() })
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

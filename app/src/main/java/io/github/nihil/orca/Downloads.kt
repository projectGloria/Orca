package io.github.nihil.orca

import android.app.*
import android.app.job.*
import android.content.*
import android.net.NetworkCapabilities
import android.net.NetworkRequest
import androidx.room.Room
import kotlinx.coroutines.*
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import java.io.File

class OrcaApplication : Application() {
    val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    lateinit var repository: DownloadRepository
    override fun onCreate() {
        super.onCreate()
        val db = Room.databaseBuilder(this, OrcaDatabase::class.java, "orca.db").build()
        repository = DownloadRepository(this, db.downloads(), YtDlpEngine(this), scope)
        getSystemService(NotificationManager::class.java).createNotificationChannel(NotificationChannel("downloads", "Downloads", NotificationManager.IMPORTANCE_LOW))
    }
}
val Context.repository get() = (applicationContext as OrcaApplication).repository

class DownloadRepository(val context: Context, val dao: DownloadsDao, val engine: DownloadEngine, private val scope: CoroutineScope) {
    val items = dao.observe()
    val preferences = Preferences(context)
    val publisher = MediaPublisher(context)
    private val claim = Mutex()
    private val gate = Mutex()
    private var operations = 0
    private val inFlight = mutableSetOf<String>()
    private val transfers = mutableMapOf<String, Job>()
    private var updating = false
    private val ready = scope.async { dao.reconcile() }
    private suspend fun <T> operation(block: suspend () -> T): T {
        gate.withLock { check(!updating) { "Engine update in progress. Try again shortly." }; operations++ }
        return try { block() } finally { withContext(NonCancellable) { gate.withLock { operations-- } } }
    }
    suspend fun inspect(url: String) = operation { engine.inspect(url) }
    suspend fun playlist(url: String) = operation { engine.playlist(url) }
    suspend fun version() = operation { engine.version() }
    suspend fun update(): String {
        ready.await()
        gate.withLock {
            check(!updating && operations == 0 && !dao.hasActive()) { "Wait until downloads and inspections finish." }
            updating = true
        }
        return try { engine.update() } finally { withContext(NonCancellable) { gate.withLock { updating = false } } }
    }
    suspend fun enqueue(media: List<MediaInfo>, options: DownloadOptions) {
        ready.await()
        gate.withLock {
            check(!updating) { "Wait for the engine update to finish." }
            claim.withLock {
                dao.putAll(media.map { info -> DownloadItem(url = info.url, title = info.title, thumbnail = info.thumbnail, duration = info.duration, audio = options.audio, height = options.height) })
                schedule()
            }
        }
    }
    suspend fun enqueueLink(url: String) = enqueue(listOf(MediaInfo(url, "Preparing download", "", 0, emptyList(), null)), DownloadOptions())
    suspend fun resume(item: DownloadItem) {
        ready.await()
        gate.withLock { check(!updating) { "Wait for the engine update to finish." }; claim.withLock {
            check(item.id !in inFlight) { "Cancellation is finishing. Try Resume in a moment." }
            val latest = dao.get(item.id) ?: return
            if (latest.state in activeStates || latest.state == "COMPLETED") return
            dao.put(latest.copy(state = "QUEUED", progress = -1f, error = "", details = "")); schedule()
        } }
    }
    private suspend fun schedule() {
        val scheduler = context.getSystemService(JobScheduler::class.java)
        try {
        for (slot in 1..2) {
            val id = 700 + slot
            if (scheduler.getPendingJob(id) == null) {
                val info = JobInfo.Builder(id, ComponentName(context, TransferService::class.java))
                    .setUserInitiated(true)
                    .setRequiredNetwork(NetworkRequest.Builder().addCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET).removeCapability(NetworkCapabilities.NET_CAPABILITY_NOT_VPN).build())
                    .build()
                check(scheduler.schedule(info) == JobScheduler.RESULT_SUCCESS) { "Android could not start the transfer. Tap Resume while Orca is open." }
            }
        }
        } catch (error: Exception) {
            dao.interruptQueued()
            throw IllegalStateException("Android could not start the transfer. Tap Resume while Orca is open.", error)
        }
    }
    suspend fun next(): DownloadItem? = claim.withLock {
        ready.await()
        dao.firstQueued()?.copy(state = "PREPARING")?.also { dao.put(it); inFlight.add(it.id) }
    }
    suspend fun finishIfEmpty(finish: () -> Unit): Boolean = claim.withLock {
        if (dao.hasQueued()) false else { finish(); true }
    }
    private suspend fun saveActive(item: DownloadItem): Boolean = claim.withLock {
        if (dao.get(item.id)?.state !in activeStates) false else { dao.put(item); true }
    }
    /** Claim and cleanup share one scope, including cancellation between acquisition and run. */
    suspend fun processNext(notify: (DownloadItem) -> Unit): Boolean {
        var item: DownloadItem? = null
        try {
            withContext(NonCancellable) { item = next() }
            val claimed = item ?: return false
            currentCoroutineContext().ensureActive()
            executeTransfer(claimed, notify)
            return true
        } finally { item?.let { release(it.id) } }
    }
    private suspend fun release(id: String) = withContext(NonCancellable) {
        claim.withLock {
            dao.get(id)?.takeIf { it.state in activeStates }?.let {
                dao.put(it.copy(state = "INTERRUPTED", error = "Interrupted. Tap Resume to continue."))
            }
            transfers.remove(id)
            inFlight.remove(id)
        }
    }
    suspend fun run(item: DownloadItem, notify: (DownloadItem) -> Unit) {
        try { executeTransfer(item, notify) } finally { release(item.id) }
    }
    private suspend fun executeTransfer(item: DownloadItem, notify: (DownloadItem) -> Unit) = supervisorScope {
        val worker = async(start = CoroutineStart.LAZY) { transfer(item, notify) }
        try {
            claim.withLock {
                if (dao.get(item.id)?.state in activeStates) transfers[item.id] = worker else worker.cancel()
            }
            worker.await()
        } catch (cancel: CancellationException) {
            // A user's Cancel ends only this transfer. Android stopping the worker propagates.
            currentCoroutineContext().ensureActive()
        } finally { withContext(NonCancellable) { worker.cancelAndJoin() } }
    }
    private suspend fun transfer(item: DownloadItem, notify: (DownloadItem) -> Unit) = operation {
        var current = item
        try {
            val directory = File(context.filesDir, "staging/${item.id}").apply { mkdirs() }
            // A publication retry uses the existing completed staging file, avoiding a second download.
            val staged = item.staging.takeIf { it.isNotBlank() }?.let(::File)?.takeIf { it.exists() }
            val output: File
            if (staged != null) output = staged else {
                val info = engine.inspect(item.url)
                check(!info.playlist) { "This is a playlist. Open its link in Home and choose items." }
                current = current.copy(title = info.title, thumbnail = info.thumbnail, duration = info.duration)
                if (dao.get(item.id)?.state != "PREPARING") return@operation
                if (!saveActive(current)) return@operation
                notify(current)
                currentCoroutineContext().ensureActive()
                var last = 0L
                val downloadContext = currentCoroutineContext()
                output = engine.download(current, directory) { percent, line ->
                    val now = System.currentTimeMillis()
                    if (downloadContext.isActive && (now - last > 600 || percent >= 100)) {
                        last = now
                        val state = if (line.contains("[Merger]") || line.contains("[ExtractAudio]")) "FINISHING" else "DOWNLOADING"
                        runBlocking { claim.withLock {
                            if (downloadContext.isActive) {
                                dao.progress(item.id, state, percent)
                                notify(current.copy(state = state, progress = percent))
                            }
                        } }
                    }
                }
            }
            if (dao.get(item.id)?.state in setOf("CANCELLED", "INTERRUPTED")) return@operation
            current = current.copy(state = "FINISHING", staging = output.absolutePath, bytes = output.length())
            if (!saveActive(current)) return@operation
            notify(current)
            val (uri, mime) = publisher.create(current, output)
            current = current.copy(uri = uri, mime = mime)
            if (!saveActive(current)) return@operation
            val transferContext = currentCoroutineContext()
            publisher.copyPending(uri, output) { transferContext.ensureActive() }
            // Cancellation and the final publication decision are serialized. Once this wins,
            // persist completion even if the worker is stopped during the provider/Room commits.
            withContext(NonCancellable) { claim.withLock {
                if (transferContext.isActive && dao.get(item.id)?.state in activeStates) {
                    publisher.complete(uri)
                    current = current.copy(state = "COMPLETED", progress = 100f, staging = "")
                    dao.put(current)
                    directory.deleteRecursively()
                    notify(current)
                }
            } }
        } catch (cancel: CancellationException) { throw cancel }
        catch (error: Exception) {
            if (dao.get(item.id)?.state !in setOf("CANCELLED", "INTERRUPTED")) {
                current = current.copy(state = "FAILED", error = friendlyError(error), details = error.stackTraceToString().take(32768))
                if (saveActive(current)) notify(current)
            }
        }
    }
    suspend fun cancel(id: String, interrupted: Boolean = false) {
        claim.withLock {
            val item = dao.get(id) ?: return
            if (item.state == "COMPLETED") return
            dao.put(item.copy(state = if (interrupted) "INTERRUPTED" else "CANCELLED", error = if (interrupted) "Interrupted. Tap Resume to continue." else "Cancelled. Tap Resume to continue."))
            transfers[id]?.cancel()
            // Keep the reservation until the best-effort native stop finishes, so a Resume
            // cannot reuse this process ID while cancellation still targets the old attempt.
            withContext(Dispatchers.IO) { runCatching { engine.cancel(id) } }
        }
    }
    suspend fun delete(item: DownloadItem) = claim.withLock {
        check(item.id !in inFlight) { "Cancellation is finishing. Try Resume in a moment." }
        if (item.uri.isNotBlank()) publisher.delete(item.uri)
        publisher.deleteRequest(item)
        File(context.filesDir, "staging/${item.id}").deleteRecursively()
        dao.delete(item.id)
    }
    companion object { val activeStates = setOf("QUEUED", "PREPARING", "DOWNLOADING", "FINISHING") }
}

fun friendlyError(error: Throwable): String {
    val text = error.message.orEmpty().lowercase()
    return when {
        "wait until" in text || "engine update" in text || "android could not start" in text || "cancellation is finishing" in text -> error.message.orEmpty()
        "this is a playlist" in text -> error.message.orEmpty()
        listOf("no space left", "not enough space", "disk full", "enospc").any { it in text } -> "Storage is full. Free some space and retry."
        "ip address is blocked" in text || "not available in your country" in text -> "This source blocks access from your network or region. Try another public link."
        "private" in text || "login" in text || "sign in" in text || "cookies" in text -> "This source requires access or blocks this request. Try another public link."
        "403" in text -> "The source refused the media request (HTTP 403). Check for an engine update in Settings, then retry. If it persists, view the technical details."
        "drm" in text -> "DRM-protected media is not supported."
        "unsupported url" in text -> "This link is not supported. Try a direct media or public video link."
        "no video could be found" in text -> "No downloadable video was found. Check the original link."
        "cannot parse data" in text -> "Could not extract this link. Update the engine in Settings, then retry."
        "network" in text || "resolve" in text || "timed out" in text || "connection" in text -> "Connection failed. Check your network and retry."
        "unavailable" in text || "404" in text -> "This media is unavailable. Check the original link."
        else -> "Could not finish. Retry or view the technical details."
    }
}

class TransferService : JobService() {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val workers = mutableMapOf<Int, Job>()
    override fun onStartJob(params: JobParameters): Boolean {
        setNotification(params, params.jobId, notification(null, params.jobId), JOB_END_NOTIFICATION_POLICY_REMOVE)
        workers[params.jobId] = scope.launch {
            val workerJob = currentCoroutineContext().job
            try {
                while (isActive) {
                    val processed = repository.processNext { value -> getSystemService(NotificationManager::class.java).notify(params.jobId, notification(value, params.jobId)) }
                    if (!processed) {
                        if (repository.finishIfEmpty { jobFinished(params, false) }) break
                        continue
                    }
                }
            } finally { withContext(NonCancellable + Dispatchers.Main) {
                if (workers[params.jobId] == workerJob) workers.remove(params.jobId)
            } }
        }
        return true
    }
    private fun notification(item: DownloadItem?, slot: Int): Notification {
        val open = PendingIntent.getActivity(this, 0, Intent(this, MainActivity::class.java), PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT)
        val builder = Notification.Builder(this, "downloads").setSmallIcon(R.drawable.ic_orca).setContentTitle(item?.title ?: "Orca")
            .setContentText(item?.state?.lowercase()?.replaceFirstChar { it.uppercase() } ?: "Preparing downloads")
            .setContentIntent(open).setOnlyAlertOnce(true).setOngoing(item?.state in DownloadRepository.activeStates || item == null)
        if (item != null && item.state in DownloadRepository.activeStates) {
            builder.setProgress(100, item.progress.toInt().coerceIn(0, 100), item.progress < 0)
            val cancel = PendingIntent.getBroadcast(this, slot, Intent(this, CancelReceiver::class.java).putExtra("id", item.id), PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT)
            builder.addAction(Notification.Action.Builder(null, "Cancel", cancel).build())
        }
        return builder.build()
    }
    override fun onStopJob(params: JobParameters): Boolean {
        workers.remove(params.jobId)?.cancel()
        (application as OrcaApplication).scope.launch { repository.dao.interruptQueued() }
        return false
    }
    override fun onDestroy() { scope.cancel(); super.onDestroy() }
}
class CancelReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val pending = goAsync()
        (context.applicationContext as OrcaApplication).scope.launch { try { intent.getStringExtra("id")?.let { context.repository.cancel(it) } } finally { pending.finish() } }
    }
}

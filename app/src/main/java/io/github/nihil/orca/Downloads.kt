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
    private var updating = false
    private val ready = scope.async { dao.reconcile() }
    private suspend fun <T> operation(block: suspend () -> T): T {
        gate.withLock { check(!updating) { "Engine update in progress. Try again shortly." }; operations++ }
        return try { block() } finally { gate.withLock { operations-- } }
    }
    suspend fun inspect(url: String) = operation { engine.inspect(url) }
    suspend fun playlist(url: String) = operation { engine.playlist(url) }
    suspend fun update(): String {
        ready.await()
        gate.withLock {
            check(!updating && operations == 0 && dao.all().none { it.state in activeStates }) { "Wait until downloads and inspections finish." }
            updating = true
        }
        return try { engine.update() } finally { gate.withLock { updating = false } }
    }
    suspend fun enqueue(media: List<MediaInfo>, options: DownloadOptions) {
        ready.await()
        gate.withLock {
            check(!updating) { "Wait for the engine update to finish." }
            claim.withLock {
                media.forEach { info -> dao.put(DownloadItem(url = info.url, title = info.title, thumbnail = info.thumbnail, duration = info.duration, audio = options.audio, height = options.height)) }
                schedule()
            }
        }
    }
    suspend fun enqueueLink(url: String) = enqueue(listOf(MediaInfo(url, "Preparing download", "", 0, emptyList(), null)), DownloadOptions())
    suspend fun resume(item: DownloadItem) {
        ready.await()
        gate.withLock { check(!updating) { "Wait for the engine update to finish." }; claim.withLock {
            check(item.id !in inFlight) { "Cancellation is finishing. Try Resume in a moment." }
            dao.put(item.copy(state = "QUEUED", progress = -1f, error = "", details = "")); schedule()
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
        dao.all().filter { it.state == "QUEUED" && it.id !in inFlight }.minByOrNull { it.created }?.copy(state = "PREPARING")?.also { dao.put(it); inFlight.add(it.id) }
    }
    suspend fun finishIfEmpty(finish: () -> Unit): Boolean = claim.withLock {
        if (dao.all().any { it.state == "QUEUED" }) false else { finish(); true }
    }
    private suspend fun saveActive(item: DownloadItem): Boolean = claim.withLock {
        if (dao.get(item.id)?.state !in activeStates) false else { dao.put(item); true }
    }
    suspend fun run(item: DownloadItem, notify: (DownloadItem) -> Unit) = try { operation {
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
                var last = 0L
                output = engine.download(current, directory) { percent, line ->
                    val now = System.currentTimeMillis()
                    if (now - last > 600 || percent >= 100) {
                        last = now
                        val state = if (line.contains("[Merger]") || line.contains("[ExtractAudio]")) "FINISHING" else "DOWNLOADING"
                        runBlocking { dao.progress(item.id, state, percent) }
                        notify(current.copy(state = state, progress = percent))
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
            publisher.publish(uri, output)
            current = current.copy(state = "COMPLETED", progress = 100f, staging = "")
            if (saveActive(current)) { directory.deleteRecursively(); notify(current) }
        } catch (cancel: CancellationException) { throw cancel }
        catch (error: Exception) {
            if (dao.get(item.id)?.state !in setOf("CANCELLED", "INTERRUPTED")) {
                current = current.copy(state = "FAILED", error = friendlyError(error), details = error.stackTraceToString())
                if (saveActive(current)) notify(current)
            }
        }
    } } finally { withContext(NonCancellable) { claim.withLock { inFlight.remove(item.id) } } }
    suspend fun cancel(id: String, interrupted: Boolean = false) {
        claim.withLock {
            val item = dao.get(id) ?: return
            if (item.state == "COMPLETED") return
            dao.put(item.copy(state = if (interrupted) "INTERRUPTED" else "CANCELLED", error = if (interrupted) "Interrupted. Tap Resume to continue." else "Cancelled. Tap Resume to continue."))
            engine.cancel(id)
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
        "private" in text || "login" in text || "sign in" in text || "403" in text || "cookies" in text -> "This source requires access or blocks this request. Try another public link."
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
    private val current = java.util.concurrent.ConcurrentHashMap<Int, String>()
    override fun onStartJob(params: JobParameters): Boolean {
        setNotification(params, params.jobId, notification(null, params.jobId), JOB_END_NOTIFICATION_POLICY_REMOVE)
        workers[params.jobId] = scope.launch {
            try {
                while (isActive) {
                    val item = repository.next()
                    if (item == null) {
                        if (repository.finishIfEmpty { jobFinished(params, false) }) break
                        continue
                    }
                    current[params.jobId] = item.id
                    repository.run(item) { value -> getSystemService(NotificationManager::class.java).notify(params.jobId, notification(value, params.jobId)) }
                    current.remove(params.jobId)
                }
            } finally { withContext(NonCancellable + Dispatchers.Main) { workers.remove(params.jobId) } }
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
        current.remove(params.jobId)?.let { id ->
            // Record interruption before cancelling the coroutine; preserve the partial files.
            runBlocking(Dispatchers.IO) { repository.cancel(id, interrupted = true) }
        }
        runBlocking(Dispatchers.IO) { repository.dao.interruptQueued() }
        workers.remove(params.jobId)?.cancel()
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

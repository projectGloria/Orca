package io.github.nihil.orca

import android.content.Context
import com.yausername.youtubedl_android.YoutubeDL
import com.yausername.youtubedl_android.YoutubeDLRequest
import com.yausername.ffmpeg.FFmpeg
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.coroutines.runInterruptible
import kotlinx.coroutines.CancellationException
import org.json.JSONObject
import java.io.File

data class Quality(val height: Int, val width: Int, val bytes: Long?) {
    val label: String get() = "${height}p" + if (width > 0 && width < height) " · Portrait" else ""
}
data class MediaInfo(val url: String, val title: String, val thumbnail: String, val duration: Long, val qualities: List<Quality>, val bytes: Long?, val playlist: Boolean = false, val available: Boolean = true)
data class DownloadOptions(val audio: Boolean = false, val height: Int = 0)

object LinkParser {
    private val link = Regex("https?://[^\\s<>\"']+", RegexOption.IGNORE_CASE)
    fun fromText(text: String): String? = link.find(text)?.value?.trimEnd('.', ',', ')', ']', '}', ';')
    fun isPlaylistOnly(url: String): Boolean = runCatching {
        val uri = java.net.URI(url)
        val host = uri.host.orEmpty().lowercase()
        (host == "youtube.com" || host.endsWith(".youtube.com")) && uri.path == "/playlist"
    }.getOrDefault(false)
    fun hasPlaylist(url: String): Boolean = runCatching {
        java.net.URI(url).rawQuery.orEmpty().split('&').any { it.startsWith("list=") }
    }.getOrDefault(false)
}

fun formatSelector(options: DownloadOptions): String = when {
    options.audio -> "bestaudio/best"
    options.height > 0 -> "bestvideo*[height<=${options.height}]+bestaudio/best[height<=${options.height}]"
    else -> "bestvideo*+bestaudio/best"
}

interface DownloadEngine {
    suspend fun inspect(url: String): MediaInfo
    suspend fun playlist(url: String): List<MediaInfo>
    suspend fun download(item: DownloadItem, directory: File, progress: (Float, String) -> Unit): File
    fun cancel(id: String)
    suspend fun version(): String
    suspend fun update(): String
}

class YtDlpEngine(private val context: Context) : DownloadEngine {
    private var initialized = false
    private val binary get() = File(context.noBackupFilesDir, "youtubedl-android/yt-dlp/yt-dlp")
    private val backup get() = EngineBackup(binary, File(context.noBackupFilesDir, "orca-engine-recovery"))
    private fun resetUpdateVersion() {
        check(context.getSharedPreferences("youtubedl-android", Context.MODE_PRIVATE).edit()
            .remove("dlpVersion").remove("dlpVersionName").commit()) { "Could not reset engine update metadata." }
    }
    @Synchronized private fun init() {
        if (initialized) return
        // Legacy backups were not committed atomically and cannot safely be restored.
        File(binary.parentFile, "yt-dlp.orca-backup").delete()
        if (backup.snapshot.exists()) {
            if (runCatching { backup.restore() }.getOrDefault(false)) resetUpdateVersion()
            else backup.snapshot.delete() // Preserve the installed engine; verify it below.
        }
        YoutubeDL.init(context); FFmpeg.init(context)
        try { verifyVersion() } catch (cancel: CancellationException) { throw cancel }
        catch (failure: Exception) {
            // A killed older installer may have left a truncated binary without a usable backup.
            check(!binary.exists() || binary.delete()) { "Could not restore the bundled engine." }
            YoutubeDL.init_ytdlp(context, binary.parentFile!!)
            resetUpdateVersion()
            verifyVersion()
        }
        backup.commit()
        initialized = true
    }
    private fun verifyVersion(): String = YoutubeDL.execute(YoutubeDLRequest(emptyList()).addOption("--version")).out.trim().also {
        check(it.matches(Regex("[0-9]{4}\\.[0-9]{2}\\.[0-9]{2}.*"))) { "The updated engine did not report a valid version." }
    }
    private fun request(url: String) = YoutubeDLRequest(url).addOption("--socket-timeout", 25).addOption("--no-warnings").addOption("--match-filter", "!is_live")
    override suspend fun inspect(url: String): MediaInfo = withContext(Dispatchers.IO) {
        init()
        val raw = runInterruptible { YoutubeDL.execute(request(url).addOption("--no-playlist").addOption("--flat-playlist").addOption("--skip-download").addOption("--dump-single-json"), "inspect-${java.util.UUID.randomUUID()}").out }
        parse(JSONObject(raw), url)
    }
    override suspend fun playlist(url: String): List<MediaInfo> = withContext(Dispatchers.IO) {
        init()
        val raw = runInterruptible { YoutubeDL.execute(request(url).addOption("--yes-playlist").addOption("--flat-playlist").addOption("--skip-download").addOption("--dump-single-json"), "playlist-${java.util.UUID.randomUUID()}").out }
        val root = JSONObject(raw)
        val entries = root.optJSONArray("entries") ?: return@withContext listOf(parse(root, url))
        (0 until entries.length()).mapNotNull { i -> entries.optJSONObject(i)?.let { entry ->
            val entryUrl = entry.optString("webpage_url").ifBlank { entry.optString("url") }
            val resolved = if (entryUrl.startsWith("http")) entryUrl else if (entry.optString("ie_key").contains("Youtube", true)) "https://www.youtube.com/watch?v=${entry.optString("id")}" else entryUrl
            parse(entry, resolved).copy(url = resolved, available = resolved.startsWith("http") && entry.optString("availability") !in setOf("private", "premium_only", "subscriber_only", "needs_auth") && entry.optString("title") !in setOf("[Deleted video]", "[Private video]"))
        } }
    }
    private fun parse(json: JSONObject, url: String): MediaInfo {
        val formats = json.optJSONArray("formats")
        val choices = (0 until (formats?.length() ?: 0)).mapNotNull { i -> formats?.optJSONObject(i)?.let { f ->
            val height = f.optInt("height")
            if (height <= 0 || f.optString("vcodec") == "none") null else Quality(height, f.optInt("width"), size(f))
        } }.groupBy { it.height }.map { (_, values) -> values.maxBy { it.bytes ?: 0 } }.sortedByDescending { it.height }
        val thumbs = json.optJSONArray("thumbnails")
        return MediaInfo(json.optString("webpage_url").ifBlank { url }, json.optString("title").ifBlank { "Untitled media" }, json.optString("thumbnail").ifBlank { thumbs?.optJSONObject((thumbs.length() - 1).coerceAtLeast(0))?.optString("url").orEmpty() }, json.optLong("duration"), choices, size(json), playlist = json.has("entries") || json.optString("_type") in setOf("playlist", "multi_video"))
    }
    private fun size(json: JSONObject): Long? = json.optLong("filesize").takeIf { it > 0 } ?: json.optLong("filesize_approx").takeIf { it > 0 }
    override suspend fun download(item: DownloadItem, directory: File, progress: (Float, String) -> Unit): File = withContext(Dispatchers.IO) {
        init(); directory.mkdirs()
        val receipt = File(directory, "result.txt").apply { delete() }
        val request = request(item.url).addOption("--no-playlist").addOption("--continue").addOption("--newline")
            .addOption("--no-simulate").addOption("--no-overwrites")
            .addOption("-f", formatSelector(DownloadOptions(item.audio, item.height)))
            .addOption("-o", File(directory, "media.%(ext)s").absolutePath)
            .addCommands(listOf("--print-to-file", "after_move:filepath", receipt.absolutePath))
        if (item.audio) request.addOption("-x").addOption("--audio-format", "best")
            .addOption("--postprocessor-args", "ExtractAudio+ffmpeg_o:-c:a copy")
        runInterruptible { YoutubeDL.execute(request, item.id) { percent, _, line -> progress(percent, line) } }
        val output = receipt.takeIf { it.exists() }?.readLines()?.lastOrNull()?.let { File(it) }
            ?: directory.listFiles()?.firstOrNull { it.name.matches(Regex("media\\.[a-zA-Z0-9]+")) && it.extension !in setOf("part", "ytdl", "temp") }
        require(output != null && output.isFile && output.length() > 0) { "The engine produced no media file." }
        output
    }
    override fun cancel(id: String) { YoutubeDL.destroyProcessById(id) }
    override suspend fun version(): String = withContext(Dispatchers.IO) { init(); runInterruptible { verifyVersion() } }
    override suspend fun update(): String = withContext(Dispatchers.IO) {
        init()
        backup.prepare()
        try { YoutubeDL.updateYoutubeDL(context); verifyVersion().also { backup.commit() } }
        catch (failure: Exception) {
            try { check(backup.restore()); resetUpdateVersion(); verifyVersion(); backup.commit() }
            catch (recovery: Exception) { failure.addSuppressed(recovery); initialized = false }
            throw failure
        }
    }
}

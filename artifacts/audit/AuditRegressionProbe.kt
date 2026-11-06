package io.github.nihil.orca

import android.Manifest
import android.content.Context
import android.content.ContextWrapper
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.Lifecycle
import androidx.media3.ui.PlayerView
import androidx.media3.common.Player
import androidx.room.Room
import androidx.test.platform.app.InstrumentationRegistry
import androidx.test.rule.GrantPermissionRule
import kotlinx.coroutines.*
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import java.io.File
import java.net.ServerSocket
import kotlin.concurrent.thread
import com.yausername.youtubedl_android.YoutubeDL

/** Audit probes assert CURRENT defects, not desired behavior. Kept outside the production test tree. */
class AuditRegressionProbe {
    @get:Rule(order = 0) val notifications = GrantPermissionRule.grant(Manifest.permission.POST_NOTIFICATIONS)
    @get:Rule(order = 1) val rule = createAndroidComposeRule<MainActivity>()

    @Test fun cancellationAtDownloadHandoffStillStartsEngine() = runBlocking {
        val context = rule.activity
        val database = Room.inMemoryDatabaseBuilder(context, OrcaDatabase::class.java).build()
        val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
        val engine = ProbeEngine()
        val repo = DownloadRepository(context, database.downloads(), engine, scope)
        var item: DownloadItem? = null
        try {
            repo.next() // Await startup reconciliation before inserting the fixture.
            item = DownloadItem(url = "https://example.invalid/audit")
            repo.dao.put(item)
            val claimed = repo.next()!!
            repo.run(claimed) { value ->
                if (value.state == "PREPARING") runBlocking { repo.cancel(value.id) }
            }
            assertEquals("CANCELLED", repo.dao.get(item.id)?.state)
            assertTrue("Defect: engine.download starts after cancellation was committed", engine.downloadStarted)
            assertTrue(engine.cancelled)
        } finally {
            item?.let { repo.dao.get(it.id)?.let { saved -> repo.delete(saved) } }
            scope.cancel(); database.close()
        }
    }

    @Test fun librarySearchIsLostOnSettingsRoundTrip() {
        rule.onNodeWithText("Downloads").performClick()
        rule.onNodeWithText("Search downloads").performTextInput("audit-preserve-my-search")
        rule.onNodeWithText("audit-preserve-my-search").assertExists()
        rule.onNodeWithContentDescription("Settings").performClick()
        rule.onNodeWithContentDescription("Back").performClick()
        rule.onNodeWithText("Your library").assertIsDisplayed()
        rule.onNodeWithText("audit-preserve-my-search").assertDoesNotExist()
    }

    @Test fun optionsAudioSelectionIsLostOnActivityRecreation() {
        rule.runOnUiThread {
            val model = ViewModelProvider(rule.activity)[OrcaModel::class.java]
            model.options.value = OptionsState(listOf(MediaInfo("https://example.invalid/audit", "Audit media", "", 12, emptyList(), null)), false)
        }
        rule.onNodeWithText("Audio").performClick().assertIsSelected()
        rule.activityRule.scenario.recreate()
        rule.onNodeWithText("Download options").assertIsDisplayed()
        rule.onNodeWithText("Video").assertIsSelected()
        rule.onNodeWithText("Audio").assertIsNotSelected()
    }

    @Test fun interruptedBackupCopyOverwritesHealthyEngineOnNextInit() = runBlocking {
        val real = InstrumentationRegistry.getInstrumentation().targetContext
        real.repository.engine.version() // Initialize upstream singleton only against the real context.
        val sandbox = File(real.cacheDir, "audit-backup-${java.util.UUID.randomUUID()}").apply { mkdirs() }
        val isolated = object : ContextWrapper(real) { override fun getNoBackupFilesDir(): File = sandbox }
        try {
            val binary = File(sandbox, "youtubedl-android/yt-dlp/yt-dlp").apply { parentFile!!.mkdirs(); writeText("healthy-full-engine") }
            File(binary.parentFile, "yt-dlp.orca-backup").writeText("partial")
            YtDlpEngine(isolated).version()
            assertEquals("Defect: partial backup replaces healthy file without validation", "partial", binary.readText())
            assertFalse(File(binary.parentFile, "yt-dlp.orca-backup").exists())
        } finally { sandbox.deleteRecursively() }
    }

    @Test fun upstreamUpdaterDeletesOrcaBackupDirectory() = runBlocking {
        val real = rule.activity
        real.repository.engine.version()
        val unique = java.util.UUID.randomUUID().toString()
        val sandbox = File(real.cacheDir, "audit-updater-$unique").apply { mkdirs() }
        val preferenceNames = mutableSetOf<String>()
        val isolated = object : ContextWrapper(real) {
            override fun getNoBackupFilesDir(): File = sandbox
            override fun getSharedPreferences(name: String, mode: Int): android.content.SharedPreferences {
                val isolatedName = "audit-$unique-$name"
                preferenceNames.add(isolatedName)
                return real.getSharedPreferences(isolatedName, mode)
            }
        }
        val server = ServerSocket(0)
        val base = "http://127.0.0.1:${server.localPort}"
        val worker = thread(isDaemon = true) {
            repeat(2) {
                server.accept().use { client ->
                    val reader = client.getInputStream().bufferedReader()
                    val request = reader.readLine()
                    while (!reader.readLine().isNullOrBlank()) { }
                    val payload = if (request.contains("/metadata")) """{"tag_name":"audit-$unique","name":"Audit fixture","assets":[{"name":"yt-dlp","browser_download_url":"$base/binary"}]}""" else "replacement-fixture"
                    val bytes = payload.toByteArray()
                    client.getOutputStream().apply { write("HTTP/1.1 200 OK\r\nContent-Length: ${bytes.size}\r\nConnection: close\r\n\r\n".toByteArray()); write(bytes); flush() }
                }
            }
        }
        try {
            val binary = File(sandbox, "youtubedl-android/yt-dlp/yt-dlp").apply { parentFile!!.mkdirs(); writeText("healthy") }
            val backup = File(binary.parentFile, "yt-dlp.orca-backup").apply { writeText("healthy") }
            YoutubeDL.updateYoutubeDL(isolated, YoutubeDL.UpdateChannel("$base/metadata"))
            assertEquals("replacement-fixture", binary.readText())
            assertFalse("Defect: updater deleted Orca's backup", backup.exists())
        } finally { server.close(); worker.join(1000); sandbox.deleteRecursively(); preferenceNames.forEach { real.deleteSharedPreferences(it) } }
    }

    @Test fun cancelledPublicationStillCreatesVisibleFile() = runBlocking {
        val context = rule.activity
        val database = Room.inMemoryDatabaseBuilder(context, OrcaDatabase::class.java).build()
        val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
        val repo = DownloadRepository(context, database.downloads(), ProbeEngine(), scope)
        val item = DownloadItem(url = "https://example.invalid/audit", title = "Audit publication")
        val directory = File(context.filesDir, "staging/${item.id}").apply { mkdirs() }
        val staged = File(directory, "media.mp4")
        try {
            java.io.RandomAccessFile(staged, "rw").use { it.setLength(128L * 1024 * 1024) }
            repo.next()
            repo.dao.put(item.copy(staging = staged.absolutePath))
            val claimed = repo.next()!!
            val runner = async(Dispatchers.IO) { repo.run(claimed) {} }
            withTimeout(15000) { while (repo.dao.get(item.id)!!.uri.isBlank()) delay(1) }
            repo.cancel(item.id)
            runner.await()
            val saved = repo.dao.get(item.id)!!
            assertEquals("Cancellation landed during copying", "CANCELLED", saved.state)
            context.contentResolver.query(android.net.Uri.parse(saved.uri), arrayOf(android.provider.MediaStore.MediaColumns.IS_PENDING), null, null, null)!!.use {
                assertTrue(it.moveToFirst())
                assertEquals("Defect: cancelled publication is public", 0, it.getInt(0))
            }
            assertTrue(staged.exists())
        } finally {
            repo.dao.get(item.id)?.let { repo.delete(it) }
            directory.deleteRecursively(); scope.cancel(); database.close()
        }
    }

    @androidx.annotation.OptIn(androidx.media3.common.util.UnstableApi::class)
    @Test fun playerKeepsPlayingAfterActivityStops(): Unit = runBlocking {
        val repo = rule.activity.repository
        val server = FixtureServer()
        val item = DownloadItem(url = server.url, title = "Audit player lifecycle")
        val directory = File(rule.activity.filesDir, "staging/${item.id}")
        var saved: DownloadItem? = null
        try {
            val output = repo.engine.download(item, directory) { _, _ -> }
            val (uri, mime) = repo.publisher.create(item, output)
            saved = item.copy(uri = uri, mime = mime, state = "COMPLETED")
            repo.publisher.publish(uri, output)
            repo.dao.put(saved)
            rule.onNodeWithText("Downloads").performClick()
            rule.waitUntil(10000) { rule.onAllNodesWithText(item.title).fetchSemanticsNodes().isNotEmpty() }
            rule.onNodeWithText(item.title).performClick()
            rule.onNodeWithContentDescription("Close player").assertIsDisplayed()
            fun find(view: android.view.View): PlayerView? {
                if (view is PlayerView) return view
                if (view is android.view.ViewGroup) for (index in 0 until view.childCount) find(view.getChildAt(index))?.let { return it }
                return null
            }
            var player: Player? = null
            rule.runOnUiThread {
                player = android.view.inspector.WindowInspector.getGlobalWindowViews().firstNotNullOfOrNull { find(it) }!!.player
                player!!.repeatMode = Player.REPEAT_MODE_ONE
                player!!.seekTo(0); player!!.play()
            }
            rule.waitUntil(10000) { var ready = false; rule.runOnUiThread { ready = player!!.isPlaying }; ready }
            rule.activityRule.scenario.moveToState(Lifecycle.State.CREATED)
            android.os.SystemClock.sleep(400)
            InstrumentationRegistry.getInstrumentation().runOnMainSync {
                assertTrue("Defect: audio remains playing with stopped Activity", player!!.isPlaying)
            }
            rule.activityRule.scenario.moveToState(Lifecycle.State.RESUMED)
            rule.onNodeWithContentDescription("Close player").performClick()
        } finally { saved?.let { repo.delete(it) }; directory.deleteRecursively(); server.close() }
    }

    private class ProbeEngine : DownloadEngine {
        var downloadStarted = false
        var cancelled = false
        override suspend fun inspect(url: String) = MediaInfo(url, "Audit fixture", "", 0, emptyList(), null)
        override suspend fun playlist(url: String) = emptyList<MediaInfo>()
        override suspend fun download(item: DownloadItem, directory: File, progress: (Float, String) -> Unit): File {
            downloadStarted = true
            return File(directory, "media.mp4").apply { writeText("fixture") }
        }
        override fun cancel(id: String) { cancelled = true }
        override suspend fun version() = "audit"
        override suspend fun update() = "audit"
    }
}

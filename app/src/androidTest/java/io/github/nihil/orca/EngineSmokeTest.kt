package io.github.nihil.orca

import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File
import androidx.test.ext.junit.rules.ActivityScenarioRule
import org.junit.Rule

@RunWith(AndroidJUnit4::class)
class EngineSmokeTest {
    @get:Rule val activity = ActivityScenarioRule(MainActivity::class.java)
    private val context get() = InstrumentationRegistry.getInstrumentation().targetContext
    @Test fun bundledJavaScriptRuntimeRuns() {
        val executable = File(context.applicationInfo.nativeLibraryDir, "libqjs.so")
        val process = ProcessBuilder(executable.absolutePath, "-e", "console.log(21 * 2)").redirectErrorStream(true).start()
        val output = process.inputStream.bufferedReader().readText()
        assertEquals(output, 0, process.waitFor())
        assertEquals("42", output.trim())
    }
    @Test fun bundledRuntimeMergesSeparateStreamsAndPublishes() = runBlocking {
        val server = FixtureServer()
        val engine = YtDlpEngine(context)
        assertTrue(engine.version().isNotBlank())
        val url = server.url
        val info = engine.inspect(url)
        assertTrue(info.title.isNotBlank())
        val item = DownloadItem(url = url, title = "Orca engine smoke test")
        val directory = File(context.filesDir, "smoke/${item.id}")
        val file = engine.download(item, directory) { _, _ -> }
        assertTrue(file.length() > 0)
        val retriever = android.media.MediaMetadataRetriever()
        try {
            retriever.setDataSource(file.absolutePath)
            assertEquals("yes", retriever.extractMetadata(android.media.MediaMetadataRetriever.METADATA_KEY_HAS_AUDIO))
            assertEquals("yes", retriever.extractMetadata(android.media.MediaMetadataRetriever.METADATA_KEY_HAS_VIDEO))
        } finally { retriever.release() }
        val publisher = MediaPublisher(context)
        val (uri, _) = publisher.create(item, file)
        val (collisionUri, _) = publisher.create(item.copy(id = java.util.UUID.randomUUID().toString()), file)
        try {
            assertNotEquals(uri, collisionUri)
            publisher.publish(uri, file)
            assertTrue(publisher.exists(uri))
            assertEquals(uri, publisher.create(item, file).first)
        } finally { publisher.delete(uri); publisher.delete(collisionUri); assertFalse(publisher.exists(uri)); directory.deleteRecursively(); server.close() }
    }
    @Test fun extractsOriginalAudioWithoutVideo() = runBlocking {
        val engine = YtDlpEngine(context)
        val server = FixtureServer()
        val url = server.url
        val item = DownloadItem(url = url, title = "Audio smoke", audio = true)
        val directory = File(context.filesDir, "smoke/${item.id}")
        try {
            val file = engine.download(item, directory) { _, _ -> }
            assertTrue(file.length() > 0)
            val retriever = android.media.MediaMetadataRetriever()
            try { retriever.setDataSource(file.absolutePath); assertEquals("yes", retriever.extractMetadata(android.media.MediaMetadataRetriever.METADATA_KEY_HAS_AUDIO)); assertNull(retriever.extractMetadata(android.media.MediaMetadataRetriever.METADATA_KEY_HAS_VIDEO)) } finally { retriever.release() }
        } finally { directory.deleteRecursively(); server.close() }
    }
}

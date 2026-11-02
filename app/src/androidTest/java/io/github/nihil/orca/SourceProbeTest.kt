package io.github.nihil.orca

import androidx.test.platform.app.InstrumentationRegistry
import kotlinx.coroutines.runBlocking
import org.junit.Assume.assumeTrue
import org.junit.Test
import java.io.File
import androidx.test.ext.junit.rules.ActivityScenarioRule
import org.junit.Rule

/** Optional live-network checks. Source failures are recorded, never treated as app-test passes. */
class SourceProbeTest {
    @get:Rule val activity = ActivityScenarioRule(MainActivity::class.java)
    @Test fun recordLiveSources() = runBlocking {
        assumeTrue(InstrumentationRegistry.getArguments().getString("sourceProbes") == "true")
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val engine = YtDlpEngine(context)
        val report = File(context.filesDir, "source-probes.txt")
        report.writeText("Bundled engine: ${engine.version()}\n")
        report.appendText("Manual update: ${runCatching { engine.update() }.fold({ it }, { "FAILED: ${it.message}" })}\n")
        val sources = linkedMapOf(
            "YouTube" to "https://www.youtube.com/watch?v=jNQXAC9IVRw",
            "Instagram" to "https://www.instagram.com/reel/Chunk8-jurw/",
            "TikTok" to "https://www.tiktok.com/@leenabhushan/video/6748451240264420610",
            "X" to "https://twitter.com/i/cards/tfw/v1/560070183650213889",
            "X alternate" to "https://twitter.com/starwars/status/665052190608723968",
            "Reddit" to "https://www.reddit.com/r/aww/comments/90bu6w/heat_index_was_110_degrees_so_we_offered_him_a/",
            "Facebook" to "https://www.facebook.com/radiokicksfm/videos/3676516585958356/",
            "Direct media" to "https://download.samplelib.com/mp4/sample-5s.mp4"
        )
        for ((name, url) in sources) {
            val only = InstrumentationRegistry.getArguments().getString("sourceOnly")
            if (only != null && only != name) continue
            report.appendText("\n$name\n$url\n")
            val directory = File(context.filesDir, "probe/$name")
            try {
                val info = engine.inspect(url)
                report.appendText("INSPECTED: ${info.title}; duration ${info.duration}s; heights ${info.qualities.map { it.height }}\n")
                if (info.duration <= 180) {
                    val file = engine.download(DownloadItem(url = url), directory) { _, _ -> }
                    report.appendText("DOWNLOADED: ${file.length()} bytes; ${file.extension}\n")
                } else report.appendText("Download not tested: sample exceeds 180 seconds.\n")
            } catch (e: Exception) { report.appendText("FAILED: ${e.message}\n") }
            finally { directory.deleteRecursively() }
        }
    }
}

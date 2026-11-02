package io.github.nihil.orca

import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import org.junit.Rule
import org.junit.Test
import org.junit.Assert.*
import androidx.test.platform.app.InstrumentationRegistry
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.first
import android.content.Intent

class OrcaUiTest {
    @get:Rule val rule = createAndroidComposeRule<MainActivity>()
    @Test fun homeLibraryAndSettingsAreReachable() {
        rule.onNodeWithText("Your links.\nYour library.").assertIsDisplayed()
        rule.onNodeWithText("Download", useUnmergedTree = true).assertIsDisplayed()
        rule.onNodeWithContentDescription("Settings").performClick()
        rule.onNodeWithText("Ask before downloading").assertIsDisplayed()
        rule.onNodeWithContentDescription("Back").performClick()
        rule.onNodeWithText("Downloads").performClick()
        rule.onNodeWithText("Search downloads").assertIsDisplayed()
        rule.onNodeWithText("Audio").performClick()
    }
    @Test fun queuedTransferCompletesWithScreenLocked() = runBlocking {
        val server = FixtureServer()
        val repo = rule.activity.repository
        var id: String? = null
        try {
            val before = repo.dao.all().map { it.id }.toSet()
            rule.onNodeWithText("Video or playlist link").performTextInput(server.url)
            rule.onNodeWithText("Download", useUnmergedTree = true).performClick()
            withTimeout(30000) {
                while (id == null) { id = repo.dao.all().firstOrNull { it.id !in before }?.id; delay(100) }
            }
            val automation = InstrumentationRegistry.getInstrumentation().uiAutomation
            automation.executeShellCommand("input keyevent KEYCODE_HOME").close()
            automation.executeShellCommand("input keyevent KEYCODE_SLEEP").close()
            withTimeout(90000) {
                while (repo.dao.get(id!!)?.state in DownloadRepository.activeStates) delay(500)
            }
            val item = repo.dao.get(id!!)!!
            assertEquals(item.details, "COMPLETED", item.state)
            assertTrue(repo.publisher.exists(item.uri))
        } finally {
            InstrumentationRegistry.getInstrumentation().uiAutomation.executeShellCommand("input keyevent KEYCODE_WAKEUP").close()
            InstrumentationRegistry.getInstrumentation().uiAutomation.executeShellCommand("wm dismiss-keyguard").close()
            id?.let { repo.dao.get(it)?.let { value -> repo.delete(value) } }
            server.close()
        }
    }
    @Test fun receivingSharedTextOnlyFillsTheLink() = runBlocking {
        val repo = rule.activity.repository
        val before = repo.dao.all().size
        rule.runOnUiThread {
            rule.activity.startActivity(Intent(rule.activity, MainActivity::class.java).setAction(Intent.ACTION_SEND)
                .putExtra(Intent.EXTRA_TEXT, "Watch this https://example.org/video.mp4.").addFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP))
        }
        rule.onNodeWithText("https://example.org/video.mp4").assertIsDisplayed()
        assertEquals(before, repo.dao.all().size)
    }
    @Test fun savedAskPreferenceOpensOptionsWithoutStartingTransfer() = runBlocking {
        val server = FixtureServer()
        val repo = rule.activity.repository
        val before = repo.dao.all().size
        try {
            repo.preferences.setAsk(true)
            rule.onNodeWithText("Video or playlist link").performTextInput(server.url)
            rule.onNodeWithText("Download", useUnmergedTree = true).performClick()
            rule.waitUntil(30000) { rule.onAllNodesWithText("Download options").fetchSemanticsNodes().isNotEmpty() }
            rule.onNodeWithText("Download options").assertIsDisplayed()
            assertTrue(repo.preferences.ask.first())
            assertEquals(before, repo.dao.all().size)
        } finally { repo.preferences.setAsk(false); server.close() }
    }
    @Test fun cancelledItemResumesIndependently() = runBlocking {
        val server = FixtureServer()
        val repo = rule.activity.repository
        var id: String? = null
        try {
            val before = repo.dao.all().map { it.id }.toSet()
            repo.enqueueLink(server.url)
            id = repo.dao.all().first { it.id !in before }.id
            repo.cancel(id)
            assertEquals("CANCELLED", repo.dao.get(id)?.state)
            withTimeout(30000) {
                while (true) {
                    val result = runCatching { repo.resume(repo.dao.get(id)!!) }
                    if (result.isSuccess) break
                    delay(200)
                }
            }
            withTimeout(90000) { while (repo.dao.get(id)?.state in DownloadRepository.activeStates) delay(200) }
            val completed = repo.dao.get(id)!!
            assertEquals(completed.details, "COMPLETED", completed.state)
        } finally { id?.let { repo.dao.get(it)?.let { item -> repo.delete(item) } }; server.close() }
    }
    @Test fun genericPlaylistAlwaysOpensPickerBeforeQueueing() = runBlocking {
        val server = FixtureServer()
        val repo = rule.activity.repository
        val before = repo.dao.all().size
        try {
            val url = server.url.substringBefore("/dash/") + "/playlist.html"
            rule.onNodeWithText("Video or playlist link").performTextInput(url)
            rule.onNodeWithText("Download", useUnmergedTree = true).performClick()
            rule.waitUntil(30000) { rule.onAllNodesWithText("Choose playlist items").fetchSemanticsNodes().isNotEmpty() }
            rule.onNodeWithText("Choose playlist items").assertIsDisplayed()
            assertEquals(before, repo.dao.all().size)
        } finally { server.close() }
    }
}

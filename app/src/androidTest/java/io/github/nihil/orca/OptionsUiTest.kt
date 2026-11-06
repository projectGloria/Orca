package io.github.nihil.orca

import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.activity.compose.setContent
import org.junit.Assert.*
import android.Manifest
import androidx.test.rule.GrantPermissionRule
import org.junit.Rule
import org.junit.Test

class OptionsUiTest {
    @get:Rule(order = 0) val notifications = GrantPermissionRule.grant(Manifest.permission.POST_NOTIFICATIONS)
    @get:Rule(order = 1) val rule = createAndroidComposeRule<MainActivity>()
    @Test fun playlistCountAndUnavailableEntriesMatchSelection() {
        val media = listOf(
            MediaInfo("https://example.org/one", "First item", "", 10, emptyList(), null),
            MediaInfo("https://example.org/two", "Second item", "", 20, emptyList(), null),
            MediaInfo("https://example.org/three", "Private item", "", 0, emptyList(), null, available = false)
        )
        var downloaded: List<MediaInfo>? = null
        rule.runOnUiThread { rule.activity.setContent { OrcaTheme { OptionsSheet(OptionsState(media, true), {}, { items, _ -> downloaded = items }) } } }
        rule.onNodeWithText("Download 2 items").assertIsDisplayed()
        captureRedesign("playlist")
        rule.onNodeWithContentDescription("Select Private item").assertIsNotEnabled()
        rule.onNodeWithContentDescription("Select Second item").performClick()
        rule.onNodeWithText("Download 1 item").assertIsDisplayed()
        rule.onNodeWithTag("confirmDownload").performClick()
        assertEquals(listOf(media.first()), downloaded)
    }
    @Test fun singleItemAudioOverrideKeepsBestSourceQuality() {
        var selected: DownloadOptions? = null
        val media = MediaInfo("https://example.org/video", "Portrait sample", "", 10, listOf(Quality(1920, 1080, null)), null)
        rule.runOnUiThread { rule.activity.setContent { OrcaTheme { OptionsSheet(OptionsState(listOf(media), false), {}, { _, options -> selected = options }) } } }
        rule.waitForIdle()
        captureRedesign("options")
        rule.onNodeWithText("Best available").performClick()
        rule.onNodeWithText("1920p · Portrait").performClick()
        rule.onNodeWithText("Audio").performClick()
        rule.onNodeWithTag("confirmDownload").performClick()
        assertEquals(DownloadOptions(audio = true, height = 1920), selected)
        assertEquals("bestaudio/best", formatSelector(selected!!))
    }
}

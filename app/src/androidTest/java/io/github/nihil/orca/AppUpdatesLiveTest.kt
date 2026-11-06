package io.github.nihil.orca

import androidx.test.platform.app.InstrumentationRegistry
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import android.os.Build
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Assume.assumeTrue
import org.junit.Rule
import org.junit.Test

/** Opt-in check against the real published Actions release, never a dependency of offline tests. */
class AppUpdatesLiveTest {
    @get:Rule val rule = createAndroidComposeRule<MainActivity>()
    @Test fun officialReleaseSupportsCurrentAndOlderInstalls(): Unit = runBlocking {
        assumeTrue(InstrumentationRegistry.getArguments().getString("liveUpdates") == "true")
        val updates = GitHubAppUpdates()
        val abis = Build.SUPPORTED_ABIS.toList()
        rule.onNodeWithContentDescription("Settings").performClick()
        rule.onNodeWithText("Check for updates").performScrollTo().performClick()
        rule.waitUntil(60000) { rule.onAllNodesWithText("You're up to date.").fetchSemanticsNodes().isNotEmpty() }
        rule.onNodeWithText("You're up to date.").assertIsDisplayed()
        val result = updates.check(1, abis) as AppUpdateResult.Available
        assertTrue(result.release.versionCode > 1)
        assertTrue(result.release.downloadUrl.startsWith("https://github.com/projectGloria/Orca/releases/download/"))
    }
}

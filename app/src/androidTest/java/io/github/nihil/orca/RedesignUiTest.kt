package io.github.nihil.orca

import android.graphics.Bitmap
import androidx.activity.compose.setContent
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.unit.Density
import androidx.test.platform.app.InstrumentationRegistry
import kotlinx.coroutines.runBlocking
import android.Manifest
import androidx.test.rule.GrantPermissionRule
import org.junit.Rule
import org.junit.Test
import java.io.File

/** Local fixtures exercise long titles, filters, navigation and larger accessibility text. */
class RedesignUiTest {
    @get:Rule(order = 0) val notifications = GrantPermissionRule.grant(Manifest.permission.POST_NOTIFICATIONS)
    @get:Rule(order = 1) val rule = createAndroidComposeRule<MainActivity>()

    private fun capture(name: String) {
        rule.waitForIdle()
        captureRedesign(name)
    }

    @Test fun collectionFiltersAndBackNavigationKeepTheirContext(): Unit = runBlocking {
        val dao = rule.activity.repository.dao
        val fixtures = listOf(
            DownloadItem(id = "design-video", created = 3L, url = "https://example.org/video", title = "A quieter world: a journey through the mountains and beyond", state = "COMPLETED", duration = 642, bytes = 145000000),
            DownloadItem(id = "design-audio", created = 2L, url = "https://example.org/audio", title = "Late afternoon / Field recordings", state = "COMPLETED", audio = true, duration = 245, bytes = 18000000),
            DownloadItem(id = "design-interrupted", created = 1L, url = "https://example.org/interrupted", title = "The places in between", state = "INTERRUPTED", error = "Interrupted. Tap Resume to continue.")
        )
        try {
            capture("home")
            rule.onNodeWithText("Downloads").performClick()
            capture("library-empty")
            fixtures.forEach { dao.put(it) }
            rule.waitUntil(10000) { rule.onAllNodesWithText(fixtures.first().title).fetchSemanticsNodes().isNotEmpty() }
            capture("library")
            rule.onNodeWithText("Search downloads").performTextInput("not-a-real-title")
            rule.onNodeWithText("No matching downloads").assertIsDisplayed()
            rule.onNodeWithText("Reset filters").performClick()
            rule.onNodeWithText(fixtures.first().title).assertIsDisplayed()
            rule.onNodeWithContentDescription("Settings").performClick()
            rule.onNodeWithText("Ask before downloading").assertIsDisplayed()
            capture("settings")
            rule.activity.onBackPressedDispatcher.let { dispatcher -> rule.runOnUiThread { dispatcher.onBackPressed() } }
            rule.onNodeWithText("Your library").assertIsDisplayed()
            rule.runOnUiThread { rule.activity.onBackPressedDispatcher.onBackPressed() }
            rule.onNodeWithText("Keep what\nmoves you.").assertIsDisplayed()
        } finally { fixtures.forEach { dao.delete(it.id) } }
    }

    @Test fun optionsRemainScrollableWithLargeText() {
        val media = MediaInfo("https://example.org/video", "A long video title that should wrap safely inside the download options preview", "", 642, listOf(Quality(1080, 1920, null)), null)
        rule.runOnUiThread {
            rule.activity.setContent {
                CompositionLocalProvider(LocalDensity provides Density(rule.activity.resources.displayMetrics.density, 1.5f)) {
                    OrcaTheme { OptionsSheet(OptionsState(listOf(media), false), {}, { _, _ -> }) }
                }
            }
        }
        rule.onNodeWithTag("confirmDownload").assertIsDisplayed()
        capture("options-large-text")
    }
}



internal fun captureRedesign(name: String) {
    val instrumentation = InstrumentationRegistry.getInstrumentation()
    instrumentation.waitForIdleSync()
    android.os.SystemClock.sleep(600) // Wait for Android window/splash transitions outside the Compose clock.
    // Surface transitions can temporarily make UiAutomation return null on an emulator.
    // Retry capture, but still fail if Android never supplies a usable image.
    var captured = instrumentation.uiAutomation.takeScreenshot()
    repeat(5) {
        if (captured == null) {
            android.os.SystemClock.sleep(300)
            captured = instrumentation.uiAutomation.takeScreenshot()
        }
    }
    val bitmap = requireNotNull(captured) { "Android did not provide a screenshot after window transitions." }
    val output = InstrumentationRegistry.getArguments().getString("additionalTestOutputDir")
        ?: instrumentation.targetContext.getExternalFilesDir(null)!!.absolutePath
    val directory = File(output, "redesign").apply { mkdirs() }
    File(directory, "$name.png").outputStream().use { bitmap.compress(Bitmap.CompressFormat.PNG, 100, it) }
}

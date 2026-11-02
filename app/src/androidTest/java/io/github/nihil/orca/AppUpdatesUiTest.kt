package io.github.nihil.orca

import androidx.activity.compose.setContent
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import org.junit.Rule
import org.junit.Test

class AppUpdatesUiTest {
    @get:Rule val rule = createAndroidComposeRule<MainActivity>()
    @Test fun settingsHasSeparateAppUpdateAction() {
        rule.onNodeWithContentDescription("Settings").performClick()
        rule.onNodeWithText("Check for updates").performScrollTo().assertIsDisplayed()
        rule.onNodeWithText("Orca ${BuildConfig.VERSION_NAME}").assertIsDisplayed()
    }
    @Test fun availableUpdateCanBeDismissedWithoutDownloading() {
        val model = OrcaModel(rule.activity.application)
        model.availableAppRelease.value = AppRelease("9.0.0", 900, "https://github.com/projectGloria/Orca/releases/download/v9.0.0/Orca-9.0.0-arm64.apk", "https://github.com/projectGloria/Orca/releases/tag/v9.0.0")
        rule.runOnUiThread { rule.activity.setContent { OrcaTheme { OrcaApp(model) } } }
        rule.onNodeWithText("Orca 9.0.0 is available").assertIsDisplayed()
        rule.onNodeWithText("Download update").assertIsDisplayed()
        rule.onNodeWithText("Not now").performClick()
        rule.onNodeWithText("Orca 9.0.0 is available").assertDoesNotExist()
    }
}

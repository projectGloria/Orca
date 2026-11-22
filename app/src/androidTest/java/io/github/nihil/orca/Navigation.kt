package io.github.nihil.orca

import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.AndroidComposeTestRule

/** Opens the navigation drawer and selects one of its destinations. */
internal fun AndroidComposeTestRule<*, *>.openDestination(label: String) {
    onNodeWithContentDescription("Open navigation").performClick()
    onNode(hasText(label) and hasClickAction()).performClick()
    waitForIdle()
}

/** Opens the new-download sheet from the floating action button. */
internal fun AndroidComposeTestRule<*, *>.openLinkSheet() {
    onNodeWithContentDescription("New download").performClick()
    waitForIdle()
}

internal fun AndroidComposeTestRule<*, *>.onHeading(text: String) = onNode(hasText(text) and isHeading())

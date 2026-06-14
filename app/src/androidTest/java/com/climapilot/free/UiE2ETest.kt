package com.climapilot.free

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollToNode
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/**
 * E2E flow for the Free edition (basic control only). Uses the safe demo mode, so no command ever
 * reaches a real device. The emulator runs in English, so assertions use the default strings.
 */
@RunWith(AndroidJUnit4::class)
class UiE2ETest {

    @get:Rule
    val rule = createAndroidComposeRule<MainActivity>()

    private fun dismissDisclaimer() {
        rule.waitForIdle()
        if (rule.onAllNodesWithText("I understand").fetchSemanticsNodes().isNotEmpty()) {
            rule.onNodeWithText("I understand").performClick()
            rule.waitForIdle()
        }
    }

    private fun scrollTo(text: String) =
        rule.onNodeWithTag("control_list").performScrollToNode(hasText(text))

    @Test
    fun launchesAndShowsDeviceScreen() {
        dismissDisclaimer()
        rule.onNodeWithText("ClimaPilot").assertIsDisplayed()
        rule.onNodeWithText("Search devices").assertIsDisplayed()
    }

    @Test
    fun demoShowsBasicControls() {
        dismissDisclaimer()
        rule.onNodeWithText("Preview without device (demo)").performClick()
        rule.waitForIdle()

        scrollTo("Mode"); rule.onNodeWithText("Mode").assertIsDisplayed()
        scrollTo("Fan speed"); rule.onNodeWithText("Fan speed").assertIsDisplayed()

        // Switch mode — pure UI state, no device traffic.
        scrollTo("Heat")
        rule.onNodeWithText("Heat").performClick()
        rule.waitForIdle()
    }

    /** EN: All v0.2 cards must be present in demo mode. DE: Alle v0.2-Karten müssen im Demo-Modus vorhanden sein. */
    @Test
    fun demoShowsAllCards() {
        dismissDisclaimer()
        rule.onNodeWithText("Preview without device (demo)").performClick()
        rule.waitForIdle()

        scrollTo("Scenes"); rule.onNodeWithText("Scenes").assertIsDisplayed()
        scrollTo("Live status"); rule.onNodeWithText("Live status").assertIsDisplayed()
        scrollTo("Options"); rule.onNodeWithText("Options").assertIsDisplayed()
        // Device-specific toggle is shown in demo (capabilities forced on).
        scrollTo("Ionizer"); rule.onNodeWithText("Ionizer").assertIsDisplayed()
        scrollTo("Sleep timer"); rule.onNodeWithText("Sleep timer").assertIsDisplayed()
    }

    /** EN: Settings opens and shows the new display/price card. DE: Einstellungen öffnen + neue Anzeige/Preis-Karte zeigen. */
    @Test
    fun settingsShowsDisplayAndPrice() {
        dismissDisclaimer()
        rule.onNodeWithContentDescription("Settings").performClick()
        rule.waitForIdle()
        rule.onNodeWithText("Display & price").assertIsDisplayed()
        rule.onNodeWithText("Price per kWh").assertIsDisplayed()
    }
}

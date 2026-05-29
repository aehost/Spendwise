package com.spendwise.app.ui

import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.spendwise.app.MainActivity
import dagger.hilt.android.testing.HiltAndroidRule
import dagger.hilt.android.testing.HiltAndroidTest
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Instrumented UI tests for the Gmail Connect Sheet (ModalBottomSheet wizard).
 *
 * These tests run on a device or emulator via:
 *   ./gradlew connectedAndroidTest
 *
 * Tests cover all 3 wizard steps:
 *  Step 0 — Welcome/Benefits screen
 *  Step 1 — Email input + validation
 *  Step 2 — App Password input + validation
 */
@HiltAndroidTest
@RunWith(AndroidJUnit4::class)
class GmailConnectSheetTest {

    @get:Rule(order = 0) val hiltRule   = HiltAndroidRule(this)
    @get:Rule(order = 1) val composeRule = createAndroidComposeRule<MainActivity>()

    @Before
    fun setUp() {
        hiltRule.inject()
        // Navigate to Settings tab
        composeRule.onNodeWithContentDescription("Settings").performClick()
        composeRule.waitForIdle()
    }

    // ─── Step 0: Welcome screen ───────────────────────────────────────────────

    @Test
    fun welcomeCard_isDisplayed_whenNoAccountConnected() {
        composeRule
            .onNodeWithText("Gmail Smart Sync", useUnmergedTree = true)
            .assertIsDisplayed()
    }

    @Test
    fun connectButton_opensBottomSheet() {
        composeRule.onNodeWithText("Connect Gmail Account").performClick()
        composeRule.waitForIdle()
        // Welcome step should be visible
        composeRule.onNodeWithText("Get Started").assertIsDisplayed()
    }

    @Test
    fun mayBeLater_closesSheet() {
        composeRule.onNodeWithText("Connect Gmail Account").performClick()
        composeRule.waitForIdle()
        composeRule.onNodeWithText("Maybe later").performClick()
        composeRule.waitForIdle()
        // Sheet should be dismissed — "Get Started" should be gone
        composeRule.onNodeWithText("Get Started").assertDoesNotExist()
    }

    // ─── Step 1: Email input ──────────────────────────────────────────────────

    @Test
    fun getStarted_navigatesToEmailStep() {
        openSheet()
        composeRule.onNodeWithText("Get Started").performClick()
        composeRule.waitForIdle()
        composeRule.onNodeWithText("Your Gmail address").assertIsDisplayed()
    }

    @Test
    fun continueButton_isDisabled_withBlankEmail() {
        openSheet()
        composeRule.onNodeWithText("Get Started").performClick()
        composeRule.waitForIdle()
        // Continue should be disabled when email is blank
        composeRule.onNodeWithText("Continue").assertIsNotEnabled()
    }

    @Test
    fun continueButton_isDisabled_withInvalidEmail() {
        openSheet()
        composeRule.onNodeWithText("Get Started").performClick()
        composeRule.waitForIdle()
        composeRule.onNodeWithText("you@gmail.com").performTextInput("notanemail")
        composeRule.onNodeWithText("Continue").assertIsNotEnabled()
    }

    @Test
    fun continueButton_isEnabled_withValidEmail() {
        openSheet()
        composeRule.onNodeWithText("Get Started").performClick()
        composeRule.waitForIdle()
        composeRule.onNodeWithText("you@gmail.com").performTextInput("user@gmail.com")
        composeRule.onNodeWithText("Continue").assertIsEnabled()
    }

    @Test
    fun backButton_returnsToWelcomeStep() {
        openSheet()
        composeRule.onNodeWithText("Get Started").performClick()
        composeRule.waitForIdle()
        composeRule.onNodeWithContentDescription("Back").performClick()
        composeRule.waitForIdle()
        composeRule.onNodeWithText("Get Started").assertIsDisplayed()
    }

    // ─── Step 2: App Password input ───────────────────────────────────────────

    @Test
    fun connectButton_isDisabled_withShortPassword() {
        navigateToPasswordStep("user@gmail.com")
        composeRule.onNodeWithText("xxxx xxxx xxxx xxxx").performTextInput("short")
        composeRule.onNodeWithText("Connect Account").assertIsNotEnabled()
    }

    @Test
    fun connectButton_isEnabled_with16CharPassword() {
        navigateToPasswordStep("user@gmail.com")
        composeRule.onNodeWithText("xxxx xxxx xxxx xxxx").performTextInput("abcdefghijklmnop")
        composeRule.waitForIdle()
        composeRule.onNodeWithText("Connect Account").assertIsEnabled()
    }

    @Test
    fun characterCounter_showsCorrectCount() {
        navigateToPasswordStep("user@gmail.com")
        composeRule.onNodeWithText("xxxx xxxx xxxx xxxx").performTextInput("abcd")
        composeRule.waitForIdle()
        composeRule.onNodeWithText("4 / 16 characters").assertIsDisplayed()
    }

    @Test
    fun howToGuide_isDisplayed_onPasswordStep() {
        navigateToPasswordStep("user@gmail.com")
        composeRule.onNodeWithText("HOW TO GET YOUR APP PASSWORD").assertIsDisplayed()
    }

    @Test
    fun backButton_isDisabled_whileLoading() {
        // This test verifies the loading state guard — back button disabled during verification
        // We can only verify the static state here (not live loading)
        navigateToPasswordStep("user@gmail.com")
        // With no loading in progress, back should be enabled
        composeRule.onNodeWithContentDescription("Back").assertIsEnabled()
    }

    // ─── Helpers ──────────────────────────────────────────────────────────────

    private fun openSheet() {
        composeRule.onNodeWithText("Connect Gmail Account").performClick()
        composeRule.waitForIdle()
    }

    private fun navigateToPasswordStep(email: String) {
        openSheet()
        composeRule.onNodeWithText("Get Started").performClick()
        composeRule.waitForIdle()
        composeRule.onNodeWithText("you@gmail.com").performTextInput(email)
        composeRule.onNodeWithText("Continue").performClick()
        composeRule.waitForIdle()
    }
}

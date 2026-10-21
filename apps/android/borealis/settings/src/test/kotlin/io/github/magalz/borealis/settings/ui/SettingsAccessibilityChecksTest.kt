package io.github.magalz.borealis.settings.ui

import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.test.ext.junit.runners.AndroidJUnit4
import io.github.magalz.borealis.designsystem.background.BackgroundStyle
import io.github.magalz.borealis.designsystem.testing.NoTouchOnTv
import io.github.magalz.borealis.designsystem.testing.assertAccessible
import io.github.magalz.borealis.designsystem.theme.FormFactor
import io.github.magalz.borealis.settings.testing.InGlassHost
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config

/**
 * Google's Accessibility Test Framework over the Settings screen on a TV, a handheld and a phone,
 * with the choice sheet and the licences dialog over it. The glass is off here
 * (`BackgroundStyle.Off`), so the contrast checks see the colours that the text really has; over
 * the animated background the contrast is measured by the tool in `design/accessibility/`, which
 * docs/ux/accessibility.md describes.
 */
@RunWith(AndroidJUnit4::class)
class SettingsAccessibilityChecksTest : SettingsScreenTestBase() {
    private fun showSettings(formFactor: FormFactor, group: SettingsGroupId) {
        if (formFactor == FormFactor.Tv) asTelevision()
        show(keyboard = formFactor != FormFactor.Phone, initialGroup = group) { content ->
            InGlassHost(formFactor, style = BackgroundStyle.Off, content = content)
        }
    }

    private fun openSection(title: String) {
        composeRule.onAllNodesWithText(title)[0].performScrollTo().performClick()
        composeRule.waitForIdle()
    }

    @Test
    @Config(qualifiers = TV)
    fun `Stream settings on a TV`() {
        showSettings(FormFactor.Tv, SettingsGroupId.STREAM)

        composeRule.assertAccessible(NoTouchOnTv)
    }

    @Test
    @Config(qualifiers = TV)
    fun `Appearance settings on a TV`() {
        showSettings(FormFactor.Tv, SettingsGroupId.APPEARANCE)

        composeRule.assertAccessible(NoTouchOnTv)
    }

    @Test
    @Config(qualifiers = TV)
    fun `Accessibility settings on a TV`() {
        showSettings(FormFactor.Tv, SettingsGroupId.ACCESSIBILITY)

        composeRule.assertAccessible(NoTouchOnTv)
    }

    @Test
    @Config(qualifiers = TV)
    fun `About on a TV`() {
        showSettings(FormFactor.Tv, SettingsGroupId.ABOUT)

        composeRule.assertAccessible(NoTouchOnTv)
    }

    @Test
    @Config(qualifiers = HANDHELD)
    fun `Stream settings on a handheld`() {
        showSettings(FormFactor.Handheld, SettingsGroupId.STREAM)

        composeRule.assertAccessible()
    }

    @Test
    @Config(qualifiers = HANDHELD)
    fun `Accessibility settings on a handheld`() {
        showSettings(FormFactor.Handheld, SettingsGroupId.ACCESSIBILITY)

        composeRule.assertAccessible()
    }

    @Test
    @Config(qualifiers = PHONE)
    fun `the sections of the settings on a phone`() {
        showSettings(FormFactor.Phone, SettingsGroupId.STREAM)
        listOf("STREAM", "APPEARANCE", "ACCESSIBILITY", "ABOUT").forEach(::openSection)

        composeRule.assertAccessible()
    }

    @Test
    @Config(qualifiers = TV)
    fun `a choice sheet on a TV`() {
        showSettings(FormFactor.Tv, SettingsGroupId.STREAM)
        composeRule.onNodeWithText("Frame pacing").performClick()
        composeRule.waitForIdle()

        composeRule.assertAccessible(NoTouchOnTv)
    }

    @Test
    @Config(qualifiers = PHONE)
    fun `a choice sheet on a phone`() {
        showSettings(FormFactor.Phone, SettingsGroupId.STREAM)
        openSection("STREAM")
        composeRule.onNodeWithText("Frame pacing").performScrollTo().performClick()
        composeRule.waitForIdle()

        composeRule.assertAccessible()
    }

    @Test
    @Config(qualifiers = PHONE)
    fun `the licences dialog on a phone`() {
        showSettings(FormFactor.Phone, SettingsGroupId.STREAM)
        openSection("ABOUT")
        composeRule.onNodeWithText("Open-source licences").performScrollTo().performClick()
        composeRule.waitForIdle()

        composeRule.assertAccessible()
    }
}

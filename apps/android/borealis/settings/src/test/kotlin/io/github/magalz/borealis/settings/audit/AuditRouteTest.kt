package io.github.magalz.borealis.settings.audit

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.test.espresso.Espresso
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.google.common.truth.Truth.assertThat
import io.github.magalz.borealis.logging.AuditType
import io.github.magalz.borealis.logging.BorealisAudit
import io.github.magalz.borealis.logging.Redactor
import io.github.magalz.borealis.settings.catalog.Settings
import io.github.magalz.borealis.settings.testing.FakeSettingsRepository
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import org.junit.After
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config

/** The route with the real ViewModel over a fake trail: the screen, masking and Back together. */
@RunWith(AndroidJUnit4::class)
@Config(qualifiers = PHONE)
class AuditRouteTest {
    @get:Rule val composeRule = createComposeRule()

    private val trail = FakeAuditTrail()
    private val settings = FakeSettingsRepository()
    private val redactor = Redactor(maskAddresses = false)
    private var backs = 0

    @Before
    fun installRedactor() {
        BorealisAudit.install({}, redactor, "1.0.0")
    }

    @After fun uninstallRedactor() = BorealisAudit.uninstall()

    @OptIn(ExperimentalCoroutinesApi::class)
    private fun show() {
        val viewModel =
            AuditViewModel(trail, settings, fixedFormatter(), UnconfinedTestDispatcher())
        composeRule.setContent { AuditRoute(onBack = { backs++ }, viewModel = viewModel) }
        composeRule.waitForIdle()
    }

    private fun switchMasking(on: Boolean) {
        redactor.maskAddresses = on
        runBlocking { settings.set(Settings.MASK_ADDRESSES, on) }
        composeRule.waitForIdle()
    }

    @Test
    fun `the entries of the trail show newest first`() {
        trail.entries.value =
            listOf(
                entry(AuditType.SESSION_END, "2026-10-16T20:42:00Z"),
                entry(AuditType.SESSION_START, "2026-10-16T20:34:00Z"),
            )
        show()

        composeRule.onNodeWithTag("audit:entry:0").assertIsDisplayed()
        composeRule.onNodeWithText("Stream ended").assertIsDisplayed()
        composeRule.onNodeWithText("SESSION_START").assertIsDisplayed()
    }

    @Test
    fun `an empty trail says so`() {
        show()

        composeRule.onNodeWithText("No events recorded yet.").assertIsDisplayed()
    }

    @Test
    fun `an entry that is recorded while the screen is open appears`() {
        show()

        trail.entries.value = listOf(entry(AuditType.LAUNCH_ATTEMPT))
        composeRule.waitForIdle()

        composeRule.onNodeWithText("App started").assertIsDisplayed()
        composeRule.onNodeWithText("No events recorded yet.").assertDoesNotExist()
    }

    @Test
    fun `switching Mask addresses re-masks the list without a change of the trail`() {
        trail.entries.value = listOf(entry(host = "Desk PC (192.168.1.20)", detail = null))
        show()
        composeRule.onNodeWithText("Desk PC (192.168.1.20)").assertIsDisplayed()

        switchMasking(true)

        composeRule.onNodeWithText("Desk PC (192.168.1.20)").assertDoesNotExist()
        composeRule.onNodeWithText("[HOST]").assertIsDisplayed()
    }

    @Test
    fun `a tap opens the entry and Back closes it before it leaves`() {
        trail.entries.value = listOf(entry(AuditType.PAIRING_FAILURE, host = "Studio"))
        show()

        composeRule.onNodeWithTag("audit:entry:0").performClick()
        composeRule.waitForIdle()
        composeRule.onNodeWithText("Result").assertIsDisplayed()

        Espresso.pressBack()
        composeRule.waitForIdle()
        composeRule.onNodeWithText("Result").assertDoesNotExist()
        assertThat(backs).isEqualTo(0)

        Espresso.pressBack()
        composeRule.waitForIdle()
        assertThat(backs).isEqualTo(1)
    }
}

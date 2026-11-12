package io.github.magalz.borealis.stream.ui.notice

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshots.Snapshot
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.assert
import androidx.compose.ui.test.assertHasNoClickAction
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.getBoundsInRoot
import androidx.compose.ui.test.isNotFocusable
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.google.common.truth.Truth.assertThat
import io.github.magalz.borealis.designsystem.testing.PHONE_LANDSCAPE
import io.github.magalz.borealis.designsystem.theme.BorealisLook
import io.github.magalz.borealis.designsystem.theme.FormFactor
import io.github.magalz.borealis.stream.ChromeMode
import io.github.magalz.borealis.stream.Panel
import io.github.magalz.borealis.stream.StreamNotice
import io.github.magalz.borealis.stream.StreamNotice.BluetoothOutput
import io.github.magalz.borealis.stream.StreamNotice.NativeTouchRefused
import io.github.magalz.borealis.stream.StreamNotice.StereoFallback
import io.github.magalz.borealis.stream.StreamSession
import io.github.magalz.borealis.stream.SurroundLayout
import io.github.magalz.borealis.stream.testing.RecordingIntents
import io.github.magalz.borealis.stream.testing.aSession
import io.github.magalz.borealis.stream.testing.aSessionInfo
import io.github.magalz.borealis.stream.testing.setChrome
import io.github.magalz.borealis.stream.ui.StreamChromeRoot
import io.github.magalz.borealis.stream.ui.StreamingLayer
import io.github.magalz.borealis.stream.ui.aStreaming
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config

/**
 * The card that tells the viewer a notice over the stream (the audio notices of Sprint 17a): one
 * line for four seconds at the top of the screen, which takes no focus and no input. The clock is
 * paused, so a test says how much time goes by.
 */
@RunWith(AndroidJUnit4::class)
@Config(qualifiers = PHONE_LANDSCAPE)
class StreamNoticeCardTest {
    @get:Rule val composeRule = createComposeRule()

    private val bluetooth = "Audio is on Bluetooth: sound may lag behind the picture."
    private val stereo71 = "This output has 2 channels: streaming stereo instead of 7.1."
    private val stereo51 = "This output has 2 channels: streaming stereo instead of 5.1."
    private val nativeTouchRefused = "This PC does not take touch; using Direct"

    // The streaming layer as the chrome draws it, with the notice that the test gives it.
    private var notice by mutableStateOf<StreamNotice?>(null)
    private var phase by mutableStateOf(aStreaming())
    private val shown = mutableListOf<StreamNotice>()

    private fun showLayer() {
        composeRule.mainClock.autoAdvance = false
        composeRule.setChrome(FormFactor.Phone) {
            StreamingLayer(
                "Desktop",
                "Gaming PC",
                phase,
                null,
                RecordingIntents(),
                notice = notice,
                onNoticeShown = { shown += it },
            )
        }
    }

    /** A write that the paused clock has to be told about, as in the test of the hint. */
    private fun change(write: () -> Unit) {
        composeRule.runOnUiThread {
            write()
            Snapshot.sendApplyNotifications()
        }
        composeRule.mainClock.advanceTimeByFrame()
    }

    private fun card() = composeRule.onNodeWithTag(NOTICE_CARD_TAG)

    // What the card says

    @Test
    fun `the card says that the audio is on Bluetooth`() {
        notice = BluetoothOutput

        showLayer()

        composeRule.onNodeWithText(bluetooth).assertIsDisplayed()
    }

    @Test
    fun `the card for a stereo fallback names the layout that the stream asked for`() {
        notice = StereoFallback(SurroundLayout.SURROUND_7_1)
        showLayer()
        composeRule.onNodeWithText(stereo71).assertIsDisplayed()

        change { notice = StereoFallback(SurroundLayout.SURROUND_5_1) }

        composeRule.onNodeWithText(stereo51).assertIsDisplayed()
        composeRule.onNodeWithText(stereo71).assertDoesNotExist()
    }

    @Test
    fun `the card says that the PC takes no touch and that the stream uses Direct`() {
        notice = NativeTouchRefused

        showLayer()

        composeRule.onNodeWithText(nativeTouchRefused).assertIsDisplayed()
    }

    @Test
    fun `there is no card while there is no notice`() {
        showLayer()

        card().assertDoesNotExist()
    }

    // Its time

    @Test
    fun `the card goes after four seconds and says that it was shown, once`() {
        notice = BluetoothOutput
        showLayer()

        composeRule.mainClock.advanceTimeBy(3_900)
        card().assertIsDisplayed()
        assertThat(shown).isEmpty()

        composeRule.mainClock.advanceTimeBy(200)
        // What the session does when it is told: the notice is gone.
        change { notice = null }

        card().assertDoesNotExist()
        assertThat(shown).containsExactly(BluetoothOutput)
    }

    @Test
    fun `the next notice has four seconds of its own`() {
        notice = BluetoothOutput
        showLayer()
        composeRule.mainClock.advanceTimeBy(3_000)

        change { notice = StereoFallback(SurroundLayout.SURROUND_7_1) }
        composeRule.mainClock.advanceTimeBy(3_900)

        composeRule.onNodeWithText(stereo71).assertIsDisplayed()
        assertThat(shown).isEmpty()
    }

    @Test
    fun `the card waits while a panel is open, and its four seconds start when it closes`() {
        phase = aStreaming(Panel.MENU)
        notice = BluetoothOutput
        showLayer()

        composeRule.mainClock.advanceTimeBy(10_000)
        card().assertDoesNotExist()
        assertThat(shown).isEmpty()

        change { phase = aStreaming() }
        composeRule.mainClock.advanceTimeBy(3_900)

        card().assertIsDisplayed()
    }

    // Where it is, and what it takes

    @Test
    fun `the card sits under the hint, not over it`() {
        phase = aStreaming().copy(showMenuHint = true)
        notice = BluetoothOutput
        showLayer()

        val hint = composeRule.onNodeWithText("Pull the handle down or press Back for the menu")
        assertThat(card().getBoundsInRoot().top).isAtLeast(hint.getBoundsInRoot().bottom)
    }

    @Test
    fun `the card takes no focus and no touch`() {
        notice = BluetoothOutput
        showLayer()

        card().assert(isNotFocusable())
        card().assertHasNoClickAction()
    }

    @Test
    fun `the card is announced politely`() {
        notice = BluetoothOutput
        showLayer()

        card()
            .assert(
                SemanticsMatcher.expectValue(SemanticsProperties.LiveRegion, LiveRegionMode.Polite)
            )
    }

    // As the chrome draws it, from the session

    private fun streamingSession(): StreamSession =
        aSession().also {
            it.onConnectionStarted(aSessionInfo)
            it.onFirstFrame()
        }

    private fun showChrome(session: StreamSession) {
        composeRule.mainClock.autoAdvance = false
        composeRule.setContent { StreamChromeRoot(session, {}, BorealisLook()) }
        settle()
    }

    /** The paused clock runs a few frames, which what the session says needs to reach the card. */
    private fun settle() = composeRule.mainClock.advanceTimeBy(SETTLE_MS)

    @Test
    fun `the chrome shows the notices of the session one at a time, each for four seconds`() {
        val session = streamingSession()
        showChrome(session)

        composeRule.runOnUiThread {
            session.notify(BluetoothOutput)
            session.notify(StereoFallback(SurroundLayout.SURROUND_7_1))
        }
        settle()
        composeRule.onNodeWithText(bluetooth).assertIsDisplayed()
        composeRule.onNodeWithText(stereo71).assertDoesNotExist()

        composeRule.mainClock.advanceTimeBy(4_300)
        composeRule.onNodeWithText(bluetooth).assertDoesNotExist()
        composeRule.onNodeWithText(stereo71).assertIsDisplayed()

        composeRule.mainClock.advanceTimeBy(4_300)
        card().assertDoesNotExist()
        assertThat(session.notice.value).isNull()
    }

    @Test
    fun `a notice given before the first frame is shown once the stream is on`() {
        val session = aSession()
        showChrome(session)
        composeRule.runOnUiThread { session.notify(BluetoothOutput) }
        composeRule.mainClock.advanceTimeBy(10_000)
        card().assertDoesNotExist()

        composeRule.runOnUiThread {
            session.onConnectionStarted(aSessionInfo)
            session.onFirstFrame()
        }
        settle()

        composeRule.onNodeWithText(bluetooth).assertIsDisplayed()
    }

    @Test
    fun `the card leaves the input where it was`() {
        val session = streamingSession()
        showChrome(session)

        composeRule.runOnUiThread { session.notify(BluetoothOutput) }
        settle()

        card().assertIsDisplayed()
        assertThat(session.chromeMode).isEqualTo(ChromeMode.IDLE)
    }

    private companion object {
        const val SETTLE_MS = 50L
    }
}

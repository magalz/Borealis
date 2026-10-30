package io.github.magalz.borealis.stream.ui

import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.toPixelMap
import androidx.compose.ui.test.captureToImage
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onRoot
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.google.common.truth.Truth.assertWithMessage
import io.github.magalz.borealis.designsystem.theme.BorealisLook
import io.github.magalz.borealis.designsystem.theme.BorealisTheme
import io.github.magalz.borealis.designsystem.theme.FormFactor
import io.github.magalz.borealis.designsystem.theme.ReduceMotion
import io.github.magalz.borealis.stream.testing.PHONE
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config

/**
 * The indicator of the Quitting and Disconnecting screens follows Reduce motion (ADR 0057): with it
 * on, the indicator is drawn the same at any time, and with it off, it moves.
 */
@RunWith(AndroidJUnit4::class)
@Config(qualifiers = PHONE)
class LeavingIndicatorTest {
    @get:Rule val composeRule = createComposeRule()

    private fun moves(motion: ReduceMotion, screen: @Composable () -> Unit): Boolean {
        composeRule.mainClock.autoAdvance = false
        composeRule.setContent {
            BorealisTheme(FormFactor.Phone, BorealisLook(reduceMotion = motion)) { screen() }
        }
        val before = composeRule.onRoot().captureToImage().toPixelMap()
        composeRule.mainClock.advanceTimeBy(MOVED_MS)
        val after = composeRule.onRoot().captureToImage().toPixelMap()
        return !before.buffer.contentEquals(after.buffer)
    }

    @Test
    fun `with Reduce motion on the Quitting indicator stands still`() {
        assertWithMessage("the indicator moved")
            .that(moves(ReduceMotion.On) { QuittingScreen("Desktop") })
            .isFalse()
    }

    @Test
    fun `with Reduce motion on the Disconnecting indicator stands still`() {
        assertWithMessage("the indicator moved")
            .that(moves(ReduceMotion.On) { DisconnectingScreen("Desktop") })
            .isFalse()
    }

    @Test
    fun `with Reduce motion off the Quitting indicator moves`() {
        assertWithMessage("the indicator did not move")
            .that(moves(ReduceMotion.Off) { QuittingScreen("Desktop") })
            .isTrue()
    }

    @Test
    fun `with Reduce motion off the Disconnecting indicator moves`() {
        assertWithMessage("the indicator did not move")
            .that(moves(ReduceMotion.Off) { DisconnectingScreen("Desktop") })
            .isTrue()
    }

    private companion object {
        const val MOVED_MS = 700L
    }
}

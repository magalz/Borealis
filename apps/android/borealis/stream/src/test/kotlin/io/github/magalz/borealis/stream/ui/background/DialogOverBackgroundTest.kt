package io.github.magalz.borealis.stream.ui.background

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.PixelMap
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.google.common.truth.Truth.assertThat
import com.google.common.truth.Truth.assertWithMessage
import io.github.magalz.borealis.designsystem.testing.frame
import io.github.magalz.borealis.designsystem.theme.BorealisColors
import io.github.magalz.borealis.designsystem.theme.FormFactor
import io.github.magalz.borealis.stream.StreamPhase
import io.github.magalz.borealis.stream.StreamVisuals
import io.github.magalz.borealis.stream.testing.PHONE_LANDSCAPE
import io.github.magalz.borealis.stream.testing.RecordingIntents
import io.github.magalz.borealis.stream.testing.aFailure
import io.github.magalz.borealis.stream.testing.setChrome
import io.github.magalz.borealis.stream.ui.CONFLICT_PANEL_TAG
import io.github.magalz.borealis.stream.ui.ConflictDialog
import io.github.magalz.borealis.stream.ui.ConnectionErrorScreen
import io.github.magalz.borealis.stream.ui.ERROR_PANEL_TAG
import io.github.magalz.borealis.stream.ui.glass.leftPixel
import kotlin.math.abs
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config

/**
 * A dialog of the screens outside the game over their background (ADR 0061, decision 5; ADR 0057,
 * decision 8): while it is open the background is blurred and held on its still frame, the dialog
 * draws no scrim, and its panel takes the glass tint. Where the host cannot blur (API 30) the scrim
 * and the solid panel stay. The two dialogs are the question about a running app and the failure
 * popup, which are made from the same dialog.
 */
@RunWith(AndroidJUnit4::class)
@Config(qualifiers = PHONE_LANDSCAPE)
class DialogOverBackgroundTest {
    @get:Rule val composeRule = createComposeRule()

    private val surface = BorealisColors().surface
    private val page = BorealisColors().page
    private var open by mutableStateOf(false)
    private var dialog: @Composable () -> Unit = { Question() }

    /** The background of the stream's screens, moving, or held on one frame by the test. */
    private fun show(moving: Boolean = false) {
        composeRule.setChrome(FormFactor.Phone) {
            if (moving) {
                OutsideGameBackground(StreamVisuals(), decoderHeld = false) {
                    if (open) dialog()
                }
            } else {
                OnStillBackground { if (open) dialog() }
            }
        }
        composeRule.waitForIdle()
    }

    @Composable
    private fun Question() =
        ConflictDialog(
            StreamPhase.Conflict("Steam", "Desktop", sameApp = false),
            RecordingIntents(),
        )

    @Composable
    private fun Error() = ConnectionErrorScreen("Desktop", "Desk PC", aFailure, RecordingIntents())

    private fun picture(): PixelMap = composeRule.frame(OUTSIDE_GAME_BACKGROUND_TAG)

    private fun openDialog() {
        composeRule.runOnIdle { open = true }
        composeRule.waitForIdle()
    }

    private fun Color.distanceFrom(other: Color) =
        abs(red - other.red) + abs(green - other.green) + abs(blue - other.blue)

    /** Where the background is farthest from the page colour, in the band over the dialog. */
    private fun brightestSpot(picture: PixelMap): Pair<Int, Int> =
        (0 until picture.width)
            .flatMap { x -> (0 until picture.height / BAND).map { y -> x to y } }
            .maxBy { (x, y) -> picture[x, y].distanceFrom(page) }

    private fun assertNoScrim() {
        show()
        val before = picture()
        val (x, y) = brightestSpot(before)
        assertThat(before[x, y].distanceFrom(page)).isGreaterThan(MIN_GLOW)

        openDialog()

        val shown = picture()[x, y].distanceFrom(page) / before[x, y].distanceFrom(page)
        assertWithMessage("how much of the background is left").that(shown).isGreaterThan(KEPT)
    }

    private fun assertTinted(panelTag: String) {
        show()
        openDialog()

        val panel = composeRule.onNodeWithTag(panelTag).leftPixel()

        assertThat(panel.distanceFrom(surface)).isGreaterThan(TINTED)
    }

    private fun assertFrozen() {
        composeRule.mainClock.autoAdvance = false
        show(moving = true)
        assertThat(moves()).isTrue()

        composeRule.runOnUiThread { open = true }
        composeRule.mainClock.advanceTimeBy(SETTLE_MS)

        assertThat(moves()).isFalse()
    }

    @Test
    fun `no scrim dims the background where the question about a running app is over it`() =
        assertNoScrim()

    @Test
    fun `no scrim dims the background where the error popup is over it`() {
        dialog = { Error() }

        assertNoScrim()
    }

    @Test
    fun `the panel of the question is the glass tint over the blurred background`() =
        assertTinted(CONFLICT_PANEL_TAG)

    @Test
    fun `the panel of the error popup is the glass tint over the blurred background`() {
        dialog = { Error() }

        assertTinted(ERROR_PANEL_TAG)
    }

    @Test
    fun `the background moves with no dialog, and is held still while the question is open`() =
        assertFrozen()

    @Test
    fun `the background moves with no dialog, and is held still while the error popup is open`() {
        dialog = { Error() }

        assertFrozen()
    }

    /** Whether the picture of the background changes over a few seconds of animation time. */
    private fun moves(): Boolean {
        composeRule.mainClock.advanceTimeBy(1_000)
        val first = picture()
        composeRule.mainClock.advanceTimeBy(5_000)
        return !first.buffer.contentEquals(picture().buffer)
    }

    private companion object {
        const val BAND = 10
        const val MIN_GLOW = 0.05f
        const val KEPT = 0.6f
        const val TINTED = 0.01f
        const val SETTLE_MS = 500L
    }
}

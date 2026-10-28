package io.github.magalz.borealis.designsystem.components

import androidx.compose.foundation.background
import androidx.compose.foundation.focusable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.size
import androidx.compose.material3.SnackbarDuration
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.SnackbarResult
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toPixelMap
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.assert
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.captureToImage
import androidx.compose.ui.test.getUnclippedBoundsInRoot
import androidx.compose.ui.test.hasAnyDescendant
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.isFocusable
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.performClick
import androidx.compose.ui.unit.dp
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.google.common.truth.Truth.assertThat
import io.github.magalz.borealis.designsystem.PIXELS_PER_DP
import io.github.magalz.borealis.designsystem.testing.setContentInKeyboardMode
import io.github.magalz.borealis.designsystem.testing.textLayout
import io.github.magalz.borealis.designsystem.theme.BorealisTheme
import io.github.magalz.borealis.designsystem.theme.FormFactor
import io.github.magalz.borealis.designsystem.theme.LocalBorealisColors
import kotlin.math.abs
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config

@RunWith(AndroidJUnit4::class)
class BorealisSnackbarHostTest {
    @get:Rule val composeRule = createComposeRule()
    private val state = SnackbarHostState()
    private var result: SnackbarResult? = null
    private var surface = Color.Unspecified

    @Test
    @Config(qualifiers = PHONE)
    fun `a short message makes a snackbar as wide as its text`() {
        composeRule.setContent { Specimen(FormFactor.Phone, SHORT) }

        val snackbar = drawn(SHORT)
        val text = composeRule.onNodeWithText(SHORT).getUnclippedBoundsInRoot()

        assertThat(snackbar.width).isAtLeast(text.right.value - text.left.value)
        // Only the padding of the snackbar is wider than the text: nothing like the window.
        assertThat(snackbar.width).isAtMost(text.right.value - text.left.value + MAX_PADDING)
    }

    @Test
    @Config(qualifiers = TV)
    fun `the snackbar is centred in the width the host is given`() {
        composeRule.setContent { Specimen(FormFactor.Tv, SHORT) }

        val snackbar = drawn(SHORT)

        assertThat(snackbar.start).isWithin(1f).of(WINDOW_TV - snackbar.end)
    }

    @Test
    @Config(qualifiers = TV)
    fun `a long message is held at 600dp on a TV`() {
        composeRule.setContent { Specimen(FormFactor.Tv, LONG) }

        val snackbar = drawn(LONG)

        assertThat(snackbar.width).isWithin(1f).of(600f)
        assertThat(snackbar.start).isWithin(1f).of(WINDOW_TV - snackbar.end)
        assertThat(lineCount(LONG)).isGreaterThan(1)
    }

    @Test
    @Config(qualifiers = PHONE, fontScale = 2f)
    fun `at twice the font size a long message wraps inside the window`() {
        composeRule.setContent { Specimen(FormFactor.Phone, LONG) }

        val snackbar = drawn(LONG)
        val text = composeRule.onNodeWithText(LONG).getUnclippedBoundsInRoot()

        assertThat(lineCount(LONG)).isGreaterThan(2)
        assertThat(snackbar.start).isAtLeast(0f)
        assertThat(snackbar.end).isAtMost(WINDOW_PHONE)
        assertThat(text.left.value).isAtLeast(snackbar.start)
        assertThat(text.right.value).isAtMost(snackbar.end)
    }

    @Test
    @Config(qualifiers = TV)
    fun `a snackbar without an action takes no focus on a TV`() {
        composeRule.setContentInKeyboardMode { Specimen(FormFactor.Tv, SHORT) }

        // The probe is the only thing to take the focus.
        composeRule.onAllNodes(isFocusable()).assertCountEquals(1)
    }

    @Test
    @Config(qualifiers = TV)
    fun `the action of a snackbar takes the focus on a TV`() {
        composeRule.setContentInKeyboardMode {
            Specimen(FormFactor.Tv, SHORT, actionLabel = "Undo")
        }

        composeRule.onAllNodes(isFocusable()).assertCountEquals(2)
        composeRule.onNodeWithText("Undo").assert(isFocusable())
    }

    @Test
    @Config(qualifiers = PHONE)
    fun `a screen reader is told of a snackbar that shows`() {
        composeRule.setContent { Specimen(FormFactor.Phone, SHORT) }

        // Material's host announces the snackbar politely; the Borealis one draws its own content
        // inside that host and must keep it.
        composeRule
            .onNode(
                SemanticsMatcher.expectValue(
                    SemanticsProperties.LiveRegion,
                    LiveRegionMode.Polite,
                ) and hasAnyDescendant(hasText(SHORT))
            )
            .assertExists()
    }

    @Test
    @Config(qualifiers = PHONE)
    fun `an action still runs and answers the call that showed the snackbar`() {
        composeRule.setContent { Specimen(FormFactor.Phone, SHORT, actionLabel = "Undo") }

        composeRule.onNodeWithText("Undo").performClick()
        composeRule.waitForIdle()

        assertThat(result).isEqualTo(SnackbarResult.ActionPerformed)
    }

    @Composable
    private fun Specimen(formFactor: FormFactor, message: String, actionLabel: String? = null) {
        BorealisTheme(formFactor) {
            surface = LocalBorealisColors.current.ink
            LaunchedEffect(message) {
                result =
                    state.showSnackbar(message, actionLabel, duration = SnackbarDuration.Indefinite)
            }
            // On the page, so the shadow of the snackbar is no colour of it. At the bottom start,
            // so a host that does not centre its snackbar leaves it there.
            Box(
                Modifier.fillMaxSize().background(LocalBorealisColors.current.page),
                contentAlignment = Alignment.BottomStart,
            ) {
                // Something that can take the focus, so the window can enter the keyboard mode.
                Box(Modifier.size(1.dp).align(Alignment.TopStart).focusable())
                BorealisSnackbarHost(state)
            }
        }
    }

    /** Where the snackbar is drawn, in dp. */
    private class Extent(val start: Float, val end: Float) {
        val width = end - start
    }

    /**
     * The snackbar as the user sees it: the run of its colour across the line of [message], from
     * the first pixel of that colour to the last. It does not depend on how the snackbar is built
     * and the margins it keeps around itself are no part of it.
     */
    private fun drawn(message: String): Extent {
        val text = composeRule.onNodeWithText(message).getUnclippedBoundsInRoot()
        val row = (((text.top + text.bottom) / 2).value * PIXELS_PER_DP).toInt()
        val pixels = composeRule.onRoot().captureToImage().toPixelMap()
        val columns = (0 until pixels.width).filter { pixels[it, row].isNear(surface) }
        assertThat(columns).isNotEmpty()
        return Extent(
            columns.first().toFloat() / PIXELS_PER_DP,
            (columns.last() + 1).toFloat() / PIXELS_PER_DP,
        )
    }

    private fun Color.isNear(other: Color) =
        abs(red - other.red) + abs(green - other.green) + abs(blue - other.blue) < TOLERANCE

    private fun lineCount(message: String): Int =
        composeRule.onNodeWithText(message).textLayout().lineCount

    private companion object {
        const val PHONE = "w360dp-h640dp-xhdpi"
        const val TV = "w960dp-h540dp-land-television-xhdpi"
        const val WINDOW_PHONE = 360f
        const val WINDOW_TV = 960f
        const val TOLERANCE = 0.02f
        const val SHORT = "App quit."
        const val LONG =
            "This session wasn't started by this device, so it cannot be quit. End streaming on " +
                "the original device or the PC itself. (Error code: 599) Then try again from " +
                "the Apps screen of the host, where the running app shows."

        /** The side padding of a snackbar, with room to spare, and no more. */
        val MAX_PADDING = 48.dp.value
    }
}

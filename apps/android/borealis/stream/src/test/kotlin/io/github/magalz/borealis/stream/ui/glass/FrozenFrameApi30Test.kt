package io.github.magalz.borealis.stream.ui.glass

import android.app.Application
import android.os.Build
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.compositeOver
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.unit.IntOffset
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.google.common.truth.Truth.assertThat
import io.github.magalz.borealis.designsystem.testing.rootFrame
import io.github.magalz.borealis.designsystem.theme.BorealisColors
import io.github.magalz.borealis.designsystem.theme.BorealisLook
import io.github.magalz.borealis.stream.BackgroundMode
import io.github.magalz.borealis.stream.StreamSession
import io.github.magalz.borealis.stream.StreamVisuals
import io.github.magalz.borealis.stream.testing.PHONE_LANDSCAPE
import io.github.magalz.borealis.stream.testing.RecordingActions
import io.github.magalz.borealis.stream.testing.aDefaults
import io.github.magalz.borealis.stream.testing.aFailure
import io.github.magalz.borealis.stream.testing.aSession
import io.github.magalz.borealis.stream.ui.ERROR_PANEL_TAG
import io.github.magalz.borealis.stream.ui.StreamChromeRoot
import kotlin.math.abs
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config

/**
 * Below API 31 nothing blurs: the error popup shows the last frame sharp, under the scrim, and its
 * panel is the solid surface, as the owner decided on 2026-10-29. The scrim is the page at 35
 * percent, as it is over the blurred frame (owner, 2026-10-30). With no frame the popup is over the
 * background and keeps the scrim of the dialogs, the page at 72 percent (ADR 0057, decision 8: on
 * API 28 to 30 the scrim stays). `FrozenFrameLookTest` has the rule; this is that the chrome asks
 * the device it runs on.
 */
@RunWith(AndroidJUnit4::class)
@Config(sdk = [Build.VERSION_CODES.R], qualifiers = PHONE_LANDSCAPE)
class FrozenFrameApi30Test {
    @get:Rule val composeRule = createComposeRule()

    private val source = FakeVideoSource(completing = true, paint = { it.eraseColor(LIGHT_GREY) })
    private val frozen = FrozenFrame(source, { IntOffset.Zero })
    private val page = BorealisColors().page

    /** The frame the chrome is given: a test takes it away, to see what the popup draws without. */
    private var inUse: FrozenFrame? by mutableStateOf(frozen)

    private fun show(session: StreamSession = failure(), copied: Boolean = true) {
        val app = ApplicationProvider.getApplicationContext<Application>()
        // A screen with the background listens for the battery saver with a receiver that older
        // systems guard.
        shadowOf(app)
            .grantPermissions("${app.packageName}.DYNAMIC_RECEIVER_NOT_EXPORTED_PERMISSION")
        if (copied) frozen.request()
        composeRule.setContent {
            CompositionLocalProvider(LocalFrozenFrame provides inUse) {
                StreamChromeRoot(session, {}, BorealisLook())
            }
        }
        composeRule.waitForIdle()
    }

    private fun failure() = aSession().also { it.onFailed(aFailure) }

    /**
     * The same failure over a background that is held Still, so two pictures of it are the same.
     */
    private fun stillFailure() =
        aSession(
                RecordingActions(),
                aDefaults.copy(visuals = StreamVisuals(background = BackgroundMode.STILL)),
            )
            .also { it.onFailed(aFailure) }

    /**
     * With no frame the popup is over the background, and it draws what it draws with no frozen
     * frame at all: the scrim of the dialogs, the page at 72 percent, which `scrim` in the tokens
     * is and `DialogOverBackgroundApi30Test` pins for the popup over the background. The test looks
     * at the place where the background is farthest from the page, where a 35 percent scrim would
     * show against that one.
     */
    private fun assertDrawsAsWithNoFrame(copyFails: Boolean) {
        inUse = null
        show(stillFailure(), copied = false)
        val without = composeRule.rootFrame()
        val (x, y) = without.farthestFrom(page)
        assertThat(without[x, y].distanceFrom(page)).isGreaterThan(MIN_GLOW)

        composeRule.runOnIdle {
            inUse = frozen
            if (copyFails) {
                source.bounds = null
                frozen.request()
            }
        }
        composeRule.waitForIdle()

        assertThat(frozen.frame).isNull()
        assertThat(frozen.pending).isFalse()
        val shown = composeRule.rootFrame()[x, y]
        assertThat(shown.red).isWithin(TOLERANCE).of(without[x, y].red)
        assertThat(shown.green).isWithin(TOLERANCE).of(without[x, y].green)
        assertThat(shown.blue).isWithin(TOLERANCE).of(without[x, y].blue)
    }

    @Test
    fun `with no copy asked for, the popup keeps the scrim of the dialogs over the background`() =
        assertDrawsAsWithNoFrame(copyFails = false)

    @Test
    fun `with a copy that failed, the popup keeps the scrim of the dialogs over the background`() =
        assertDrawsAsWithNoFrame(copyFails = true)

    @Test
    fun `the popup stands on the last frame, sharp, under the scrim`() {
        show()

        val margin = composeRule.rootFrame()[MARGIN_X, 2]
        assertThat(margin.luminance()).isGreaterThan(FRAME_SHOWS)
        assertThat(margin.luminance()).isLessThan(Color(LIGHT_GREY).luminance())
    }

    @Test
    fun `the scrim over the sharp frame is the page at 35 percent`() {
        show()

        val expected = BorealisColors().page.copy(alpha = SCRIM).compositeOver(Color(LIGHT_GREY))
        val margin = composeRule.rootFrame()[MARGIN_X, 2]
        assertThat(margin.red).isWithin(TOLERANCE).of(expected.red)
        assertThat(margin.green).isWithin(TOLERANCE).of(expected.green)
        assertThat(margin.blue).isWithin(TOLERANCE).of(expected.blue)
    }

    @Test
    fun `the panel is the solid surface`() {
        show()

        val panel = composeRule.onNodeWithTag(ERROR_PANEL_TAG).leftPixel()
        val surface = BorealisColors().surface

        assertThat(abs(panel.red - surface.red) + abs(panel.green - surface.green))
            .isLessThan(SOLID)
        assertThat(
                frozenFrameLook(BorealisLook(), BorealisColors(), Build.VERSION_CODES.R, true).blur
            )
            .isNull()
    }

    private companion object {
        const val LIGHT_GREY = 0xFFC0C0C0.toInt()
        const val MARGIN_X = 60
        const val FRAME_SHOWS = 0.035f
        const val MIN_GLOW = 0.05f
        const val SCRIM = 0.35f
        const val TOLERANCE = 0.012f
        const val SOLID = 0.01f
    }
}

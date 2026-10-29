package io.github.magalz.borealis.stream.ui.glass

import android.app.Application
import android.os.Build
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.graphics.Color
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
import io.github.magalz.borealis.stream.testing.PHONE_LANDSCAPE
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
 * panel is the solid surface, as the owner decided on 2026-10-29. `FrozenFrameLookTest` has the
 * rule; this is that the chrome asks the device it runs on.
 */
@RunWith(AndroidJUnit4::class)
@Config(sdk = [Build.VERSION_CODES.R], qualifiers = PHONE_LANDSCAPE)
class FrozenFrameApi30Test {
    @get:Rule val composeRule = createComposeRule()

    private val source = FakeVideoSource(completing = true, paint = { it.eraseColor(LIGHT_GREY) })
    private val frozen = FrozenFrame(source, { IntOffset.Zero })

    private fun show() {
        val app = ApplicationProvider.getApplicationContext<Application>()
        // A screen with the background listens for the battery saver with a receiver that older
        // systems guard.
        shadowOf(app)
            .grantPermissions("${app.packageName}.DYNAMIC_RECEIVER_NOT_EXPORTED_PERMISSION")
        frozen.request()
        composeRule.setContent {
            CompositionLocalProvider(LocalFrozenFrame provides frozen) {
                StreamChromeRoot(aSession().also { it.onFailed(aFailure) }, {}, BorealisLook())
            }
        }
        composeRule.waitForIdle()
    }

    @Test
    fun `the popup stands on the last frame, sharp, under the scrim`() {
        show()

        val margin = composeRule.rootFrame()[MARGIN_X, 2]
        assertThat(margin.luminance()).isGreaterThan(FRAME_SHOWS)
        assertThat(margin.luminance()).isLessThan(Color(LIGHT_GREY).luminance())
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
        const val SOLID = 0.01f
    }
}

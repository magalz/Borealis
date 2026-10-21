package io.github.magalz.borealis.designsystem.glass

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.compositeOver
import androidx.compose.ui.graphics.toPixelMap
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.captureToImage
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.unit.DpOffset
import androidx.compose.ui.unit.DpSize
import androidx.compose.ui.unit.dp
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.google.common.truth.Truth.assertWithMessage
import io.github.magalz.borealis.designsystem.background.BackgroundStyle
import io.github.magalz.borealis.designsystem.pixelAt
import io.github.magalz.borealis.designsystem.theme.BorealisLook
import io.github.magalz.borealis.designsystem.theme.BorealisTheme
import io.github.magalz.borealis.designsystem.theme.FormFactor
import io.github.magalz.borealis.designsystem.theme.Transparency
import io.github.magalz.borealis.designsystem.tokens.BorealisTokens
import kotlin.math.abs
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/**
 * What every glass surface of the design system does, whatever it is: over the bright backdrop that
 * the host draws, a Standard glass is 40 percent of the surface colour, Reduced is 85 percent, and
 * Off, High contrast and the plain page are the solid colour that the surface had before the glass,
 * pixel for pixel. A subclass names one surface, with the points of it where nothing but the
 * surface is drawn. The cases are those of the subclass, so a failure names the surface.
 */
@RunWith(AndroidJUnit4::class)
abstract class GlassSurfaceCases {
    @get:Rule val composeRule = createComposeRule()

    /** The form factor that the surface is shown in; a subclass sets the window to match. */
    protected open val formFactor = FormFactor.Phone

    /** The size of the host that the surface is shown in. */
    protected open val hostSize = DpSize(360.dp, 280.dp)

    /** Where in the host, in dp from its corner, nothing but the surface is drawn. */
    protected abstract val probes: List<DpOffset>

    /**
     * The colour of the surface where the glass is solid. It is the `surface` colour unless the
     * surface had another before the glass.
     */
    protected open val solid: Color = BorealisTokens.Color.Surface

    /** What the surface draws over its own colour at a probe, such as the tint of a card. */
    protected open fun over(base: Color): Color = base

    /** The surface. It is shown at the top left of the host. */
    @Composable protected abstract fun Specimen()

    // The glass

    @Test
    fun `Standard is 40 percent of the surface over the backdrop that shows through`() {
        show(BorealisLook(), BackgroundStyle.AuroraCurtains)

        assertProbes(inHost = true) { tinted(0.4f) }
    }

    @Test
    fun `Reduced is 85 percent of the surface, which is more than Standard`() {
        show(BorealisLook(transparency = Transparency.Reduced), BackgroundStyle.AuroraCurtains)

        assertProbes(inHost = true) { tinted(0.85f) }
    }

    // The solid surface

    @Test
    fun `Off is the solid surface and the same pixels as the surface outside any host`() {
        show(BorealisLook(transparency = Transparency.Off), BackgroundStyle.AuroraCurtains)

        assertSolid()
    }

    @Test
    fun `High contrast is the solid surface and the same pixels as the surface outside any host`() {
        show(BorealisLook(highContrast = true), BackgroundStyle.AuroraCurtains)

        assertSolid()
    }

    @Test
    fun `the plain page is the solid surface and the same pixels as the surface outside any host`() {
        show(BorealisLook(), BackgroundStyle.Off)

        assertSolid()
    }

    // Helpers

    /**
     * Shows the surface twice, each in a box of [hostSize] over white: in a host of [style] under
     * [look], and outside any host, which is what the surface was before the glass.
     */
    private fun show(look: BorealisLook, style: BackgroundStyle) {
        composeRule.setContent {
            BorealisTheme(formFactor, look) {
                Column {
                    GlassHostOver(
                        style,
                        background = { Box(Modifier.fillMaxSize().background(Color.White)) },
                        modifier = Modifier.size(hostSize).testTag(IN_HOST),
                    ) {
                        Specimen()
                    }
                    Box(Modifier.size(hostSize).background(Color.White).testTag(OUTSIDE)) {
                        Specimen()
                    }
                }
            }
        }
        composeRule.waitForIdle()
    }

    private fun tinted(percent: Float): Color =
        over(BorealisTokens.Color.Surface.copy(alpha = percent).compositeOver(Color.White))

    private fun assertSolid() {
        assertProbes(inHost = true) { over(solid) }
        assertProbes(inHost = false) { over(solid) }
        val inHost = composeRule.onNodeWithTag(IN_HOST).captureToImage().toPixelMap()
        val outside = composeRule.onNodeWithTag(OUTSIDE).captureToImage().toPixelMap()
        // The anti-aliased corner of an opaque border (high contrast) differs by a few levels
        // between a layer and a plain draw, so a pixel is the same within SAME_LEVELS of 255 in a
        // channel.
        val apart =
            inHost.buffer.indices.count { i ->
                CHANNEL_SHIFTS.any {
                    abs(
                        ((inHost.buffer[i] shr it) and 0xFF) - ((outside.buffer[i] shr it) and 0xFF)
                    ) > SAME_LEVELS
                }
            }
        assertWithMessage("the surface in a host and outside one are not the same pixels")
            .that(apart)
            .isEqualTo(0)
    }

    private fun assertProbes(inHost: Boolean, expected: () -> Color) {
        val tag = if (inHost) IN_HOST else OUTSIDE
        val want = expected()
        for (probe in probes) {
            val actual = composeRule.onNodeWithTag(tag).pixelAt(probe.x.value, probe.y.value)
            val distance =
                abs(actual.red - want.red) +
                    abs(actual.green - want.green) +
                    abs(actual.blue - want.blue)
            assertWithMessage("at $probe the pixel is $actual, expected $want")
                .that(distance < TOLERANCE)
                .isTrue()
        }
    }

    private companion object {
        const val IN_HOST = "glass-in-host"
        const val OUTSIDE = "glass-outside"
        const val TOLERANCE = 0.04f
        const val SAME_LEVELS = 4
        val CHANNEL_SHIFTS = listOf(0, 8, 16)
    }
}

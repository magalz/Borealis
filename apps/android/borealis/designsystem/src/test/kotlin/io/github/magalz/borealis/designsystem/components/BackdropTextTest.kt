package io.github.magalz.borealis.designsystem.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.PixelMap
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.graphics.toPixelMap
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.assert
import androidx.compose.ui.test.captureToImage
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.text.TextLayoutResult
import androidx.compose.ui.unit.dp
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.google.common.truth.Truth.assertThat
import io.github.magalz.borealis.designsystem.background.BackgroundStyle
import io.github.magalz.borealis.designsystem.glass.GlassHostOver
import io.github.magalz.borealis.designsystem.theme.BorealisColors
import io.github.magalz.borealis.designsystem.theme.BorealisLook
import io.github.magalz.borealis.designsystem.theme.BorealisTheme
import io.github.magalz.borealis.designsystem.theme.FormFactor
import io.github.magalz.borealis.designsystem.theme.Transparency
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config

/**
 * The text that stands on the animated background, outside any glass, has a soft dark halo and its
 * secondary ink is ink-2 (issue #33). Where there is no background, which is the plain page, there is
 * no halo and the ink is the token, as it was.
 */
@RunWith(AndroidJUnit4::class)
@Config(qualifiers = "w960dp-h540dp-land-television-xhdpi")
class BackdropTextTest {
    @get:Rule val composeRule = createComposeRule()

    private val colors = BorealisColors()

    // Where the halo is drawn

    @Test
    fun `the halo is drawn over a background of every style that draws one`() {
        for (style in BackgroundStyle.entries.filter { it != BackgroundStyle.Off }) {
            show(style) { BackdropText("Hosts") }

            assertThat(haloOf("Hosts")).isTrue()
        }
    }

    @Test
    fun `the halo is drawn where the glass is Off or High contrast, for the background is still behind`() {
        show(BackgroundStyle.AuroraCurtains, BorealisLook(transparency = Transparency.Off)) {
            BackdropText("Hosts")
        }
        assertThat(haloOf("Hosts")).isTrue()

        show(BackgroundStyle.AuroraCurtains, BorealisLook(highContrast = true)) {
            BackdropText("Hosts")
        }
        assertThat(haloOf("Hosts")).isTrue()
    }

    @Test
    fun `no halo is drawn on the plain page`() {
        show(BackgroundStyle.Off) { BackdropText("Hosts") }

        assertThat(haloOf("Hosts")).isFalse()
    }

    @Test
    fun `no halo is drawn outside a host, which has no background`() {
        composeRule.setContent { BorealisTheme(FormFactor.Tv) { BackdropText("Hosts") } }

        assertThat(haloOf("Hosts")).isFalse()
    }

    // The ink

    @Test
    fun `the primary inks are the tokens whatever is behind the text`() {
        show(BackgroundStyle.Starfield) {
            BackdropText("Ink")
            BackdropText("Ink two", ink = BackdropInk.Ink2)
        }

        assertThat(textColor("Ink")).isEqualTo(colors.ink)
        assertThat(textColor("Ink two")).isEqualTo(colors.ink2)
    }

    @Test
    fun `ink-3 and ink-4 are ink-2 over a background`() {
        show(BackgroundStyle.Starfield) {
            BackdropText("Three", ink = BackdropInk.Ink3)
            BackdropText("Four", ink = BackdropInk.Ink4)
        }

        assertThat(textColor("Three")).isEqualTo(colors.ink2)
        assertThat(textColor("Four")).isEqualTo(colors.ink2)
    }

    @Test
    fun `ink-3 and ink-4 are the tokens on the plain page`() {
        show(BackgroundStyle.Off) {
            BackdropText("Three", ink = BackdropInk.Ink3)
            BackdropText("Four", ink = BackdropInk.Ink4)
        }

        assertThat(textColor("Three")).isEqualTo(colors.ink3)
        assertThat(textColor("Four")).isEqualTo(colors.ink4)
    }

    @Test
    fun `a kicker is capitals in ink-2 over a background and ink-4 on the plain page`() {
        show(BackgroundStyle.NorthernLights) { BackdropKicker("Hosts") }
        assertThat(textColor("HOSTS")).isEqualTo(colors.ink2)
        assertThat(haloOf("HOSTS")).isTrue()

        show(BackgroundStyle.Off) { BackdropKicker("Hosts") }
        assertThat(textColor("HOSTS")).isEqualTo(colors.ink4)
        assertThat(haloOf("HOSTS")).isFalse()
    }

    @Test
    fun `an empty state is drawn as backdrop text, its body in ink-2`() {
        show(BackgroundStyle.NorthernLights) { EmptyState("Nothing here", "Add a host") }

        assertThat(haloOf("Nothing here")).isTrue()
        assertThat(haloOf("Add a host")).isTrue()
        assertThat(textColor("Add a host")).isEqualTo(colors.ink2)
    }

    // The drawing

    @Test
    fun `the halo darkens the bright background round the text and the text is drawn over it`() {
        show(BackgroundStyle.Starfield, host = HOST) {
            Box(Modifier.padding(24.dp)) {
                BackdropText("Hosts", style = MaterialTheme.typography.titleLarge)
            }
        }

        val pixels = composeRule.onNodeWithTag(HOST).captureToImage().toPixelMap()

        assertThat(pixels.count { it.luminance() < DARK }).isGreaterThan(MIN_HALO_PIXELS)
        assertThat(pixels.count { it.near(colors.ink) }).isGreaterThan(MIN_TEXT_PIXELS)
    }

    @Test
    fun `no halo is drawn on the plain page, so nothing round the text is dark`() {
        show(BackgroundStyle.Off, host = HOST) {
            Box(Modifier.padding(24.dp)) {
                BackdropText("Hosts", style = MaterialTheme.typography.titleLarge)
            }
        }

        val pixels = composeRule.onNodeWithTag(HOST).captureToImage().toPixelMap()

        assertThat(pixels.count { it.luminance() < DARK }).isEqualTo(0)
        assertThat(pixels.count { it.near(colors.ink) }).isGreaterThan(MIN_TEXT_PIXELS)
    }

    // Helpers

    /** [content] in a host of [style], over a bright backdrop, under [look]. */
    private fun show(
        style: BackgroundStyle,
        look: BorealisLook = BorealisLook(),
        host: String? = null,
        content: @Composable () -> Unit,
    ) {
        composeRule.setContent {
            BorealisTheme(FormFactor.Tv, look) {
                GlassHostOver(
                    style,
                    background = { Box(Modifier.fillMaxSize().background(Color.White)) },
                    modifier =
                        if (host != null) Modifier.size(HOST_WIDTH.dp, HOST_HEIGHT.dp).testTag(host)
                        else Modifier,
                    content = content,
                )
            }
        }
        composeRule.waitForIdle()
    }

    private fun haloOf(text: String): Boolean {
        val node = composeRule.onNodeWithText(text, useUnmergedTree = true)
        node.assert(SemanticsMatcher.keyIsDefined(BackdropTextHalo))
        return node.fetchSemanticsNode().config[BackdropTextHalo]
    }

    private fun textColor(text: String): Color {
        val results = mutableListOf<TextLayoutResult>()
        composeRule
            .onNodeWithText(text, useUnmergedTree = true)
            .fetchSemanticsNode()
            .config[SemanticsActions.GetTextLayoutResult]
            .action
            ?.invoke(results)
        return results.single().layoutInput.style.color
    }

    private fun PixelMap.count(test: (Color) -> Boolean): Int {
        var found = 0
        for (y in 0 until height) for (x in 0 until width) if (test(this[x, y])) found++
        return found
    }

    private fun Color.near(other: Color) =
        kotlin.math.abs(red - other.red) +
            kotlin.math.abs(green - other.green) +
            kotlin.math.abs(blue - other.blue) < NEAR

    private companion object {
        const val HOST = "backdrop-host"
        const val HOST_WIDTH = 220
        const val HOST_HEIGHT = 90

        /** A pixel of the halo over white is `page` at 80 percent over it: well under this. */
        const val DARK = 0.5f
        const val MIN_HALO_PIXELS = 400
        const val MIN_TEXT_PIXELS = 30
        const val NEAR = 0.03f
    }
}

package io.github.magalz.borealis.stream.ui.overlay

import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.assert
import androidx.compose.ui.test.assertHasNoClickAction
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.test.ext.junit.runners.AndroidJUnit4
import io.github.magalz.borealis.designsystem.theme.FormFactor
import io.github.magalz.borealis.stream.OverlayMode
import io.github.magalz.borealis.stream.Panel
import io.github.magalz.borealis.stream.testing.PHONE
import io.github.magalz.borealis.stream.testing.TV
import io.github.magalz.borealis.stream.testing.aPerf
import io.github.magalz.borealis.stream.ui.LayerFixture
import io.github.magalz.borealis.stream.ui.aStreaming
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config

/** UC-004-T6: what each overlay mode draws over the stream. */
@RunWith(AndroidJUnit4::class)
class PerfOverlayTest {
    @get:Rule val composeRule = createComposeRule()

    private val compact = "60 fps · host 2.0 ms · net 12 ms · HEVC"

    private fun layer(
        formFactor: FormFactor,
        overlay: OverlayMode,
        panel: Panel = Panel.NONE,
        withPerf: Boolean = true,
    ): LayerFixture {
        val perf = if (withPerf) aPerf() else null
        return LayerFixture(composeRule, formFactor, aStreaming(panel, overlay), perf).also {
            it.show()
        }
    }

    @Test
    @Config(qualifiers = PHONE)
    fun `the overlay is not drawn when it is off`() {
        layer(FormFactor.Phone, OverlayMode.OFF)

        composeRule.onNodeWithText(compact).assertDoesNotExist()
        composeRule.onNodeWithText("Decode 4.0 ms").assertDoesNotExist()
    }

    @Test
    @Config(qualifiers = PHONE)
    fun `compact is one line with the fps, the host time, the network time and the codec`() {
        layer(FormFactor.Phone, OverlayMode.COMPACT)

        composeRule.onNodeWithText(compact).assertIsDisplayed()
        composeRule.onNodeWithText("Decode 4.0 ms").assertDoesNotExist()
    }

    @Test
    @Config(qualifiers = TV)
    fun `detailed has a line for each figure`() {
        layer(FormFactor.Tv, OverlayMode.DETAILED)

        composeRule.onNodeWithText("1920×1080 · 60 fps · HEVC").assertIsDisplayed()
        composeRule.onNodeWithText("Decoder c2.qti.hevc.decoder").assertIsDisplayed()
        composeRule
            .onNodeWithText("Host processing 1.0 / 2.0 / 5.0 ms (min/avg/max)")
            .assertIsDisplayed()
        composeRule.onNodeWithText("Network 12 ms ± 3 · loss 0.5 % · 18.5 Mbps").assertIsDisplayed()
        composeRule.onNodeWithText("Decode 4.0 ms").assertIsDisplayed()
        composeRule.onNodeWithText(compact).assertDoesNotExist()
    }

    @Test
    @Config(qualifiers = PHONE)
    fun `the overlay shows dashes until the first reading arrives`() {
        val fixture = layer(FormFactor.Phone, OverlayMode.COMPACT, withPerf = false)

        composeRule.onNodeWithText("— fps · host — ms · net — ms · HEVC").assertIsDisplayed()

        fixture.perf = aPerf(totalFps = 59f)
        composeRule.waitForIdle()

        composeRule.onNodeWithText("59 fps · host 2.0 ms · net 12 ms · HEVC").assertIsDisplayed()
    }

    @Test
    @Config(qualifiers = PHONE)
    fun `the overlay follows the mode the user picks`() {
        val fixture = layer(FormFactor.Phone, OverlayMode.COMPACT)

        fixture.phase = aStreaming(overlay = OverlayMode.DETAILED)
        composeRule.waitForIdle()

        composeRule.onNodeWithText("Decode 4.0 ms").assertIsDisplayed()
        composeRule.onNodeWithText(compact).assertDoesNotExist()
    }

    @Test
    @Config(qualifiers = PHONE)
    fun `the overlay stays while the menu is open`() {
        layer(FormFactor.Phone, OverlayMode.COMPACT, Panel.MENU)

        composeRule.onNodeWithText(compact).assertIsDisplayed()
    }

    @Test
    @Config(qualifiers = PHONE)
    fun `the overlay stays while the keyboard is open`() {
        layer(FormFactor.Phone, OverlayMode.COMPACT, Panel.KEYBOARD)

        composeRule.onNodeWithText(compact).assertIsDisplayed()
    }

    @Test
    @Config(qualifiers = PHONE)
    fun `the overlay takes neither a tap nor the focus`() {
        layer(FormFactor.Phone, OverlayMode.COMPACT)

        composeRule
            .onNodeWithText(compact)
            .assertHasNoClickAction()
            .assert(SemanticsMatcher.keyNotDefined(SemanticsProperties.Focused))
    }
}

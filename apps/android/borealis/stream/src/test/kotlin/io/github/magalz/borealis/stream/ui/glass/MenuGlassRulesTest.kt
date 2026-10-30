package io.github.magalz.borealis.stream.ui.glass

import android.graphics.Color as AndroidColor
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.test.assertIsFocused
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.requestFocus
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.google.common.truth.Truth.assertThat
import io.github.magalz.borealis.designsystem.theme.BorealisColors
import io.github.magalz.borealis.designsystem.theme.BorealisLook
import io.github.magalz.borealis.designsystem.theme.ReduceMotion
import io.github.magalz.borealis.designsystem.theme.Transparency
import io.github.magalz.borealis.stream.testing.PHONE_LANDSCAPE
import org.junit.After
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config

/**
 * The glass is solid, and no copy runs, where the accessibility settings and the device say so (ADR
 * 0061, decision 3). Each test starts with the glass up over a red video and then sets what makes
 * it solid, so a test that passes could have failed: the glass was there first.
 */
@RunWith(AndroidJUnit4::class)
@Config(qualifiers = PHONE_LANDSCAPE)
class MenuGlassRulesTest {
    @get:Rule val composeRule = createComposeRule()

    private val surface = BorealisColors().surface
    private val glass =
        GlassFixture(composeRule, FakeVideoSource(frameColor = AndroidColor.RED, completing = true))

    @After fun `the battery saver is off again`() = setPowerSave(false)

    private fun isGlass(pixel: Color) = pixel.red > surface.red + SHOWS_THROUGH

    private fun showsGlass() {
        glass.show()
        glass.run(100)
        assertThat(isGlass(glass.panel().leftPixel())).isTrue()
    }

    private fun assertSolidAndSilent() {
        glass.run(100)
        val asked = glass.source!!.requests.size
        glass.run(1_000)

        assertThat(isGlass(glass.panel().leftPixel())).isFalse()
        assertThat(glass.panel().leftPixel().red).isWithin(TOLERANCE).of(surface.red)
        assertThat(glass.source.requests).hasSize(asked)
    }

    @Test
    fun `Transparency Off is solid and copies nothing`() {
        showsGlass()

        glass.look = BorealisLook(transparency = Transparency.Off)

        assertSolidAndSilent()
    }

    @Test
    fun `high contrast is solid and copies nothing`() {
        showsGlass()

        glass.look = BorealisLook(highContrast = true)

        assertSolidAndSilent()
    }

    @Test
    fun `Reduce motion is solid and copies nothing`() {
        showsGlass()

        glass.look = BorealisLook(reduceMotion = ReduceMotion.On)

        assertSolidAndSilent()
    }

    @Test
    fun `the menu glass option set to Solid is solid and copies nothing`() {
        showsGlass()

        glass.videoGlass = false

        assertSolidAndSilent()
    }

    @Test
    fun `the battery saver coming on makes it solid and stops the copies, going off brings it back`() {
        showsGlass()

        setPowerSave(true)
        assertSolidAndSilent()

        setPowerSave(false)
        glass.run(500)
        assertThat(isGlass(glass.panel().leftPixel())).isTrue()
    }

    @Test
    fun `the battery saver on as the menu opens is solid from the first frame`() {
        setPowerSave(true)
        glass.show()

        assertSolidAndSilent()
    }

    @Test
    fun `the Quit popup is solid under the same rules`() {
        showsGlass()
        glass.openQuitPopup()
        assertThat(isGlass(glass.popup().rightPixel())).isTrue()

        glass.look = BorealisLook(transparency = Transparency.Off)
        glass.run(100)

        assertThat(isGlass(glass.popup().rightPixel())).isFalse()
    }

    // The battery saver

    @Test
    fun `the glass listens for the battery saver, once`() {
        showsGlass()

        assertThat(powerSaveReceivers()).hasSize(1)
    }

    @Test
    fun `the menu glass option set to Solid registers no receiver for the battery saver`() {
        glass.videoGlass = false
        glass.show()
        glass.run(100)

        assertThat(powerSaveReceivers()).isEmpty()
    }

    @Test
    fun `a stream with no video registers no receiver for the battery saver`() {
        val noVideo = GlassFixture(composeRule, source = null)
        noVideo.show()
        noVideo.run(100)

        assertThat(powerSaveReceivers()).isEmpty()
    }

    @Test
    fun `the battery saver coming on keeps the focus and what the user opened in the menu`() {
        glass.keyboardMode = true
        showsGlass()
        composeRule.onNodeWithText("Send keys").performClick()
        composeRule.onNodeWithText("Esc").assertExists()
        composeRule.onNodeWithText("Keyboard").requestFocus()
        composeRule.onNodeWithText("Keyboard").assertIsFocused()

        setPowerSave(true)
        glass.run(100)

        assertThat(isGlass(glass.panel().leftPixel())).isFalse()
        composeRule.onNodeWithText("Esc").assertExists()
        composeRule.onNodeWithText("Keyboard").assertIsFocused()
    }

    private companion object {
        const val SHOWS_THROUGH = 0.05f
        const val TOLERANCE = 0.012f
    }
}

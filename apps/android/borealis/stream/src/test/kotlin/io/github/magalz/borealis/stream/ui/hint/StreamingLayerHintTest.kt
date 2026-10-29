package io.github.magalz.borealis.stream.ui.hint

import android.view.InputDevice
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import io.github.magalz.borealis.designsystem.testing.connect
import io.github.magalz.borealis.designsystem.testing.inputDevice
import io.github.magalz.borealis.designsystem.theme.FormFactor
import io.github.magalz.borealis.stream.testing.PHONE
import io.github.magalz.borealis.stream.ui.LayerFixture
import io.github.magalz.borealis.stream.ui.aStreaming
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config

/**
 * The first-run hint as the streaming layer draws it: the mouse navigation buttons setting of the
 * phase reaches the hint, so a stream played with a mouse names the Back hold when it is on.
 */
@RunWith(AndroidJUnit4::class)
class StreamingLayerHintTest {
    @get:Rule val composeRule = createComposeRule()

    private val chordHint = "Press Ctrl+Alt+Shift+M for the menu"
    private val chordAndBackHint =
        "Press Ctrl+Alt+Shift+M or hold the mouse Back button for the menu"

    private fun layerWithMouse(mouseNavButtons: Boolean) {
        connect(
            ApplicationProvider.getApplicationContext(),
            inputDevice(3, InputDevice.SOURCE_MOUSE),
        )
        composeRule.mainClock.autoAdvance = false
        LayerFixture(
                composeRule,
                FormFactor.Phone,
                aStreaming().copy(showMenuHint = true, mouseNavButtons = mouseNavButtons),
            )
            .show()
    }

    @Test
    @Config(qualifiers = PHONE)
    fun `the stream hint names the mouse Back hold when the navigation buttons are on`() {
        layerWithMouse(mouseNavButtons = true)

        composeRule.onNodeWithText(chordAndBackHint).assertIsDisplayed()
        composeRule.onNodeWithText(chordHint).assertDoesNotExist()
    }

    @Test
    @Config(qualifiers = PHONE)
    fun `the stream hint names the chord alone when the navigation buttons are off`() {
        layerWithMouse(mouseNavButtons = false)

        composeRule.onNodeWithText(chordHint).assertIsDisplayed()
        composeRule.onNodeWithText(chordAndBackHint).assertDoesNotExist()
    }
}

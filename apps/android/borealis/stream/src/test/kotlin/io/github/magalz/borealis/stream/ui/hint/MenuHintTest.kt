package io.github.magalz.borealis.stream.ui.hint

import android.view.InputDevice
import androidx.compose.runtime.snapshots.Snapshot
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.google.common.truth.Truth.assertThat
import io.github.magalz.borealis.designsystem.testing.connect
import io.github.magalz.borealis.designsystem.testing.inputDevice
import io.github.magalz.borealis.designsystem.theme.FormFactor
import io.github.magalz.borealis.stream.Panel
import io.github.magalz.borealis.stream.testing.HANDHELD
import io.github.magalz.borealis.stream.testing.PHONE
import io.github.magalz.borealis.stream.testing.TV
import io.github.magalz.borealis.stream.ui.LayerFixture
import io.github.magalz.borealis.stream.ui.aStreaming
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config

/** The one-line hint that says how to open the menu on this device, the first time. */
@RunWith(AndroidJUnit4::class)
class MenuHintTest {
    @get:Rule val composeRule = createComposeRule()

    private val phoneHint = "Pull the handle down or press Back for the menu"

    private fun layer(formFactor: FormFactor, showHint: Boolean = true): LayerFixture {
        composeRule.mainClock.autoAdvance = false
        return LayerFixture(composeRule, formFactor, aStreaming().copy(showMenuHint = showHint))
            .also {
                it.show()
            }
    }

    /** Opens or closes a panel; the clock is paused, so the write is applied by hand. */
    private fun show(fixture: LayerFixture, panel: Panel) {
        composeRule.runOnUiThread {
            fixture.phase = aStreaming(panel).copy(showMenuHint = true)
            Snapshot.sendApplyNotifications()
        }
    }

    @Test
    @Config(qualifiers = PHONE)
    fun `a phone is told to pull the handle or press Back`() {
        layer(FormFactor.Phone)

        composeRule.onNodeWithText(phoneHint).assertIsDisplayed()
    }

    @Test
    @Config(qualifiers = HANDHELD)
    fun `a handheld is told to hold Select and Start`() {
        layer(FormFactor.Handheld)

        composeRule.onNodeWithText("Hold Select + Start for the menu").assertIsDisplayed()
    }

    @Test
    @Config(qualifiers = TV)
    fun `a TV is told to press Back`() {
        layer(FormFactor.Tv)

        composeRule.onNodeWithText("Press Back for the menu").assertIsDisplayed()
    }

    // The keyboard and the mouse

    private val keyboardMouseHint =
        "Press Ctrl+Alt+Shift+M or hold the mouse Back button for the menu"
    private val mouse = InputDevice.SOURCE_MOUSE
    private val keyboard = InputDevice.SOURCE_KEYBOARD
    private val letters = InputDevice.KEYBOARD_TYPE_ALPHABETIC

    /** A device that Android has before the stream starts. */
    private fun plugIn(id: Int, sources: Int, keyboardType: Int = InputDevice.KEYBOARD_TYPE_NONE) =
        connect(
            ApplicationProvider.getApplicationContext(),
            inputDevice(id, sources, keyboardType = keyboardType),
        )

    @Test
    @Config(qualifiers = PHONE)
    fun `a mouse and no pad is told about the chord and the Back button`() {
        plugIn(3, mouse)

        layer(FormFactor.Phone)

        composeRule.onNodeWithText(keyboardMouseHint).assertIsDisplayed()
        composeRule.onNodeWithText(phoneHint).assertDoesNotExist()
    }

    @Test
    @Config(qualifiers = PHONE)
    fun `a keyboard with letters and no pad is told about the chord and the Back button`() {
        plugIn(4, keyboard, letters)

        layer(FormFactor.Phone)

        composeRule.onNodeWithText(keyboardMouseHint).assertIsDisplayed()
    }

    @Test
    @Config(qualifiers = TV)
    fun `a TV that has a mouse is told about the chord and the Back button`() {
        plugIn(3, mouse)

        layer(FormFactor.Tv)

        composeRule.onNodeWithText(keyboardMouseHint).assertIsDisplayed()
    }

    @Test
    @Config(qualifiers = HANDHELD)
    fun `a pad wins over a keyboard and a mouse`() {
        plugIn(3, mouse)
        plugIn(4, keyboard, letters)
        plugIn(5, InputDevice.SOURCE_GAMEPAD or keyboard)

        layer(FormFactor.Handheld)

        composeRule.onNodeWithText("Hold Select + Start for the menu").assertIsDisplayed()
        composeRule.onNodeWithText(keyboardMouseHint).assertDoesNotExist()
    }

    @Test
    @Config(qualifiers = PHONE)
    fun `a virtual keyboard, a remote and a touchscreen are no keyboard or mouse`() {
        // The virtual keyboard of every phone has letters; a remote has keys but no letters.
        plugIn(-1, keyboard, letters)
        plugIn(6, keyboard or InputDevice.SOURCE_DPAD, InputDevice.KEYBOARD_TYPE_NON_ALPHABETIC)
        plugIn(7, InputDevice.SOURCE_TOUCHSCREEN)

        layer(FormFactor.Phone)

        composeRule.onNodeWithText(phoneHint).assertIsDisplayed()
        composeRule.onNodeWithText(keyboardMouseHint).assertDoesNotExist()
    }

    @Test
    @Config(qualifiers = PHONE)
    fun `the line for the keyboard and the mouse goes after six seconds like the others`() {
        plugIn(3, mouse)
        val fixture = layer(FormFactor.Phone)

        composeRule.mainClock.advanceTimeBy(5_900)
        composeRule.onNodeWithText(keyboardMouseHint).assertIsDisplayed()
        composeRule.mainClock.advanceTimeBy(300)
        composeRule.mainClock.advanceTimeBy(5_000)

        assertThat(fixture.intents.calls).containsExactly("dismissMenuHint")
        composeRule.onNodeWithText(keyboardMouseHint).assertDoesNotExist()
    }

    @Test
    @Config(qualifiers = PHONE)
    fun `nothing is shown when the hint was seen before`() {
        layer(FormFactor.Phone, showHint = false)

        composeRule.onNodeWithText(phoneHint).assertDoesNotExist()
    }

    @Test
    @Config(qualifiers = PHONE)
    fun `the hint stays for six seconds and then dismisses itself once`() {
        val fixture = layer(FormFactor.Phone)

        composeRule.mainClock.advanceTimeBy(5_900)
        assertThat(fixture.intents.calls).isEmpty()
        composeRule.onNodeWithText(phoneHint).assertIsDisplayed()

        composeRule.mainClock.advanceTimeBy(300)
        composeRule.mainClock.advanceTimeBy(5_000)

        assertThat(fixture.intents.calls).containsExactly("dismissMenuHint")
        composeRule.onNodeWithText(phoneHint).assertDoesNotExist()
    }

    @Test
    @Config(qualifiers = PHONE)
    fun `opening the menu dismisses the hint at once`() {
        val fixture = layer(FormFactor.Phone)

        show(fixture, Panel.MENU)
        composeRule.mainClock.advanceTimeBy(100)

        assertThat(fixture.intents.calls).containsExactly("dismissMenuHint")
        composeRule.onNodeWithText(phoneHint).assertDoesNotExist()
    }

    @Test
    @Config(qualifiers = PHONE)
    fun `a hint dismissed by the menu does not dismiss itself again later`() {
        val fixture = layer(FormFactor.Phone)
        show(fixture, Panel.MENU)
        composeRule.mainClock.advanceTimeBy(100)

        show(fixture, Panel.NONE)
        composeRule.mainClock.advanceTimeBy(10_000)

        assertThat(fixture.intents.calls).containsExactly("dismissMenuHint")
    }
}

package io.github.magalz.borealis.stream.ui.keyboard

import android.content.Context
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.assert
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.assertIsFocused
import androidx.compose.ui.test.assertTextEquals
import androidx.compose.ui.test.getUnclippedBoundsInRoot
import androidx.compose.ui.test.isFocused
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performKeyInput
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.pressKey
import androidx.compose.ui.unit.height
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.google.common.truth.Truth.assertThat
import io.github.magalz.borealis.designsystem.testing.setContentInKeyboardMode
import io.github.magalz.borealis.designsystem.theme.BorealisTheme
import io.github.magalz.borealis.designsystem.theme.FormFactor
import io.github.magalz.borealis.stream.keys.KeyChord
import io.github.magalz.borealis.stream.keys.KeyModifier.ALT
import io.github.magalz.borealis.stream.keys.KeyModifier.CTRL
import io.github.magalz.borealis.stream.keys.KeyModifier.SHIFT
import io.github.magalz.borealis.stream.keys.Shortcuts
import io.github.magalz.borealis.stream.testing.HANDHELD
import io.github.magalz.borealis.stream.testing.PHONE
import io.github.magalz.borealis.stream.testing.RecordingIntents
import io.github.magalz.borealis.stream.testing.TV
import io.github.magalz.borealis.stream.ui.StreamKeyboard
import java.io.File
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config

@RunWith(AndroidJUnit4::class)
class StreamKeyboardTest {
    @get:Rule val composeRule = createComposeRule()
    private val context: Context = ApplicationProvider.getApplicationContext()
    private val intents = RecordingIntents()

    /** Key events that no key of the keyboard consumed, as an outer handler sees them. */
    private val passedUp = mutableListOf<Pair<KeyEventType, Key>>()

    private fun showInKeyboardMode(formFactor: FormFactor) {
        composeRule.setContentInKeyboardMode { Host(docked = false, formFactor) }
    }

    private fun showPhone() {
        composeRule.setContent { Host(docked = true, FormFactor.Phone) }
    }

    @Composable
    private fun Host(docked: Boolean, formFactor: FormFactor) {
        BorealisTheme(formFactor) {
            Box(
                Modifier.fillMaxSize().onKeyEvent {
                    passedUp += it.type to it.key
                    false
                }
            ) {
                StreamKeyboard(docked, intents)
            }
        }
    }

    private fun tapKey(caption: String) = composeRule.onNodeWithTag("key:$caption").performClick()

    private fun tapChip(label: String) = composeRule.onNodeWithTag("chip:$label").performClick()

    // The shortcut chips scroll sideways on a phone; one that is off screen is scrolled to first.
    private fun tapShortcut(label: String) =
        composeRule.onNodeWithTag("chip:$label").performScrollTo().performClick()

    private fun pressOnFocused(key: Key) = composeRule.onRoot().performKeyInput { pressKey(key) }

    private fun keysPassedUp() = passedUp.map { it.second }

    // UC-005-T1
    @Test
    @Config(qualifiers = PHONE)
    fun `Ctrl latched and then C sends Ctrl+C once and clears the latch`() {
        showPhone()

        tapKey("Ctrl")
        tapKey("C")
        tapKey("C")

        assertThat(intents.chords)
            .containsExactly(KeyChord(0x43, setOf(CTRL)), KeyChord(0x43))
            .inOrder()
    }

    @Test
    @Config(qualifiers = PHONE)
    fun `a latched modifier is shown as locked and an unlatched one is not`() {
        showPhone()

        tapKey("Alt")

        composeRule
            .onNodeWithTag("key:Alt")
            .assert(SemanticsMatcher.expectValue(SemanticsProperties.StateDescription, "Locked"))
        composeRule
            .onNodeWithTag("key:Ctrl")
            .assert(SemanticsMatcher.keyNotDefined(SemanticsProperties.StateDescription))
    }

    @Test
    @Config(qualifiers = PHONE)
    fun `tapping a latched modifier again unlatches it`() {
        showPhone()

        tapKey("Shift")
        tapKey("Shift")
        tapKey("A")

        assertThat(intents.chords).containsExactly(KeyChord(0x41))
    }

    @Test
    @Config(qualifiers = PHONE)
    fun `two latches go together with the next key`() {
        showPhone()

        tapKey("Ctrl")
        tapKey("Shift")
        tapKey("Enter")

        assertThat(intents.chords).containsExactly(KeyChord(0x0D, setOf(CTRL, SHIFT)))
    }

    @Test
    @Config(qualifiers = PHONE)
    fun `Space, Tab and Backspace are sent as keys`() {
        showPhone()

        tapKey("Space")
        tapKey("Tab")
        tapKey("Backspace")

        assertThat(intents.chords)
            .containsExactly(KeyChord(0x20), KeyChord(0x09), KeyChord(0x08))
            .inOrder()
    }

    // UC-005-T2
    @Test
    @Config(qualifiers = PHONE)
    fun `the shortcut chips send their chords`() {
        showPhone()

        for (shortcut in Shortcuts.keyboardRowPhone) {
            tapShortcut(context.getString(shortcut.label))
        }

        assertThat(intents.chords)
            .containsExactlyElementsIn(Shortcuts.keyboardRowPhone.map { it.chord })
            .inOrder()
        assertThat(intents.chords).contains(KeyChord(0x09, setOf(ALT)))
    }

    @Test
    @Config(qualifiers = PHONE)
    fun `a shortcut chip ignores and clears the latches`() {
        showPhone()

        tapKey("Ctrl")
        tapChip("Alt+Tab")
        tapKey("A")

        assertThat(intents.chords)
            .containsExactly(KeyChord(0x09, setOf(ALT)), KeyChord(0x41))
            .inOrder()
    }

    @Test
    @Config(qualifiers = PHONE)
    fun `Close closes the panel and the System keyboard key opens the Android keyboard`() {
        showPhone()

        tapChip("Close")
        tapChip("System keyboard")

        assertThat(intents.calls).containsExactly("closePanel", "openSystemKeyboard").inOrder()
        assertThat(intents.chords).isEmpty()
    }

    // The Fn layer
    @Test
    @Config(qualifiers = PHONE)
    fun `Fn shows the digits, the function keys and the arrows, and Fn again the letters`() {
        showPhone()

        tapKey("Fn 123")

        composeRule.onNodeWithTag("key:F1").assertExists()
        composeRule.onNodeWithTag("key:1").assertExists()
        composeRule.onNodeWithTag("key:Home").assertExists()
        composeRule.onNodeWithTag("key:Q").assertDoesNotExist()

        tapKey("Fn 123")

        composeRule.onNodeWithTag("key:Q").assertExists()
        composeRule.onNodeWithTag("key:F1").assertDoesNotExist()
    }

    @Test
    @Config(qualifiers = PHONE)
    fun `the Fn key says that the digits are behind it and keeps its caption on the layer`() {
        showPhone()

        composeRule.onNodeWithTag("key:Fn 123").assertTextEquals("Fn 123")
        tapKey("Fn 123")

        composeRule
            .onNodeWithTag("key:Fn 123")
            .assertTextEquals("Fn 123")
            .assert(SemanticsMatcher.expectValue(SemanticsProperties.StateDescription, "Locked"))
    }

    @Test
    @Config(qualifiers = PHONE)
    fun `a latch survives the switch to the Fn layer`() {
        showPhone()

        tapKey("Shift")
        tapKey("Fn 123")
        tapKey("→")
        tapKey("F5")

        assertThat(intents.chords)
            .containsExactly(KeyChord(0x27, setOf(SHIFT)), KeyChord(0x74))
            .inOrder()
    }

    // UC-005-T3
    @Test
    @Config(qualifiers = TV)
    fun `on a TV the focus starts on Q`() {
        showInKeyboardMode(FormFactor.Tv)

        composeRule.onNodeWithTag("key:Q").assertIsFocused()
    }

    @Test
    @Config(qualifiers = HANDHELD)
    fun `on a handheld the focus starts on Q`() {
        showInKeyboardMode(FormFactor.Handheld)

        composeRule.onNodeWithTag("key:Q").assertIsFocused()
    }

    @Test
    @Config(qualifiers = TV)
    fun `the D-pad moves across a row and OK types the focused key`() {
        showInKeyboardMode(FormFactor.Tv)

        pressOnFocused(Key.DirectionRight)
        composeRule.onNodeWithTag("key:W").assertIsFocused()
        pressOnFocused(Key.DirectionCenter)

        assertThat(intents.chords).containsExactly(KeyChord(0x57))
    }

    @Test
    @Config(qualifiers = TV)
    fun `the D-pad moves down from the first row to the second`() {
        showInKeyboardMode(FormFactor.Tv)

        pressOnFocused(Key.DirectionDown)

        composeRule.onAllNodes(isFocused()).assertCountEquals(1)
        pressOnFocused(Key.DirectionCenter)
        assertThat(intents.chords.map { it.vk }).containsAnyOf(0x41, 0x53)
    }

    @Test
    @Config(qualifiers = TV)
    fun `the D-pad reaches the shortcut row above the keys`() {
        showInKeyboardMode(FormFactor.Tv)

        pressOnFocused(Key.DirectionUp)
        pressOnFocused(Key.DirectionCenter)

        // A chip sent its chord, or Close and System keyboard ran their intents.
        assertThat(intents.chords.size + intents.calls.size).isEqualTo(1)
        assertThat(intents.chords.map { it.vk }).containsNoneOf(0x51, 0x57)
    }

    // UC-005-T4
    @Test
    @Config(qualifiers = HANDHELD)
    fun `Y sends Backspace and is consumed`() {
        showInKeyboardMode(FormFactor.Handheld)

        pressOnFocused(Key.ButtonY)

        assertThat(intents.chords).containsExactly(KeyChord(0x08))
        assertThat(keysPassedUp()).doesNotContain(Key.ButtonY)
    }

    @Test
    @Config(qualifiers = HANDHELD)
    fun `B closes the panel once and both its key events are consumed`() {
        showInKeyboardMode(FormFactor.Handheld)

        pressOnFocused(Key.ButtonB)

        assertThat(intents.calls).containsExactly("closePanel")
        assertThat(keysPassedUp()).doesNotContain(Key.ButtonB)
    }

    @Test
    @Config(qualifiers = TV)
    fun `Back with the focus on a key closes the panel at once`() {
        showInKeyboardMode(FormFactor.Tv)

        pressOnFocused(Key.Back)

        assertThat(intents.calls).containsExactly("closePanel")
        assertThat(keysPassedUp()).doesNotContain(Key.Back)
    }

    @Test
    @Config(qualifiers = HANDHELD)
    fun `holding B closes the panel once`() {
        showInKeyboardMode(FormFactor.Handheld)

        // The test dispatcher repeats a key that stays down while time passes.
        composeRule.onRoot().performKeyInput {
            keyDown(Key.ButtonB)
            advanceEventTime(1_000)
            keyUp(Key.ButtonB)
        }

        assertThat(intents.calls).containsExactly("closePanel")
        assertThat(keysPassedUp()).doesNotContain(Key.ButtonB)
    }

    @Test
    @Config(qualifiers = HANDHELD)
    fun `A is not consumed, so the platform can turn it into OK`() {
        showInKeyboardMode(FormFactor.Handheld)

        pressOnFocused(Key.ButtonA)

        assertThat(keysPassedUp()).contains(Key.ButtonA)
        assertThat(intents.chords).isEmpty()
    }

    // UC-005-A3: a physical keyboard still types into the PC.
    @Test
    @Config(qualifiers = PHONE)
    fun `the docked keyboard takes no focus, so physical keys are not its to handle`() {
        showPhone()

        composeRule.onAllNodes(isFocused()).assertCountEquals(0)
        pressOnFocused(Key.C)
        pressOnFocused(Key.Enter)
        pressOnFocused(Key.ButtonB)

        assertThat(intents.chords).isEmpty()
        assertThat(intents.calls).isEmpty()
    }

    // UC-005-T5: the part this panel owns. The picture above it is the chrome's, and the device's.
    @Test
    @Config(qualifiers = PHONE)
    fun `the docked keyboard fills only the bottom of the screen`() {
        showPhone()

        val screen = composeRule.onRoot().getUnclippedBoundsInRoot().height
        val keyboard = composeRule.onNodeWithTag("keyboard").getUnclippedBoundsInRoot().height

        assertThat(keyboard).isLessThan(screen / 2)
    }

    @Test
    @Config(qualifiers = TV)
    fun `on a TV the shortcut chips are centred over the keyboard, like the rows of keys`() {
        showInKeyboardMode(FormFactor.Tv)

        val keyboard = composeRule.onNodeWithTag("keyboard").getUnclippedBoundsInRoot()
        val first = composeRule.onNodeWithTag("chip:Alt+Tab").getUnclippedBoundsInRoot()
        val last = composeRule.onNodeWithTag("chip:Close").getUnclippedBoundsInRoot()

        val chipsCentre = (first.left.value + last.right.value) / 2
        val keyboardCentre = (keyboard.left.value + keyboard.right.value) / 2
        assertThat(chipsCentre).isWithin(1f).of(keyboardCentre)
    }

    // UC-005-T6
    @Test
    fun `the keyboard code writes no log`() {
        val sources =
            listOf(File("src/main/kotlin/io/github/magalz/borealis/stream/ui/StreamKeyboard.kt")) +
                File("src/main/kotlin/io/github/magalz/borealis/stream/ui/keyboard")
                    .listFiles { file -> file.extension == "kt" }
                    .orEmpty()

        assertThat(sources.size).isAtLeast(3)
        for (source in sources) {
            val text = source.readText()
            assertThat(text).doesNotContain("BorealisLog")
            assertThat(text).doesNotContain("android.util.Log")
            assertThat(text).doesNotContain("println(")
        }
    }

    // O9: the label shows the case the next letter will have.
    @Test
    @Config(qualifiers = PHONE)
    fun `letter keys are lowercase until Shift is latched`() {
        showPhone()

        composeRule.onNodeWithTag("key:Q").assertTextEquals("q")
        composeRule.onNodeWithTag("key:M").assertTextEquals("m")

        tapKey("Shift")

        composeRule.onNodeWithTag("key:Q").assertTextEquals("Q")
        composeRule.onNodeWithTag("key:M").assertTextEquals("M")
    }

    @Test
    @Config(qualifiers = PHONE)
    fun `the letters are lowercase again after the latched letter is typed`() {
        showPhone()
        tapKey("Shift")

        tapKey("Q")

        composeRule.onNodeWithTag("key:Q").assertTextEquals("q")
        composeRule.onNodeWithTag("key:W").assertTextEquals("w")
    }

    @Test
    @Config(qualifiers = PHONE)
    fun `unlatching Shift or sending a shortcut makes the letters lowercase again`() {
        showPhone()

        tapKey("Shift")
        tapKey("Shift")
        composeRule.onNodeWithTag("key:A").assertTextEquals("a")

        tapKey("Shift")
        tapShortcut("Esc")
        composeRule.onNodeWithTag("key:A").assertTextEquals("a")
    }

    @Test
    @Config(qualifiers = PHONE)
    fun `Ctrl does not change the case and the keys still send the same chords`() {
        showPhone()

        tapKey("Ctrl")
        composeRule.onNodeWithTag("key:C").assertTextEquals("c")
        tapKey("C")

        assertThat(intents.chords).containsExactly(KeyChord(0x43, setOf(CTRL)))
    }

    @Test
    @Config(qualifiers = PHONE)
    fun `the Fn layer and the named keys keep their captions whatever the latch`() {
        showPhone()

        composeRule.onNodeWithTag("key:Enter").assertTextEquals("Enter")
        composeRule.onNodeWithTag("key:Space").assertTextEquals("Space")
        tapKey("Shift")
        composeRule.onNodeWithTag("key:Enter").assertTextEquals("Enter")
        tapKey("Fn 123")
        composeRule.onNodeWithTag("key:F1").assertTextEquals("F1")
        composeRule.onNodeWithTag("key:Home").assertTextEquals("Home")
    }
}

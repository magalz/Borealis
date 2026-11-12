package io.github.magalz.borealis.stream.ui

import androidx.compose.ui.test.assertIsFocused
import androidx.compose.ui.test.assertIsNotFocused
import androidx.compose.ui.test.getUnclippedBoundsInRoot
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.google.common.truth.Truth.assertThat
import io.github.magalz.borealis.designsystem.testing.HANDHELD
import io.github.magalz.borealis.designsystem.testing.PHONE
import io.github.magalz.borealis.designsystem.theme.FormFactor
import io.github.magalz.borealis.stream.Panel
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config

/**
 * Whether the keyboard the chrome draws takes the focus and the keys is what the session decided
 * when it opened, not what the form factor of the moment would pick: the window and the chrome host
 * must agree on who gets the keys. Its shortcut row is the other way round: it follows the form
 * factor of the window alone, so a phone-width window with a pad gets the row of the phone.
 */
@RunWith(AndroidJUnit4::class)
class KeyboardDockedByPhaseTest {
    @get:Rule val composeRule = createComposeRule()

    private fun show(formFactor: FormFactor, dockedKeyboard: Boolean) =
        LayerFixture(
                composeRule,
                formFactor,
                aStreaming(Panel.KEYBOARD).copy(dockedKeyboard = dockedKeyboard),
                keyboardMode = true,
            )
            .show()

    /** Where the shortcut chip [name] starts, from the left edge of the keyboard. */
    private fun chipStart(name: String) =
        composeRule.onNodeWithTag("chip:$name").getUnclippedBoundsInRoot().left.value

    @Test
    @Config(qualifiers = HANDHELD)
    fun `a keyboard that opened docked does not start its focus on Q, whatever the form factor`() {
        show(FormFactor.Handheld, dockedKeyboard = true)

        composeRule.onNodeWithTag("key:Q").assertIsNotFocused()
    }

    @Test
    @Config(qualifiers = PHONE)
    fun `a keyboard that opened covering takes the focus, even on a phone`() {
        show(FormFactor.Phone, dockedKeyboard = false)

        composeRule.onNodeWithTag("key:Q").assertIsFocused()
    }

    // A phone in split screen with a pad: the keyboard covers the stream, and Esc is still first.
    @Test
    @Config(qualifiers = PHONE)
    fun `a keyboard that opened covering on a phone-width window starts its shortcut row with Esc`() {
        show(FormFactor.Phone, dockedKeyboard = false)

        assertThat(chipStart("Esc")).isLessThan(chipStart("Alt+Tab"))
    }
}

package io.github.magalz.borealis.stream.ui

import androidx.compose.ui.test.assertIsFocused
import androidx.compose.ui.test.getUnclippedBoundsInRoot
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.google.common.truth.Truth.assertThat
import io.github.magalz.borealis.designsystem.theme.FormFactor
import io.github.magalz.borealis.stream.Panel
import io.github.magalz.borealis.stream.testing.HANDHELD
import io.github.magalz.borealis.stream.testing.PHONE
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config

/**
 * The keyboard the chrome draws is the one the session decided when it opened, not the one the form
 * factor of the moment would pick: the window and the chrome host must agree on who gets the keys.
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

    // The docked keyboard of a phone starts its shortcut row with Esc (UC-005-T7).
    @Test
    @Config(qualifiers = HANDHELD)
    fun `a keyboard that opened docked is drawn docked, whatever the form factor says now`() {
        show(FormFactor.Handheld, dockedKeyboard = true)

        assertThat(chipStart("Esc")).isLessThan(chipStart("Alt+Tab"))
    }

    @Test
    @Config(qualifiers = PHONE)
    fun `a keyboard that opened covering takes the focus, even on a phone`() {
        show(FormFactor.Phone, dockedKeyboard = false)

        composeRule.onNodeWithTag("key:Q").assertIsFocused()
    }
}

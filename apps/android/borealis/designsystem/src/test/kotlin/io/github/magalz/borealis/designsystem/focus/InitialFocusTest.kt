package io.github.magalz.borealis.designsystem.focus

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.input.InputMode
import androidx.compose.ui.input.InputModeManager
import androidx.compose.ui.platform.LocalInputModeManager
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.assertIsFocused
import androidx.compose.ui.test.assertIsNotFocused
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.unit.dp
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.google.common.truth.Truth.assertThat
import io.github.magalz.borealis.designsystem.components.Text
import io.github.magalz.borealis.designsystem.theme.BorealisTheme
import io.github.magalz.borealis.designsystem.theme.FormFactor
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class InitialFocusTest {
    @get:Rule val composeRule = createComposeRule()
    private val second = FocusRequester()
    private val first = FocusRequester()
    private val recompositions = mutableIntStateOf(0)
    private var manager: InputModeManager? = null

    @Test
    fun `the element with the initial focus has focus after the first layout`() {
        composeRule.setContent { Screen(FormFactor.Tv) }
        composeRule.waitForIdle()

        composeRule.onNodeWithTag(SECOND).assertIsFocused()
        composeRule.onNodeWithTag(FIRST).assertIsNotFocused()
    }

    @Test
    fun `a handheld also starts with focus on the element`() {
        composeRule.setContent { Screen(FormFactor.Handheld) }
        composeRule.waitForIdle()

        composeRule.onNodeWithTag(SECOND).assertIsFocused()
    }

    @Test
    fun `the helper asks for focus once and does not take it back later`() {
        composeRule.setContent { Screen(FormFactor.Tv) }
        composeRule.waitForIdle()
        composeRule.runOnIdle { first.requestFocus() }

        composeRule.runOnIdle { recompositions.intValue++ }
        composeRule.waitForIdle()

        composeRule.onNodeWithTag(FIRST).assertIsFocused()
    }

    @Test
    fun `a TV window switches to keyboard mode`() {
        composeRule.setContent { Screen(FormFactor.Tv) }
        composeRule.waitForIdle()

        assertThat(checkNotNull(manager).inputMode).isEqualTo(InputMode.Keyboard)
    }

    @Test
    fun `a handheld window switches to keyboard mode`() {
        composeRule.setContent { Screen(FormFactor.Handheld) }
        composeRule.waitForIdle()

        assertThat(checkNotNull(manager).inputMode).isEqualTo(InputMode.Keyboard)
    }

    @Test
    fun `a phone window stays in touch mode`() {
        composeRule.setContent { Screen(FormFactor.Phone) }
        composeRule.waitForIdle()

        assertThat(checkNotNull(manager).inputMode).isEqualTo(InputMode.Touch)
    }

    @Composable
    private fun Screen(formFactor: FormFactor) {
        val current = LocalInputModeManager.current
        SideEffect { manager = current }
        BorealisTheme(formFactor) {
            Column {
                Text("${recompositions.intValue}")
                Box(Modifier.testTag(FIRST).size(40.dp).focusRequester(first).clickable {})
                Box(Modifier.testTag(SECOND).size(40.dp).initialFocus(second).clickable {})
            }
        }
    }

    private companion object {
        const val FIRST = "first"
        const val SECOND = "second"
    }
}

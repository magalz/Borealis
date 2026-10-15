package io.github.magalz.borealis.designsystem.focus

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.assertIsFocused
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.unit.dp
import androidx.test.ext.junit.runners.AndroidJUnit4
import io.github.magalz.borealis.designsystem.theme.BorealisTheme
import io.github.magalz.borealis.designsystem.theme.FormFactor
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/** The screen-scoped guard: a screen takes its initial focus once, not once per element. */
@RunWith(AndroidJUnit4::class)
class InitialFocusStateTest {
    @get:Rule val composeRule = createComposeRule()
    private val anchor = FocusRequester()
    private val early = FocusRequester()
    private val late = FocusRequester()

    @Test
    fun `the first element with the state takes the focus`() {
        composeRule.setContent {
            val state = rememberInitialFocusState()
            Screen { Item(EARLY, early, state) }
        }
        composeRule.waitForIdle()

        composeRule.onNodeWithTag(EARLY).assertIsFocused()
    }

    @Test
    fun `an element that appears later does not take the focus the user moved`() {
        val showLate = mutableStateOf(false)
        composeRule.setContent {
            val state = rememberInitialFocusState()
            Screen {
                Item(EARLY, early, state)
                if (showLate.value) Item(LATE, late, state)
            }
        }
        composeRule.waitForIdle()
        composeRule.runOnIdle { anchor.requestFocus() }

        showLate.value = true
        composeRule.waitForIdle()

        composeRule.onNodeWithTag(ANCHOR).assertIsFocused()
    }

    @Test
    fun `an element that returns after leaving does not take the focus again`() {
        val showEarly = mutableStateOf(true)
        composeRule.setContent {
            val state = rememberInitialFocusState()
            Screen { if (showEarly.value) Item(EARLY, early, state) }
        }
        composeRule.waitForIdle()
        composeRule.runOnIdle { anchor.requestFocus() }

        showEarly.value = false
        composeRule.waitForIdle()
        showEarly.value = true
        composeRule.waitForIdle()

        composeRule.onNodeWithTag(ANCHOR).assertIsFocused()
    }

    @Test
    fun `a new key arms the state again`() {
        val phase = mutableIntStateOf(0)
        composeRule.setContent {
            val state = rememberInitialFocusState(phase.intValue)
            Screen {
                if (phase.intValue == 0) Item(EARLY, early, state) else Item(LATE, late, state)
            }
        }
        composeRule.waitForIdle()
        composeRule.runOnIdle { anchor.requestFocus() }

        phase.intValue = 1
        composeRule.waitForIdle()

        composeRule.onNodeWithTag(LATE).assertIsFocused()
    }

    @Composable
    private fun Screen(content: @Composable () -> Unit) {
        BorealisTheme(FormFactor.Tv) {
            Column {
                Box(Modifier.testTag(ANCHOR).size(40.dp).focusRequester(anchor).clickable {})
                content()
            }
        }
    }

    @Composable
    private fun Item(tag: String, requester: FocusRequester, state: InitialFocusState) {
        Box(Modifier.testTag(tag).size(40.dp).initialFocus(requester, state).clickable {})
    }

    private companion object {
        const val ANCHOR = "anchor"
        const val EARLY = "early"
        const val LATE = "late"
    }
}

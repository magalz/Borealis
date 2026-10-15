package io.github.magalz.borealis.designsystem.components

import androidx.compose.foundation.layout.Column
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.platform.SoftwareKeyboardController
import androidx.compose.ui.test.assertIsFocused
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performImeAction
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.google.common.truth.Truth.assertThat
import io.github.magalz.borealis.designsystem.theme.BorealisTheme
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config

@RunWith(AndroidJUnit4::class)
@Config(qualifiers = "w360dp-h640dp-xhdpi")
class BorealisTextFieldTest {
    @get:Rule val composeRule = createComposeRule()

    @Test
    fun `Done without a callback keeps the default behaviour and hides the keyboard`() {
        composeRule.setContent { Field() }
        composeRule.onNodeWithText("Address").performClick()

        composeRule.onNodeWithText("Address").performImeAction()

        assertThat(keyboard.hidden).isEqualTo(1)
    }

    @Test
    fun `Done with a callback calls it and still hides the keyboard`() {
        var done = 0
        composeRule.setContent { Field(onDone = { done++ }) }
        composeRule.onNodeWithText("Address").performClick()

        composeRule.onNodeWithText("Address").performImeAction()

        assertThat(done).isEqualTo(1)
        assertThat(keyboard.hidden).isEqualTo(1)
    }

    @Test
    fun `Done leaves focus on the field so a D-pad is not thrown out of it`() {
        composeRule.setContent { Field(onDone = {}) }
        composeRule.onNodeWithText("Address").performClick()

        composeRule.onNodeWithText("Address").performImeAction()

        composeRule.onNodeWithText("Address").assertIsFocused()
    }

    /** Counts the requests to hide the keyboard; there is no real one under Robolectric. */
    private class CountingKeyboard : SoftwareKeyboardController {
        var hidden = 0

        override fun show() = Unit

        override fun hide() {
            hidden++
        }
    }

    private val keyboard = CountingKeyboard()

    @Composable
    private fun Field(onDone: (() -> Unit)? = null) {
        var value by remember { mutableStateOf("") }
        CompositionLocalProvider(LocalSoftwareKeyboardController provides keyboard) {
            BorealisTheme {
                Column {
                    BorealisTextField(
                        value = value,
                        onValueChange = { value = it },
                        label = "Address",
                        onDone = onDone,
                    )
                }
            }
        }
    }
}

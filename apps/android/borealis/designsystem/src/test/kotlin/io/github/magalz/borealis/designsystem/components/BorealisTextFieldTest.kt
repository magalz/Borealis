package io.github.magalz.borealis.designsystem.components

import androidx.compose.foundation.layout.Column
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.test.assertIsFocused
import androidx.compose.ui.test.assertIsNotFocused
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
    fun `Done without a callback keeps the default behaviour and clears focus`() {
        composeRule.setContent { Field() }
        composeRule.onNodeWithText("Address").performClick()
        composeRule.onNodeWithText("Address").assertIsFocused()

        composeRule.onNodeWithText("Address").performImeAction()

        composeRule.onNodeWithText("Address").assertIsNotFocused()
    }

    @Test
    fun `Done with a callback calls it and then clears focus`() {
        var done = 0
        composeRule.setContent { Field(onDone = { done++ }) }
        composeRule.onNodeWithText("Address").performClick()

        composeRule.onNodeWithText("Address").performImeAction()

        assertThat(done).isEqualTo(1)
        composeRule.onNodeWithText("Address").assertIsNotFocused()
    }

    @Composable
    private fun Field(onDone: (() -> Unit)? = null) {
        var value by remember { mutableStateOf("") }
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

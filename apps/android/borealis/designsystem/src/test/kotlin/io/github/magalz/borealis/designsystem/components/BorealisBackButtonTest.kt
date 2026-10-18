package io.github.magalz.borealis.designsystem.components

import androidx.compose.runtime.Composable
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.assert
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.assertTouchHeightIsEqualTo
import androidx.compose.ui.test.assertTouchWidthIsEqualTo
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.performClick
import androidx.compose.ui.unit.dp
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.google.common.truth.Truth.assertThat
import io.github.magalz.borealis.designsystem.theme.BorealisTheme
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config

@RunWith(AndroidJUnit4::class)
@Config(qualifiers = "w360dp-h640dp-xhdpi")
class BorealisBackButtonTest {
    @get:Rule val composeRule = createComposeRule()
    private var clicks = 0

    @Test
    fun `a tap goes back`() {
        composeRule.setContent { Specimen() }

        composeRule.onNodeWithContentDescription(DESCRIPTION).performClick()

        assertThat(clicks).isEqualTo(1)
    }

    @Test
    fun `the arrow is announced with the description it was given`() {
        composeRule.setContent { Specimen() }

        composeRule.onNodeWithContentDescription(DESCRIPTION).assertIsDisplayed()
    }

    @Test
    fun `the arrow is announced as a button`() {
        composeRule.setContent { Specimen() }

        composeRule
            .onNodeWithContentDescription(DESCRIPTION)
            .assertIsEnabled()
            .assert(SemanticsMatcher.expectValue(SemanticsProperties.Role, Role.Button))
    }

    @Test
    fun `a disabled arrow does not go back`() {
        composeRule.setContent { Specimen(enabled = false) }

        composeRule.onNodeWithContentDescription(DESCRIPTION).assertIsNotEnabled().performClick()

        assertThat(clicks).isEqualTo(0)
    }

    @Test
    fun `the arrow is a touch target of 48dp`() {
        composeRule.setContent { Specimen() }

        composeRule
            .onNodeWithContentDescription(DESCRIPTION)
            .assertTouchWidthIsEqualTo(48.dp)
            .assertTouchHeightIsEqualTo(48.dp)
    }

    @Composable
    private fun Specimen(enabled: Boolean = true) {
        BorealisTheme {
            BorealisBackButton(onClick = { clicks++ }, DESCRIPTION, enabled = enabled)
        }
    }

    private companion object {
        const val DESCRIPTION = "Back to Hosts"
    }
}

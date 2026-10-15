package io.github.magalz.borealis.designsystem.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.assertContentDescriptionEquals
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.unit.dp
import androidx.test.ext.junit.runners.AndroidJUnit4
import io.github.magalz.borealis.designsystem.assertPixel
import io.github.magalz.borealis.designsystem.theme.BorealisTheme
import io.github.magalz.borealis.designsystem.tokens.BorealisTokens
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config

@RunWith(AndroidJUnit4::class)
@Config(qualifiers = "w360dp-h640dp-xhdpi")
class StatusDotTest {
    @get:Rule val composeRule = createComposeRule()

    @Test
    fun `an online host is a success dot`() {
        composeRule.setContent { Specimen(DotStatus.Online) }

        assertCentre(BorealisTokens.Color.Success)
    }

    @Test
    fun `an offline host is an offline dot`() {
        composeRule.setContent { Specimen(DotStatus.Offline) }

        assertCentre(BorealisTokens.Color.Offline)
    }

    @Test
    fun `an unknown state looks like offline`() {
        composeRule.setContent { Specimen(DotStatus.Unknown) }

        assertCentre(BorealisTokens.Color.Offline)
    }

    @Test
    fun `the dot carries its content description`() {
        composeRule.setContent { Specimen(DotStatus.Online, description = "Online") }

        composeRule.onNodeWithContentDescription("Online").assertContentDescriptionEquals("Online")
    }

    private fun assertCentre(expected: androidx.compose.ui.graphics.Color) {
        // The dot is 8dp wide inside an 8dp padding, so its centre is at 12dp.
        composeRule.onNodeWithTag(FRAME).assertPixel(x = 12f, y = 12f, expected)
    }

    @Composable
    private fun Specimen(status: DotStatus, description: String? = null) {
        BorealisTheme {
            Box(Modifier.testTag(FRAME).background(BorealisTokens.Color.Page).padding(8.dp)) {
                StatusDot(status, contentDescription = description)
            }
        }
    }

    private companion object {
        const val FRAME = "frame"
    }
}

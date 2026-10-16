package io.github.magalz.borealis.designsystem.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.click
import androidx.compose.ui.test.getUnclippedBoundsInRoot
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.unit.dp
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.google.common.truth.Truth.assertThat
import io.github.magalz.borealis.designsystem.assertPixel
import io.github.magalz.borealis.designsystem.theme.BorealisTheme
import io.github.magalz.borealis.designsystem.theme.FormFactor
import io.github.magalz.borealis.designsystem.tokens.BorealisTokens
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config

@RunWith(AndroidJUnit4::class)
class SidePanelTest {
    @get:Rule val composeRule = createComposeRule()
    private var dismissed = 0

    @Test
    @Config(qualifiers = PHONE)
    fun `a tap on the scrim dismisses`() {
        composeRule.setContent { Specimen() }

        // The panel takes the right half or more: the left edge is scrim.
        composeRule.onRoot().performTouchInput { click(Offset(10f, 10f)) }

        assertThat(dismissed).isEqualTo(1)
    }

    @Test
    @Config(qualifiers = PHONE)
    fun `a tap inside the panel does not dismiss`() {
        composeRule.setContent { Specimen() }

        composeRule.onNodeWithText("Inside").performClick()
        composeRule.onNodeWithTag(CONTENT).performClick()

        assertThat(dismissed).isEqualTo(0)
    }

    @Test
    @Config(qualifiers = PHONE)
    fun `the panel is 52 percent of a 360dp parent`() {
        composeRule.setContent { Specimen() }

        assertThat(contentWidth()).isWithin(WIDTH_TOLERANCE).of(187.2f)
    }

    @Test
    @Config(qualifiers = "w600dp-h400dp-xhdpi")
    fun `the panel is 52 percent of a 600dp parent`() {
        composeRule.setContent { Specimen() }

        assertThat(contentWidth()).isWithin(WIDTH_TOLERANCE).of(312f)
    }

    @Test
    @Config(qualifiers = "w1280dp-h720dp-xhdpi")
    fun `the panel is 360dp at most`() {
        composeRule.setContent { Specimen() }

        assertThat(contentWidth()).isWithin(WIDTH_TOLERANCE).of(360f)
    }

    @Test
    @Config(qualifiers = PHONE)
    fun `the panel sits at the right edge with the surface colour`() {
        composeRule.setContent { Specimen() }

        // 24dp from the right edge of the 360dp window and 50dp from the top: inside the panel.
        composeRule
            .onNodeWithTag(FRAME)
            .assertPixel(x = 336f, y = 50f, BorealisTokens.Color.Surface)
    }

    @Test
    @Config(qualifiers = PHONE)
    fun `the content scrolls when it is taller than the panel`() {
        composeRule.setContent {
            Specimen(extra = { repeat(40) { index -> Text("Row $index", Modifier.height(40.dp)) } })
        }

        composeRule.onNodeWithText("Row 39").performScrollTo().assertIsDisplayed()
    }

    private fun contentWidth() =
        composeRule.onNodeWithTag(CONTENT).getUnclippedBoundsInRoot().let {
            it.right.value - it.left.value
        }

    @Composable
    private fun Specimen(extra: @Composable () -> Unit = {}) {
        BorealisTheme(FormFactor.Phone) {
            Box(Modifier.fillMaxSize().testTag(FRAME).background(BorealisTokens.Color.Page)) {
                SidePanel(onDismiss = { dismissed++ }) {
                    Text("Inside")
                    Box(Modifier.fillMaxWidth().height(20.dp).testTag(CONTENT))
                    extra()
                }
            }
        }
    }

    private companion object {
        const val FRAME = "frame"
        const val CONTENT = "content"
        const val PHONE = "w360dp-h640dp-xhdpi"
        const val WIDTH_TOLERANCE = 0.5f
    }
}

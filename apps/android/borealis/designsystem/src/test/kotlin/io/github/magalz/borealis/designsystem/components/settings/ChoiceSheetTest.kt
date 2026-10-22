package io.github.magalz.borealis.designsystem.components.settings

import android.graphics.Bitmap
import android.graphics.Canvas
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.InternalComposeUiApi
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.platform.ViewRootForTest
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.assert
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsFocused
import androidx.compose.ui.test.getUnclippedBoundsInRoot
import androidx.compose.ui.test.isRoot
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performKeyInput
import androidx.compose.ui.test.pressKey
import androidx.compose.ui.test.requestFocus
import androidx.compose.ui.unit.dp
import androidx.test.espresso.Espresso
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.google.common.truth.Truth.assertThat
import com.google.common.truth.Truth.assertWithMessage
import io.github.magalz.borealis.designsystem.testing.textColor
import io.github.magalz.borealis.designsystem.theme.AccentChoice
import io.github.magalz.borealis.designsystem.theme.BorealisLook
import io.github.magalz.borealis.designsystem.theme.BorealisTheme
import io.github.magalz.borealis.designsystem.theme.FormFactor
import io.github.magalz.borealis.designsystem.theme.borealisColors
import kotlin.math.abs
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config

@RunWith(AndroidJUnit4::class)
class ChoiceSheetTest {
    @get:Rule val composeRule = createComposeRule()
    private val selections = mutableListOf<String>()
    private var dismissed = 0

    private val options =
        listOf(
            ChoiceOption("720p", "1280 x 720"),
            ChoiceOption("1080p", "1920 x 1080", summary = "Matches most displays"),
            ChoiceOption("native", "Native"),
        )

    @Test
    @Config(qualifiers = TV)
    fun `the title and every option show`() {
        composeRule.setContent { Specimen(FormFactor.Tv) }

        composeRule.onNodeWithText("Resolution").assertIsDisplayed()
        composeRule.onNodeWithText("1280 x 720").assertIsDisplayed()
        composeRule.onNodeWithText("1920 x 1080").assertIsDisplayed()
        composeRule.onNodeWithText("Matches most displays").assertIsDisplayed()
        composeRule.onNodeWithText("Native").assertIsDisplayed()
    }

    @Test
    @Config(qualifiers = TV)
    fun `the selected option is marked and the others are not`() {
        composeRule.setContent { Specimen(FormFactor.Tv, selected = "1080p") }

        composeRule
            .onNodeWithText("1920 x 1080")
            .assert(SemanticsMatcher.expectValue(SemanticsProperties.Role, Role.RadioButton))
            .assert(SemanticsMatcher.expectValue(SemanticsProperties.Selected, true))
        composeRule
            .onNodeWithText("1280 x 720")
            .assert(SemanticsMatcher.expectValue(SemanticsProperties.Selected, false))
    }

    @Test
    @Config(qualifiers = TV)
    fun `the label of the selected option is accent-soft and the others are ink`() {
        var look by mutableStateOf(BorealisLook())
        composeRule.setContent { Specimen(FormFactor.Tv, selected = "1080p", look = look) }

        for (accent in AccentChoice.entries) {
            look = BorealisLook(accent = accent)
            composeRule.waitForIdle()
            val colors = borealisColors(look)

            assertWithMessage("$accent")
                .that(composeRule.textColor("1920 x 1080"))
                .isEqualTo(colors.accentSoft)
            assertThat(composeRule.textColor("1280 x 720")).isEqualTo(colors.ink)
        }
    }

    @Test
    @Config(qualifiers = TV)
    fun `the check of the selected option is accent-soft and not the accent`() {
        var look by mutableStateOf(BorealisLook())
        composeRule.setContent { Specimen(FormFactor.Tv, selected = "1080p", look = look) }

        for (accent in AccentChoice.entries) {
            look = BorealisLook(accent = accent)
            composeRule.waitForIdle()
            val colors = borealisColors(look)

            val soft = checkPixels(colors.accentSoft)
            val plain = checkPixels(colors.accent)
            assertWithMessage("$accent: pixels of accent-soft").that(soft).isAtLeast(CHECK_PIXELS)
            assertWithMessage("$accent: pixels of the accent").that(plain).isLessThan(CHECK_PIXELS)
        }
    }

    /**
     * How many pixels of [colour] the right end of the selected row has, where its check is. A
     * screenshot of a node does not show a dialog, so the view of its window is drawn on a bitmap.
     */
    @OptIn(InternalComposeUiApi::class)
    private fun checkPixels(colour: Color): Int {
        val view =
            (composeRule.onAllNodes(isRoot())[1].fetchSemanticsNode().root as ViewRootForTest).view
        val image = Bitmap.createBitmap(view.width, view.height, Bitmap.Config.ARGB_8888)
        view.draw(Canvas(image))
        val row = composeRule.onNodeWithText("1920 x 1080").getUnclippedBoundsInRoot()
        var count = 0
        for (y in (row.top.value * DENSITY).toInt() until (row.bottom.value * DENSITY).toInt()) {
            for (x in
                ((row.right.value - CHECK_ZONE_DP) * DENSITY).toInt() until
                    (row.right.value * DENSITY).toInt()) {
                val pixel = Color(image.getPixel(x, y))
                val distance =
                    abs(pixel.red - colour.red) +
                        abs(pixel.green - colour.green) +
                        abs(pixel.blue - colour.blue)
                if (distance < TOLERANCE) count++
            }
        }
        return count
    }

    @Test
    @Config(qualifiers = TV)
    fun `the selected option has the initial focus on a TV`() {
        composeRule.setContent { Specimen(FormFactor.Tv, selected = "1080p") }
        composeRule.waitForIdle()

        composeRule.onNodeWithText("1920 x 1080").assertIsFocused()
    }

    @Test
    @Config(qualifiers = TV)
    fun `with no option selected the first has the initial focus`() {
        composeRule.setContent { Specimen(FormFactor.Tv, selected = "unknown") }
        composeRule.waitForIdle()

        composeRule.onNodeWithText("1280 x 720").assertIsFocused()
    }

    @Test
    @Config(qualifiers = TV)
    fun `OK on another option returns its key and closes`() {
        composeRule.setContent { Specimen(FormFactor.Tv, selected = "1080p") }
        composeRule.waitForIdle()
        composeRule.onNodeWithText("Native").requestFocus()

        composeRule.onNodeWithText("Native").performKeyInput { pressKey(Key.DirectionCenter) }

        assertThat(selections).containsExactly("native")
        assertThat(dismissed).isEqualTo(1)
    }

    @Test
    @Config(qualifiers = PHONE)
    fun `a tap returns the key and closes`() {
        composeRule.setContent { Specimen(FormFactor.Phone, selected = "1080p") }
        composeRule.waitForIdle()

        composeRule.onNodeWithText("1280 x 720").performClick()

        assertThat(selections).containsExactly("720p")
        assertThat(dismissed).isEqualTo(1)
    }

    @Test
    @Config(qualifiers = TV)
    fun `Back closes the dialog without a choice`() {
        composeRule.setContent { Specimen(FormFactor.Tv) }
        composeRule.waitForIdle()

        Espresso.pressBack()
        composeRule.waitForIdle()

        assertThat(dismissed).isEqualTo(1)
        assertThat(selections).isEmpty()
    }

    @Test
    @Config(qualifiers = PHONE)
    fun `Back closes the bottom sheet without a choice`() {
        composeRule.setContent { Specimen(FormFactor.Phone) }
        composeRule.waitForIdle()

        Espresso.pressBack()
        composeRule.waitForIdle()

        assertThat(dismissed).isEqualTo(1)
        assertThat(selections).isEmpty()
    }

    @Test
    @Config(qualifiers = TV)
    fun `the title is a heading and names the dialog`() {
        composeRule.setContent { Specimen(FormFactor.Tv) }

        composeRule
            .onNodeWithText("Resolution")
            .assert(SemanticsMatcher.keyIsDefined(SemanticsProperties.Heading))
        composeRule
            .onNode(SemanticsMatcher.keyIsDefined(SemanticsProperties.PaneTitle))
            .assert(SemanticsMatcher.expectValue(SemanticsProperties.PaneTitle, "Resolution"))
    }

    @Test
    @Config(qualifiers = TV)
    fun `the options are one selectable group`() {
        composeRule.setContent { Specimen(FormFactor.Tv) }

        composeRule
            .onNode(SemanticsMatcher.keyIsDefined(SemanticsProperties.SelectableGroup))
            .assertExists()
    }

    @Test
    @Config(qualifiers = TV)
    fun `the first option row starts at least 16dp under the title so its outline has room`() {
        composeRule.setContent { Specimen(FormFactor.Tv) }

        val title = composeRule.onNodeWithText("Resolution").getUnclippedBoundsInRoot()
        val first = composeRule.onNodeWithText("1280 x 720").getUnclippedBoundsInRoot()

        // The row of the option, not its text: the outline reaches 5dp above the row.
        assertThat((first.top - title.bottom).value).isAtLeast(16f)
    }

    @Test
    @Config(qualifiers = HANDHELD)
    fun `on a handheld the sheet takes 60 percent of the window`() {
        composeRule.setContent { Specimen(FormFactor.Handheld) }

        // An option fills the dialog less the 8dp list padding on each side.
        val option = composeRule.onNodeWithText("1280 x 720").getUnclippedBoundsInRoot()

        assertThat(option.right - option.left).isEqualTo(464.dp)
    }

    @Test
    @Config(qualifiers = TV)
    fun `on a TV the sheet is a compact dialog`() {
        composeRule.setContent { Specimen(FormFactor.Tv) }

        val option = composeRule.onNodeWithText("1280 x 720").getUnclippedBoundsInRoot()

        assertThat(option.right - option.left).isEqualTo(404.dp)
    }

    @Composable
    private fun Specimen(
        formFactor: FormFactor,
        selected: String = "720p",
        look: BorealisLook = BorealisLook(),
    ) {
        BorealisTheme(formFactor, look) {
            ChoiceSheet(
                title = "Resolution",
                options = options,
                selected = selected,
                onSelect = { selections += it },
                onDismiss = { dismissed++ },
            )
        }
    }

    private companion object {
        const val HANDHELD = "w800dp-h360dp-land-xhdpi"
        const val PHONE = "w360dp-h640dp-xhdpi"
        const val TV = "w960dp-h540dp-land-television-xhdpi"

        // The tests run at xhdpi, where one dp is two pixels.
        const val DENSITY = 2

        /** The width at the right end of a row, in dp, where its check is. */
        const val CHECK_ZONE_DP = 48f

        /** The pixels of one colour that a check fills, and the edges of a label do not reach. */
        const val CHECK_PIXELS = 8
        const val TOLERANCE = 0.02f
    }
}

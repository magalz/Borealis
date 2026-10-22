package io.github.magalz.borealis.designsystem.components

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.compositeOver
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.google.common.truth.Truth.assertWithMessage
import io.github.magalz.borealis.designsystem.glass.AA_RATIO
import io.github.magalz.borealis.designsystem.glass.LAID
import io.github.magalz.borealis.designsystem.glass.contrast
import io.github.magalz.borealis.designsystem.glass.luminance
import io.github.magalz.borealis.designsystem.testing.textColor
import io.github.magalz.borealis.designsystem.theme.AccentChoice
import io.github.magalz.borealis.designsystem.theme.BorealisColors
import io.github.magalz.borealis.designsystem.theme.BorealisLook
import io.github.magalz.borealis.designsystem.theme.BorealisTheme
import io.github.magalz.borealis.designsystem.theme.borealisColors
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config

/**
 * The two texts that were the last to be drawn in the accent, which is under 4.5:1 over a glass in
 * violet (3.21:1 at worst, `SelectedLabelOnGlassTest`): the number of the current step of a
 * [StepList], and the label of a latched [KeyboardKey]. Both stand on the tint of the accent. They
 * keep 4.5:1 over the brightest glass with that tint laid on it, for every accent (the backdrops
 * are those of `SelectedBackdrops.kt`), and over every solid fill in High contrast, where the glass
 * is solid.
 */
@RunWith(AndroidJUnit4::class)
@Config(qualifiers = "w360dp-h640dp-xhdpi")
class AccentTextContrastTest {
    @get:Rule val composeRule = createComposeRule()

    private var look by mutableStateOf(BorealisLook())
    private var started = false

    @Test
    fun `the number of the current step keeps 4_5 to 1 over its tint on the glass for every accent`() {
        for (accent in AccentChoice.entries) {
            showStep(BorealisLook(accent = accent))

            assertOverGlass("the current step in $accent", accent, composeRule.textColor(CURRENT))
        }
    }

    @Test
    fun `the number of the current step keeps 4_5 to 1 over its tint in high contrast`() {
        for (accent in AccentChoice.entries) {
            val highContrast = BorealisLook(accent = accent, highContrast = true)
            showStep(highContrast)

            assertOverSolid(
                "the current step in $accent, high contrast",
                borealisColors(highContrast),
                composeRule.textColor(CURRENT),
            )
        }
    }

    @Test
    fun `the label of a latched key keeps 4_5 to 1 over its tint on the glass for every accent`() {
        for (accent in AccentChoice.entries) {
            showKey(BorealisLook(accent = accent))

            assertOverGlass("the latched key in $accent", accent, composeRule.textColor(KEY))
        }
    }

    @Test
    fun `the label of a latched key keeps 4_5 to 1 over its tint in high contrast`() {
        for (accent in AccentChoice.entries) {
            val highContrast = BorealisLook(accent = accent, highContrast = true)
            showKey(highContrast)

            assertOverSolid(
                "the latched key in $accent, high contrast",
                borealisColors(highContrast),
                composeRule.textColor(KEY),
            )
        }
    }

    /** The text over the glass with the tint laid on it: the brightest backdrop of each style. */
    private fun assertOverGlass(what: String, accent: AccentChoice, text: Color) {
        for ((style, laid) in LAID.getValue(accent)) {
            assertWithMessage("$what over $style: ${contrast(text, laid.standard)}")
                .that(contrast(text, laid.standard))
                .isAtLeast(AA_RATIO)
            assertWithMessage("$what over $style at Reduced: ${contrast(text, laid.reduced)}")
                .that(contrast(text, laid.reduced))
                .isAtLeast(AA_RATIO)
        }
    }

    /** The text over the tint laid on each of the solid fills of high contrast. */
    private fun assertOverSolid(what: String, colors: BorealisColors, text: Color) {
        val fills =
            mapOf(
                "page" to colors.page,
                "surface" to colors.surface,
                "surface-raised" to colors.surfaceRaised,
            )
        for ((name, fill) in fills) {
            val ratio = contrast(text, luminance(colors.accentTint.compositeOver(fill)))
            assertWithMessage("$what over its tint on $name: $ratio")
                .that(ratio)
                .isAtLeast(AA_RATIO)
        }
    }

    private fun showStep(next: BorealisLook) = show(next) { StepList(STEPS, current = 1) }

    private fun showKey(next: BorealisLook) = show(next) { KeyboardKey("Ctrl", {}, latched = true) }

    /**
     * Shows [content] in the [next] look; the first call sets the content, the others change it.
     */
    private fun show(next: BorealisLook, content: @Composable () -> Unit) {
        look = next
        if (!started) {
            started = true
            composeRule.setContent { BorealisTheme(look = look, content = content) }
        }
        composeRule.waitForIdle()
    }

    private companion object {
        val STEPS = listOf("Find the host", "Pair", "Start the app")

        /** The number of the current step, which is the second of the three. */
        const val CURRENT = "2"
        const val KEY = "Ctrl"
    }
}

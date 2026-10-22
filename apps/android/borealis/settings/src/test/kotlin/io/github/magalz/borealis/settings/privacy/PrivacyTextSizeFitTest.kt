package io.github.magalz.borealis.settings.privacy

import androidx.compose.ui.test.junit4.v2.createComposeRule
import io.github.magalz.borealis.designsystem.background.BackgroundStyle
import io.github.magalz.borealis.designsystem.testing.FitCase
import io.github.magalz.borealis.designsystem.testing.assertNoClippedText
import io.github.magalz.borealis.designsystem.testing.fitCases
import io.github.magalz.borealis.designsystem.theme.BorealisLook
import io.github.magalz.borealis.designsystem.theme.FormFactor
import io.github.magalz.borealis.designsystem.theme.TextSize
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.ParameterizedRobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * Privacy and diagnostics at the largest text: 130 percent over a system font scale of 1.3, which
 * is 1.69 of the text that the screen is laid out for (ADR 0057, decision 11). Every text must fit
 * where it is, or the screen scrolls.
 */
@RunWith(ParameterizedRobolectricTestRunner::class)
class PrivacyTextSizeFitTest(private val case: FitCase) {
    @get:Rule val composeRule = createComposeRule()

    @Test
    @Config(qualifiers = PHONE, fontScale = SYSTEM_SCALE)
    fun `Privacy on a phone fits`() = assertFits(FormFactor.Phone)

    @Test
    @Config(qualifiers = TV, fontScale = SYSTEM_SCALE)
    fun `Privacy on a TV fits`() = assertFits(FormFactor.Tv)

    private fun assertFits(formFactor: FormFactor) {
        val largest =
            BorealisLook(textSize = TextSize.Largest, font = case.font, textSpacing = case.spacing)
        composeRule.showPrivacy(formFactor, largest, BackgroundStyle.Off)

        composeRule.assertNoClippedText()
    }

    companion object {
        @JvmStatic
        @ParameterizedRobolectricTestRunner.Parameters(name = "{0}")
        fun cases(): List<FitCase> = fitCases()

        private const val SYSTEM_SCALE = 1.3f
    }
}

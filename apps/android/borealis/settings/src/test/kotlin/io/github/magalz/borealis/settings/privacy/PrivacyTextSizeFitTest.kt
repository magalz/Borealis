package io.github.magalz.borealis.settings.privacy

import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.test.ext.junit.runners.AndroidJUnit4
import io.github.magalz.borealis.designsystem.background.BackgroundStyle
import io.github.magalz.borealis.designsystem.testing.assertNoClippedText
import io.github.magalz.borealis.designsystem.theme.BorealisLook
import io.github.magalz.borealis.designsystem.theme.FormFactor
import io.github.magalz.borealis.designsystem.theme.TextSize
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config

/**
 * Privacy and diagnostics at the largest text: 130 percent over a system font scale of 1.3, which
 * is 1.69 of the text that the screen is laid out for (ADR 0057, decision 11). Every text must fit
 * where it is, or the screen scrolls.
 */
@RunWith(AndroidJUnit4::class)
class PrivacyTextSizeFitTest {
    @get:Rule val composeRule = createComposeRule()

    @Test
    @Config(qualifiers = PHONE, fontScale = SYSTEM_SCALE)
    fun `Privacy on a phone fits`() = assertFits(FormFactor.Phone)

    @Test
    @Config(qualifiers = TV, fontScale = SYSTEM_SCALE)
    fun `Privacy on a TV fits`() = assertFits(FormFactor.Tv)

    private fun assertFits(formFactor: FormFactor) {
        val largest = BorealisLook(textSize = TextSize.Largest)
        composeRule.showPrivacy(formFactor, largest, BackgroundStyle.Off)

        composeRule.assertNoClippedText()
    }

    private companion object {
        const val SYSTEM_SCALE = 1.3f
    }
}

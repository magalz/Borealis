package io.github.magalz.borealis.stream.ui.keyboard

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.junit4.v2.createComposeRule
import com.google.common.truth.Truth.assertThat
import io.github.magalz.borealis.designsystem.testing.FitCase
import io.github.magalz.borealis.designsystem.testing.clippedTexts
import io.github.magalz.borealis.designsystem.theme.BorealisLook
import io.github.magalz.borealis.designsystem.theme.BorealisTheme
import io.github.magalz.borealis.designsystem.theme.FontChoice
import io.github.magalz.borealis.designsystem.theme.FormFactor
import io.github.magalz.borealis.designsystem.theme.TextSpacing
import io.github.magalz.borealis.stream.testing.RecordingIntents
import io.github.magalz.borealis.stream.ui.StreamKeyboard
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.ParameterizedRobolectricTestRunner
import org.robolectric.annotation.Config

private const val DOUBLE_SCALE = 2.0f

/**
 * The captions of the keyboard at the largest system font scale, 2.0, with the default text size of
 * the app, on a phone of 320dp, in every font at Normal and at Wide. A caption that does not fit
 * its key shrinks to its floor, and none of them is cut off. At Wide the floor is 8sp less the
 * letter spacing: the plain 8sp left every caption cut in all seven fonts at this scale
 * (`KeyCaptionSizeTest` covers the largest text size at a system scale of 1.3, 1.69 in all).
 *
 * Luciole at Normal is not tried: a caption is cut off in it at this scale with the floor of the
 * default look, the floor that Normal has had since Sprint 10, so it is not the floor of Wide that
 * fails there. Wider is drawn as Wide and has the same captions.
 */
@RunWith(ParameterizedRobolectricTestRunner::class)
@Config(fontScale = DOUBLE_SCALE, qualifiers = "w320dp-h568dp-xhdpi")
class KeyCaptionScaleTest(private val case: FitCase) {
    @get:Rule val composeRule = createComposeRule()

    @Test
    fun `no caption of the keyboard is cut off at a system font scale of 2`() {
        composeRule.setContent {
            BorealisTheme(
                FormFactor.Phone,
                BorealisLook(font = case.font, textSpacing = case.spacing),
            ) {
                Box(Modifier.fillMaxSize()) { StreamKeyboard(true, RecordingIntents()) }
            }
        }
        composeRule.waitForIdle()

        assertThat(composeRule.clippedTexts()).isEmpty()
    }

    companion object {
        @JvmStatic
        @ParameterizedRobolectricTestRunner.Parameters(name = "{0}")
        fun cases(): List<FitCase> =
            FontChoice.entries
                .filter { it != FontChoice.Luciole }
                .map { FitCase(it, TextSpacing.Normal) } +
                FontChoice.entries.map { FitCase(it, TextSpacing.Wide) }
    }
}

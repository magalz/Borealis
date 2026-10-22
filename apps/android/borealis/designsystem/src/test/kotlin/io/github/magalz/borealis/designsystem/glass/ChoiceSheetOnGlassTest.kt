package io.github.magalz.borealis.designsystem.glass

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.google.common.truth.Truth.assertWithMessage
import io.github.magalz.borealis.designsystem.background.BackgroundStyle
import io.github.magalz.borealis.designsystem.components.settings.ChoiceOption
import io.github.magalz.borealis.designsystem.components.settings.ChoiceSheet
import io.github.magalz.borealis.designsystem.testing.textColor
import io.github.magalz.borealis.designsystem.theme.AccentChoice
import io.github.magalz.borealis.designsystem.theme.BorealisLook
import io.github.magalz.borealis.designsystem.theme.BorealisTheme
import io.github.magalz.borealis.designsystem.theme.FormFactor
import io.github.magalz.borealis.designsystem.theme.Transparency
import io.github.magalz.borealis.designsystem.theme.borealisColors
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config

/**
 * The selected option of a choice sheet over the glass, drawn in accent-soft like the other
 * selected items (ADR 0058, decision 5): the sheet is a glass panel over the blurred window, so its
 * label has the backdrops of [LAID] and keeps 4.5:1 for every accent, where the accent is 3.21:1 at
 * worst for violet.
 */
@RunWith(AndroidJUnit4::class)
@Config(qualifiers = "w960dp-h540dp-land-television-xhdpi")
class ChoiceSheetOnGlassTest {
    @get:Rule val composeRule = createComposeRule()

    private var look by mutableStateOf(BorealisLook())
    private var style by mutableStateOf(BackgroundStyle.AuroraCurtains)

    @Composable
    private fun Sheet() {
        BorealisTheme(FormFactor.Tv, look) {
            GlassHostOver(
                style,
                background = { Box(Modifier.fillMaxSize().background(Color.White)) },
            ) {
                ChoiceSheet(
                    title = "Resolution",
                    options =
                        listOf(
                            ChoiceOption("720p", "1280 x 720"),
                            ChoiceOption("1080p", "1920 x 1080"),
                        ),
                    selected = "1080p",
                    onSelect = {},
                    onDismiss = {},
                )
            }
        }
    }

    @Test
    fun `the selected option is accent-soft and keeps 4_5 to 1 over the glass for every accent`() {
        composeRule.setContent { Sheet() }

        for ((accent, styles) in LAID) {
            for ((shown, laid) in styles) {
                for (level in listOf(Transparency.Standard, Transparency.Reduced)) {
                    look = BorealisLook(accent = accent, transparency = level)
                    style = shown
                    composeRule.waitForIdle()
                    val soft = borealisColors(look).accentSoft
                    val backdrop =
                        if (level == Transparency.Standard) laid.standard else laid.reduced

                    val label = composeRule.textColor("1920 x 1080")

                    assertWithMessage("$accent over $shown, $level").that(label).isEqualTo(soft)
                    assertWithMessage("$accent over $shown, $level")
                        .that(contrast(label, backdrop))
                        .isAtLeast(AA_RATIO)
                }
            }
        }
    }

    @Test
    fun `an option that is not selected keeps ink`() {
        composeRule.setContent { Sheet() }
        look = BorealisLook(accent = AccentChoice.White)
        composeRule.waitForIdle()

        val label = composeRule.textColor("1280 x 720")

        assertWithMessage("not selected").that(label).isEqualTo(borealisColors(look).ink)
    }
}

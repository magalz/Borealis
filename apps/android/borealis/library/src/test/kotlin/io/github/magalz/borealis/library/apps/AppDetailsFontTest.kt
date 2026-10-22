package io.github.magalz.borealis.library.apps

import android.content.Context
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.google.common.truth.Truth.assertThat
import io.github.magalz.borealis.designsystem.testing.fontFileNames
import io.github.magalz.borealis.designsystem.testing.textLayout
import io.github.magalz.borealis.designsystem.theme.BorealisLook
import io.github.magalz.borealis.designsystem.theme.BorealisTheme
import io.github.magalz.borealis.designsystem.theme.FontChoice
import io.github.magalz.borealis.designsystem.theme.FormFactor
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config

/** The id of an app in its details draws in the monospaced companion of the font of the look. */
@RunWith(AndroidJUnit4::class)
@Config(qualifiers = "w960dp-h540dp-land-television-xhdpi")
class AppDetailsFontTest {
    @get:Rule val composeRule = createComposeRule()

    private fun files(text: String): List<String> =
        composeRule
            .onNodeWithText(text, useUnmergedTree = true)
            .textLayout()
            .layoutInput
            .style
            .fontFamily
            .fontFileNames(ApplicationProvider.getApplicationContext<Context>().resources)

    private fun show(font: FontChoice) {
        composeRule.setContent {
            BorealisTheme(FormFactor.Tv, BorealisLook(font = font)) {
                AppDetailsDialog(
                    AppItemUiState(
                        id = 1234,
                        name = "Steam Big Picture",
                        running = false,
                        hidden = false,
                        hdrSupported = true,
                    ),
                    onDismiss = {},
                )
            }
        }
        composeRule.waitForIdle()
    }

    @Test
    fun `with Atkinson the id draws in Atkinson Hyperlegible Mono`() {
        show(FontChoice.AtkinsonNext)

        assertThat(files("1234"))
            .containsExactly(
                "atkinson_mono_regular",
                "atkinson_mono_medium",
                "atkinson_mono_semibold",
            )
            .inOrder()
    }

    @Test
    fun `with Atkinson the label of the id draws in Atkinson Hyperlegible Next`() {
        show(FontChoice.AtkinsonNext)

        assertThat(files("Steam Big Picture"))
            .containsExactly(
                "atkinson_next_regular",
                "atkinson_next_medium",
                "atkinson_next_semibold",
            )
            .inOrder()
    }
}

package io.github.magalz.borealis.stream.ui

import androidx.compose.runtime.Composable
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.google.common.truth.Truth.assertThat
import io.github.magalz.borealis.designsystem.theme.BorealisLook
import io.github.magalz.borealis.designsystem.theme.ColourVision
import io.github.magalz.borealis.designsystem.theme.FormFactor
import io.github.magalz.borealis.designsystem.theme.LocalBorealisLook
import io.github.magalz.borealis.designsystem.theme.LocalFormFactor
import io.github.magalz.borealis.designsystem.theme.ReduceMotion
import io.github.magalz.borealis.designsystem.theme.TextSize
import io.github.magalz.borealis.stream.testing.PHONE
import io.github.magalz.borealis.stream.testing.TV
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config

/** The theme that every screen of the stream chrome wears: the window and the look (ADR 0057). */
@RunWith(AndroidJUnit4::class)
class StreamChromeThemeTest {
    @get:Rule val composeRule = createComposeRule()

    private val chosen =
        BorealisLook(
            reduceMotion = ReduceMotion.On,
            textSize = TextSize.Largest,
            highContrast = true,
            colourVision = ColourVision.BlueYellow,
        )
    private var seenLook: BorealisLook? = null
    private var seenFormFactor: FormFactor? = null

    @Composable
    private fun Probe() {
        seenLook = LocalBorealisLook.current
        seenFormFactor = LocalFormFactor.current
    }

    @Test
    @Config(qualifiers = TV)
    fun `the screens get the look that the stream started with and the form factor of the window`() {
        composeRule.setContent { StreamChromeTheme(chosen) { Probe() } }
        composeRule.waitForIdle()

        assertThat(seenLook).isEqualTo(chosen)
        assertThat(seenFormFactor).isEqualTo(FormFactor.Tv)
    }

    @Test
    @Config(qualifiers = PHONE)
    fun `a look of the default gives the screens the look of the app with nothing changed`() {
        composeRule.setContent { StreamChromeTheme(BorealisLook()) { Probe() } }
        composeRule.waitForIdle()

        assertThat(seenLook).isEqualTo(BorealisLook())
        assertThat(seenFormFactor).isEqualTo(FormFactor.Phone)
    }
}

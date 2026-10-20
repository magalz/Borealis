package io.github.magalz.borealis.library.shell

import androidx.compose.runtime.Composable
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.google.common.truth.Truth.assertThat
import io.github.magalz.borealis.designsystem.background.BackgroundStyle
import io.github.magalz.borealis.designsystem.theme.BorealisLook
import io.github.magalz.borealis.designsystem.theme.BorealisTheme
import io.github.magalz.borealis.designsystem.theme.FormFactor
import io.github.magalz.borealis.designsystem.theme.LocalBorealisLook
import io.github.magalz.borealis.designsystem.theme.TextSize
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/** The frame of the Library gives the look to the screens inside it (ADR 0057, decision 12). */
@RunWith(AndroidJUnit4::class)
class LibraryScaffoldLookTest {
    @get:Rule val composeRule = createComposeRule()

    private val chosen = BorealisLook(highContrast = true, textSize = TextSize.Larger)
    private var seen: BorealisLook? = null

    private fun show(look: BorealisLook?, screen: @Composable () -> Unit) {
        val actions = RailActions({}, {}, {})
        composeRule.setContent {
            if (look == null) {
                LibraryScaffold(
                    FormFactor.Tv,
                    RailSelection.Hosts,
                    actions,
                    backgroundStyle = BackgroundStyle.Off,
                ) {
                    screen()
                }
            } else {
                LibraryScaffold(
                    FormFactor.Tv,
                    RailSelection.Hosts,
                    actions,
                    look = look,
                    backgroundStyle = BackgroundStyle.Off,
                ) {
                    screen()
                }
            }
        }
        composeRule.waitForIdle()
    }

    @Composable
    private fun Probe() {
        seen = LocalBorealisLook.current
    }

    @Test
    fun `a frame given a look gives it to the screen`() {
        show(chosen) { Probe() }

        assertThat(seen).isEqualTo(chosen)
    }

    @Test
    fun `a frame given no look gives the default look`() {
        show(null) { Probe() }

        assertThat(seen).isEqualTo(BorealisLook())
    }

    @Test
    fun `a screen that wears its own theme inside the frame keeps the look of the frame`() {
        show(chosen) { BorealisTheme(FormFactor.Tv) { Probe() } }

        assertThat(seen).isEqualTo(chosen)
    }
}

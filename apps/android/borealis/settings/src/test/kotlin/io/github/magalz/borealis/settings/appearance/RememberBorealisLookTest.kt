package io.github.magalz.borealis.settings.appearance

import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.google.common.truth.Truth.assertThat
import io.github.magalz.borealis.designsystem.theme.BorealisLook
import io.github.magalz.borealis.designsystem.theme.TextSize
import io.github.magalz.borealis.designsystem.theme.Transparency
import io.github.magalz.borealis.settings.catalog.Settings
import io.github.magalz.borealis.settings.repository.SettingsSnapshot
import io.github.magalz.borealis.settings.testing.FakeSettingsRepository
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/** The look that the screens draw with is the one that the settings store, as it changes. */
@RunWith(AndroidJUnit4::class)
class RememberBorealisLookTest {
    @get:Rule val composeRule = createComposeRule()

    private var drawn: BorealisLook? = null

    /** How many times the body of the content ran: once, and again for each recomposition. */
    private var runs = 0

    private fun show(repository: FakeSettingsRepository) {
        composeRule.setContent {
            runs++
            drawn = rememberBorealisLook(repository)
        }
        composeRule.waitForIdle()
    }

    @Test
    fun `nothing stored gives the default look`() {
        show(FakeSettingsRepository())

        assertThat(drawn).isEqualTo(BorealisLook())
    }

    @Test
    fun `the stored look is drawn from the first frame`() {
        show(
            FakeSettingsRepository(
                SettingsSnapshot.fromStored(
                    mapOf(Settings.TEXT_SIZE to "larger", Settings.HIGH_CONTRAST to true)
                )
            )
        )

        assertThat(drawn).isEqualTo(BorealisLook(textSize = TextSize.Larger, highContrast = true))
    }

    @Test
    fun `a change of a setting reaches the screens at once`() {
        val repository = FakeSettingsRepository()
        show(repository)

        repository.setNow(Settings.TRANSPARENCY, "off")
        composeRule.waitForIdle()
        assertThat(drawn).isEqualTo(BorealisLook(transparency = Transparency.Off))

        repository.setNow(Settings.THICK_FOCUS, true)
        composeRule.waitForIdle()
        assertThat(drawn)
            .isEqualTo(BorealisLook(transparency = Transparency.Off, thickFocus = true))
    }

    @Test
    fun `an unknown stored value draws the default`() {
        val repository =
            FakeSettingsRepository(
                SettingsSnapshot.fromStored(mapOf(Settings.TRANSPARENCY to "bogus"))
            )
        show(repository)
        assertThat(drawn).isEqualTo(BorealisLook())

        repository.setNow(Settings.TRANSPARENCY, "reduced")
        composeRule.waitForIdle()
        assertThat(drawn?.transparency).isEqualTo(Transparency.Reduced)

        repository.setNow(Settings.TRANSPARENCY, "another-bogus")
        composeRule.waitForIdle()
        assertThat(drawn).isEqualTo(BorealisLook())
    }

    @Test
    fun `a change of a setting that is not in the look composes nothing again`() {
        val repository = FakeSettingsRepository()
        show(repository)
        assertThat(runs).isEqualTo(1)

        repository.setNow(Settings.DEADZONE, 9)
        repository.setNow(Settings.BACKGROUND, "starfield")
        composeRule.waitForIdle()

        assertThat(drawn).isEqualTo(BorealisLook())
        // The look did not change, so nothing that draws it is composed again.
        assertThat(runs).isEqualTo(1)
    }
}

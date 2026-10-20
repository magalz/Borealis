package io.github.magalz.borealis.settings.appearance

import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.google.common.truth.Truth.assertThat
import io.github.magalz.borealis.designsystem.background.BackgroundStyle
import io.github.magalz.borealis.settings.catalog.Settings
import io.github.magalz.borealis.settings.repository.SettingsSnapshot
import io.github.magalz.borealis.settings.testing.FakeSettingsRepository
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/** The style that the shell draws is the one that the Appearance setting stores. */
@RunWith(AndroidJUnit4::class)
class RememberBackgroundStyleTest {
    @get:Rule val composeRule = createComposeRule()

    private var drawn: BackgroundStyle? = null

    /** How many times the body of the content ran: once, and again for each recomposition. */
    private var runs = 0

    private fun show(repository: FakeSettingsRepository) {
        composeRule.setContent {
            runs++
            drawn = rememberBackgroundStyle(repository)
        }
        composeRule.waitForIdle()
    }

    @Test
    fun `nothing stored gives the default style`() {
        show(FakeSettingsRepository())

        assertThat(drawn).isEqualTo(BackgroundStyle.AuroraCurtains)
    }

    @Test
    fun `the stored style is drawn from the first frame`() {
        show(
            FakeSettingsRepository(
                SettingsSnapshot.fromStored(mapOf(Settings.BACKGROUND to "starfield"))
            )
        )

        assertThat(drawn).isEqualTo(BackgroundStyle.Starfield)
    }

    @Test
    fun `a change of the setting reaches the shell at once`() {
        val repository = FakeSettingsRepository()
        show(repository)

        repository.setNow(Settings.BACKGROUND, "northern_lights")
        composeRule.waitForIdle()
        assertThat(drawn).isEqualTo(BackgroundStyle.NorthernLights)

        repository.setNow(Settings.BACKGROUND, "off")
        composeRule.waitForIdle()
        assertThat(drawn).isEqualTo(BackgroundStyle.Off)
    }

    @Test
    fun `an unknown stored value draws the default style`() {
        val repository =
            FakeSettingsRepository(
                SettingsSnapshot.fromStored(mapOf(Settings.BACKGROUND to "bogus"))
            )
        show(repository)
        assertThat(drawn).isEqualTo(BackgroundStyle.Default)

        repository.setNow(Settings.BACKGROUND, "soft_glow")
        composeRule.waitForIdle()
        assertThat(drawn).isEqualTo(BackgroundStyle.SoftGlow)

        repository.setNow(Settings.BACKGROUND, "another-bogus")
        composeRule.waitForIdle()
        assertThat(drawn).isEqualTo(BackgroundStyle.Default)
    }

    @Test
    fun `a change of another setting does not change the style`() {
        val repository = FakeSettingsRepository()
        show(repository)
        assertThat(runs).isEqualTo(1)

        repository.setNow(Settings.DEADZONE, 9)
        composeRule.waitForIdle()

        assertThat(drawn).isEqualTo(BackgroundStyle.Default)
        // The style did not change, so nothing that draws it is composed again.
        assertThat(runs).isEqualTo(1)
    }
}

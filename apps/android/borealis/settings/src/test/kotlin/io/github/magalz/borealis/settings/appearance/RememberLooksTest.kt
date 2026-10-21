package io.github.magalz.borealis.settings.appearance

import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.google.common.truth.Truth.assertThat
import io.github.magalz.borealis.designsystem.background.BackgroundStyle
import io.github.magalz.borealis.designsystem.theme.BorealisLook
import io.github.magalz.borealis.designsystem.theme.TextSize
import io.github.magalz.borealis.designsystem.theme.Transparency
import io.github.magalz.borealis.settings.catalog.Settings
import io.github.magalz.borealis.settings.repository.SettingsSnapshot
import io.github.magalz.borealis.settings.testing.FakeSettingsRepository
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/**
 * The look and the background that the screens draw with are the ones that the settings store, as
 * they change.
 */
@RunWith(AndroidJUnit4::class)
class RememberLooksTest {
    @get:Rule val composeRule = createComposeRule()

    private var drawn: Looks? = null

    /** How many times the body of the content ran: once, and again for each recomposition. */
    private var runs = 0

    private val look: BorealisLook?
        get() = drawn?.look

    private val background: BackgroundStyle?
        get() = drawn?.background

    private fun show(repository: FakeSettingsRepository) {
        composeRule.setContent {
            runs++
            drawn = rememberLooks(repository)
        }
        composeRule.waitForIdle()
    }

    @Test
    fun `nothing stored gives the default look`() {
        show(FakeSettingsRepository())

        assertThat(look).isEqualTo(BorealisLook())
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

        assertThat(look).isEqualTo(BorealisLook(textSize = TextSize.Larger, highContrast = true))
    }

    @Test
    fun `a change of a setting reaches the screens at once`() {
        val repository = FakeSettingsRepository()
        show(repository)

        repository.setNow(Settings.TRANSPARENCY, "off")
        composeRule.waitForIdle()
        assertThat(look).isEqualTo(BorealisLook(transparency = Transparency.Off))

        repository.setNow(Settings.THICK_FOCUS, true)
        composeRule.waitForIdle()
        assertThat(look).isEqualTo(BorealisLook(transparency = Transparency.Off, thickFocus = true))
    }

    @Test
    fun `an unknown stored value draws the default`() {
        val repository =
            FakeSettingsRepository(
                SettingsSnapshot.fromStored(mapOf(Settings.TRANSPARENCY to "bogus"))
            )
        show(repository)
        assertThat(look).isEqualTo(BorealisLook())

        repository.setNow(Settings.TRANSPARENCY, "reduced")
        composeRule.waitForIdle()
        assertThat(look?.transparency).isEqualTo(Transparency.Reduced)

        repository.setNow(Settings.TRANSPARENCY, "another-bogus")
        composeRule.waitForIdle()
        assertThat(look).isEqualTo(BorealisLook())
    }

    @Test
    fun `a change of a setting that is not drawn composes nothing again`() {
        val repository = FakeSettingsRepository()
        show(repository)
        assertThat(runs).isEqualTo(1)

        repository.setNow(Settings.DEADZONE, 9)
        repository.setNow(Settings.FRAME_RATE, "90")
        composeRule.waitForIdle()

        assertThat(look).isEqualTo(BorealisLook())
        assertThat(background).isEqualTo(BackgroundStyle.Default)
        // Nothing that the screens draw with changed, so nothing that draws it is composed again.
        assertThat(runs).isEqualTo(1)
    }

    @Test
    fun `nothing stored gives the default style`() {
        show(FakeSettingsRepository())

        assertThat(background).isEqualTo(BackgroundStyle.AuroraCurtains)
    }

    @Test
    fun `the stored style is drawn from the first frame`() {
        show(
            FakeSettingsRepository(
                SettingsSnapshot.fromStored(mapOf(Settings.BACKGROUND to "starfield"))
            )
        )

        assertThat(background).isEqualTo(BackgroundStyle.Starfield)
    }

    @Test
    fun `a change of the background setting reaches the shell at once, and not the look`() {
        val repository = FakeSettingsRepository()
        show(repository)

        repository.setNow(Settings.BACKGROUND, "northern_lights")
        composeRule.waitForIdle()
        assertThat(background).isEqualTo(BackgroundStyle.NorthernLights)

        repository.setNow(Settings.BACKGROUND, "off")
        composeRule.waitForIdle()
        assertThat(background).isEqualTo(BackgroundStyle.Off)
        assertThat(look).isEqualTo(BorealisLook())
    }

    @Test
    fun `an unknown stored value draws the default style`() {
        val repository =
            FakeSettingsRepository(
                SettingsSnapshot.fromStored(mapOf(Settings.BACKGROUND to "bogus"))
            )
        show(repository)
        assertThat(background).isEqualTo(BackgroundStyle.Default)

        repository.setNow(Settings.BACKGROUND, "soft_glow")
        composeRule.waitForIdle()
        assertThat(background).isEqualTo(BackgroundStyle.SoftGlow)

        repository.setNow(Settings.BACKGROUND, "another-bogus")
        composeRule.waitForIdle()
        assertThat(background).isEqualTo(BackgroundStyle.Default)
    }
}

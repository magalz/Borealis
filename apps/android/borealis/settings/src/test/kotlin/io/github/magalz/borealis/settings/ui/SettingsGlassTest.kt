package io.github.magalz.borealis.settings.ui

import androidx.test.ext.junit.runners.AndroidJUnit4
import io.github.magalz.borealis.designsystem.background.BackgroundStyle
import io.github.magalz.borealis.designsystem.theme.BorealisLook
import io.github.magalz.borealis.designsystem.theme.FormFactor
import io.github.magalz.borealis.designsystem.theme.Transparency
import io.github.magalz.borealis.settings.testing.GlassProbes
import io.github.magalz.borealis.settings.testing.InGlassHost
import io.github.magalz.borealis.settings.testing.Probe
import io.github.magalz.borealis.settings.testing.noAnimations
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config

/**
 * The surfaces of the Settings screen over the still of Flowing light: Standard shows the
 * background through them, Reduced shows it less, and Off, High contrast and the plain page are the
 * `surface` colour (see [GlassProbes]).
 */
abstract class SettingsGlassCases : SettingsScreenTestBase() {
    /** The title beside which the surface is read, and how far across, in dp. */
    protected abstract val probes: List<Probe>

    /** Shows the screen in a glass host of [style] under [look]. */
    protected abstract fun showScreen(look: BorealisLook, style: BackgroundStyle)

    @Before fun theBackgroundIsItsStill() = noAnimations()

    private fun probes() = GlassProbes(composeRule, probes)

    @Test
    fun `Standard shows the background through the surface`() {
        showScreen(BorealisLook(), BackgroundStyle.FlowingLight)

        probes().assertStandard()
    }

    @Test
    fun `Reduced shows the background through less than Standard`() {
        showScreen(BorealisLook(transparency = Transparency.Reduced), BackgroundStyle.FlowingLight)

        probes().assertReduced()
    }

    @Test
    fun `Off is the surface colour`() {
        showScreen(BorealisLook(transparency = Transparency.Off), BackgroundStyle.FlowingLight)

        probes().assertSolid()
    }

    @Test
    fun `High contrast is the surface colour`() {
        showScreen(BorealisLook(highContrast = true), BackgroundStyle.FlowingLight)

        probes().assertSolid()
    }

    @Test
    fun `the plain page is the surface colour`() {
        showScreen(BorealisLook(), BackgroundStyle.Off)

        probes().assertSolid()
    }
}

/** The sections of the phone Settings are glass cards. */
@RunWith(AndroidJUnit4::class)
@Config(qualifiers = PHONE)
class PhoneSettingsGlassTest : SettingsGlassCases() {
    // In the padding of the card, which is at 8dp, 2dp in from its edge, beside the title of the
    // first section; the background is read 2dp outside the card.
    override val probes = listOf(Probe("STREAM", 10f, outside = 6f))

    override fun showScreen(look: BorealisLook, style: BackgroundStyle) {
        show { content -> InGlassHost(FormFactor.Phone, look, style, content) }
    }
}

/** The Privacy and diagnostics row of the phone Settings is a glass row. */
@RunWith(AndroidJUnit4::class)
@Config(qualifiers = PHONE)
class PhonePrivacyRowGlassTest : SettingsGlassCases() {
    // Beside the row, in the padding of its card, which is at 8dp: 2dp in, and 2dp outside.
    override val probes = listOf(Probe("Privacy and diagnostics", 10f, outside = 6f))

    // With no group, the row is the first thing under the bar, where the still is bright.
    override fun showScreen(look: BorealisLook, style: BackgroundStyle) {
        val state = viewModel().uiState.value.copy(groups = emptyList())
        composeRule.setContent {
            InGlassHost(FormFactor.Phone, look, style) {
                SettingsScreen(
                    state = state,
                    formFactor = FormFactor.Phone,
                    actions = SettingsActions({ _, _ -> }, { _, _ -> }, { _, _ -> }, {}),
                    onBack = {},
                    onOpenPrivacy = {},
                    privacyPane = {},
                    about = testAbout(),
                )
            }
        }
        composeRule.waitForIdle()
    }
}

/** The group list and the pane of the TV Settings are a glass panel each. */
@RunWith(AndroidJUnit4::class)
@Config(qualifiers = TV)
class TvSettingsGlassTest : SettingsGlassCases() {
    // In the padding of the group list, which is at the 34dp gutter, beside a group; and in the
    // padding of the pane, which is 12dp on from the 220dp list, at 266dp, beside the first row.
    // Each is read 2dp in from the edge of its panel, and the background 2dp outside it.
    override val probes =
        listOf(Probe("Video", 36f, outside = 32f), Probe("Resolution", 268f, outside = 264f))

    override fun showScreen(look: BorealisLook, style: BackgroundStyle) {
        asTelevision()
        show { content -> InGlassHost(FormFactor.Tv, look, style, content) }
    }
}

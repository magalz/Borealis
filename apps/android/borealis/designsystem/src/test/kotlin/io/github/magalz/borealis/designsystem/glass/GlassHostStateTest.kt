package io.github.magalz.borealis.designsystem.glass

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.google.common.truth.Truth.assertThat
import io.github.magalz.borealis.designsystem.background.BackgroundStyle
import io.github.magalz.borealis.designsystem.background.FakeBackgroundPlayers
import io.github.magalz.borealis.designsystem.background.LocalBackgroundPlayerFactory
import io.github.magalz.borealis.designsystem.background.TestLifecycle
import io.github.magalz.borealis.designsystem.theme.BorealisLook
import io.github.magalz.borealis.designsystem.theme.LocalBorealisLook
import io.github.magalz.borealis.designsystem.theme.Transparency
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config

/**
 * What a [GlassHost] keeps when the glass changes under it. The Library puts its whole navigation
 * in the host, so a content that is made again loses the scroll, the focus, what is typed and the
 * clip that plays: the look and the background style change in Settings while the screen is on.
 */
@RunWith(AndroidJUnit4::class)
@Config(qualifiers = "w360dp-h640dp-xhdpi")
class GlassHostStateTest {
    @get:Rule val composeRule = createComposeRule()

    private val players = FakeBackgroundPlayers()
    private val lifecycle = TestLifecycle()
    private var look by mutableStateOf(BorealisLook())
    private var style by mutableStateOf(BackgroundStyle.AuroraCurtains)
    private var contentStarts = 0
    private var backgroundStarts = 0

    @Test
    fun `the content keeps its state when the transparency goes from Standard to Off and back`() {
        show()

        change { look = BorealisLook(transparency = Transparency.Off) }
        change { look = BorealisLook() }

        assertThat(contentStarts).isEqualTo(1)
        assertThat(backgroundStarts).isEqualTo(1)
    }

    @Test
    fun `the content keeps its state when high contrast comes on and goes off`() {
        show()

        change { look = BorealisLook(highContrast = true) }
        change { look = BorealisLook() }

        assertThat(contentStarts).isEqualTo(1)
        assertThat(backgroundStarts).isEqualTo(1)
    }

    @Test
    fun `the content keeps its state when the background goes to the plain page and back`() {
        show()

        change { style = BackgroundStyle.Off }
        change { style = BackgroundStyle.AuroraCurtains }

        assertThat(contentStarts).isEqualTo(1)
        assertThat(backgroundStarts).isEqualTo(1)
    }

    @Test
    fun `the content keeps its state when the transparency goes from Standard to Reduced`() {
        show()

        change { look = BorealisLook(transparency = Transparency.Reduced) }

        assertThat(contentStarts).isEqualTo(1)
        assertThat(backgroundStarts).isEqualTo(1)
    }

    @Test
    fun `the content keeps its state when Reduced goes to Off`() {
        show()
        change { look = BorealisLook(transparency = Transparency.Reduced) }

        change { look = BorealisLook(transparency = Transparency.Off) }

        assertThat(contentStarts).isEqualTo(1)
        assertThat(backgroundStarts).isEqualTo(1)
    }

    @Test
    fun `a clip keeps its player when the glass goes off and on`() {
        style = BackgroundStyle.NorthernLights
        showWithClip()
        val player = players.live.single()

        change { look = BorealisLook(transparency = Transparency.Off) }
        change { look = BorealisLook() }

        assertThat(players.created).hasSize(1)
        assertThat(player.released).isFalse()
    }

    /** Shows the host over a background that counts how many times it is started. */
    private fun show() {
        composeRule.setContent {
            CompositionLocalProvider(LocalBorealisLook provides look) {
                GlassHostOver(
                    style,
                    background = {
                        remember { ++backgroundStarts }
                        Box(Modifier.fillMaxSize())
                    },
                    modifier = Modifier.testTag(ROOT),
                ) {
                    remember { ++contentStarts }
                }
            }
        }
        composeRule.waitForIdle()
        assertThat(contentStarts).isEqualTo(1)
    }

    /** Shows the host with its own background, which plays a clip on the fake players. */
    private fun showWithClip() {
        composeRule.setContent {
            CompositionLocalProvider(
                LocalBorealisLook provides look,
                LocalBackgroundPlayerFactory provides players,
                LocalLifecycleOwner provides lifecycle,
            ) {
                GlassHost(style, Modifier.testTag(ROOT)) { remember { ++contentStarts } }
            }
        }
        composeRule.waitForIdle()
    }

    private fun change(edit: () -> Unit) {
        composeRule.runOnIdle(edit)
        composeRule.waitForIdle()
    }

    private companion object {
        const val ROOT = "root"
    }
}

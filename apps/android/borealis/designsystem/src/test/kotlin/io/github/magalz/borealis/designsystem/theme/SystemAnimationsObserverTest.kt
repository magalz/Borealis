package io.github.magalz.borealis.designsystem.theme

import android.content.Context
import android.provider.Settings
import androidx.compose.foundation.background
import androidx.compose.foundation.focusable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.unit.dp
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.google.common.truth.Truth.assertThat
import io.github.magalz.borealis.designsystem.assertPixel
import io.github.magalz.borealis.designsystem.focus.borealisFocusIndication
import io.github.magalz.borealis.designsystem.testing.setContentInKeyboardMode
import io.github.magalz.borealis.designsystem.testing.systemAnimationScale
import io.github.magalz.borealis.designsystem.tokens.BorealisTokens
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config

/**
 * What the theme does about the system animation scale: it watches the setting once for all that is
 * under it, and the pieces that ask whether animations are removed read what it found, so a screen
 * does not register an observer for each of its animations.
 */
@RunWith(AndroidJUnit4::class)
@Config(qualifiers = "w360dp-h640dp-xhdpi")
class SystemAnimationsObserverTest {
    @get:Rule val composeRule = createComposeRule()

    private val context: Context = ApplicationProvider.getApplicationContext()
    private val scale = Settings.Global.getUriFor(Settings.Global.ANIMATOR_DURATION_SCALE)
    private val focus = FocusRequester()
    private val elsewhere = FocusRequester()
    private var removed = emptyList<Boolean>()

    private fun observers() = shadowOf(context.contentResolver).getContentObservers(scale)

    @Composable
    private fun Readers(count: Int) {
        removed = List(count) { rememberAnimationsRemoved() }
    }

    @Test
    fun `two readers under one theme watch the system scale once`() {
        composeRule.setContent { BorealisTheme { Readers(count = 2) } }
        composeRule.waitForIdle()

        assertThat(observers()).hasSize(1)
    }

    @Test
    fun `a theme inside a theme watches nothing of its own`() {
        composeRule.setContent { BorealisTheme { BorealisTheme { Readers(count = 2) } } }
        composeRule.waitForIdle()

        assertThat(observers()).hasSize(1)
    }

    @Test
    fun `the watch is let go when the theme leaves`() {
        var shown by mutableStateOf(true)
        composeRule.setContent { if (shown) BorealisTheme { Readers(count = 2) } }
        composeRule.waitForIdle()
        assertThat(observers()).hasSize(1)

        shown = false
        composeRule.waitForIdle()

        assertThat(observers()).isEmpty()
    }

    @Test
    fun `readers under the theme follow a change of the system scale while composed`() {
        systemAnimationScale(1f)
        composeRule.setContent { BorealisTheme { Readers(count = 2) } }
        composeRule.waitForIdle()
        assertThat(removed).containsExactly(false, false)

        systemAnimationScale(0f)
        composeRule.waitForIdle()
        assertThat(removed).containsExactly(true, true)

        systemAnimationScale(1f)
        composeRule.waitForIdle()
        assertThat(removed).containsExactly(false, false)
    }

    @Test
    fun `a reader outside any theme still follows the system scale`() {
        systemAnimationScale(1f)
        composeRule.setContent { Readers(count = 1) }
        composeRule.waitForIdle()
        assertThat(removed).containsExactly(false)

        systemAnimationScale(0f)
        composeRule.waitForIdle()

        assertThat(removed).containsExactly(true)
    }

    @Test
    fun `a tile that takes focus after the system removed the animations scales at once`() {
        systemAnimationScale(1f)
        composeRule.setContentInKeyboardMode {
            BorealisTheme {
                Column {
                    Box(Modifier.testTag(FRAME).background(PAGE).padding(24.dp)) {
                        Box(
                            Modifier.size(100.dp)
                                .borealisFocusIndication(
                                    RoundedCornerShape(8.dp),
                                    scaleOnFocus = true,
                                )
                                .background(FILL, RoundedCornerShape(8.dp))
                                .focusRequester(focus)
                                .focusable()
                        )
                    }
                    Box(Modifier.size(10.dp).focusRequester(elsewhere).focusable())
                }
            }
        }
        composeRule.runOnIdle { elsewhere.requestFocus() }
        composeRule.waitForIdle()
        systemAnimationScale(0f)
        composeRule.waitForIdle()
        composeRule.mainClock.autoAdvance = false

        composeRule.runOnIdle { focus.requestFocus() }
        composeRule.mainClock.advanceTimeBy(EARLY_MILLIS)

        // The tile has grown past its 24dp frame margin: a scale in progress would not have.
        composeRule.onNodeWithTag(FRAME).assertPixel(x = 74f, y = 24f - 1f, FILL)
    }

    private companion object {
        const val FRAME = "frame"

        /** Early enough that a 150 to 250 ms animation has not reached the tile scale. */
        const val EARLY_MILLIS = 32L
        val PAGE = BorealisTokens.Color.Page
        val FILL = BorealisTokens.Color.SurfaceRaised
    }
}

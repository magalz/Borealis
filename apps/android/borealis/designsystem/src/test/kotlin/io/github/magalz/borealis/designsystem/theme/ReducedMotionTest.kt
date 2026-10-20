package io.github.magalz.borealis.designsystem.theme

import android.content.Context
import android.provider.Settings
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.google.common.truth.Truth.assertThat
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Shadows.shadowOf

/**
 * Whether animations are removed: the setting Reduce motion of the app decides, and Follow system
 * hands the decision to the system animation scale, which is read again when it changes.
 */
@RunWith(AndroidJUnit4::class)
class ReducedMotionTest {
    @get:Rule val composeRule = createComposeRule()

    private val context: Context = ApplicationProvider.getApplicationContext()
    private var removed: Boolean? = null

    private fun systemScale(scale: Float) {
        Settings.Global.putFloat(
            context.contentResolver,
            Settings.Global.ANIMATOR_DURATION_SCALE,
            scale,
        )
    }

    private fun show(reduceMotion: ReduceMotion) {
        composeRule.setContent { Under(BorealisLook(reduceMotion = reduceMotion)) }
        composeRule.waitForIdle()
    }

    @Composable
    private fun Under(look: BorealisLook) {
        CompositionLocalProvider(LocalBorealisLook provides look) {
            removed = rememberAnimationsRemoved()
        }
    }

    @Test
    fun `following the system, a scale of zero removes the animations`() {
        systemScale(0f)

        show(ReduceMotion.FollowSystem)

        assertThat(removed).isTrue()
    }

    @Test
    fun `following the system, a scale of one keeps the animations`() {
        systemScale(1f)

        show(ReduceMotion.FollowSystem)

        assertThat(removed).isFalse()
    }

    @Test
    fun `following the system, a scale that only slows the animations keeps them`() {
        systemScale(0.5f)

        show(ReduceMotion.FollowSystem)

        assertThat(removed).isFalse()
    }

    @Test
    fun `following the system, a system that has not set the scale keeps the animations`() {
        show(ReduceMotion.FollowSystem)

        assertThat(removed).isFalse()
    }

    @Test
    fun `On removes the animations when the system keeps them`() {
        systemScale(1f)

        show(ReduceMotion.On)

        assertThat(removed).isTrue()
    }

    @Test
    fun `On removes the animations when the system removes them too`() {
        systemScale(0f)

        show(ReduceMotion.On)

        assertThat(removed).isTrue()
    }

    @Test
    fun `Off keeps the animations also when the system removes them`() {
        systemScale(0f)

        show(ReduceMotion.Off)

        assertThat(removed).isFalse()
    }

    @Test
    fun `Off keeps the animations when the system keeps them`() {
        systemScale(1f)

        show(ReduceMotion.Off)

        assertThat(removed).isFalse()
    }

    @Test
    fun `a change of the system scale while composed changes the answer`() {
        systemScale(1f)
        show(ReduceMotion.FollowSystem)
        assertThat(removed).isFalse()

        systemScale(0f)
        composeRule.waitForIdle()
        assertThat(removed).isTrue()

        systemScale(1f)
        composeRule.waitForIdle()
        assertThat(removed).isFalse()
    }

    @Test
    fun `a change of the system scale does not move On`() {
        systemScale(1f)
        show(ReduceMotion.On)

        systemScale(0f)
        composeRule.waitForIdle()
        systemScale(1f)
        composeRule.waitForIdle()

        assertThat(removed).isTrue()
    }

    @Test
    fun `a change of the system scale does not move Off`() {
        systemScale(1f)
        show(ReduceMotion.Off)

        systemScale(0f)
        composeRule.waitForIdle()

        assertThat(removed).isFalse()
    }

    @Test
    fun `a change of the setting while composed changes the answer`() {
        systemScale(0f)
        var reduceMotion by mutableStateOf(ReduceMotion.FollowSystem)
        composeRule.setContent { Under(BorealisLook(reduceMotion = reduceMotion)) }
        composeRule.waitForIdle()
        assertThat(removed).isTrue()

        reduceMotion = ReduceMotion.Off
        composeRule.waitForIdle()
        assertThat(removed).isFalse()

        reduceMotion = ReduceMotion.On
        composeRule.waitForIdle()
        assertThat(removed).isTrue()
    }

    @Test
    fun `the system scale is watched while composed and let go when the content leaves`() {
        val uri = Settings.Global.getUriFor(Settings.Global.ANIMATOR_DURATION_SCALE)
        val resolver = shadowOf(context.contentResolver)
        var shown by mutableStateOf(true)
        composeRule.setContent { if (shown) Under(BorealisLook()) }
        composeRule.waitForIdle()
        assertThat(resolver.getContentObservers(uri)).isNotEmpty()

        shown = false
        composeRule.waitForIdle()

        assertThat(resolver.getContentObservers(uri)).isEmpty()
    }

    @Test
    fun `the pure rule is On, Off or the system`() {
        for (system in listOf(true, false)) {
            assertThat(animationsRemoved(ReduceMotion.FollowSystem, system)).isEqualTo(system)
            assertThat(animationsRemoved(ReduceMotion.On, system)).isTrue()
            assertThat(animationsRemoved(ReduceMotion.Off, system)).isFalse()
        }
    }
}

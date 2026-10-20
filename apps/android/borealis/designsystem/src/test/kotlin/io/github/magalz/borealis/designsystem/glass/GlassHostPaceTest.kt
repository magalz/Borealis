package io.github.magalz.borealis.designsystem.glass

import android.app.Application
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.google.common.collect.Range
import com.google.common.truth.Truth.assertThat
import io.github.magalz.borealis.designsystem.background.BackgroundStyle
import io.github.magalz.borealis.designsystem.background.CycleLoop
import io.github.magalz.borealis.designsystem.background.LocalCycleLoop
import io.github.magalz.borealis.designsystem.background.runCycleLoop
import io.github.magalz.borealis.designsystem.theme.BorealisLook
import io.github.magalz.borealis.designsystem.theme.FormFactor
import io.github.magalz.borealis.designsystem.theme.LocalBorealisLook
import io.github.magalz.borealis.designsystem.theme.LocalFormFactor
import io.github.magalz.borealis.designsystem.theme.Transparency
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config

/**
 * How fast a [GlassHost] runs its drawn background: the whole way from the host to the frame loop,
 * with the loop of the app on the clock of the test. On a TV the blur is paid for with 24 frames a
 * second instead of 30 (ADR 0057), and nothing else is slowed.
 */
@RunWith(AndroidJUnit4::class)
@Config(qualifiers = "w360dp-h640dp-xhdpi")
class GlassHostPaceTest {
    @get:Rule val composeRule = createComposeRule()

    private var updates = 0
    private val asked = mutableListOf<Int>()

    private val loop = CycleLoop { framesPerSecond, onCycle ->
        asked += framesPerSecond
        runCycleLoop(framesPerSecond, nanoTime = { composeRule.mainClock.currentTime * NANOS }) {
            updates++
            onCycle(it)
        }
    }

    @Test
    fun `a TV with the blur runs the background about 24 times a second`() {
        runForTwoSeconds(FormFactor.Tv)

        assertThat(asked).containsExactly(24)
        assertThat(updates).isIn(Range.closed(2 * 24 - TOLERANCE, 2 * 24 + TOLERANCE))
    }

    @Test
    fun `a phone runs the background about 30 times a second`() {
        runForTwoSeconds(FormFactor.Phone)

        assertThat(asked).containsExactly(30)
        assertThat(updates).isIn(Range.closed(2 * 30 - TOLERANCE, 2 * 30 + TOLERANCE))
    }

    @Test
    fun `a handheld runs the background about 30 times a second`() {
        runForTwoSeconds(FormFactor.Handheld)

        assertThat(asked).containsExactly(30)
    }

    @Test
    fun `a TV runs the background at 30 where the glass is off`() {
        runForTwoSeconds(FormFactor.Tv, BorealisLook(transparency = Transparency.Off))

        assertThat(asked).containsExactly(30)
        assertThat(updates).isIn(Range.closed(2 * 30 - TOLERANCE, 2 * 30 + TOLERANCE))
    }

    @Test
    @Config(sdk = [API_NO_BLUR])
    fun `a TV on API 30, where the glass is the tint only, runs the background at 30`() {
        val app = ApplicationProvider.getApplicationContext<Application>()
        // The battery saver of the background listens with a receiver that older systems guard.
        shadowOf(app)
            .grantPermissions("${app.packageName}.DYNAMIC_RECEIVER_NOT_EXPORTED_PERMISSION")

        runForTwoSeconds(FormFactor.Tv)

        assertThat(asked).containsExactly(30)
    }

    private fun runForTwoSeconds(formFactor: FormFactor, look: BorealisLook = BorealisLook()) {
        composeRule.mainClock.autoAdvance = false
        composeRule.setContent {
            CompositionLocalProvider(
                LocalBorealisLook provides look,
                LocalFormFactor provides formFactor,
                LocalCycleLoop provides loop,
            ) {
                GlassHost(BackgroundStyle.AuroraCurtains) {}
            }
        }
        composeRule.mainClock.advanceTimeBy(START_MILLIS)
        updates = 0
        composeRule.mainClock.advanceTimeBy(TWO_SECONDS_MILLIS)
    }

    private companion object {
        const val API_NO_BLUR = 30
        const val NANOS = 1_000_000L
        const val START_MILLIS = 200L
        const val TWO_SECONDS_MILLIS = 2_000L
        const val TOLERANCE = 4
    }
}

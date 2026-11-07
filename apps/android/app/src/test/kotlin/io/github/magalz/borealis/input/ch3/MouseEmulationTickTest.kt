package io.github.magalz.borealis.input.ch3

import android.view.KeyEvent.KEYCODE_BUTTON_R1
import com.google.common.truth.Truth.assertThat
import com.limelight.preferences.PreferenceConfiguration.AnalogStickForScrolling
import io.github.magalz.borealis.input.events.EventsRig
import io.github.magalz.borealis.input.events.Pad
import io.github.magalz.borealis.testing.OwnedResources
import java.time.Duration
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.shadows.ShadowSystemClock

/**
 * The 16 ms tick of a pad in mouse mode (`mouseEmulationRunnable`): what the held RB does to every
 * stick it reads, the time it counts for a tick that runs late, the period, and the tick that finds
 * the pad out of mouse mode. A full stick at 100 percent speed is 20.48 pixels a tick; with RB held
 * it is 5.12, which the motion rounds down and carries over.
 */
@RunWith(RobolectricTestRunner::class)
class MouseEmulationTickTest {
    private val full = 32766.toShort()

    @get:Rule val owned = OwnedResources()

    private fun rigWith(scrolling: AnalogStickForScrolling) =
        owned.own(
            EventsRig {
                gamepadMouseSpeed = 100
                analogStickForScrolling = scrolling
            }
        )

    private fun EventsRig.mousePad(vararg sticks: Pair<String, Short>): Pad {
        val pad = addPad(7, fields = sticks.toMap())
        handler.setMouseEmulation(true)
        return pad
    }

    // The pixels of ten ticks at 5.12 a tick: the carried part makes the ninth tick 6
    private val slowed = listOf(5, 5, 5, 5, 5, 5, 5, 5, 6, 5)

    @Test
    fun `with the right stick for scrolling RB slows the move of the left stick and the scroll of the right`() {
        val rig = rigWith(AnalogStickForScrolling.RIGHT)
        val pad = rig.mousePad("leftStickX" to full, "rightStickY" to full)
        pad.press(KEYCODE_BUTTON_R1)

        rig.advanceBy(160)

        assertThat(rig.connection.mouse)
            .containsExactlyElementsIn(slowed.flatMap { listOf("move $it 0", "hires scroll $it") })
            .inOrder()
    }

    @Test
    fun `with the left stick for scrolling RB slows the move of the right stick and the scroll of the left`() {
        val rig = rigWith(AnalogStickForScrolling.LEFT)
        val pad = rig.mousePad("rightStickX" to full, "leftStickY" to full)
        pad.press(KEYCODE_BUTTON_R1)

        rig.advanceBy(160)

        assertThat(rig.connection.mouse)
            .containsExactlyElementsIn(slowed.flatMap { listOf("move $it 0", "hires scroll $it") })
            .inOrder()
    }

    @Test
    fun `with no stick for scrolling RB slows the move of both sticks`() {
        val rig = rigWith(AnalogStickForScrolling.NONE)
        val pad = rig.mousePad("leftStickX" to full, "rightStickY" to full)
        pad.press(KEYCODE_BUTTON_R1)

        rig.advanceBy(160)

        assertThat(rig.connection.mouse)
            .containsExactlyElementsIn(slowed.flatMap { listOf("move $it 0", "move 0 -$it") })
            .inOrder()
    }

    @Test
    fun `letting go of RB gives the speed back from the next tick`() {
        val rig = rigWith(AnalogStickForScrolling.NONE)
        val pad = rig.mousePad("leftStickX" to full)
        pad.press(KEYCODE_BUTTON_R1)
        rig.advanceBy(16)
        pad.release(KEYCODE_BUTTON_R1, downTime = rig.now - 100)

        rig.advanceBy(16)

        assertThat(rig.connection.mouse).containsExactly("move 5 0", "move 20 0").inOrder()
    }

    @Test
    fun `a tick that runs late moves the pointer for the time since the last tick`() {
        val rig = rigWith(AnalogStickForScrolling.NONE)
        rig.mousePad("leftStickX" to full)

        // 48 ms at 1280 pixels a second is 61.44 pixels; the tick was due after 16 ms
        ShadowSystemClock.advanceBy(Duration.ofMillis(48))
        rig.advanceBy(0)

        assertThat(rig.connection.mouse).containsExactly("move 61 0")
        // The next tick is 16 ms after the late one, and counts from it
        rig.advanceBy(15)
        assertThat(rig.connection.mouse).hasSize(1)
        rig.advanceBy(1)
        assertThat(rig.connection.mouse).containsExactly("move 61 0", "move 20 0").inOrder()
    }

    @Test
    fun `the tick comes every 16 ms and each one queues the next`() {
        val rig = rigWith(AnalogStickForScrolling.NONE)
        rig.mousePad("leftStickX" to full)

        rig.advanceBy(15)
        assertThat(rig.connection.mouse).isEmpty()
        rig.advanceBy(1)
        assertThat(rig.connection.mouse).hasSize(1)
        rig.advanceBy(15)
        assertThat(rig.connection.mouse).hasSize(1)
        rig.advanceBy(1)
        assertThat(rig.connection.mouse).hasSize(2)
        rig.advanceBy(32)
        assertThat(rig.connection.mouse).hasSize(4)
    }

    @Test
    fun `a tick that finds the pad out of mouse mode sends nothing and queues no other`() {
        val rig = rigWith(AnalogStickForScrolling.NONE)
        val pad = rig.mousePad("leftStickX" to full)
        // The mode ends without the handler removing the queued tick, as a tick already running
        // would find it
        pad.set("mouseEmulationActive", false)
        val before = pad.get("mouseEmulationLastTick")

        rig.advanceBy(16)
        pad.set("mouseEmulationActive", true)
        rig.advanceBy(160)

        assertThat(rig.connection.mouse).isEmpty()
        // The tick that ended did not even count the time
        assertThat(pad.get("mouseEmulationLastTick")).isEqualTo(before)
    }
}

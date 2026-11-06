package io.github.magalz.borealis.input.ch2

import android.view.MotionEvent.AXIS_RZ
import android.view.MotionEvent.AXIS_Z
import com.google.common.truth.Truth.assertThat
import io.github.magalz.borealis.input.events.EventsRig
import io.github.magalz.borealis.input.events.Sent
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

/**
 * `handleAxisSet` for triggers that rest at -1, when the right trigger is the one that is touched
 * first. `AxesCharacterizationTest` has the left trigger touched first; each trigger keeps its own
 * flag, so the right one is mapped to 0 to 1 while the left one is still read as it comes.
 */
@RunWith(RobolectricTestRunner::class)
class RightTriggerIdleNegativeTest {
    @get:Rule val rig = EventsRig()

    private fun idleNegativePad() =
        rig.addPad(
            7,
            fields =
                mapOf(
                    "leftTriggerAxis" to AXIS_Z,
                    "rightTriggerAxis" to AXIS_RZ,
                    "triggersIdleNegative" to true,
                    "triggerDeadzone" to 0.13f,
                ),
        )

    @Test
    fun `the right trigger is mapped once it is used, and the left one still is not`() {
        val idle = idleNegativePad()
        fun send(z: Float, rz: Float): Sent {
            idle.motion(mapOf(AXIS_Z to z, AXIS_RZ to rz))
            return rig.packets.last()
        }

        // The right trigger at rest marks it as used; the left one has sent only zeros
        assertThat(send(0f, -1f)).isEqualTo(Sent(0, 1, 0))
        assertThat(idle.getBoolean("rightTriggerAxisUsed")).isTrue()
        assertThat(idle.getBoolean("leftTriggerAxisUsed")).isFalse()
        // The range of the right trigger is -1 to 1 from then on, which is mapped to 0 to 1
        assertThat(send(0f, 1f).rightTrigger).isEqualTo(-1)
        assertThat(send(0f, 0f)).isEqualTo(Sent(0, 1, 0, rightTrigger = 127))
    }
}

package com.limelight.binding.input.touch

import com.google.common.truth.Truth.assertThat
import io.github.magalz.borealis.connection.ShadowRecordingBridge
import java.util.concurrent.atomic.AtomicReference
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * What the trackpad context sends to the host as the finger moves: the deltas scaled by the size of
 * the view (rounding, the zero deltas, the short wraps), the absolute mouse mode, and the factors
 * that each touch down reads. `Game` calls the contexts in a fixed order (every context gets the
 * pointer count first, then the touch goes to the context of its pointer), and these tests follow
 * it.
 */
@RunWith(RobolectricTestRunner::class)
@Config(shadows = [ShadowRecordingBridge::class])
class RelativeTouchContextTest {
    private val rig = TouchRig()

    @Test
    fun `the context knows its pointer`() {
        assertThat(rig.relative(0).actionIndex).isEqualTo(0)
        assertThat(rig.relative(1).actionIndex).isEqualTo(1)
    }

    @Test
    fun `the timers run on the main looper, whichever thread built the context`() {
        val built = AtomicReference<RelativeTouchContext>()
        Thread { built.set(rig.relative()) }.apply {
            start()
            join()
        }
        val context = built.get()
        assertThat(context).isNotNull()

        context.start(100, 100)
        rig.advance(650)

        assertThat(rig.sent()).containsExactly("down LEFT")
    }

    // The deltas

    @Test
    fun `a move is the touch delta times the reference size over the view size, per axis`() {
        val rig = TouchRig(width = 2560, height = 360)
        val context = rig.relative()
        context.start(100, 100)

        context.moveTo(110, 110)
        context.moveTo(100, 100)

        assertThat(rig.sent()).containsExactly("sendMouseMove(5, 20)", "sendMouseMove(-5, -20)")
            .inOrder()
    }

    @Test
    fun `a view smaller than the reference scales a delta up and rounds it`() {
        val rig = TouchRig(width = 1000, height = 720)
        val context = rig.relative()
        context.start(100, 100)

        context.moveTo(101, 100) // 1 * 1.28 = 1.28
        context.moveTo(111, 100) // 10 * 1.28 = 12.8
        context.moveTo(112, 100) // 1 * 1.28 = 1.28

        assertThat(rig.sent())
            .containsExactly("sendMouseMove(1, 0)", "sendMouseMove(13, 0)", "sendMouseMove(1, 0)")
            .inOrder()
    }

    @Test
    fun `at 1920 by 1080 a delta is two thirds of itself, rounded to the nearest`() {
        val rig = TouchRig(width = 1920, height = 1080)
        val context = rig.relative()
        context.start(100, 100)

        context.moveTo(101, 101) // 0.67
        context.moveTo(103, 103) // 1.33
        context.moveTo(106, 106) // 2

        assertThat(rig.sent())
            .containsExactly("sendMouseMove(1, 1)", "sendMouseMove(1, 1)", "sendMouseMove(2, 2)")
            .inOrder()
    }

    @Test
    fun `a delta of a half rounds up, in both directions`() {
        val rig = TouchRig(width = 2560, height = 1440)
        val context = rig.relative()
        context.start(100, 100)

        // The deltas are 1, -1, 3, 5, -8, -3 and -5, which scale to 0.5, 0.5, 1.5, 2.5, 4, 1.5, 2.5
        context.moveTo(101, 101)
        context.moveTo(100, 100)
        context.moveTo(103, 103)
        context.moveTo(108, 108)
        context.moveTo(100, 100)
        context.moveTo(97, 97)
        context.moveTo(92, 92)

        assertThat(rig.sent())
            .containsExactly(
                "sendMouseMove(1, 1)",
                "sendMouseMove(-1, -1)",
                "sendMouseMove(2, 2)",
                "sendMouseMove(3, 3)",
                "sendMouseMove(-4, -4)",
                "sendMouseMove(-2, -2)",
                "sendMouseMove(-3, -3)",
            )
            .inOrder()
    }

    @Test
    fun `a delta that scales to zero leaves the last touch where it was until the deltas add up`() {
        val rig = TouchRig(width = 5000, height = 720)
        val context = rig.relative()
        context.start(100, 100)

        context.moveTo(101, 100) // 0.256: zero, the last touch stays at 100
        context.moveTo(102, 100) // 2 from 100: 0.512, one, the last touch is 102
        context.moveTo(103, 100) // 1 from 102: zero
        context.moveTo(106, 100) // 4 from 102: 1.024, one

        assertThat(rig.sent())
            .containsExactly(
                "sendMouseMove(0, 0)",
                "sendMouseMove(1, 0)",
                "sendMouseMove(0, 0)",
                "sendMouseMove(1, 0)",
            )
            .inOrder()
    }

    @Test
    fun `each axis keeps its own last touch when its delta scales to zero`() {
        val rig = TouchRig(width = 1280, height = 5760)
        val context = rig.relative()
        context.start(100, 100)

        context.moveTo(100, 103) // 0.375: zero
        context.moveTo(100, 104) // 4 from 100: 0.5, one
        context.moveTo(100, 105) // 1 from 104: 0.125, zero
        context.moveTo(101, 105) // x is unscaled, and y stays

        assertThat(rig.sent())
            .containsExactly(
                "sendMouseMove(0, 0)",
                "sendMouseMove(0, 1)",
                "sendMouseMove(0, 0)",
                "sendMouseMove(1, 0)",
            )
            .inOrder()
    }

    @Test
    fun `a view of no width makes the x factor infinite and the x delta the int cast of the largest long`() {
        val rig = TouchRig(width = 0, height = 720)
        val context = rig.relative()
        context.start(100, 100)

        context.moveTo(103, 100) // Infinity, which Math.round makes Long.MAX_VALUE: the int is -1
        context.moveTo(100, 100) // the same magnitude, and the sign is flipped for a move left
        context.moveTo(100, 105) // no x delta: 0 times Infinity is NaN, which Math.round makes 0

        assertThat(rig.sent())
            .containsExactly("sendMouseMove(-1, 0)", "sendMouseMove(1, 0)", "sendMouseMove(0, 5)")
            .inOrder()
    }

    @Test
    fun `a view of no height makes the y factor infinite and the y delta the int cast of the largest long`() {
        val rig = TouchRig(width = 1280, height = 0)
        val context = rig.relative()
        context.start(100, 100)

        context.moveTo(100, 103)
        context.moveTo(100, 100)
        context.moveTo(105, 100)

        assertThat(rig.sent())
            .containsExactly("sendMouseMove(0, -1)", "sendMouseMove(0, 1)", "sendMouseMove(5, 0)")
            .inOrder()
    }

    @Test
    fun `the deltas are shorts, negative ones wrap too`() {
        val rig = TouchRig(width = 1, height = 1)
        val context = rig.relative()
        context.start(100, 100)

        // 60 * 1280 = 76800 and 100 * 720 = 72000 do not fit a short: 76800 - 65536 and so on
        context.moveTo(160, 200)
        context.moveTo(100, 100)

        assertThat(rig.sent())
            .containsExactly("sendMouseMove(11264, 6464)", "sendMouseMove(-11264, -6464)")
            .inOrder()
    }

    @Test
    fun `the absolute mouse mode sends the delta with the size of the view`() {
        rig.prefs.absoluteMouseMode = true
        val context = rig.relative()
        context.start(100, 100)

        context.moveTo(105, 107)

        assertThat(rig.sent()).containsExactly("sendMouseMoveAsMousePosition(5, 7, 1280, 720)")
    }

    @Test
    fun `the view size sent in the absolute mouse mode is the size now, and a short`() {
        rig.prefs.absoluteMouseMode = true
        val context = rig.relative()
        context.start(100, 100)

        // The factor was read at the touch down and stays 1: only the size follows the view
        rig.resize(70000, 40000)
        context.moveTo(110, 100)

        assertThat(rig.sent())
            .containsExactly("sendMouseMoveAsMousePosition(10, 0, 4464, -25536)") // 70000 and 40000
    }

    @Test
    fun `the absolute mouse mode is read at every move`() {
        val context = rig.relative()
        context.start(100, 100)

        rig.prefs.absoluteMouseMode = true
        context.moveTo(101, 100)
        rig.prefs.absoluteMouseMode = false
        context.moveTo(102, 100)

        assertThat(rig.sent())
            .containsExactly("sendMouseMoveAsMousePosition(1, 0, 1280, 720)", "sendMouseMove(1, 0)")
            .inOrder()
    }

    @Test
    fun `the factors are read at every touch down and kept until the next`() {
        val context = rig.relative()
        rig.resize(2560, 1440)
        context.start(100, 100)

        context.moveTo(110, 110) // 10 * 0.5
        rig.resize(1280, 720)
        context.moveTo(120, 120) // still 10 * 0.5
        context.touchDownEvent(120, 120, T0, false)
        context.moveTo(130, 130) // now 10 * 1

        assertThat(rig.sent())
            .containsExactly("sendMouseMove(5, 5)", "sendMouseMove(5, 5)", "sendMouseMove(10, 10)")
            .inOrder()
    }

    @Test
    fun `a move to the place of the last touch sends nothing`() {
        val context = rig.relative()
        context.start(100, 100)

        context.moveTo(100, 100)

        assertThat(rig.sent()).isEmpty()
    }

    @Test
    fun `a finger that takes over as the first moves the origin of the deltas`() {
        val context = rig.relative()
        context.start(100, 100)

        assertThat(context.touchDownEvent(500, 500, T0 + 100, false)).isTrue()
        context.moveTo(505, 500)

        assertThat(rig.sent()).containsExactly("sendMouseMove(5, 0)")
    }

    @Test
    fun `a finger that takes over as the first keeps the time of the gesture, so it is no tap later`() {
        val context = rig.relative()
        context.start(100, 100)
        context.touchDownEvent(100, 100, T0 + 300, false)

        context.touchUpEvent(100, 100, T0 + 320)
        rig.advance(1000)

        assertThat(rig.sent()).isEmpty()
    }

    @Test
    fun `a finger that takes over as the first starts no drag`() {
        val context = rig.relative()
        context.start(100, 100)
        context.touchUpEvent(100, 100, T0 + 300) // too slow for a tap, and it removes the timer

        context.touchDownEvent(100, 100, T0 + 400, false)
        rig.advance(1000)

        assertThat(rig.sent()).isEmpty()
    }
}

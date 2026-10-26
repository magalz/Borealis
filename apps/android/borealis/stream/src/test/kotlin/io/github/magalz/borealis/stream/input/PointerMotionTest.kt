package io.github.magalz.borealis.stream.input

import com.google.common.truth.Truth.assertThat
import kotlin.math.hypot
import org.junit.Test

class PointerMotionTest {
    private val motion = PointerMotion()

    /** The speed of a stick at the setting's default, with no session factor and no precision. */
    private val nominal = PointerSpeed(percent = 100, sessionFactor = 1f, precision = false)

    private fun step(
        x: Int,
        y: Int = 0,
        elapsedMs: Long = 100,
        speed: PointerSpeed = nominal,
    ) = motion.step(x.toShort(), y.toShort(), elapsedMs, speed)

    /** The pixels moved in one second of ticks of [tickMs] with the stick held. */
    private fun movedInOneSecond(x: Int, y: Int = 0, tickMs: Long = 100): Pair<Int, Int> {
        var moved = Pair(0, 0)
        repeat((1000 / tickMs).toInt()) {
            val delta = step(x, y, elapsedMs = tickMs)
            moved = Pair(moved.first + delta.x, moved.second + delta.y)
        }
        return moved
    }

    @Test
    fun `a full stick moves 1280 pixels a second`() {
        assertThat(step(32766).x).isEqualTo(128)
        assertThat(movedInOneSecond(32766).first).isEqualTo(1280)
    }

    @Test
    fun `half the stick moves an eighth of that`() {
        assertThat(movedInOneSecond(16383).first).isEqualTo(160)
    }

    @Test
    fun `a quarter and three quarters of the stick follow the cube`() {
        assertThat(movedInOneSecond(8191).first).isWithin(1).of(20)
        assertThat(movedInOneSecond(24574).first).isWithin(1).of(540)
    }

    @Test
    fun `the direction of the stick is kept`() {
        val delta = step(-16383, -16383)

        assertThat(delta.x).isLessThan(0)
        assertThat(delta.y).isLessThan(0)
        assertThat(delta.x).isEqualTo(delta.y)
    }

    @Test
    fun `a stick past the full deflection moves no faster than a full one`() {
        val delta = step(32767, 32767)

        assertThat(hypot(delta.x.toDouble(), delta.y.toDouble())).isWithin(1.5).of(128.0)
        assertThat(delta.x).isEqualTo(delta.y)
    }

    @Test
    fun `slow movement adds up to whole pixels instead of being dropped`() {
        // 0.3 of the stick is about 0.55 px a tick of 16 ms
        assertThat(step(9830, elapsedMs = 16).x).isEqualTo(0)
        assertThat(step(9830, elapsedMs = 16).x).isEqualTo(1)
        assertThat(movedInOneSecond(9830, tickMs = 16).first).isAtLeast(30)
    }

    @Test
    fun `a slow diagonal moves on both axes`() {
        val moved = movedInOneSecond(7000, 7000, tickMs = 16)

        assertThat(moved.first).isGreaterThan(15)
        assertThat(moved.second).isEqualTo(moved.first)
    }

    @Test
    fun `the speed setting scales the move`() {
        assertThat(step(32766, speed = nominal.copy(percent = 50)).x).isEqualTo(64)
        assertThat(step(32766, speed = nominal.copy(percent = 200)).x).isEqualTo(256)
        assertThat(step(32766, speed = nominal.copy(percent = 10)).x).isEqualTo(12)
    }

    @Test
    fun `the session factor scales the move`() {
        assertThat(step(32766, speed = nominal.copy(sessionFactor = 2f)).x).isEqualTo(256)
        assertThat(step(32766, speed = nominal.copy(sessionFactor = 0.5f)).x).isEqualTo(64)
    }

    @Test
    fun `holding precision moves at a quarter`() {
        assertThat(step(32766, speed = nominal.copy(precision = true)).x).isEqualTo(32)
    }

    @Test
    fun `the factors multiply`() {
        assertThat(
                step(
                        32766,
                        speed = PointerSpeed(percent = 50, sessionFactor = 0.5f, precision = true),
                    )
                    .x
            )
            .isEqualTo(8)
    }

    @Test
    fun `a stalled tick moves no further than 100 ms of motion`() {
        assertThat(step(32766, elapsedMs = 5_000).x).isEqualTo(128)
    }

    @Test
    fun `no time passed moves nothing`() {
        assertThat(step(32766, elapsedMs = 0)).isEqualTo(PixelDelta(0, 0))
    }

    @Test
    fun `reset drops what was left over`() {
        step(9830, elapsedMs = 16)
        motion.reset()

        assertThat(step(9830, elapsedMs = 16).x).isEqualTo(0)
    }

    @Test
    fun `a stick back at center drops what was left over`() {
        step(9830, elapsedMs = 16)
        step(0, elapsedMs = 16)

        assertThat(step(9830, elapsedMs = 16).x).isEqualTo(0)
    }

    @Test
    fun `at 100 percent and 50 ms it moves as the old curve did, less its cut-off`() {
        listOf(
                20000 to 10000,
                32766 to 0,
                0 to -32766,
                16383 to 16383,
                -9000 to 25000,
                30000 to 0,
            )
            .forEach { (x, y) ->
                val fresh = PointerMotion()
                val expected = oldCurve(x, y)

                val delta = fresh.step(x.toShort(), y.toShort(), 50, PointerSpeed(100, 1f, false))

                assertThat(delta).isEqualTo(expected)
            }
    }

    /**
     * The curve of the stick before this change: 64 px per tick of 50 ms at full stick, cubic, per
     * component truncated.
     */
    private fun oldCurve(x: Int, y: Int): PixelDelta {
        val scale = 4.0 / 32766.0
        val vx = x * scale
        val vy = y * scale
        val magnitude = hypot(vx, vy)
        return PixelDelta(
            (vx * magnitude * magnitude).toInt(),
            (vy * magnitude * magnitude).toInt(),
        )
    }
}

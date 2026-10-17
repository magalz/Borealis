package io.github.magalz.borealis.stream.input

import kotlin.math.hypot
import kotlin.math.min

/**
 * A move in whole pixels (or scroll units), in the direction of the stick: y is positive when the
 * stick is up.
 */
data class PixelDelta(val x: Int, val y: Int)

/**
 * Turns a stick into the moves of the gamepad mouse. One instance follows one stick use: the
 * pointer or the scroll of one pad. The distance per tick is a speed times the time since the
 * previous tick, so the motion is the same at any tick rate; the part of a pixel that a tick cannot
 * send is kept for the next one.
 */
class PointerMotion {
    private var remainderX = 0.0
    private var remainderY = 0.0

    /**
     * The whole-pixel move to send for a stick at ([x], [y]) that was held for [elapsedMs] since
     * the previous tick. The speed is [PEAK_PER_SECOND] at full deflection, scaled by
     * [speedPercent], [sessionFactor] and, while [precision] is held, [PRECISION_FACTOR].
     */
    fun step(
        x: Short,
        y: Short,
        elapsedMs: Long,
        speedPercent: Int,
        sessionFactor: Float,
        precision: Boolean,
    ): PixelDelta {
        val length = hypot(x.toDouble(), y.toDouble())
        if (length == 0.0) {
            reset()
            return PixelDelta(0, 0)
        }
        val deflection = min(1.0, length / STICK_MAX)
        var perSecond = PEAK_PER_SECOND * deflection * deflection * deflection
        perSecond *= speedPercent / 100.0 * sessionFactor
        if (precision) perSecond *= PRECISION_FACTOR
        val distance = perSecond * elapsedMs.coerceIn(0L, MAX_ELAPSED_MS) / 1000.0

        val moveX = remainderX + distance * x / length
        val moveY = remainderY + distance * y / length
        val wholeX = moveX.toInt()
        val wholeY = moveY.toInt()
        remainderX = moveX - wholeX
        remainderY = moveY - wholeY
        return PixelDelta(wholeX, wholeY)
    }

    /** Drops the part of a pixel kept between ticks. */
    fun reset() {
        remainderX = 0.0
        remainderY = 0.0
    }

    companion object {
        /** Pixels a second at full deflection, before the factors. */
        const val PEAK_PER_SECOND = 1280.0

        /** What holding precision does to the speed. */
        const val PRECISION_FACTOR = 0.25

        /** A tick that stalled counts for no more than this, so the pointer cannot jump. */
        const val MAX_ELAPSED_MS = 100L

        private const val STICK_MAX = 32766.0
    }
}

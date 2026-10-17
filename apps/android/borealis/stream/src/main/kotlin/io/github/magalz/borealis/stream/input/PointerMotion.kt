package io.github.magalz.borealis.stream.input

/** A move in whole pixels (or scroll units), in the direction of the stick: y is positive when the stick is up. */
data class PixelDelta(val x: Int, val y: Int)

/**
 * Turns a stick into the moves of the gamepad mouse. One instance follows one stick use: the
 * pointer or the scroll of one pad.
 */
class PointerMotion {
    fun step(
        x: Short,
        y: Short,
        elapsedMs: Long,
        speedPercent: Int,
        sessionFactor: Float,
        precision: Boolean,
    ): PixelDelta = PixelDelta(0, 0)

    fun reset() {}
}

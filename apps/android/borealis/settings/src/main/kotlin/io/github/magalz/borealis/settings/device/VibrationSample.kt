package io.github.magalz.borealis.settings.device

/** Plays a short vibration, so that the user can feel what a vibration setting does. */
fun interface VibrationSample {
    /**
     * Plays one pulse of [durationMs] milliseconds at [strengthPercent], the strength setting: 0 to
     * 200 percent, where 100 is the default. A strength of 0 plays nothing. On a device without a
     * vibrator it does nothing.
     */
    fun play(durationMs: Long, strengthPercent: Int)
}

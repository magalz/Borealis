package io.github.magalz.borealis.settings

import android.content.Context
import dagger.hilt.android.qualifiers.ApplicationContext
import io.github.magalz.borealis.settings.device.VibrationSample
import javax.inject.Inject

/** The [VibrationSample] of the app. */
internal class AndroidVibrationSample
@Inject
constructor(@ApplicationContext private val context: Context) : VibrationSample {
    override fun play(durationMs: Long, strengthPercent: Int) = Unit
}

/** The amplitude, 1 to 255, of a pulse at [strengthPercent]; 0 for no pulse. */
internal fun sampleAmplitude(strengthPercent: Int): Int = 0

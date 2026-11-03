package io.github.magalz.borealis.settings

import android.content.Context
import android.media.AudioAttributes
import android.os.Build
import android.os.VibrationAttributes
import android.os.VibrationEffect
import android.os.Vibrator
import dagger.hilt.android.qualifiers.ApplicationContext
import io.github.magalz.borealis.settings.device.VibrationSample
import javax.inject.Inject

private const val MAX_STRENGTH_PERCENT = 200
private const val MAX_AMPLITUDE = 255

/**
 * The [VibrationSample] of the app: one pulse on the vibrator of the device. It has the usage of
 * the stream vibration, which the system does not silence with the touch feedback setting, so the
 * sample is felt when the stream would be.
 *
 * A device without amplitude control plays the pulse at its own strength.
 */
internal class AndroidVibrationSample
@Inject
constructor(@ApplicationContext private val context: Context) : VibrationSample {
    override fun play(durationMs: Long, strengthPercent: Int) {
        val amplitude = sampleAmplitude(strengthPercent)
        val vibrator = context.getSystemService(Vibrator::class.java)
        if (amplitude == 0 || vibrator?.hasVibrator() != true) return
        val effect =
            VibrationEffect.createOneShot(
                durationMs,
                if (vibrator.hasAmplitudeControl()) amplitude
                else VibrationEffect.DEFAULT_AMPLITUDE,
            )
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            vibrator.vibrate(
                effect,
                VibrationAttributes.Builder().setUsage(VibrationAttributes.USAGE_MEDIA).build(),
            )
        } else {
            @Suppress("DEPRECATION") // The AudioAttributes overload is all that API 28 to 32 offer.
            vibrator.vibrate(
                effect,
                AudioAttributes.Builder().setUsage(AudioAttributes.USAGE_GAME).build(),
            )
        }
    }
}

/**
 * The amplitude, 1 to 255, of a pulse at [strengthPercent], which maps 0 to 200 percent onto the
 * range of the vibrator; 0 for no pulse, and a strength out of the range is kept in it.
 */
internal fun sampleAmplitude(strengthPercent: Int): Int =
    if (strengthPercent <= 0) {
        0
    } else {
        (strengthPercent.coerceAtMost(MAX_STRENGTH_PERCENT) * MAX_AMPLITUDE / MAX_STRENGTH_PERCENT)
            .coerceAtLeast(1)
    }

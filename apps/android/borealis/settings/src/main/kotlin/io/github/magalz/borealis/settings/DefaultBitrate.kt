package io.github.magalz.borealis.settings

import kotlin.math.sqrt

/**
 * The automatic bitrate: the value that applies while the bitrate setting is absent. It is a port
 * of `PreferenceConfiguration.getDefaultBitrate`, kept in step by a parity test, and gives the same
 * result for every input, including the same float rounding.
 *
 * The bitrate scales with the pixel count, interpolated between the standard sizes (360p, 480p,
 * 720p, 1080p, 1440p and 4K, with the factors 1, 2, 5, 10, 20 and 40 Mbps at 30 fps) and held at
 * the first and last factor outside them, and with the frame rate, which is scaled by the square
 * root of its ratio to 60 above 60 fps.
 */
object DefaultBitrate {
    private const val KBPS_PER_MBPS = 1000
    private val PIXELS =
        intArrayOf(640 * 360, 854 * 480, 1280 * 720, 1920 * 1080, 2560 * 1440, 3840 * 2160)
    private val FACTORS = intArrayOf(1, 2, 5, 10, 20, 40)

    /**
     * The automatic bitrate in kbps for a [resolution] such as `1920x1080` and a [frameRate] such
     * as `60`. Throws [NumberFormatException] or [IndexOutOfBoundsException] for a value that is
     * not of that form, as the inherited code does.
     */
    fun kbps(resolution: String, frameRate: String): Int {
        val parts = resolution.split("x")
        return kbps(parts[0].toInt(), parts[1].toInt(), frameRate.toInt())
    }

    /**
     * The automatic bitrate in kbps for a [width] by [height] resolution at [fps] frames per
     * second.
     */
    fun kbps(width: Int, height: Int, fps: Int): Int {
        // Do not scale the bitrate linearly beyond 60 fps.
        val frameRateFactor =
            (if (fps <= 60) fps.toDouble() else sqrt((fps / 60f).toDouble()) * 60.0) / 30.0
        val mbps = Math.round(resolutionFactor(width * height) * frameRateFactor).toInt()
        return mbps * KBPS_PER_MBPS
    }

    /**
     * Linear interpolation of the factor table; the arithmetic is in Float, as in the inherited
     * code.
     */
    private fun resolutionFactor(pixels: Int): Float {
        val index = PIXELS.indexOfFirst { pixels <= it }
        return when {
            index == -1 -> FACTORS.last().toFloat()
            pixels == PIXELS[index] || index == 0 -> FACTORS[index].toFloat()
            else -> {
                val below = PIXELS[index - 1]
                ((pixels - below).toFloat() / (PIXELS[index] - below)) *
                    (FACTORS[index] - FACTORS[index - 1]) + FACTORS[index - 1]
            }
        }
    }
}

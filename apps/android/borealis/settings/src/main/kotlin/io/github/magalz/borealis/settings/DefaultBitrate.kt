package io.github.magalz.borealis.settings

/**
 * The automatic bitrate: the value that applies while the bitrate setting is absent. It is a port
 * of `PreferenceConfiguration.getDefaultBitrate`, kept in step by a parity test.
 */
object DefaultBitrate {
    /**
     * The automatic bitrate in kbps for a [resolution] such as `1920x1080` and a [frameRate] such
     * as `60`. Throws [NumberFormatException] or [IndexOutOfBoundsException] for a value that is
     * not of that form, as the Java does.
     */
    fun kbps(resolution: String, frameRate: String): Int {
        val parts = resolution.split("x")
        return kbps(parts[0].toInt(), parts[1].toInt(), frameRate.toInt())
    }

    /**
     * The automatic bitrate in kbps for a [width] by [height] resolution at [fps] frames per
     * second.
     */
    fun kbps(width: Int, height: Int, fps: Int): Int = 0
}

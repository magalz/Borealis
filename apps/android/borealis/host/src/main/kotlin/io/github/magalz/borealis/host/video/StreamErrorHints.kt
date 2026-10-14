package io.github.magalz.borealis.host.video

import io.github.magalz.borealis.host.R

/**
 * LuminalShine-specific hints for the stream error dialog. The upstream text stays; the hint is
 * appended to it.
 */
object StreamErrorHints {
    /** `MoonBridge.ML_ERROR_NO_VIDEO_TRAFFIC`. */
    const val ML_ERROR_NO_VIDEO_TRAFFIC = -100

    /** `MoonBridge.ML_ERROR_NO_VIDEO_FRAME`. */
    const val ML_ERROR_NO_VIDEO_FRAME = -101

    /**
     * Returns [message] for [errorCode], followed by the hint that the host may still be preparing its
     * display or encoder when the stream ended before any video arrived. [getString] resolves a
     * string resource id.
     */
    @JvmStatic
    fun withHint(errorCode: Int, message: String, getString: (Int) -> String): String =
        when (errorCode) {
            ML_ERROR_NO_VIDEO_TRAFFIC,
            ML_ERROR_NO_VIDEO_FRAME -> message + "\n\n" + getString(R.string.stream_luminalshine_hint)
            else -> message
        }
}

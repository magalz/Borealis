package io.github.magalz.borealis.host.video

/** Stub, replaced by the implementation commit. */
object StreamErrorHints {
    const val ML_ERROR_NO_VIDEO_TRAFFIC = -100
    const val ML_ERROR_NO_VIDEO_FRAME = -101

    @JvmStatic
    fun withHint(errorCode: Int, message: String, getString: (Int) -> String): String = message
}

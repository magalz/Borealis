package com.limelight.binding.video

fun interface CrashListener {
    fun notifyCrash(e: Exception)

    /**
     * The codec refused the Surface of the renderer, which stopped decoding; nothing crashed.
     * Called at most once, on the thread that recovers the codec, while the other threads wait for
     * it: it must not block.
     */
    fun surfaceLost() {}
}

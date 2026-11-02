package com.limelight.binding.video

fun interface CrashListener {
    fun notifyCrash(e: Exception)

    /** The renderer lost its Surface and stopped decoding; nothing crashed. */
    fun surfaceLost() {}
}

package io.github.magalz.borealis.nativecore

/** The JNI surface of the host rules; a seam so JVM tests can stand in for the native library. */
internal interface HostNative {
    fun parseCaps(appVersion: ByteArray?, codecMask: Int): IntArray?

    fun decideLaunch(verb: Int, status: Int, message: ByteArray?, attempts: Int): IntArray?

    fun classifyPairing(status: Int, message: ByteArray?): IntArray?

    fun launchFps(isGfe: Boolean, launchFps: Int, streamFps: Int): Int
}

/** The production [HostNative]: straight calls into [CoreJni]. */
internal object CoreHostNative : HostNative {
    override fun parseCaps(appVersion: ByteArray?, codecMask: Int): IntArray? =
        CoreJni.parseCaps(appVersion, codecMask)

    override fun decideLaunch(
        verb: Int,
        status: Int,
        message: ByteArray?,
        attempts: Int,
    ): IntArray? = CoreJni.decideLaunch(verb, status, message, attempts)

    override fun classifyPairing(status: Int, message: ByteArray?): IntArray? =
        CoreJni.classifyPairing(status, message)

    override fun launchFps(isGfe: Boolean, launchFps: Int, streamFps: Int): Int =
        CoreJni.launchFps(isGfe, launchFps, streamFps)
}

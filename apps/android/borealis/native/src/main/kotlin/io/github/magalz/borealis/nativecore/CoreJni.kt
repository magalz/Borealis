package io.github.magalz.borealis.nativecore

/**
 * JNI entry points implemented in src/main/cpp/borealis_jni.cpp. Only primitives and byte arrays
 * cross the boundary; the host rules answer with an int array whose first element is the
 * `BOREALIS_*` status (see [HostMarshalling] for the layouts).
 */
internal object CoreJni {
    @JvmStatic external fun version(): String?

    @JvmStatic external fun parseCaps(appVersion: ByteArray?, codecMask: Int): IntArray?

    @JvmStatic
    external fun decideLaunch(verb: Int, status: Int, message: ByteArray?, attempts: Int): IntArray?

    @JvmStatic external fun launchFps(isGfe: Boolean, launchFps: Int, streamFps: Int): Int
}

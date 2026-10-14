package io.github.magalz.borealis.nativecore

/** JNI entry points implemented in src/main/cpp/borealis_jni.cpp. */
internal object CoreJni {
    @JvmStatic external fun version(): String?
}

package io.github.magalz.borealis.stream

/** The bytes of typed text as the host expects them. */
object Utf8Text {
    /**
     * Standard UTF-8. `GetStringUTFChars` in JNI gives Java's modified UTF-8 instead, which encodes
     * an emoji as two three-byte surrogates and corrupts it on the host.
     */
    @JvmStatic fun bytes(text: String): ByteArray = text.toByteArray(Charsets.UTF_8)
}

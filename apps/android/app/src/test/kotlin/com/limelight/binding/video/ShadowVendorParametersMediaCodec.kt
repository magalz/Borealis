package com.limelight.binding.video

import android.media.MediaCodec
import org.robolectric.annotation.Implementation
import org.robolectric.annotation.Implements
import org.robolectric.shadows.ShadowMediaCodec

/**
 * A `MediaCodec` that lists vendor parameters, as Android 12 and later do, and counts its releases.
 * Robolectric's own shadow leaves the native call for the parameters empty. Use it with
 * `@Config(shadows = [ShadowVendorParametersMediaCodec::class])`.
 */
@Implements(MediaCodec::class)
open class ShadowVendorParametersMediaCodec : ShadowMediaCodec() {
    @Implementation
    protected fun native_getSupportedVendorParameters(): List<String> {
        failure?.let { throw it }
        return parameters
    }

    @Implementation
    protected fun native_release() {
        released++
    }

    companion object {
        /** The vendor parameters that every codec lists. */
        @JvmStatic var parameters: List<String> = emptyList()

        /** What listing the parameters throws, or `null` to list them. */
        @JvmStatic var failure: RuntimeException? = null

        /** How many codecs were released. */
        @JvmStatic var released = 0

        /**
         * Puts the parameters, the failure and the count back; Robolectric does not reset a shadow
         * of @Config.
         */
        @JvmStatic
        fun reset() {
            parameters = emptyList()
            failure = null
            released = 0
        }
    }
}

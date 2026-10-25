// The package name is the inherited one: ADR 0059 keeps it, and it has an underscore.
@file:Suppress("PackageNaming")

package com.limelight.binding.input.virtual_controller

import com.limelight.nvstream.jni.MoonBridge
import org.robolectric.annotation.Implementation
import org.robolectric.annotation.Implements
import org.robolectric.util.ReflectionHelpers

/**
 * Keeps the native library off the on-screen controller tests. The default layout reads the
 * settings, and reading them touches the audio configurations of `MoonBridge`, whose static
 * initialiser loads a library that is not there off a device. This shadow skips the library and
 * sets the three configurations the settings read.
 *
 * Use it with `@Config(shadows = [ShadowOscMoonBridge::class])`.
 */
@Implements(MoonBridge::class)
object ShadowOscMoonBridge {
    private const val STEREO_CHANNELS = 2
    private const val STEREO_MASK = 0x3
    private const val SURROUND_51_CHANNELS = 6
    private const val SURROUND_51_MASK = 0x3F
    private const val SURROUND_71_CHANNELS = 8
    private const val SURROUND_71_MASK = 0x63F

    @Suppress("unused") // Robolectric calls it in place of the static initialiser.
    @Implementation
    @JvmStatic
    fun __staticInitializer__() {
        set("AUDIO_CONFIGURATION_STEREO", MoonBridge.AudioConfiguration(STEREO_CHANNELS, STEREO_MASK))
        set(
            "AUDIO_CONFIGURATION_51_SURROUND",
            MoonBridge.AudioConfiguration(SURROUND_51_CHANNELS, SURROUND_51_MASK),
        )
        set(
            "AUDIO_CONFIGURATION_71_SURROUND",
            MoonBridge.AudioConfiguration(SURROUND_71_CHANNELS, SURROUND_71_MASK),
        )
    }

    private fun set(name: String, value: MoonBridge.AudioConfiguration) {
        ReflectionHelpers.setStaticField(MoonBridge::class.java, name, value)
    }
}

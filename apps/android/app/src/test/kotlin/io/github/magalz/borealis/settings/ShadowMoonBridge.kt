package io.github.magalz.borealis.settings

import com.limelight.nvstream.jni.MoonBridge
import org.robolectric.annotation.Implementation
import org.robolectric.annotation.Implements
import org.robolectric.util.ReflectionHelpers

/**
 * Lets `PreferenceConfiguration.readPreferences` run on the JVM. It reads the audio configuration
 * constants of [MoonBridge], whose static initializer loads the native `moonlight-core` library
 * and fails off a device. This shadow skips the library and sets the three audio configurations
 * with the values of the Java source, which are all that `readPreferences` uses.
 *
 * Use it with `@Config(shadows = [ShadowMoonBridge::class])`.
 */
@Implements(MoonBridge::class)
class ShadowMoonBridge {
    companion object {
        @Suppress("unused")
        @Implementation
        @JvmStatic
        fun __staticInitializer__() {
            set("AUDIO_CONFIGURATION_STEREO", MoonBridge.AudioConfiguration(2, 0x3))
            set("AUDIO_CONFIGURATION_51_SURROUND", MoonBridge.AudioConfiguration(6, 0x3F))
            set("AUDIO_CONFIGURATION_71_SURROUND", MoonBridge.AudioConfiguration(8, 0x63F))
        }

        private fun set(name: String, value: MoonBridge.AudioConfiguration) {
            ReflectionHelpers.setStaticField(MoonBridge::class.java, name, value)
        }
    }
}

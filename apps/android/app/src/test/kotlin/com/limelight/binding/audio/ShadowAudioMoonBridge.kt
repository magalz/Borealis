package com.limelight.binding.audio

import com.limelight.nvstream.jni.MoonBridge
import java.util.concurrent.CopyOnWriteArrayList
import org.robolectric.annotation.Implementation
import org.robolectric.annotation.Implements
import org.robolectric.util.ReflectionHelpers

/**
 * Stands in for the native `moonlight-core` library, whose static initialiser a unit test cannot
 * run. The audio renderer asks it how much audio is waiting to be played; the answers of a test
 * come from [pendingDurations], one per call.
 *
 * State lives in this object, which every test of one Robolectric sandbox shares: call [reset]
 * before each test.
 *
 * Use it with `@Config(shadows = [ShadowAudioMoonBridge::class, ...])`.
 */
@Implements(MoonBridge::class)
internal object ShadowAudioMoonBridge {
    private val answers = CopyOnWriteArrayList<Int>()

    /** How many times the renderer asked for the pending duration. */
    @Volatile var asked = 0
        private set

    /**
     * The answers to the next calls of `getPendingAudioDuration`, in order. The last one answers
     * every call after the list ends, and an empty list answers 0.
     */
    var pendingDurations: List<Int>
        get() = answers.toList()
        set(value) {
            answers.clear()
            answers.addAll(value)
        }

    fun reset() {
        answers.clear()
        asked = 0
    }

    @Suppress("unused")
    @Implementation
    @JvmStatic
    fun __staticInitializer__() {
        // No native library to load. The class is initialized once per Robolectric sandbox, by
        // whichever test reaches it first, and the settings and launch tests that share the sandbox
        // read these three constants: set them as the Java source does.
        set("AUDIO_CONFIGURATION_STEREO", MoonBridge.AudioConfiguration(2, 0x3))
        set("AUDIO_CONFIGURATION_51_SURROUND", MoonBridge.AudioConfiguration(6, 0x3F))
        set("AUDIO_CONFIGURATION_71_SURROUND", MoonBridge.AudioConfiguration(8, 0x63F))
    }

    private fun set(name: String, value: MoonBridge.AudioConfiguration) {
        ReflectionHelpers.setStaticField(MoonBridge::class.java, name, value)
    }

    @Suppress("unused")
    @Implementation
    @JvmStatic
    fun getPendingAudioDuration(): Int {
        val index = asked++
        return answers.getOrElse(index) { answers.lastOrNull() ?: 0 }
    }
}

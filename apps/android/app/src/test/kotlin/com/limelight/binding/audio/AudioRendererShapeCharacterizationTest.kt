package com.limelight.binding.audio

import android.content.Context
import com.google.common.truth.Truth.assertThat
import com.limelight.nvstream.av.audio.AudioRenderer
import java.lang.invoke.MethodType
import java.lang.reflect.Modifier
import org.junit.Test

/**
 * The shape of `AndroidAudioRenderer` that its Java caller (`Game`) and the native layer depend on:
 * it is an `AudioRenderer` with a public constructor of a context and the audio effects flag.
 *
 * Characterization: these tests pass on the Java class and on its Kotlin conversion.
 */
class AudioRendererShapeCharacterizationTest {
    private val type = AndroidAudioRenderer::class.java

    @Test
    fun `it is a public class that implements the audio renderer contract`() {
        assertThat(Modifier.isPublic(type.modifiers)).isTrue()
        assertThat(type.interfaces.toList()).containsExactly(AudioRenderer::class.java)
        assertThat(type.superclass).isEqualTo(Any::class.java)
    }

    @Test
    fun `its one constructor is public and takes the context and the audio effects flag`() {
        val constructor = type.constructors.single()

        assertThat(constructor.parameterTypes.toList())
            .containsExactly(Context::class.java, Boolean::class.javaPrimitiveType)
            .inOrder()
        assertThat(constructor.exceptionTypes).isEmpty()
    }

    @Test
    fun `its public methods are the five of the contract`() {
        val methods =
            type.declaredMethods
                .filter { Modifier.isPublic(it.modifiers) && !it.isSynthetic }
                .map { it.name + MethodType.methodType(it.returnType, it.parameterTypes).toMethodDescriptorString() }

        assertThat(methods)
            .containsExactly(
                "setup(Lcom/limelight/nvstream/jni/MoonBridge\$AudioConfiguration;II)I",
                "start()V",
                "stop()V",
                "playDecodedAudio([S)V",
                "cleanup()V",
            )
    }

    @Test
    fun `it declares no public field`() {
        assertThat(type.fields).isEmpty()
    }
}

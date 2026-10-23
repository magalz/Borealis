package io.github.magalz.borealis.stream

import com.google.common.truth.Truth.assertThat
import com.limelight.nvstream.av.audio.AudioRenderer
import com.limelight.nvstream.av.video.VideoDecoderRenderer
import java.lang.invoke.MethodType
import java.lang.reflect.Method
import java.lang.reflect.Modifier
import org.junit.Test

/**
 * The shape of the two renderer contracts that the native layer reaches through `MoonBridge`: the
 * JVM descriptor of each method, which the JNI callbacks and the Java renderers
 * (`MediaCodecDecoderRenderer`, `AndroidAudioRenderer`) depend on. The frame latency of
 * `submitDecodeUnit` is a `char` (`C`) because the JNI signature of `bridgeDrSubmitDecodeUnit` is
 * `([BIIIICJJ)I`.
 *
 * Characterization: these tests pass on the Java types and on their Kotlin conversion.
 */
class RendererContractCharacterizationTest {
    private fun descriptor(method: Method): String =
        method.name +
            MethodType.methodType(method.returnType, method.parameterTypes)
                .toMethodDescriptorString()

    private fun declared(type: Class<*>): List<Method> =
        type.declaredMethods.filterNot { it.isSynthetic }

    @Test
    fun `a video decoder renderer is an abstract class with these abstract methods`() {
        val type = VideoDecoderRenderer::class.java

        assertThat(Modifier.isAbstract(type.modifiers)).isTrue()
        assertThat(type.isInterface).isFalse()
        assertThat(type.superclass).isEqualTo(Any::class.java)
        assertThat(declared(type).map(::descriptor))
            .containsExactly(
                "setup(IIII)I",
                "start()V",
                "stop()V",
                "submitDecodeUnit([BIIIICJJ)I",
                "cleanup()V",
                "getCapabilities()I",
                "setHdrMode(Z[B)V",
            )
        for (method in declared(type)) {
            assertThat(Modifier.isAbstract(method.modifiers)).isTrue()
            assertThat(Modifier.isPublic(method.modifiers)).isTrue()
            assertThat(method.exceptionTypes).isEmpty()
        }
    }

    @Test
    fun `a video decoder renderer can be extended with a public constructor`() {
        val constructors = VideoDecoderRenderer::class.java.constructors

        assertThat(constructors.map { it.parameterCount }).containsExactly(0)
    }

    @Test
    fun `an audio renderer is an interface with these methods`() {
        val type = AudioRenderer::class.java

        assertThat(type.isInterface).isTrue()
        assertThat(type.interfaces).isEmpty()
        assertThat(declared(type).map(::descriptor))
            .containsExactly(
                "setup(Lcom/limelight/nvstream/jni/MoonBridge\$AudioConfiguration;II)I",
                "start()V",
                "stop()V",
                "playDecodedAudio([S)V",
                "cleanup()V",
            )
        for (method in declared(type)) {
            assertThat(Modifier.isAbstract(method.modifiers)).isTrue()
            assertThat(method.exceptionTypes).isEmpty()
        }
    }
}

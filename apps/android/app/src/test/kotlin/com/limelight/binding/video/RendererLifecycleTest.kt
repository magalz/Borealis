package com.limelight.binding.video

import android.os.Process
import com.google.common.truth.Truth.assertThat
import com.limelight.nvstream.jni.MoonBridge
import com.limelight.preferences.PreferenceConfiguration
import org.junit.Assert.assertThrows
import org.junit.Test

/**
 * Characterizes `start`, `stop`, `prepareForStop` and `cleanup`: the threads the renderer starts,
 * what stopping does to them and to the frames that still arrive, and the calls that are made in
 * the wrong order.
 */
class RendererLifecycleTest : RendererTestBase() {
    private val rendererThreadName = "Video - Renderer (MediaCodec)"
    private val choreographerThreadName = "Video - Choreographer"

    private fun ready(
        pacing: Int = PreferenceConfiguration.FRAME_PACING_MIN_LATENCY
    ): MediaCodecDecoderRenderer {
        install(codec("vendor.test.avc.decoder") { mime = "video/avc" })
        return renderer(prefs { framePacing = pacing }).also {
            assertThat(it.prepare(MoonBridge.VIDEO_FORMAT_H264)).isEqualTo(0)
        }
    }

    // Starting

    @Test
    fun `start runs the renderer thread with its name and a priority two above normal`() {
        val renderer = ready()

        renderer.begin()

        val thread = checkNotNull(threadNamed(rendererThreadName))
        assertThat(thread.priority).isEqualTo(Thread.NORM_PRIORITY + 2)
    }

    @Test
    fun `start runs no choreographer thread unless frame pacing is balanced`() {
        for (pacing in
            listOf(
                PreferenceConfiguration.FRAME_PACING_MIN_LATENCY,
                PreferenceConfiguration.FRAME_PACING_CAP_FPS,
                PreferenceConfiguration.FRAME_PACING_MAX_SMOOTHNESS,
            )) {
            val renderer = ready(pacing)

            renderer.begin()

            assertThat(threadNamed(choreographerThreadName)).isNull()
            renderer.stop()
        }
    }

    @Suppress("DEPRECATION") // Thread.id has no other name before Java 19.
    @Test
    fun `balanced frame pacing runs a choreographer thread a little more favoured than default`() {
        val renderer = ready(PreferenceConfiguration.FRAME_PACING_BALANCED)

        renderer.begin()

        val thread = checkNotNull(threadNamed(choreographerThreadName))
        assertThat(Process.getThreadPriority(thread.id.toInt()))
            .isEqualTo(Process.THREAD_PRIORITY_DEFAULT + Process.THREAD_PRIORITY_MORE_FAVORABLE)
        assertThat(threadNamed(rendererThreadName)).isNotNull()
    }

    // Stopping

    @Test
    fun `stop waits for the renderer thread to end`() {
        val renderer = ready()
        renderer.begin()
        val thread = checkNotNull(threadNamed(rendererThreadName))

        renderer.stop()

        assertThat(thread.isAlive).isFalse()
    }

    @Test
    fun `stop waits for the choreographer thread to end`() {
        val renderer = ready(PreferenceConfiguration.FRAME_PACING_BALANCED)
        renderer.begin()
        val choreographer = checkNotNull(threadNamed(choreographerThreadName))
        val rendering = checkNotNull(threadNamed(rendererThreadName))

        renderer.stop()

        assertThat(choreographer.isAlive).isFalse()
        assertThat(rendering.isAlive).isFalse()
    }

    @Test
    fun `stop can be called twice`() {
        val renderer = ready()
        renderer.begin()

        renderer.stop()
        renderer.stop()

        assertThat(threadNamed(rendererThreadName)).isNull()
    }

    @Test
    fun `prepareForStop alone ends the renderer thread`() {
        val renderer = ready()
        renderer.begin()
        val thread = checkNotNull(threadNamed(rendererThreadName))

        renderer.prepareForStop()
        thread.join(JOIN_MS)

        assertThat(thread.isAlive).isFalse()
    }

    @Test
    fun `frames that arrive after the renderer was told to stop are dropped as delivered`() {
        val renderer = ready()
        renderer.begin()
        renderer.prepareForStop()

        val results = (1..3).map { renderer.submit(pFrame(it)) }

        assertThat(results).containsExactly(MoonBridge.DR_OK, MoonBridge.DR_OK, MoonBridge.DR_OK)
        assertThat(events<CodecEvent.Queued>()).isEmpty()
    }

    @Test
    fun `prepareForStop can run before start`() {
        val renderer = ready()

        renderer.prepareForStop()

        assertThat(renderer.submit(pFrame(1))).isEqualTo(MoonBridge.DR_OK)
    }

    @Test
    fun `prepareForStop can run when setup never happened`() {
        install(codec("vendor.test.avc.decoder") { mime = "video/avc" })

        renderer().prepareForStop()
    }

    @Test
    fun `stop before start fails with a null pointer after it has told the decoder to stop`() {
        val renderer = ready()

        assertThrows(NullPointerException::class.java) { renderer.stop() }

        assertThat(renderer.submit(pFrame(1))).isEqualTo(MoonBridge.DR_OK)
        assertThat(events<CodecEvent.Queued>()).isEmpty()
    }

    @Test
    fun `an interrupted stop returns at once and leaves the interrupt flag set`() {
        val renderer = ready(PreferenceConfiguration.FRAME_PACING_BALANCED)
        renderer.begin()

        Thread.currentThread().interrupt()
        renderer.stop()

        assertThat(Thread.interrupted()).isTrue()
    }

    @Test
    fun `an interrupted stop of a renderer without a choreographer leaves the flag set`() {
        val renderer = ready()
        renderer.begin()

        Thread.currentThread().interrupt()
        renderer.stop()

        assertThat(Thread.interrupted()).isTrue()
    }

    // Cleaning up

    @Test
    fun `cleanup releases the codec`() {
        val renderer = ready()
        val created = events<CodecEvent.Created>().single()

        renderer.cleanup()

        assertThat(events<CodecEvent.Released>().single().codec).isEqualTo(created.codec)
    }

    @Test
    fun `cleanup after stop releases the codec`() {
        val renderer = ready()
        renderer.begin()
        renderer.stop()

        renderer.cleanup()

        assertThat(events<CodecEvent.Released>()).hasSize(1)
    }

    @Test
    fun `cleanup before setup fails with a null pointer`() {
        install(codec("vendor.test.avc.decoder") { mime = "video/avc" })

        assertThrows(NullPointerException::class.java) { renderer().cleanup() }
    }

    private companion object {
        const val JOIN_MS = 30_000L
    }
}

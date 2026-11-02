package com.limelight.binding.video

import com.google.common.truth.Truth.assertThat
import com.limelight.nvstream.jni.MoonBridge
import io.github.magalz.borealis.stream.PerfSnapshot
import java.util.concurrent.CopyOnWriteArrayList
import java.util.concurrent.atomic.AtomicInteger
import org.junit.Test

/**
 * Characterizes where the audio delay of the overlay comes from: the stream activity gives the
 * renderer a source for it, and the renderer asks it when it builds a snapshot, once a second at
 * most, never for each frame. Without a source there is no delay to show.
 */
class RendererAudioDelayTest : RendererTestBase() {
    private val snapshots = CopyOnWriteArrayList<PerfSnapshot>()

    /** A renderer for H.264 whose overlay feed collects into [snapshots]. */
    private fun counting(): MediaCodecDecoderRenderer {
        install(codec("vendor.test.avc.decoder") { mime = "video/avc" })
        val renderer = renderer()
        assertThat(renderer.prepare(MoonBridge.VIDEO_FORMAT_H264)).isEqualTo(0)
        renderer.setPerfEnabled(true)
        renderer.setPerfSink { snapshots += it }
        return renderer
    }

    /** Submits [number] and gives the codec's output back, as a renderer thread would. */
    private fun MediaCodecDecoderRenderer.feed(number: Int) {
        submit(pFrame(number))
        ShadowRecordingVideoCodec.freeOutputs()
    }

    /** Feeds frames until a second has passed, which closes the window and pushes a snapshot. */
    private fun MediaCodecDecoderRenderer.closeWindow(first: Int) {
        feed(first)
        advanceClock(1_000)
        feed(first + 1)
    }

    @Test
    fun `a snapshot carries the audio delay of the source`() {
        val renderer = counting()
        renderer.setAudioDelaySource { 37 }

        renderer.closeWindow(1)

        assertThat(snapshots.single().audioDelayMs).isEqualTo(37)
    }

    @Test
    fun `a snapshot has no audio delay when the source has none yet`() {
        val renderer = counting()
        renderer.setAudioDelaySource { null }

        renderer.closeWindow(1)

        assertThat(snapshots.single().audioDelayMs).isNull()
    }

    @Test
    fun `a snapshot has no audio delay until a source is set`() {
        val renderer = counting()

        renderer.closeWindow(1)

        assertThat(snapshots.single().audioDelayMs).isNull()
    }

    @Test
    fun `the source is asked once for each snapshot and not for each frame`() {
        val renderer = counting()
        val asked = AtomicInteger()
        renderer.setAudioDelaySource { asked.incrementAndGet() }

        renderer.feed(1)
        renderer.feed(2)
        renderer.feed(3)
        assertThat(asked.get()).isEqualTo(0)
        advanceClock(1_000)
        renderer.feed(4)
        renderer.closeWindow(5)

        assertThat(snapshots.map { it.audioDelayMs }).containsExactly(1, 2).inOrder()
    }

    @Test
    fun `the source is not asked when the overlay is off`() {
        val renderer = counting()
        renderer.setPerfEnabled(false)
        val asked = AtomicInteger()
        renderer.setAudioDelaySource { asked.incrementAndGet() }

        renderer.closeWindow(1)

        assertThat(asked.get()).isEqualTo(0)
        assertThat(snapshots).isEmpty()
    }
}

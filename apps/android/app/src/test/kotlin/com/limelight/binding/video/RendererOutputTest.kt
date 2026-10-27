package com.limelight.binding.video

import android.media.MediaCodec
import com.google.common.truth.Truth.assertThat
import com.limelight.nvstream.jni.MoonBridge
import com.limelight.preferences.PreferenceConfiguration
import io.github.magalz.borealis.stream.PerfSnapshot
import java.util.concurrent.CopyOnWriteArrayList
import java.util.concurrent.atomic.AtomicInteger
import org.junit.Test

/**
 * Characterizes what the renderer does with the frames the codec has decoded: which of them it
 * shows, which it drops, and when, for each frame pacing mode. The fake codec turns every queued
 * input into an output at once, so frames queued before `start` are all waiting when the renderer
 * thread begins.
 */
class RendererOutputTest : RendererTestBase() {
    private val snapshots = CopyOnWriteArrayList<PerfSnapshot>()
    private val frameSignals = AtomicInteger()

    /** A renderer set up for H.264 with the frame pacing [pacing] and [frames] frames queued. */
    private fun withFrames(
        pacing: Int,
        frames: Int,
        fps: Int = 60,
    ): MediaCodecDecoderRenderer {
        install(codec("vendor.test.avc.decoder") { mime = "video/avc" })
        val renderer = renderer(prefs { framePacing = pacing })
        assertThat(renderer.prepare(MoonBridge.VIDEO_FORMAT_H264, fps = fps)).isEqualTo(0)
        renderer.firstFrameSignal.listener = Runnable { frameSignals.incrementAndGet() }
        renderer.setPerfEnabled(true)
        renderer.setPerfSink { snapshots += it }
        for (number in 1..frames) assertThat(renderer.submit(pFrame(number))).isEqualTo(0)
        return renderer
    }

    private fun outputs() = events<CodecEvent.Output>()

    private fun queuedIndexes() = events<CodecEvent.Queued>().map { it.index }

    /** Waits until the renderer thread has released [count] frames and is waiting for more. */
    private fun awaitReleases(count: Int) {
        await("$count releases") { outputs().size >= count }
        val polls = ShadowRecordingVideoCodec.dequeueOutputCalls.get()
        await("the renderer thread to ask for more") {
            ShadowRecordingVideoCodec.dequeueOutputCalls.get() > polls
        }
    }

    // The frame pacing modes that show the newest frame

    @Test
    fun `with minimum latency only the newest waiting frame is shown and the older ones are dropped`() {
        val renderer = withFrames(PreferenceConfiguration.FRAME_PACING_MIN_LATENCY, frames = 3)

        renderer.begin()
        awaitReleases(3)

        val (first, second, third) = outputs()
        assertThat(first.render).isFalse()
        assertThat(first.index).isEqualTo(queuedIndexes()[0])
        assertThat(second.render).isFalse()
        assertThat(second.index).isEqualTo(queuedIndexes()[1])
        // The newest is shown at the time the thread took it, so that a newer one may replace it.
        assertThat(third.index).isEqualTo(queuedIndexes()[2])
        assertThat(third.render).isNull()
        assertThat(third.atNs).isGreaterThan(0L)
    }

    @Test
    fun `with capped frame rate or maximum smoothness no frame is ever dropped by the display`() {
        for (pacing in
            listOf(
                PreferenceConfiguration.FRAME_PACING_CAP_FPS,
                PreferenceConfiguration.FRAME_PACING_MAX_SMOOTHNESS,
            )) {
            ShadowRecordingVideoCodec.forgetEverything()
            val renderer = withFrames(pacing, frames = 2)

            renderer.begin()
            awaitReleases(2)

            val (older, newest) = outputs()
            assertThat(older.render).isFalse()
            assertThat(newest.atNs).isEqualTo(0L)
            renderer.stop()
        }
    }

    @Test
    fun `a single waiting frame is shown and nothing is dropped`() {
        val renderer = withFrames(PreferenceConfiguration.FRAME_PACING_MIN_LATENCY, frames = 1)

        renderer.begin()
        awaitReleases(1)

        assertThat(outputs()).hasSize(1)
        assertThat(outputs().single().atNs).isGreaterThan(0L)
    }

    @Test
    fun `the renderer counts the frames it shows, not the frames it drops`() {
        val renderer = withFrames(PreferenceConfiguration.FRAME_PACING_MIN_LATENCY, frames = 3)
        renderer.begin()
        awaitReleases(3)
        advanceClock(1_000)

        renderer.submit(pFrame(4))

        val snapshot = snapshots.single()
        assertThat(snapshot.receivedFps).isEqualTo(3f)
        assertThat(snapshot.renderedFps).isEqualTo(1f)
    }

    @Test
    fun `the first frame signal is raised once, when the first frame is shown`() {
        val renderer = withFrames(PreferenceConfiguration.FRAME_PACING_MIN_LATENCY, frames = 1)
        assertThat(frameSignals.get()).isEqualTo(0)

        renderer.begin()
        awaitReleases(1)
        renderer.submit(pFrame(2))
        awaitReleases(2)

        assertThat(frameSignals.get()).isEqualTo(1)
    }

    @Test
    fun `the renderer thread logs the change of the output format once`() {
        val renderer = withFrames(PreferenceConfiguration.FRAME_PACING_MIN_LATENCY, frames = 1)

        renderer.begin()
        awaitReleases(1)

        assertThat(log.lines().filter { it == "INFO: Output format changed" }).hasSize(1)
        assertThat(log.lines().filter { it.startsWith("INFO: New output format: {") }).hasSize(1)
    }

    @Suppress("DEPRECATION") // The renderer ignores this answer, which is no longer sent.
    @Test
    fun `a codec answer that is neither a frame nor a known change is ignored`() {
        val renderer = withFrames(PreferenceConfiguration.FRAME_PACING_MIN_LATENCY, frames = 1)
        // The deprecated "output buffers changed" answer, then the format change.
        ShadowRecordingVideoCodec.answerNextOutputDequeue(MediaCodec.INFO_OUTPUT_BUFFERS_CHANGED)

        renderer.begin()
        awaitReleases(1)

        assertThat(outputs()).hasSize(1)
    }

    // Balanced frame pacing

    @Suppress("DEPRECATION") // The renderer reads the default display the same way.
    private val vsyncOffset: Long
        get() = activity.windowManager.defaultDisplay.appVsyncOffsetNanos

    @Test
    fun `balanced pacing keeps the two newest frames and drops the older ones`() {
        val renderer = withFrames(PreferenceConfiguration.FRAME_PACING_BALANCED, frames = 4)

        renderer.begin()
        awaitReleases(2)

        val (first, second) = outputs()
        assertThat(first.render).isFalse()
        assertThat(first.index).isEqualTo(queuedIndexes()[0])
        assertThat(second.render).isFalse()
        assertThat(second.index).isEqualTo(queuedIndexes()[1])
    }

    @Test
    fun `balanced pacing shows one waiting frame for each frame time that is due`() {
        val renderer = withFrames(PreferenceConfiguration.FRAME_PACING_BALANCED, frames = 3)
        renderer.begin()
        awaitReleases(1)

        renderer.doFrame(FRAME_TIME)
        renderer.doFrame(FRAME_TIME + DUE)

        val shown = outputs().drop(1)
        assertThat(shown.map { it.index }).containsExactly(queuedIndexes()[1], queuedIndexes()[2]).inOrder()
        assertThat(shown.map { it.atNs })
            .containsExactly(FRAME_TIME - vsyncOffset, FRAME_TIME + DUE - vsyncOffset)
            .inOrder()
    }

    @Test
    fun `a frame time that comes too soon after the last shown frame shows nothing`() {
        val renderer = withFrames(PreferenceConfiguration.FRAME_PACING_BALANCED, frames = 3)
        renderer.begin()
        awaitReleases(1)
        renderer.doFrame(FRAME_TIME)

        renderer.doFrame(FRAME_TIME + DUE - 1)

        assertThat(outputs()).hasSize(2)
    }

    @Test
    fun `a frame time exactly 80 percent of a frame after the last one shows a frame`() {
        val renderer = withFrames(PreferenceConfiguration.FRAME_PACING_BALANCED, frames = 3)
        renderer.begin()
        awaitReleases(1)
        renderer.doFrame(FRAME_TIME)

        renderer.doFrame(FRAME_TIME + DUE)

        assertThat(outputs()).hasSize(3)
    }

    @Test
    fun `the due time follows the frame rate of the setup`() {
        val renderer = withFrames(PreferenceConfiguration.FRAME_PACING_BALANCED, frames = 3, fps = 120)
        renderer.begin()
        awaitReleases(1)
        renderer.doFrame(FRAME_TIME)

        renderer.doFrame(FRAME_TIME + DUE_AT_120 - 1)
        assertThat(outputs()).hasSize(2)
        renderer.doFrame(FRAME_TIME + DUE_AT_120)

        assertThat(outputs()).hasSize(3)
    }

    @Test
    fun `a frame time with nothing waiting shows nothing and fails nowhere`() {
        val renderer = withFrames(PreferenceConfiguration.FRAME_PACING_BALANCED, frames = 0)
        renderer.begin()

        renderer.doFrame(FRAME_TIME)

        assertThat(outputs()).isEmpty()
    }

    @Test
    fun `a frame time after the renderer was told to stop shows nothing`() {
        val renderer = withFrames(PreferenceConfiguration.FRAME_PACING_BALANCED, frames = 3)
        renderer.begin()
        awaitReleases(1)
        renderer.prepareForStop()

        renderer.doFrame(FRAME_TIME)

        assertThat(outputs()).hasSize(1)
    }

    @Test
    fun `a frame shown by a frame time raises the first frame signal and is counted`() {
        val renderer = withFrames(PreferenceConfiguration.FRAME_PACING_BALANCED, frames = 3)
        renderer.begin()
        awaitReleases(1)
        assertThat(frameSignals.get()).isEqualTo(0)

        renderer.doFrame(FRAME_TIME)
        renderer.doFrame(FRAME_TIME + DUE)

        assertThat(frameSignals.get()).isEqualTo(1)
        advanceClock(1_000)
        renderer.submit(pFrame(4))
        assertThat(snapshots.single().renderedFps).isEqualTo(2f)
    }

    @Test
    fun `a frame whose release fails is dropped without being shown`() {
        val renderer = withFrames(PreferenceConfiguration.FRAME_PACING_BALANCED, frames = 3)
        renderer.begin()
        awaitReleases(1)
        ShadowRecordingVideoCodec.failNext(CodecPoint.RELEASE_OUTPUT) {
            IllegalStateException("surface gone")
        }

        renderer.doFrame(FRAME_TIME)

        val dropped = outputs().last()
        assertThat(dropped.render).isFalse()
        assertThat(dropped.index).isEqualTo(queuedIndexes()[1])
        assertThat(frameSignals.get()).isEqualTo(0)
        // The failed frame did not count as shown, so the next frame time is due at once.
        renderer.doFrame(FRAME_TIME + 1)
        assertThat(outputs().last().index).isEqualTo(queuedIndexes()[2])
    }

    private companion object {
        const val FRAME_TIME = 1_000_000_000L

        // 80 percent of a frame at 60 FPS and at 120 FPS, in whole nanoseconds.
        const val DUE = 13_333_333L
        const val DUE_AT_120 = 6_666_666L
    }
}

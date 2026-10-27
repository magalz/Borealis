package io.github.magalz.borealis.stream.game

import android.net.wifi.WifiManager
import android.os.HandlerThread
import android.view.View
import com.google.common.truth.Truth.assertThat
import com.limelight.binding.input.ControllerHandler
import com.limelight.binding.input.virtual_controller.VirtualController
import com.limelight.nvstream.jni.MoonBridge
import io.github.magalz.borealis.connection.ShadowRecordingBridge
import io.github.magalz.borealis.host.launch.ConflictAnswer
import io.github.magalz.borealis.host.launch.ConflictChoice
import io.github.magalz.borealis.logging.AuditType
import io.github.magalz.borealis.stream.StreamPhase
import java.util.concurrent.atomic.AtomicReference
import org.junit.Test
import org.robolectric.shadows.ShadowToast
import org.robolectric.util.ReflectionHelpers

/** What `Game.onStop`, `onPause` and `onDestroy` stop, unbind and release. */
internal class GameStopTest : GameTestBase() {
    private val stopCalls = listOf("interruptConnection", "stopConnection", "cleanupBridge")

    /** The decoder numbers of a stream that ran: [frames] frames, in [totalMs] and [decoderMs]. */
    private fun decodedFrames(frames: Int, totalMs: Long, decoderMs: Long, videoFormat: Int) {
        val stats = ReflectionHelpers.getField<Any>(rig.renderer, "globalVideoStats")
        ReflectionHelpers.setField(stats, "totalFramesReceived", frames)
        ReflectionHelpers.setField(stats, "totalTimeMs", totalMs)
        ReflectionHelpers.setField(stats, "decoderTimeMs", decoderMs)
        ReflectionHelpers.setField(rig.renderer, "videoFormat", videoFormat)
    }

    private fun latencyToast(): String = ShadowToast.getTextOfLatestToast()

    @Test
    fun `onStop ends the activity and stops the connection that runs`() {
        rig.streaming()

        rig.stop()

        assertThat(rig.game.isFinishing).isTrue()
        assertThat(rig.field<Boolean>("connected")).isFalse()
        assertThat(ShadowRecordingBridge.names).containsExactlyElementsIn(stopCalls).inOrder()
        // The stop runs on a thread of its own, with the bridge locked, so the UI never waits for
        // it.
        assertThat(ShadowRecordingBridge.named("stopConnection").single().locked).isTrue()
    }

    @Test
    fun `onStop stops the controller handler of a running stream`() {
        rig.streaming()
        val handler = rig.field<ControllerHandler>("controllerHandler")
        assertThat(ReflectionHelpers.getField<Boolean>(handler, "stopped")).isFalse()

        rig.stop()

        assertThat(ReflectionHelpers.getField<Boolean>(handler, "stopped")).isTrue()
    }

    @Test
    fun `onStop leaves the controller handler of a stream that never connected to onDestroy`() {
        rig.create()
        val handler = rig.field<ControllerHandler>("controllerHandler")

        rig.stop()
        assertThat(ReflectionHelpers.getField<Boolean>(handler, "stopped")).isFalse()

        rig.destroyActivity()
        assertThat(ReflectionHelpers.getField<Boolean>(handler, "stopped")).isTrue()
    }

    @Test
    fun `onStop ends the session of the audit trail as a stop by the user`() {
        rig.streaming()
        rig.audit.clear()

        rig.stop()

        val event = rig.audit.single()
        assertThat(event.type).isEqualTo(AuditType.SESSION_END)
        assertThat(event.detail).startsWith("reason=user")
    }

    @Test
    fun `onStop leaves the native library alone when no connection started`() {
        rig.create()

        rig.stop()

        assertThat(rig.game.isFinishing).isTrue()
        assertThat(ShadowRecordingBridge.names).isEmpty()
        assertThat(rig.audit).isEmpty()
    }

    @Test
    fun `onStop marks the failure screen as shown and ends the launch retries`() {
        rig.create()

        rig.stop()

        assertThat(rig.field<Boolean>("displayedFailureDialog")).isTrue()
        assertThat(ReflectionHelpers.getField<Boolean>(rig.connection, "launchCancelled")).isTrue()
    }

    @Test
    fun `onStop answers a question about another running app with cancel`() {
        rig.create()
        val answer = ConflictAnswer()
        rig.game.launchConflict(10, "Other App", GameIntent.APP_ID, "Desktop", answer)
        assertThat(rig.phase).isInstanceOf(StreamPhase.Conflict::class.java)

        rig.stop()

        val chosen = ReflectionHelpers.getField<AtomicReference<ConflictChoice?>>(answer, "chosen")
        assertThat(chosen.get()).isEqualTo(ConflictChoice.CANCEL)
    }

    @Test
    fun `onStop hides the on-screen controller`() {
        rig.preference("checkbox_show_onscreen_controls", true)
        rig.create()
        val controller = rig.field<VirtualController>("virtualController")
        val configure = ReflectionHelpers.getField<View>(controller, "buttonConfigure")
        assertThat(configure.visibility).isEqualTo(View.VISIBLE)

        rig.stop()

        assertThat(configure.visibility).isEqualTo(View.INVISIBLE)
    }

    @Test
    fun `onStop shows the latency of the stream when the setting is on`() {
        rig.preference("checkbox_enable_post_stream_toast", true)
        rig.streaming()
        decodedFrames(10, 300, 50, MoonBridge.VIDEO_FORMAT_H264)

        rig.stop()

        assertThat(latencyToast())
            .isEqualTo(
                "Average frame decoding latency: 30 ms (hardware decoder latency: 5 ms) [H.264]"
            )
    }

    @Test
    fun `onStop shows the decoder latency alone when there is no end-to-end latency`() {
        rig.preference("checkbox_enable_post_stream_toast", true)
        rig.streaming()
        decodedFrames(10, 0, 50, MoonBridge.VIDEO_FORMAT_H264)

        rig.stop()

        assertThat(latencyToast()).isEqualTo("Average hardware decoding latency: 5 ms [H.264]")
    }

    @Test
    fun `onStop names the codec and the HDR of the stream in the latency toast`() {
        rig.preference("checkbox_enable_post_stream_toast", true)
        rig.streaming()
        decodedFrames(10, 300, 0, MoonBridge.VIDEO_FORMAT_H265_MAIN10)

        rig.stop()

        assertThat(latencyToast()).isEqualTo("Average frame decoding latency: 30 ms [HEVC HDR]")
    }

    @Test
    fun `onStop names AV1 in the latency toast`() {
        rig.preference("checkbox_enable_post_stream_toast", true)
        rig.streaming()
        decodedFrames(10, 300, 0, MoonBridge.VIDEO_FORMAT_AV1_MAIN8)

        rig.stop()

        assertThat(latencyToast()).isEqualTo("Average frame decoding latency: 30 ms [AV1]")
    }

    @Test
    fun `onStop does not know the codec of a stream that never chose one`() {
        rig.preference("checkbox_enable_post_stream_toast", true)
        rig.streaming()
        decodedFrames(10, 300, 0, NO_FORMAT)

        rig.stop()

        assertThat(latencyToast()).isEqualTo("Average frame decoding latency: 30 ms [UNKNOWN]")
    }

    @Test
    fun `onStop shows nothing when the stream decoded no frame`() {
        rig.preference("checkbox_enable_post_stream_toast", true)
        rig.streaming()

        rig.stop()

        assertToasts()
    }

    @Test
    fun `onStop shows nothing when the setting is off`() {
        rig.streaming()
        decodedFrames(10, 300, 50, MoonBridge.VIDEO_FORMAT_H264)

        rig.stop()

        assertToasts()
    }

    @Test
    fun `onPause of a finishing activity lets the pointer go`() {
        rig.streaming()
        assertThat(rig.field<Boolean>("grabbedInput")).isTrue()
        rig.game.finish()

        rig.pause()

        assertThat(rig.field<Boolean>("grabbedInput")).isFalse()
    }

    @Test
    fun `onPause of an activity that goes on keeps the pointer`() {
        rig.streaming()

        rig.pause()

        assertThat(rig.field<Boolean>("grabbedInput")).isTrue()
    }

    @Test
    fun `onDestroy releases the Wi-Fi locks`() {
        rig.create()
        val locks =
            listOf("highPerfWifiLock", "lowLatencyWifiLock").map {
                rig.field<WifiManager.WifiLock>(it)
            }
        assertThat(locks.map { it.isHeld }).containsExactly(true, true)

        rig.destroyActivity()

        assertThat(locks.map { it.isHeld }).containsExactly(false, false)
    }

    @Test
    fun `onDestroy ends the thread of the controller handler`() {
        rig.create()
        val handlerThread =
            ReflectionHelpers.getField<HandlerThread>(
                rig.field<ControllerHandler>("controllerHandler"),
                "backgroundHandlerThread",
            )
        assertThat(handlerThread.isAlive).isTrue()

        rig.destroyActivity()

        handlerThread.join(JOIN_MS)
        assertThat(handlerThread.isAlive).isFalse()
    }

    @Test
    fun `onDestroy ends the launch retries`() {
        rig.create()

        rig.destroyActivity()

        assertThat(ReflectionHelpers.getField<Boolean>(rig.connection, "launchCancelled")).isTrue()
    }

    @Test
    fun `a connection that was terminated is not stopped again by onStop`() {
        rig.streaming()
        rig.game.connectionTerminated(MoonBridge.ML_ERROR_GRACEFUL_TERMINATION)
        rig.awaitStop()

        rig.stop()

        assertThat(ShadowRecordingBridge.named("stopConnection")).hasSize(1)
    }

    private companion object {
        const val JOIN_MS = 5_000L
        const val NO_FORMAT = 0
    }
}

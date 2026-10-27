package io.github.magalz.borealis.stream.game.session

import android.content.Intent
import android.content.pm.ShortcutInfo
import android.content.pm.ShortcutManager
import android.view.View
import android.view.WindowManager
import android.widget.TextView
import com.google.common.truth.Truth.assertThat
import com.limelight.R
import com.limelight.binding.video.codec
import com.limelight.nvstream.jni.MoonBridge
import io.github.magalz.borealis.connection.ShadowRecordingBridge
import io.github.magalz.borealis.host.failure.FailureKind
import io.github.magalz.borealis.host.video.FirstFrameWatchdog
import io.github.magalz.borealis.logging.AuditOutcome
import io.github.magalz.borealis.logging.AuditType
import io.github.magalz.borealis.stream.SessionInfo
import io.github.magalz.borealis.stream.StartStage
import io.github.magalz.borealis.stream.StreamPhase
import io.github.magalz.borealis.stream.game.AVC_DECODER
import io.github.magalz.borealis.stream.game.GameIntent
import io.github.magalz.borealis.stream.game.GameTestBase
import io.github.magalz.borealis.stream.game.RecordingSurfaceHolder
import io.github.magalz.borealis.stream.game.phase
import java.util.concurrent.TimeUnit
import org.junit.Test
import org.robolectric.Shadows.shadowOf
import org.robolectric.shadows.ShadowLooper
import org.robolectric.util.ReflectionHelpers

/**
 * What the activity does when the connection starts and while it runs: the session, the display,
 * the audit trail, the game mode of the system, the notice of a poor connection and the watchdog
 * that waits for the first frame.
 */
internal class GameConnectionTest : GameTestBase() {
    private val hevcDecoder = "c2.test.hevc.decoder"

    private val notice: TextView
        get() = rig.game.findViewById(R.id.notificationOverlay)

    private fun streamFormat(format: Int) {
        ReflectionHelpers.setField(rig.renderer, "videoFormat", format)
    }

    private fun info(): SessionInfo = (rig.phase as StreamPhase.Streaming).info

    // The connection starts

    @Test
    fun `a connection that started waits for the first frame`() {
        rig.create()

        rig.game.connectionStarted()

        assertThat(rig.phase).isEqualTo(StreamPhase.Starting(StartStage.FIRST_FRAME))
        assertThat(rig.field<Boolean>("connected")).isTrue()
    }

    @Test
    fun `the first frame of a connection that started is the stream`() {
        rig.create()
        rig.game.connectionStarted()

        rig.firstFrame()

        assertThat(rig.phase).isInstanceOf(StreamPhase.Streaming::class.java)
    }

    @Test
    fun `the session info is the stream that the connection agreed on`() {
        rig.create()
        streamFormat(MoonBridge.VIDEO_FORMAT_H264)
        rig.game.connectionStarted()
        rig.firstFrame()

        assertThat(info())
            .isEqualTo(
                SessionInfo(
                    width = 1280,
                    height = 720,
                    frameRate = 60,
                    codec = "H.264",
                    hdr = false,
                    decoder = AVC_DECODER,
                    bitrateLimitKbps = 10000,
                )
            )
    }

    @Test
    fun `the session info has the resolution, the frame rate and the bitrate of the settings`() {
        rig.preference("list_resolution", "1920x1080")
        rig.preference("list_fps", "90")
        rig.preference("seekbar_bitrate_kbps", 25000)
        rig.create()
        rig.game.connectionStarted()
        rig.firstFrame()

        assertThat(info().width).isEqualTo(1920)
        assertThat(info().height).isEqualTo(1080)
        assertThat(info().frameRate).isEqualTo(90)
        assertThat(info().bitrateLimitKbps).isEqualTo(25000)
    }

    @Test
    fun `the session info has the frame rate that was negotiated, not the one that was asked for`() {
        rig.preference("frame_pacing", "cap-fps")
        rig.create()
        rig.game.connectionStarted()
        rig.firstFrame()

        assertThat(info().frameRate).isEqualTo(59)
    }

    @Test
    fun `the session info names the codec of the decoder that the renderer chose`() {
        rig.decoders(codec(AVC_DECODER), codec(hevcDecoder) { mime = "video/hevc" })
        rig.create()
        streamFormat(MoonBridge.VIDEO_FORMAT_H265_MAIN10)
        rig.game.connectionStarted()
        rig.firstFrame()

        assertThat(info().codec).isEqualTo("HEVC")
        assertThat(info().hdr).isTrue()
        assertThat(info().decoder).isEqualTo(hevcDecoder)
    }

    @Test
    fun `the session info has no decoder before the renderer chose a format`() {
        rig.create()
        rig.game.connectionStarted()
        rig.firstFrame()

        assertThat(info().codec).isEqualTo("Unknown")
        assertThat(info().decoder).isNull()
    }

    @Test
    fun `a connection that started keeps the display on`() {
        rig.create()
        assertThat(
                rig.game.window.attributes.flags and WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON
            )
            .isEqualTo(0)

        rig.game.connectionStarted()

        assertThat(
                rig.game.window.attributes.flags and WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON
            )
            .isNotEqualTo(0)
    }

    @Test
    fun `a connection that started is in the audit trail with what was negotiated`() {
        rig.preference("list_resolution", "1920x1080")
        rig.preference("list_fps", "90")
        rig.create()
        streamFormat(MoonBridge.VIDEO_FORMAT_H265_MAIN10)

        rig.game.connectionStarted()

        val start = rig.audit.single()
        assertThat(start.type).isEqualTo(AuditType.SESSION_START)
        assertThat(start.outcome).isEqualTo(AuditOutcome.SUCCESS)
        assertThat(start.host).isEqualTo("Den PC (192.168.1.5)")
        assertThat(start.detail).isEqualTo("codec=HEVC hdr=true resolution=1920x1080 fps=90")
    }

    @Test
    fun `the audit trail has the frame rate that was negotiated, not the one that was asked for`() {
        rig.preference("frame_pacing", "cap-fps")
        rig.create()

        rig.game.connectionStarted()

        assertThat(rig.audit.single().detail)
            .isEqualTo("codec=unknown hdr=false resolution=1280x720 fps=59")
    }

    @Test
    fun `a connection that started tells the system that the game runs`() {
        rig.create()
        val modes = GameModeRecorder().also { it.install(rig.game) }

        rig.game.connectionStarted()

        assertThat(modes.calls).containsExactly(GameModes.UNINTERRUPTIBLE)
    }

    @Test
    fun `a connection that the connection thread reports starts on the main thread`() {
        rig.create()

        onConnectionThread { rig.game.connectionStarted() }
        assertThat(rig.field<Boolean>("connected")).isFalse()
        assertThat(rig.phase).isEqualTo(StreamPhase.Starting(StartStage.CONNECTING))

        ShadowLooper.idleMainLooper()
        assertThat(rig.field<Boolean>("connected")).isTrue()
        assertThat(rig.phase).isEqualTo(StreamPhase.Starting(StartStage.FIRST_FRAME))
    }

    // The shortcut of the computer

    private val shortcuts: ShortcutManager
        get() = rig.application.getSystemService(ShortcutManager::class.java)

    private fun addShortcut(id: String) {
        val shortcut =
            ShortcutInfo.Builder(rig.application, id)
                .setShortLabel("Den PC")
                .setIntent(Intent(Intent.ACTION_MAIN))
                .build()
        shortcuts.addDynamicShortcuts(listOf(shortcut))
    }

    @Test
    fun `a connection that started reports the shortcut of its computer as used`() {
        addShortcut(GameIntent.PC_UUID)
        rig.create()

        rig.game.connectionStarted()

        assertThat(shadowOf(shortcuts).reportedShortcutsUsed).containsExactly(GameIntent.PC_UUID)
    }

    @Test
    fun `a computer that has no shortcut reports none`() {
        addShortcut("another-pc")
        rig.create()

        rig.game.connectionStarted()

        assertThat(shadowOf(shortcuts).reportedShortcutsUsed).isEmpty()
    }

    @Test
    fun `the shortcut is reported on the thread of the connection, not on the main thread`() {
        addShortcut(GameIntent.PC_UUID)
        rig.create()

        onConnectionThread { rig.game.connectionStarted() }

        // The main thread has not run the part of the callback that it owns.
        assertThat(rig.field<Boolean>("connected")).isFalse()
        assertThat(shadowOf(shortcuts).reportedShortcutsUsed).containsExactly(GameIntent.PC_UUID)
    }

    // The connection stops

    @Test
    fun `a connection that stops tells the system that the game ended`() {
        rig.streaming()
        val modes = GameModeRecorder().also { it.install(rig.game) }

        rig.stop()

        assertThat(modes.calls).containsExactly(GameModes.ENDED)
    }

    @Test
    fun `an activity that never connected has no game to end`() {
        rig.create()
        val modes = GameModeRecorder().also { it.install(rig.game) }

        rig.stop()

        assertThat(modes.calls).isEmpty()
    }

    // The notice of a poor connection

    @Test
    fun `a poor connection at a high bitrate is a slow one and asks to reduce the bitrate`() {
        rig.create()

        rig.game.connectionStatusUpdate(MoonBridge.CONN_STATUS_POOR)

        assertThat(notice.text.toString()).isEqualTo("Slow connection to PC\nReduce your bitrate")
        assertThat(notice.visibility).isEqualTo(View.VISIBLE)
    }

    @Test
    fun `a poor connection at a low bitrate is only a poor one`() {
        rig.preference("seekbar_bitrate_kbps", 5000)
        rig.create()

        rig.game.connectionStatusUpdate(MoonBridge.CONN_STATUS_POOR)

        assertThat(notice.text.toString()).isEqualTo("Poor connection to PC")
        assertThat(notice.visibility).isEqualTo(View.VISIBLE)
    }

    @Test
    fun `a bitrate just above 5000 is still a high one`() {
        rig.preference("seekbar_bitrate_kbps", 5001)
        rig.create()

        rig.game.connectionStatusUpdate(MoonBridge.CONN_STATUS_POOR)

        assertThat(notice.text.toString()).isEqualTo("Slow connection to PC\nReduce your bitrate")
    }

    @Test
    fun `a connection that is okay again hides the notice`() {
        rig.create()
        rig.game.connectionStatusUpdate(MoonBridge.CONN_STATUS_POOR)

        rig.game.connectionStatusUpdate(MoonBridge.CONN_STATUS_OKAY)

        assertThat(notice.visibility).isEqualTo(View.GONE)
    }

    @Test
    fun `a status that is neither okay nor poor leaves the notice as it is`() {
        rig.create()
        rig.game.connectionStatusUpdate(MoonBridge.CONN_STATUS_POOR)

        rig.game.connectionStatusUpdate(UNKNOWN_STATUS)

        assertThat(notice.visibility).isEqualTo(View.VISIBLE)
        assertThat(notice.text.toString()).isEqualTo("Slow connection to PC\nReduce your bitrate")
    }

    @Test
    fun `the setting that disables warnings keeps the notice away`() {
        rig.preference("checkbox_disable_warnings", true)
        rig.create()

        rig.game.connectionStatusUpdate(MoonBridge.CONN_STATUS_POOR)

        assertThat(notice.visibility).isEqualTo(View.GONE)
        assertThat(notice.text.toString()).isEmpty()
    }

    @Test
    fun `a status that the connection thread reports reaches the notice on the main thread`() {
        rig.create()

        onConnectionThread { rig.game.connectionStatusUpdate(MoonBridge.CONN_STATUS_POOR) }
        assertThat(notice.visibility).isEqualTo(View.GONE)

        ShadowLooper.idleMainLooper()
        assertThat(notice.visibility).isEqualTo(View.VISIBLE)
    }

    // The watchdog of the first frame

    private fun idleWatchdog(millis: Long) =
        ShadowLooper.idleMainLooper(millis, TimeUnit.MILLISECONDS)

    private fun watchdogFired(): Boolean {
        joinThreadsNamed(WATCHDOG_THREAD)
        return ShadowRecordingBridge.named("getPortFlagsFromTerminationErrorCode").isNotEmpty()
    }

    @Test
    fun `a stream whose first frame never comes ends with no video frame`() {
        rig.create()
        rig.game.connectionStarted()

        idleWatchdog(FirstFrameWatchdog.DEFAULT_TIMEOUT_MILLIS)

        assertThat(watchdogFired()).isTrue()
        ShadowLooper.idleMainLooper()
        rig.awaitStop()
        assertThat(
                ShadowRecordingBridge.named("getPortFlagsFromTerminationErrorCode").single().args
            )
            .containsExactly(MoonBridge.ML_ERROR_NO_VIDEO_FRAME)
        val failed = rig.phase as StreamPhase.Failed
        assertThat(failed.failure.kind).isEqualTo(FailureKind.GENERIC)
        assertThat(failed.failure.rawCode).isEqualTo("Error -101")
    }

    @Test
    fun `the watchdog waits until its time is up`() {
        rig.create()
        rig.game.connectionStarted()

        idleWatchdog(FirstFrameWatchdog.DEFAULT_TIMEOUT_MILLIS - 1_000)

        assertThat(watchdogFired()).isFalse()
    }

    @Test
    fun `a stream that waits for the connection has no watchdog yet`() {
        rig.create()

        idleWatchdog(FirstFrameWatchdog.DEFAULT_TIMEOUT_MILLIS * 2)

        assertThat(watchdogFired()).isFalse()
    }

    @Test
    fun `the first frame ends the watchdog`() {
        rig.create()
        rig.game.connectionStarted()
        rig.firstFrame()

        idleWatchdog(FirstFrameWatchdog.DEFAULT_TIMEOUT_MILLIS * 2)

        assertThat(watchdogFired()).isFalse()
    }

    @Test
    fun `a connection that ended ends the watchdog`() {
        rig.create()
        rig.game.connectionStarted()
        rig.game.connectionTerminated(MoonBridge.ML_ERROR_GRACEFUL_TERMINATION)
        rig.awaitStop()
        val ended = ShadowRecordingBridge.named("getPortFlagsFromTerminationErrorCode").size

        idleWatchdog(FirstFrameWatchdog.DEFAULT_TIMEOUT_MILLIS * 2)
        joinThreadsNamed(WATCHDOG_THREAD)

        assertThat(ShadowRecordingBridge.named("getPortFlagsFromTerminationErrorCode"))
            .hasSize(ended)
    }

    @Test
    fun `an activity that stopped ends the watchdog`() {
        rig.create()
        rig.game.connectionStarted()
        rig.stop()

        idleWatchdog(FirstFrameWatchdog.DEFAULT_TIMEOUT_MILLIS * 2)

        assertThat(watchdogFired()).isFalse()
    }

    @Test
    fun `a surface that goes away under a running stream ends the watchdog`() {
        val holder = RecordingSurfaceHolder()
        rig.create()
        rig.game.surfaceCreated(holder)
        rig.game.connectionStarted()
        rig.markConnectionAttempted()

        rig.game.surfaceDestroyed(holder)
        rig.awaitStop()
        idleWatchdog(FirstFrameWatchdog.DEFAULT_TIMEOUT_MILLIS * 2)

        assertThat(watchdogFired()).isFalse()
    }

    private companion object {
        const val UNKNOWN_STATUS = 2
        const val WATCHDOG_THREAD = "Borealis first-frame timeout"
    }
}

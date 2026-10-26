package com.limelight.nvstream.jni

import com.google.common.truth.Truth.assertThat
import com.limelight.nvstream.NvConnectionListener
import com.limelight.nvstream.av.audio.AudioRenderer
import com.limelight.nvstream.av.video.VideoDecoderRenderer
import com.limelight.nvstream.jni.MoonBridge.AudioConfiguration
import io.github.magalz.borealis.connection.ShadowRecordingBridge
import io.github.magalz.borealis.logging.BorealisLog
import io.github.magalz.borealis.logging.LogRecord
import io.github.magalz.borealis.logging.LogSink
import io.github.magalz.borealis.logging.LogTag
import java.util.concurrent.CopyOnWriteArrayList
import org.junit.After
import org.junit.Assert.assertThrows
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/** One call that a fake received: which, and with which values. */
private data class Call(val name: String, val args: List<Any?> = emptyList())

private class FakeVideoRenderer(private val received: MutableList<Call>) : VideoDecoderRenderer() {
    var setupResult = 0
    var submitResult = 0

    override fun setup(format: Int, width: Int, height: Int, redrawRate: Int): Int {
        received += Call("video.setup", listOf(format, width, height, redrawRate))
        return setupResult
    }

    override fun start() {
        received += Call("video.start")
    }

    override fun stop() {
        received += Call("video.stop")
    }

    override fun cleanup() {
        received += Call("video.cleanup")
    }

    override fun submitDecodeUnit(
        decodeUnitData: ByteArray?,
        decodeUnitLength: Int,
        decodeUnitType: Int,
        frameNumber: Int,
        frameType: Int,
        frameHostProcessingLatency: Char,
        receiveTimeUs: Long,
        enqueueTimeUs: Long,
    ): Int {
        received +=
            Call(
                "video.submit",
                listOf(
                    decodeUnitData,
                    decodeUnitLength,
                    decodeUnitType,
                    frameNumber,
                    frameType,
                    frameHostProcessingLatency,
                    receiveTimeUs,
                    enqueueTimeUs,
                ),
            )
        return submitResult
    }

    // MoonBridge never calls these two: the native side has no callback for them
    override fun getCapabilities(): Int = error("getCapabilities is not a bridge callback")

    override fun setHdrMode(enabled: Boolean, hdrMetadata: ByteArray?) {
        error("setHdrMode is not a bridge callback of the renderer")
    }
}

private class FakeAudioRenderer(private val received: MutableList<Call>) : AudioRenderer {
    var setupResult = 0

    override fun setup(
        audioConfiguration: AudioConfiguration,
        sampleRate: Int,
        samplesPerFrame: Int,
    ): Int {
        received += Call("audio.setup", listOf(audioConfiguration, sampleRate, samplesPerFrame))
        return setupResult
    }

    override fun start() {
        received += Call("audio.start")
    }

    override fun stop() {
        received += Call("audio.stop")
    }

    override fun playDecodedAudio(audioData: ShortArray?) {
        received += Call("audio.play", listOf(audioData))
    }

    override fun cleanup() {
        received += Call("audio.cleanup")
    }
}

private class FakeListener(private val received: MutableList<Call>) : NvConnectionListener {
    override fun stageStarting(stage: String?) {
        received += Call("listener.stageStarting", listOf(stage))
    }

    override fun stageComplete(stage: String?) {
        received += Call("listener.stageComplete", listOf(stage))
    }

    override fun stageFailed(stage: String, portFlags: Int, errorCode: Int) {
        received += Call("listener.stageFailed", listOf(stage, portFlags, errorCode))
    }

    override fun connectionStarted() {
        received += Call("listener.connectionStarted")
    }

    override fun connectionTerminated(errorCode: Int) {
        received += Call("listener.connectionTerminated", listOf(errorCode))
    }

    override fun connectionStatusUpdate(connectionStatus: Int) {
        received += Call("listener.connectionStatusUpdate", listOf(connectionStatus))
    }

    override fun displayMessage(message: String) {
        error("displayMessage is not a bridge callback")
    }

    override fun displayTransientMessage(message: String) {
        error("displayTransientMessage is not a bridge callback")
    }

    override fun rumble(controllerNumber: Short, lowFreqMotor: Short, highFreqMotor: Short) {
        received += Call("listener.rumble", listOf(controllerNumber, lowFreqMotor, highFreqMotor))
    }

    override fun rumbleTriggers(controllerNumber: Short, leftTrigger: Short, rightTrigger: Short) {
        received +=
            Call("listener.rumbleTriggers", listOf(controllerNumber, leftTrigger, rightTrigger))
    }

    override fun setHdrMode(enabled: Boolean, hdrMetadata: ByteArray?) {
        received += Call("listener.setHdrMode", listOf(enabled, hdrMetadata))
    }

    override fun setMotionEventState(controllerNumber: Short, motionType: Byte, reportRateHz: Short) {
        received +=
            Call("listener.setMotionEventState", listOf(controllerNumber, motionType, reportRateHz))
    }

    override fun setControllerLED(controllerNumber: Short, r: Byte, g: Byte, b: Byte) {
        received += Call("listener.setControllerLED", listOf(controllerNumber, r, g, b))
    }
}

/**
 * What `MoonBridge` does when the native library calls back: the `bridge*` methods that the C code
 * looks up by name (see [MoonBridgeJniContractTest]). Each forwards to the renderer or the listener
 * that `setupBridge` stored, with the values as they came, answers a fixed value when nothing is
 * stored, and does nothing else. The stand-in for the library is [ShadowRecordingBridge], which
 * replaces the static initializer and the natives and lets `setupBridge` and `cleanupBridge` run.
 */
@RunWith(RobolectricTestRunner::class)
@Config(shadows = [ShadowRecordingBridge::class])
class MoonBridgeCallbacksTest {
    private val received = mutableListOf<Call>()
    private val video = FakeVideoRenderer(received)
    private val audio = FakeAudioRenderer(received)
    private val listener = FakeListener(received)

    @Before
    fun connect() {
        ShadowRecordingBridge.reset()
        MoonBridge.setupBridge(video, audio, listener)
    }

    @After
    fun disconnect() {
        MoonBridge.cleanupBridge()
        BorealisLog.shutdown()
    }

    // The video renderer

    @Test
    fun `a video setup reaches the renderer with its four values and returns its answer`() {
        video.setupResult = 7

        val result = MoonBridge.bridgeDrSetup(MoonBridge.VIDEO_FORMAT_H265, 1920, 1080, 120)

        assertThat(result).isEqualTo(7)
        assertThat(received).containsExactly(Call("video.setup", listOf(0x0100, 1920, 1080, 120)))
    }

    @Test
    fun `a video setup without a renderer answers minus one`() {
        MoonBridge.cleanupBridge()

        assertThat(MoonBridge.bridgeDrSetup(MoonBridge.VIDEO_FORMAT_H264, 1280, 720, 60))
            .isEqualTo(-1)
        assertThat(received).isEmpty()
    }

    @Test
    fun `the video start, stop and cleanup reach the renderer in the order they come`() {
        MoonBridge.bridgeDrStart()
        MoonBridge.bridgeDrStop()
        MoonBridge.bridgeDrCleanup()

        assertThat(received.map { it.name })
            .containsExactly("video.start", "video.stop", "video.cleanup")
            .inOrder()
    }

    @Test
    fun `the video start, stop and cleanup without a renderer do nothing`() {
        MoonBridge.cleanupBridge()

        MoonBridge.bridgeDrStart()
        MoonBridge.bridgeDrStop()
        MoonBridge.bridgeDrCleanup()

        assertThat(received).isEmpty()
    }

    @Test
    fun `a decode unit reaches the renderer with every value and returns its answer`() {
        val data = byteArrayOf(1, 2, 3, 4)
        video.submitResult = MoonBridge.DR_NEED_IDR

        val result =
            MoonBridge.bridgeDrSubmitDecodeUnit(
                data,
                3,
                MoonBridge.BUFFER_TYPE_SPS,
                42,
                MoonBridge.FRAME_TYPE_IDR,
                0xFFFF.toChar(),
                1_000_000_000_000L,
                2_000_000_000_000L,
            )

        assertThat(result).isEqualTo(-1)
        assertThat(received)
            .containsExactly(
                Call(
                    "video.submit",
                    listOf(data, 3, 1, 42, 1, '￿', 1_000_000_000_000L, 2_000_000_000_000L),
                )
            )
    }

    @Test
    fun `a decode unit with no buffer reaches the renderer as it is`() {
        MoonBridge.bridgeDrSubmitDecodeUnit(null, 0, 0, 0, 0, '\u0000', 0L, 0L)

        assertThat(received.single().args.first()).isNull()
    }

    @Test
    fun `a decode unit without a renderer is accepted`() {
        MoonBridge.cleanupBridge()

        val result = MoonBridge.bridgeDrSubmitDecodeUnit(byteArrayOf(1), 1, 0, 1, 0, '\u0001', 1L, 1L)

        assertThat(result).isEqualTo(MoonBridge.DR_OK)
        assertThat(received).isEmpty()
    }

    // The audio renderer

    @Test
    fun `an audio init unpacks the configuration and reaches the renderer`() {
        audio.setupResult = 3

        val result = MoonBridge.bridgeArInit(AudioConfiguration(6, 0x3F).toInt(), 48_000, 240)

        assertThat(result).isEqualTo(3)
        assertThat(received)
            .containsExactly(Call("audio.setup", listOf(AudioConfiguration(6, 0x3F), 48_000, 240)))
    }

    @Test
    fun `an audio init with a packed value that has no magic byte throws and stays away from the renderer`() {
        val refused =
            assertThrows(IllegalArgumentException::class.java) {
                MoonBridge.bridgeArInit(0x000302C9, 48_000, 240)
            }

        assertThat(refused).hasMessageThat().isEqualTo("Audio configuration has invalid magic byte!")
        assertThat(received).isEmpty()
    }

    @Test
    fun `an audio init without a renderer answers minus one and unpacks nothing`() {
        MoonBridge.cleanupBridge()

        assertThat(MoonBridge.bridgeArInit(0, 48_000, 240)).isEqualTo(-1)
        assertThat(received).isEmpty()
    }

    @Test
    fun `the audio start, stop and cleanup reach the renderer in the order they come`() {
        MoonBridge.bridgeArStart()
        MoonBridge.bridgeArStop()
        MoonBridge.bridgeArCleanup()

        assertThat(received.map { it.name })
            .containsExactly("audio.start", "audio.stop", "audio.cleanup")
            .inOrder()
    }

    @Test
    fun `a decoded sample reaches the renderer as the same array`() {
        val pcm = shortArrayOf(1, -2, 3)

        MoonBridge.bridgeArPlaySample(pcm)
        MoonBridge.bridgeArPlaySample(null)

        assertThat(received)
            .containsExactly(Call("audio.play", listOf(pcm)), Call("audio.play", listOf(null)))
            .inOrder()
    }

    @Test
    fun `the audio callbacks without a renderer do nothing`() {
        MoonBridge.cleanupBridge()

        MoonBridge.bridgeArStart()
        MoonBridge.bridgeArStop()
        MoonBridge.bridgeArPlaySample(shortArrayOf(1))
        MoonBridge.bridgeArCleanup()

        assertThat(received).isEmpty()
    }

    // The connection listener

    @Test
    fun `a stage that starts or completes reaches the listener by the name the library gives it`() {
        MoonBridge.bridgeClStageStarting(4)
        MoonBridge.bridgeClStageComplete(2)

        assertThat(received)
            .containsExactly(
                Call("listener.stageStarting", listOf("RTSP handshake")),
                Call("listener.stageComplete", listOf("stage 2")),
            )
            .inOrder()
    }

    @Test
    fun `a failed stage carries its name, the port flags of the stage and the error code`() {
        ShadowRecordingBridge.answers["getPortFlagsFromStage"] = 0x0101

        MoonBridge.bridgeClStageFailed(4, -2)

        assertThat(received)
            .containsExactly(Call("listener.stageFailed", listOf("RTSP handshake", 0x0101, -2)))
        assertThat(ShadowRecordingBridge.named("getPortFlagsFromStage").map { it.args })
            .containsExactly(listOf(4))
    }

    @Test
    fun `the connection events reach the listener with their value`() {
        MoonBridge.bridgeClConnectionStarted()
        MoonBridge.bridgeClConnectionTerminated(MoonBridge.ML_ERROR_NO_VIDEO_TRAFFIC)
        MoonBridge.bridgeClConnectionStatusUpdate(MoonBridge.CONN_STATUS_POOR)

        assertThat(received)
            .containsExactly(
                Call("listener.connectionStarted"),
                Call("listener.connectionTerminated", listOf(-100)),
                Call("listener.connectionStatusUpdate", listOf(1)),
            )
            .inOrder()
    }

    @Test
    fun `a rumble arrives with its three shorts, the unsigned ones as negative numbers`() {
        MoonBridge.bridgeClRumble(1, 0xFFFF.toShort(), 0x7FFF)
        MoonBridge.bridgeClRumbleTriggers(2, 0x8000.toShort(), 3)

        assertThat(received)
            .containsExactly(
                Call("listener.rumble", listOf(1.toShort(), (-1).toShort(), 0x7FFF.toShort())),
                Call(
                    "listener.rumbleTriggers",
                    listOf(2.toShort(), Short.MIN_VALUE, 3.toShort()),
                ),
            )
            .inOrder()
    }

    @Test
    fun `a motion state and a controller light arrive with their shorts and bytes`() {
        MoonBridge.bridgeClSetMotionEventState(3, MoonBridge.LI_MOTION_TYPE_GYRO, 100)
        MoonBridge.bridgeClSetControllerLED(4, 0xFF.toByte(), 0x7F, 0)

        assertThat(received)
            .containsExactly(
                Call(
                    "listener.setMotionEventState",
                    listOf(3.toShort(), MoonBridge.LI_MOTION_TYPE_GYRO, 100.toShort()),
                ),
                Call(
                    "listener.setControllerLED",
                    listOf(4.toShort(), 0xFF.toByte(), 0x7F.toByte(), 0.toByte()),
                ),
            )
            .inOrder()
    }

    @Test
    fun `the HDR mode arrives with the metadata, or with none`() {
        val metadata = byteArrayOf(9, 8, 7)

        MoonBridge.bridgeClSetHdrMode(true, metadata)
        MoonBridge.bridgeClSetHdrMode(true, null)
        MoonBridge.bridgeClSetHdrMode(false, null)

        assertThat(received)
            .containsExactly(
                Call("listener.setHdrMode", listOf(true, metadata)),
                Call("listener.setHdrMode", listOf(true, null)),
                Call("listener.setHdrMode", listOf(false, null)),
            )
            .inOrder()
    }

    @Test
    fun `the listener callbacks without a listener do nothing`() {
        MoonBridge.cleanupBridge()

        MoonBridge.bridgeClStageStarting(1)
        MoonBridge.bridgeClStageComplete(1)
        MoonBridge.bridgeClStageFailed(1, -1)
        MoonBridge.bridgeClConnectionStarted()
        MoonBridge.bridgeClConnectionTerminated(0)
        MoonBridge.bridgeClConnectionStatusUpdate(0)
        MoonBridge.bridgeClRumble(0, 0, 0)
        MoonBridge.bridgeClRumbleTriggers(0, 0, 0)
        MoonBridge.bridgeClSetHdrMode(false, null)
        MoonBridge.bridgeClSetMotionEventState(0, 0, 0)
        MoonBridge.bridgeClSetControllerLED(0, 0, 0, 0)

        assertThat(received).isEmpty()
        assertThat(ShadowRecordingBridge.named("getPortFlagsFromStage")).isEmpty()
    }

    // setupBridge and cleanupBridge

    @Test
    fun `setupBridge stores the three, each apart from the other two`() {
        MoonBridge.setupBridge(null, null, listener)
        MoonBridge.bridgeClConnectionStarted()
        assertThat(MoonBridge.bridgeDrSetup(0, 0, 0, 0)).isEqualTo(-1)
        assertThat(MoonBridge.bridgeArInit(0, 0, 0)).isEqualTo(-1)

        MoonBridge.setupBridge(video, null, null)
        assertThat(MoonBridge.bridgeDrSetup(0, 0, 0, 0)).isEqualTo(0)
        assertThat(MoonBridge.bridgeArInit(0, 0, 0)).isEqualTo(-1)
        MoonBridge.bridgeClConnectionStarted()

        MoonBridge.setupBridge(null, audio, null)
        assertThat(MoonBridge.bridgeArInit(AudioConfiguration(2, 0x3).toInt(), 1, 1)).isEqualTo(0)

        assertThat(received.map { it.name })
            .containsExactly(
                "listener.connectionStarted",
                "video.setup",
                "audio.setup",
            )
            .inOrder()
    }

    @Test
    fun `setupBridge replaces what an earlier call stored`() {
        val received2 = mutableListOf<Call>()

        MoonBridge.setupBridge(
            FakeVideoRenderer(received2),
            FakeAudioRenderer(received2),
            FakeListener(received2),
        )
        MoonBridge.bridgeDrStart()
        MoonBridge.bridgeArStart()
        MoonBridge.bridgeClConnectionStarted()

        assertThat(received).isEmpty()
        assertThat(received2.map { it.name })
            .containsExactly("video.start", "audio.start", "listener.connectionStarted")
            .inOrder()
    }

    @Test
    fun `cleanupBridge forgets the renderers and the listener`() {
        MoonBridge.cleanupBridge()

        MoonBridge.bridgeDrStart()
        MoonBridge.bridgeArStart()
        MoonBridge.bridgeClConnectionStarted()

        assertThat(received).isEmpty()
    }

    // The one helper that is not a callback

    @Test
    fun `the number of slices goes to the top byte of the video capabilities`() {
        assertThat(MoonBridge.CAPABILITY_SLICES_PER_FRAME(0)).isEqualTo(0)
        assertThat(MoonBridge.CAPABILITY_SLICES_PER_FRAME(1)).isEqualTo(0x01000000)
        assertThat(MoonBridge.CAPABILITY_SLICES_PER_FRAME(4)).isEqualTo(0x04000000)
        assertThat(MoonBridge.CAPABILITY_SLICES_PER_FRAME(0xFF.toByte())).isEqualTo(0xFF000000.toInt())
    }

    // The log

    @Test
    fun `a log line from the library goes to the log of the app and is reported as handled`() {
        val records = CopyOnWriteArrayList<LogRecord>()
        BorealisLog.initialize(
            listOf(
                object : LogSink {
                    override fun write(record: LogRecord) {
                        records += record
                    }
                }
            )
        )

        val handled = MoonBridge.bridgeClLogMessage("RTSP handshake complete\n".toByteArray())

        assertThat(handled).isTrue()
        assertThat(records.map { it.tag to it.message })
            .containsExactly(LogTag.COMMONC to "RTSP handshake complete")
    }

    @Test
    fun `a log line is reported as not handled while the log of the app is not running`() {
        BorealisLog.shutdown()

        assertThat(MoonBridge.bridgeClLogMessage("a line\n".toByteArray())).isFalse()
    }

    @Test
    fun `a log line that is null throws, so that the native side falls back to logcat`() {
        assertThrows(RuntimeException::class.java) { MoonBridge.bridgeClLogMessage(null) }
    }
}

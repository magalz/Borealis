package io.github.magalz.borealis.stream.testing

import io.github.magalz.borealis.host.failure.FailureKind
import io.github.magalz.borealis.host.failure.StreamFailure
import io.github.magalz.borealis.host.launch.ConflictChoice
import io.github.magalz.borealis.stream.LatencyRange
import io.github.magalz.borealis.stream.OverlayMode
import io.github.magalz.borealis.stream.PerfSnapshot
import io.github.magalz.borealis.stream.SessionInfo
import io.github.magalz.borealis.stream.StreamActions
import io.github.magalz.borealis.stream.StreamDefaults
import io.github.magalz.borealis.stream.StreamSession
import io.github.magalz.borealis.stream.TouchMode
import io.github.magalz.borealis.stream.keys.KeyChord

/** Records every call so a test can say what the session forwarded, and in which order. */
class RecordingActions : StreamActions {
    val calls = mutableListOf<String>()

    override fun send(chord: KeyChord) {
        calls += "send ${chord.vk}"
    }

    override fun sendText(text: String) {
        calls += "sendText $text"
    }

    override fun openSystemKeyboard() {
        calls += "openSystemKeyboard"
    }

    override fun requestKeyframe() {
        calls += "requestKeyframe"
    }

    override fun setTouchMode(mode: TouchMode) {
        calls += "setTouchMode $mode"
    }

    override fun setGamepadMouse(enabled: Boolean) {
        calls += "setGamepadMouse $enabled"
    }

    override fun setOverlay(mode: OverlayMode) {
        calls += "setOverlay $mode"
    }

    override fun menuHintShown() {
        calls += "menuHintShown"
    }

    override fun disconnect() {
        calls += "disconnect"
    }

    override fun quitApp() {
        calls += "quitApp"
    }

    override fun leave() {
        calls += "leave"
    }

    override fun retry() {
        calls += "retry"
    }

    override fun wakeHost() {
        calls += "wakeHost"
    }

    override fun pairAgain() {
        calls += "pairAgain"
    }

    override fun openDiagnostics() {
        calls += "openDiagnostics"
    }

    override fun answerConflict(choice: ConflictChoice) {
        calls += "answerConflict $choice"
    }
}

val aDefaults =
    StreamDefaults(
        overlay = OverlayMode.OFF,
        touchMode = TouchMode.TRACKPAD,
        gamepadMouse = false,
        showMenuHint = true,
    )

val aSessionInfo =
    SessionInfo(
        width = 1920,
        height = 1080,
        frameRate = 60,
        codec = "HEVC",
        hdr = false,
        decoder = "c2.qti.hevc.decoder",
        bitrateLimitKbps = 20_000,
    )

val aFailure = StreamFailure(FailureKind.GENERIC, "Error -1 · RTSP handshake", null)

fun aPerf(totalFps: Float = 60f) =
    PerfSnapshot(
        totalFps = totalFps,
        receivedFps = 59f,
        renderedFps = 58f,
        lossPercent = 0.5f,
        rttMs = 12,
        rttVarianceMs = 3,
        hostLatency = LatencyRange(min = 1f, avg = 2f, max = 5f),
        decodeMs = 4f,
        bitrateMbps = 18.5f,
    )

fun aSession(
    actions: RecordingActions = RecordingActions(),
    defaults: StreamDefaults = aDefaults,
    dockedKeyboard: Boolean = false,
) = StreamSession("Desktop", "Gaming PC", defaults, dockedKeyboard, actions)

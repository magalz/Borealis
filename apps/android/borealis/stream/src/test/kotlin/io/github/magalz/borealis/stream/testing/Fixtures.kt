package io.github.magalz.borealis.stream.testing

import io.github.magalz.borealis.host.failure.FailureKind
import io.github.magalz.borealis.host.failure.StreamFailure
import io.github.magalz.borealis.stream.LatencyRange
import io.github.magalz.borealis.stream.OverlayMode
import io.github.magalz.borealis.stream.PerfSnapshot
import io.github.magalz.borealis.stream.SessionInfo
import io.github.magalz.borealis.stream.StreamDefaults
import io.github.magalz.borealis.stream.StreamSession
import io.github.magalz.borealis.stream.TouchMode

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
    dockKeyboard: () -> Boolean = { false },
    clockMs: () -> Long = { 0L },
) = StreamSession("Desktop", "Gaming PC", defaults, dockKeyboard, actions, clockMs)

package io.github.magalz.borealis.stream.ui

import androidx.compose.runtime.Composable
import io.github.magalz.borealis.stream.PerfSnapshot
import io.github.magalz.borealis.stream.StreamIntents
import io.github.magalz.borealis.stream.StreamPhase

/**
 * The overlay, the menu hint and the in-game menu over the stream. Filled in by the Sprint 5
 * in-game menu and overlay task.
 */
@Suppress("UnusedParameter")
@Composable
fun StreamingLayer(
    appName: String,
    hostName: String,
    phase: StreamPhase.Streaming,
    perf: PerfSnapshot?,
    intents: StreamIntents,
) = Unit

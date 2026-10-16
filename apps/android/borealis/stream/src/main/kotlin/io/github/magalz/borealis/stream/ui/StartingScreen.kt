package io.github.magalz.borealis.stream.ui

import androidx.compose.runtime.Composable
import io.github.magalz.borealis.stream.StreamIntents
import io.github.magalz.borealis.stream.StreamPhase

/**
 * The stages of starting a stream and the host-busy retry. Filled in by the Sprint 5
 * starting-stream task.
 */
@Suppress("UnusedParameter")
@Composable
fun StartingScreen(
    appName: String,
    hostName: String,
    phase: StreamPhase.Starting,
    intents: StreamIntents,
) = Unit

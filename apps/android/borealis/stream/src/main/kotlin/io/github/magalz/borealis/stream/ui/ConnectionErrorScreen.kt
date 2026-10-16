package io.github.magalz.borealis.stream.ui

import androidx.compose.runtime.Composable
import io.github.magalz.borealis.host.failure.StreamFailure
import io.github.magalz.borealis.stream.StreamIntents

/**
 * What happened to the connection and the next step. Filled in by the Sprint 5 connection-error
 * task.
 */
@Suppress("UnusedParameter")
@Composable
fun ConnectionErrorScreen(
    appName: String,
    hostName: String,
    failure: StreamFailure,
    intents: StreamIntents,
) = Unit

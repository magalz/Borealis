package io.github.magalz.borealis.library.shell

import androidx.compose.material3.SnackbarHostState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.platform.LocalResources
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.repeatOnLifecycle
import io.github.magalz.borealis.library.R
import kotlinx.coroutines.launch

/**
 * Shows the notices of [quitNotice] in [snackbarHostState] while this is in the composition, and
 * only while the screen is started: a notice that comes before waits for it.
 */
@Composable
internal fun ShowQuitNotice(quitNotice: QuitNotice, snackbarHostState: SnackbarHostState) {
    val resources = LocalResources.current
    val lifecycle = LocalLifecycleOwner.current.lifecycle
    DisposableEffect(quitNotice) {
        quitNotice.attach()
        onDispose { quitNotice.detach() }
    }
    LaunchedEffect(quitNotice, lifecycle) {
        lifecycle.repeatOnLifecycle(Lifecycle.State.STARTED) {
            quitNotice.notices.collect {
                // A snackbar waits for its own end; the next notice must not wait with it.
                launch {
                    snackbarHostState.showSnackbar(
                        resources.getString(R.string.apps_message_quit_done)
                    )
                }
            }
        }
    }
}

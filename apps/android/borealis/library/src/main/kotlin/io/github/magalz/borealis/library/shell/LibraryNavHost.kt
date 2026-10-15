package io.github.magalz.borealis.library.shell

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalResources
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavBackStackEntry
import androidx.navigation.NavDestination.Companion.hasRoute
import androidx.navigation.NavHostController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import androidx.navigation.toRoute
import io.github.magalz.borealis.designsystem.adaptive.rememberFormFactor
import io.github.magalz.borealis.library.R
import io.github.magalz.borealis.library.addhost.AddHostRoute
import io.github.magalz.borealis.library.apps.AppsRoute
import io.github.magalz.borealis.library.domain.StreamRequest
import io.github.magalz.borealis.library.hosts.HostsRoute
import io.github.magalz.borealis.library.pairing.PairingRoute
import kotlinx.coroutines.launch

/** The key, in the saved state of the Hosts entry, of the message the next screen leaves for it. */
private const val HOSTS_MESSAGE = "hostsMessage"

private val SNACKBAR_PADDING = 16.dp

/**
 * The Library screens and the way between them (ADR 0052): Hosts leads to Apps, Pairing and Add
 * host, pairing replaces itself with Apps, and the screens that end say so on Hosts through its
 * saved state. [onStartStream] starts a stream and answers false when the host is no longer known,
 * which the shell says on the current screen. [onOpenSettings] opens the stream settings.
 */
@Composable
internal fun LibraryNavHost(
    onStartStream: (StreamRequest) -> Boolean,
    onOpenSettings: () -> Unit,
    modifier: Modifier = Modifier,
    navController: NavHostController = rememberNavController(),
) {
    val resources = LocalResources.current
    val scope = rememberCoroutineScope()
    val snackbarHostState = remember { SnackbarHostState() }
    val destination = navController.currentBackStackEntryAsState().value?.destination
    val startStream = { request: StreamRequest ->
        if (!onStartStream(request)) {
            scope.launch {
                snackbarHostState.showSnackbar(resources.getString(R.string.shell_host_unavailable))
            }
        }
    }
    val rail =
        RailActions(
            onHosts = { navController.popBackStack<Hosts>(inclusive = false) },
            // Add host closes onto Hosts, also when it opens from Apps.
            onAddHost = {
                navController.navigate(AddHost) {
                    popUpTo<Hosts>()
                    launchSingleTop = true
                }
            },
            onSettings = onOpenSettings,
        )

    LibraryScaffold(
        formFactor = rememberFormFactor(),
        showRail = destination?.let { it.hasRoute<Hosts>() || it.hasRoute<Apps>() } == true,
        actions = rail,
        modifier = modifier,
    ) {
        Box {
            NavHost(navController, startDestination = Hosts) {
                composable<Hosts> { entry ->
                    val handle = entry.savedStateHandle
                    val message by
                        handle
                            .getStateFlow<String?>(HOSTS_MESSAGE, null)
                            .collectAsStateWithLifecycle()
                    HostsRoute(
                        onOpenApps = {
                            navController.navigate(Apps(it)) { launchSingleTop = true }
                        },
                        onOpenPairing = { id, name ->
                            navController.navigate(Pairing(id, name)) { launchSingleTop = true }
                        },
                        onOpenAddHost = {
                            navController.navigate(AddHost) { launchSingleTop = true }
                        },
                        onOpenSettings = onOpenSettings,
                        onStartStream = startStream,
                        message = message,
                        onMessageShown = { handle[HOSTS_MESSAGE] = null },
                    )
                }
                composable<AddHost> { entry ->
                    AddHostRoute(
                        onClose = { navController.close(entry) },
                        onAdded = { name ->
                            navController.close(
                                entry,
                                resources.getString(R.string.shell_host_added, name),
                            )
                        },
                    )
                }
                composable<Pairing> { entry ->
                    val route = entry.toRoute<Pairing>()
                    PairingRoute(
                        hostId = route.hostId,
                        hostName = route.hostName,
                        // Back from Apps returns to Hosts, not to a pairing that is done.
                        onOpenApps = { hostId ->
                            navController.navigate(Apps(hostId)) {
                                popUpTo<Pairing> { inclusive = true }
                                launchSingleTop = true
                            }
                        },
                        onClose = { navController.close(entry) },
                    )
                }
                composable<Apps> { entry ->
                    AppsRoute(
                        hostId = entry.toRoute<Apps>().hostId,
                        onClose = { message -> navController.close(entry, message) },
                        onStartStream = startStream,
                    )
                }
            }
            SnackbarHost(
                snackbarHostState,
                Modifier.align(Alignment.BottomCenter)
                    .windowInsetsPadding(WindowInsets.safeDrawing)
                    .padding(SNACKBAR_PADDING),
            )
        }
    }
}

/**
 * Goes back from [entry] to Hosts, leaving [message] for it when there is one. A screen can end
 * more than once (its own Back and an effect in the same frame); only the first one counts.
 */
private fun NavHostController.close(entry: NavBackStackEntry, message: String? = null) {
    if (currentBackStackEntry != entry) return
    if (message != null) previousBackStackEntry?.savedStateHandle?.set(HOSTS_MESSAGE, message)
    popBackStack()
}

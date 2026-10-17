package io.github.magalz.borealis.library.shell

import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.MutableState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalResources
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavBackStackEntry
import androidx.navigation.NavDestination.Companion.hasRoute
import androidx.navigation.NavGraphBuilder
import androidx.navigation.NavHostController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import androidx.navigation.toRoute
import io.github.magalz.borealis.designsystem.adaptive.rememberFormFactor
import io.github.magalz.borealis.library.LibraryLaunchRequest
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

/** The cross-fade between two screens, much shorter than the 700 ms default of the NavHost. */
private const val FADE_MS = 150

/**
 * The Library screens and the way between them (ADR 0052), inside the [LibraryScaffold]: Hosts
 * leads to Apps, Pairing and Add host, pairing replaces itself with Apps, and the screens that end
 * say so on Hosts through its saved state. [onStartStream] starts a stream and answers false when
 * the host is no longer known, which the shell says on the current screen. [onOpenSettings] opens
 * the stream settings. A [request] from outside (a launcher shortcut, the stream error screen) puts
 * its screens on the back stack; [onRequestHandled] says that it was taken, so it is not taken
 * twice.
 */
@Composable
internal fun LibraryNavHost(
    onStartStream: (StreamRequest) -> Boolean,
    onOpenSettings: () -> Unit,
    modifier: Modifier = Modifier,
    request: LibraryLaunchRequest? = null,
    onRequestHandled: () -> Unit = {},
    navController: NavHostController = rememberNavController(),
) {
    val resources = LocalResources.current
    val scope = rememberCoroutineScope()
    val snackbarHostState = remember { SnackbarHostState() }
    val destination = navController.currentBackStackEntryAsState().value?.destination
    // The app a Launch request asks for, until its Apps screen has the list and selects it.
    val pendingLaunch = remember { mutableStateOf<LibraryLaunchRequest.Launch?>(null) }
    LaunchedEffect(request) {
        val incoming = request ?: return@LaunchedEffect
        navController.show(incoming)
        pendingLaunch.value = incoming as? LibraryLaunchRequest.Launch
        onRequestHandled()
    }
    val startStream = { streamRequest: StreamRequest ->
        if (!onStartStream(streamRequest)) {
            scope.launch {
                snackbarHostState.showSnackbar(resources.getString(R.string.shell_host_unavailable))
            }
        }
    }
    val rail =
        RailActions(
            onHosts = { navController.popBackStack<Hosts>(inclusive = false) },
            // Add host closes onto Hosts, also when it opens from Apps. Choosing it while it shows
            // does nothing: it would recreate the screen and lose what was typed.
            onAddHost = {
                if (navController.currentDestination?.hasRoute<AddHost>() != true) {
                    navController.navigate(AddHost) {
                        popUpTo<Hosts>()
                        launchSingleTop = true
                    }
                }
            },
            onSettings = onOpenSettings,
        )

    LibraryScaffold(
        formFactor = rememberFormFactor(),
        selected =
            if (destination?.hasRoute<AddHost>() == true) RailSelection.AddHost
            else RailSelection.Hosts,
        actions = rail,
        modifier = modifier,
    ) {
        Box {
            FadingNavHost(navController) {
                libraryGraph(
                    navController,
                    startStream,
                    onOpenSettings,
                    pendingLaunch,
                )
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

/** The NavHost with a quick cross-fade between screens; the rail is outside it and stays put. */
@Composable
private fun FadingNavHost(navController: NavHostController, graph: NavGraphBuilder.() -> Unit) {
    NavHost(
        navController,
        startDestination = Hosts,
        enterTransition = { fadeIn(tween(FADE_MS)) },
        exitTransition = { fadeOut(tween(FADE_MS)) },
        popEnterTransition = { fadeIn(tween(FADE_MS)) },
        popExitTransition = { fadeOut(tween(FADE_MS)) },
        builder = graph,
    )
}

private fun NavGraphBuilder.libraryGraph(
    navController: NavHostController,
    startStream: (StreamRequest) -> Unit,
    onOpenSettings: () -> Unit,
    pendingLaunch: MutableState<LibraryLaunchRequest.Launch?>,
) {
    composable<Hosts> { entry ->
        val handle = entry.savedStateHandle
        val message by
            handle.getStateFlow<String?>(HOSTS_MESSAGE, null).collectAsStateWithLifecycle()
        HostsRoute(
            onOpenApps = { navController.navigate(Apps(it)) { launchSingleTop = true } },
            onOpenPairing = { id, name ->
                navController.navigate(Pairing(id, name)) { launchSingleTop = true }
            },
            onOpenAddHost = { navController.navigate(AddHost) { launchSingleTop = true } },
            onOpenSettings = onOpenSettings,
            onStartStream = startStream,
            message = message,
            onMessageShown = { handle[HOSTS_MESSAGE] = null },
        )
    }
    composable<AddHost> { entry ->
        val resources = LocalResources.current
        AddHostRoute(
            onClose = { navController.close(entry) },
            onAdded = { name ->
                navController.close(entry, resources.getString(R.string.shell_host_added, name))
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
        val hostId = entry.toRoute<Apps>().hostId
        AppsRoute(
            hostId = hostId,
            onClose = { message -> navController.close(entry, message) },
            onStartStream = startStream,
            launchAppId = pendingLaunch.value?.takeIf { it.hostId == hostId }?.appId,
            onLaunchHandled = { pendingLaunch.value = null },
        )
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

/**
 * Puts the screens of [request] on the back stack: always above Hosts, so Back from them leads
 * there. A Launch shows Apps; the launch itself waits there for the app list. A request for the
 * screen that is on top already leaves it in place: it keeps its ViewModel, which a Launch needs to
 * take its app from the new request.
 */
private fun NavHostController.show(request: LibraryLaunchRequest) {
    if (showsAlready(request)) return
    popBackStack<Hosts>(inclusive = false)
    when (request) {
        LibraryLaunchRequest.Hosts -> Unit
        is LibraryLaunchRequest.Apps -> navigate(Apps(request.hostId)) { launchSingleTop = true }
        is LibraryLaunchRequest.Launch -> navigate(Apps(request.hostId)) { launchSingleTop = true }
        is LibraryLaunchRequest.Pair -> navigate(Pairing(request.hostId)) { launchSingleTop = true }
        LibraryLaunchRequest.Privacy -> Unit
    }
}

private fun NavHostController.showsAlready(request: LibraryLaunchRequest): Boolean {
    val entry = currentBackStackEntry ?: return false
    val destination = entry.destination
    return when (request) {
        LibraryLaunchRequest.Hosts -> false
        is LibraryLaunchRequest.Apps ->
            destination.hasRoute<Apps>() && entry.toRoute<Apps>().hostId == request.hostId
        is LibraryLaunchRequest.Launch ->
            destination.hasRoute<Apps>() && entry.toRoute<Apps>().hostId == request.hostId
        is LibraryLaunchRequest.Pair ->
            destination.hasRoute<Pairing>() && entry.toRoute<Pairing>().hostId == request.hostId
        LibraryLaunchRequest.Privacy -> false
    }
}

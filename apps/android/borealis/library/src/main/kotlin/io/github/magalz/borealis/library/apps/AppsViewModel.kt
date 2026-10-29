package io.github.magalz.borealis.library.apps

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.assisted.Assisted
import dagger.assisted.AssistedFactory
import dagger.assisted.AssistedInject
import dagger.hilt.android.lifecycle.HiltViewModel
import io.github.magalz.borealis.library.domain.AppRepository
import io.github.magalz.borealis.library.domain.AppsSnapshot
import io.github.magalz.borealis.library.domain.HostShortcuts
import io.github.magalz.borealis.library.domain.QuitResult
import io.github.magalz.borealis.library.domain.Reachability
import io.github.magalz.borealis.library.domain.StreamRequest
import io.github.magalz.borealis.library.shell.ResumeChoice
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/** The apps of one host. The screen closes when the host is gone, not paired or offline. */
@Suppress("TooManyFunctions") // One handler per event of the screen.
@HiltViewModel(assistedFactory = AppsViewModel.Factory::class)
class AppsViewModel
@AssistedInject
constructor(
    @Assisted private val hostId: String,
    private val repository: AppRepository,
    private val shortcuts: HostShortcuts,
    private val resumeChoice: ResumeChoice,
) : ViewModel() {
    private val showHidden = MutableStateFlow(false)
    private val actionsAppId = MutableStateFlow<Int?>(null)
    private val detailsAppId = MutableStateFlow<Int?>(null)
    private val effectChannel = Channel<AppsEffect>(Channel.BUFFERED)
    private var closed = false
    private var hostReported = false

    /**
     * The last app list, for a launch request, which looks at every app and not at the visible
     * ones.
     */
    private var latest: AppsSnapshot? = null

    /** Collecting it polls the app list; it stops five seconds after the last collector leaves. */
    val uiState: StateFlow<AppsUiState> =
        combine(
                repository
                    .apps(hostId)
                    .onEach { latest = it }
                    .onEach(::closeIfUnavailable)
                    .onEach(::reportHostOpened)
                    .onEach(::clearGoneActions),
                showHidden,
                actionsAppId,
                detailsAppId,
            ) { snapshot, showHidden, actionsAppId, detailsAppId ->
                snapshot.toUiState(showHidden, actionsAppId, detailsAppId, shortcuts.canPinApps)
            }
            .stateIn(viewModelScope, SharingStarted.WhileSubscribed(STOP_TIMEOUT_MS), AppsUiState())

    val effects: Flow<AppsEffect> = effectChannel.receiveAsFlow()

    /**
     * A launch request: a tap on [appId], also when the user hid the app since, unless the screen
     * is closing because the host is gone, offline or not paired. The caller waits until the apps
     * are loaded. It is the user's choice to resume only after Try again on the stream error
     * screen, which [resumeChoice] says; a shortcut or a TV program is not, so the stream asks.
     */
    fun onLaunchRequested(appId: Int) {
        // Taken first: the request ends the choice whatever comes of it.
        val chosen = resumeChoice.take(hostId, appId)
        if (closed) return
        val snapshot = latest ?: return
        val running = snapshot.host?.runningApp?.id
        val app = snapshot.items(running).firstOrNull { it.id == appId } ?: return
        select(app, running, resumeChosen = chosen)
    }

    /**
     * A tap: start or resume when that is unambiguous, otherwise open the actions. A tap on the
     * running app is the user's choice to resume it.
     */
    fun onAppSelected(appId: Int) {
        val state = uiState.value
        val app = state.items.firstOrNull { it.id == appId } ?: return
        select(app, state.runningAppId, resumeChosen = app.running)
    }

    private fun select(app: AppItemUiState, runningAppId: Int?, resumeChosen: Boolean) {
        if (runningAppId == null || app.running) {
            send(AppsEffect.StartStream(app.toRequest(hostId, resumeChosen)))
        } else {
            actionsAppId.value = app.id
        }
    }

    fun onActions(appId: Int) {
        if (uiState.value.items.any { it.id == appId }) actionsAppId.value = appId
    }

    fun onActionChosen(action: AppAction) {
        val sheet = uiState.value.actions ?: return
        if (action !in sheet.choices) return
        val app = sheet.app
        actionsAppId.value = null
        when (action) {
            AppAction.RESUME ->
                send(AppsEffect.StartStream(app.toRequest(hostId, resumeChosen = true)))
            AppAction.START -> send(AppsEffect.StartStream(app.toRequest(hostId)))
            AppAction.QUIT ->
                viewModelScope.launch {
                    send(AppsEffect.ShowMessage(repository.quit(hostId).toMessage()))
                }
            AppAction.QUIT_AND_START -> viewModelScope.launch { quitThenStart(app) }
            AppAction.DETAILS -> detailsAppId.value = app.id
            AppAction.HIDE -> viewModelScope.launch { repository.setHidden(hostId, app.id, true) }
            AppAction.UNHIDE ->
                viewModelScope.launch { repository.setHidden(hostId, app.id, false) }
            AppAction.ADD_TO_HOME_SCREEN -> pin(app)
        }
    }

    fun onActionsDismissed() {
        actionsAppId.value = null
    }

    fun onDetailsDismissed() {
        detailsAppId.value = null
    }

    fun onToggleShowHidden() {
        showHidden.update { !it }
    }

    private fun pin(app: AppItemUiState) {
        val hostName = uiState.value.hostName ?: return
        // The art may come from the host: the screen does not wait for it.
        viewModelScope.launch {
            if (!shortcuts.pinApp(hostId, hostName, app.id, app.name, app.hdrSupported)) {
                send(AppsEffect.ShowMessage(AppsMessage.PinFailed))
            }
        }
    }

    private suspend fun quitThenStart(app: AppItemUiState) {
        when (val result = repository.quit(hostId)) {
            QuitResult.Quit -> send(AppsEffect.StartStream(app.toRequest(hostId)))
            is QuitResult.Failed -> send(AppsEffect.ShowMessage(result.toMessage()))
        }
    }

    private fun closeIfUnavailable(snapshot: AppsSnapshot) {
        val host = snapshot.host
        val reason =
            when {
                host == null -> AppsMessage.HostUnknown
                host.reachability == Reachability.OFFLINE -> AppsMessage.HostOffline
                // Right after pairing the state is UNKNOWN and not paired until the next poll.
                host.reachability == Reachability.ONLINE && !host.paired ->
                    AppsMessage.HostNotPaired
                else -> return
            }
        if (!closed) {
            closed = true
            send(AppsEffect.Close(reason))
        }
    }

    /** The host is open: its launcher shortcut is published once it is paired and not offline. */
    private fun reportHostOpened(snapshot: AppsSnapshot) {
        val host = snapshot.host ?: return
        val usable = host.paired && host.reachability != Reachability.OFFLINE
        if (closed || hostReported || !usable) return
        hostReported = true
        shortcuts.reportHostOpened(hostId, host.name)
    }

    /** A sheet for an app that is gone must not reopen if the app returns. */
    private fun clearGoneActions(snapshot: AppsSnapshot) {
        val id = actionsAppId.value ?: return
        if (snapshot.apps.none { it.id == id }) actionsAppId.value = null
    }

    private fun send(effect: AppsEffect) {
        effectChannel.trySend(effect)
    }

    @AssistedFactory
    interface Factory {
        fun create(hostId: String): AppsViewModel
    }

    private companion object {
        const val STOP_TIMEOUT_MS = 5_000L
    }
}

/** Every app, hidden ones included, with the running one marked. */
private fun AppsSnapshot.items(runningAppId: Int?) = apps.map {
    AppItemUiState(it.id, it.name, it.id == runningAppId, it.hidden, it.hdrSupported)
}

private fun AppsSnapshot.toUiState(
    showHidden: Boolean,
    actionsAppId: Int?,
    detailsAppId: Int?,
    canPin: Boolean,
): AppsUiState {
    val running = host?.runningApp
    val all = items(running?.id)
    val items =
        all.filter { showHidden || !it.hidden }
            .sortedWith(
                compareByDescending<AppItemUiState> { it.running }.thenBy { it.name.lowercase() }
            )
    return AppsUiState(
        loading = loading,
        hostName = host?.name,
        runningAppId = running?.id,
        runningAppName = running?.name,
        showHidden = showHidden,
        items = items,
        actions =
            all.firstOrNull { it.id == actionsAppId }
                ?.let {
                    AppActionsUiState(
                        it,
                        running?.name,
                        choicesFor(it, running != null, canPin),
                        runningAppIsDesktop = running?.isDesktop == true,
                    )
                },
        details = all.firstOrNull { it.id == detailsAppId },
    )
}

private fun choicesFor(
    app: AppItemUiState,
    somethingRuns: Boolean,
    canPin: Boolean,
): List<AppAction> {
    val hide = if (app.hidden) AppAction.UNHIDE else AppAction.HIDE
    val choices =
        when {
            app.running -> listOf(AppAction.RESUME, AppAction.QUIT, AppAction.DETAILS)
            somethingRuns -> listOf(AppAction.QUIT_AND_START, AppAction.DETAILS, hide)
            else -> listOf(AppAction.START, AppAction.DETAILS, hide)
        }
    return if (canPin) choices + AppAction.ADD_TO_HOME_SCREEN else choices
}

private fun AppItemUiState.toRequest(hostId: String, resumeChosen: Boolean = false) =
    StreamRequest(hostId, id, name, hdrSupported, resumeChosen)

private fun QuitResult.toMessage(): AppsMessage =
    when (this) {
        QuitResult.Quit -> AppsMessage.QuitDone
        is QuitResult.Failed -> AppsMessage.QuitFailed(message)
    }

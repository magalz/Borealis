package io.github.magalz.borealis.library.apps

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.assisted.Assisted
import dagger.assisted.AssistedFactory
import dagger.assisted.AssistedInject
import dagger.hilt.android.lifecycle.HiltViewModel
import io.github.magalz.borealis.library.domain.AppRepository
import io.github.magalz.borealis.library.domain.AppsSnapshot
import io.github.magalz.borealis.library.domain.QuitResult
import io.github.magalz.borealis.library.domain.Reachability
import io.github.magalz.borealis.library.domain.StreamRequest
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
@HiltViewModel(assistedFactory = AppsViewModel.Factory::class)
class AppsViewModel
@AssistedInject
constructor(@Assisted private val hostId: String, private val repository: AppRepository) :
    ViewModel() {
    private val showHidden = MutableStateFlow(false)
    private val actionsAppId = MutableStateFlow<Int?>(null)
    private val detailsAppId = MutableStateFlow<Int?>(null)
    private val effectChannel = Channel<AppsEffect>(Channel.BUFFERED)
    private var closed = false

    /** Collecting it polls the app list; it stops five seconds after the last collector leaves. */
    val uiState: StateFlow<AppsUiState> =
        combine(
                repository.apps(hostId).onEach(::closeIfUnavailable).onEach(::clearGoneActions),
                showHidden,
                actionsAppId,
                detailsAppId,
                AppsSnapshot::toUiState,
            )
            .stateIn(viewModelScope, SharingStarted.WhileSubscribed(STOP_TIMEOUT_MS), AppsUiState())

    val effects: Flow<AppsEffect> = effectChannel.receiveAsFlow()

    /** A tap: start or resume when that is unambiguous, otherwise open the actions. */
    fun onAppSelected(appId: Int) {
        val state = uiState.value
        val app = state.items.firstOrNull { it.id == appId } ?: return
        if (state.runningAppId == null || app.running) {
            send(AppsEffect.StartStream(app.toRequest(hostId)))
        } else {
            actionsAppId.value = appId
        }
    }

    fun onActions(appId: Int) {
        if (uiState.value.items.any { it.id == appId }) actionsAppId.value = appId
    }

    fun onActionChosen(action: AppAction) {
        val app = uiState.value.actions?.app ?: return
        actionsAppId.value = null
        when (action) {
            AppAction.RESUME,
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
                !host.paired -> AppsMessage.HostNotPaired
                host.reachability == Reachability.OFFLINE -> AppsMessage.HostOffline
                else -> return
            }
        if (!closed) {
            closed = true
            send(AppsEffect.Close(reason))
        }
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

private fun AppsSnapshot.toUiState(
    showHidden: Boolean,
    actionsAppId: Int?,
    detailsAppId: Int?,
): AppsUiState {
    val running = host?.runningApp
    val all = apps.map {
        AppItemUiState(it.id, it.name, it.id == running?.id, it.hidden, it.hdrSupported)
    }
    val items =
        all.filter { showHidden || !it.hidden }
            .sortedWith(
                compareByDescending<AppItemUiState> { it.running }.thenBy { it.name.lowercase() }
            )
    return AppsUiState(
        loading = loading,
        hostName = host?.name,
        runningAppId = running?.id,
        showHidden = showHidden,
        items = items,
        actions =
            all.firstOrNull { it.id == actionsAppId }
                ?.let {
                    AppActionsUiState(it, running?.name, choicesFor(it, running != null))
                },
        details = all.firstOrNull { it.id == detailsAppId },
    )
}

private fun choicesFor(app: AppItemUiState, somethingRuns: Boolean): List<AppAction> {
    val hide = if (app.hidden) AppAction.UNHIDE else AppAction.HIDE
    return when {
        app.running -> listOf(AppAction.RESUME, AppAction.QUIT, AppAction.DETAILS)
        somethingRuns -> listOf(AppAction.QUIT_AND_START, AppAction.DETAILS, hide)
        else -> listOf(AppAction.START, AppAction.DETAILS, hide)
    }
}

private fun AppItemUiState.toRequest(hostId: String) = StreamRequest(hostId, id, name, hdrSupported)

private fun QuitResult.toMessage(): AppsMessage =
    when (this) {
        QuitResult.Quit -> AppsMessage.QuitDone
        is QuitResult.Failed -> AppsMessage.QuitFailed(message)
    }

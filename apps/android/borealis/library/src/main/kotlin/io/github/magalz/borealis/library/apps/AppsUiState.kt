package io.github.magalz.borealis.library.apps

import io.github.magalz.borealis.library.domain.StreamRequest

data class AppItemUiState(
    val id: Int,
    val name: String,
    val running: Boolean,
    val hidden: Boolean,
    val hdrSupported: Boolean,
)

/**
 * One action of the app actions sheet. The screen maps each to its label and one-line consequence
 * (ADR 0052, D6).
 */
enum class AppAction {
    /** Rejoin the app that is running. */
    RESUME,

    /** Start this app; nothing is running. */
    START,

    /** Quit the app that is running. */
    QUIT,

    /** Quit the app that is running, then start this one. */
    QUIT_AND_START,
    DETAILS,
    HIDE,
    UNHIDE,
}

data class AppActionsUiState(
    val app: AppItemUiState,
    /** The name of the app that is running, for the consequence text; null when nothing runs. */
    val runningAppName: String?,
    val choices: List<AppAction>,
)

data class AppsUiState(
    val loading: Boolean = true,
    val hostName: String? = null,
    val runningAppId: Int? = null,
    /** The name of the app that is running, also when it is hidden and not in [items]. */
    val runningAppName: String? = null,
    val showHidden: Boolean = false,
    /** The visible apps: the running app first, then by name. */
    val items: List<AppItemUiState> = emptyList(),
    /** The open app actions sheet, or null. */
    val actions: AppActionsUiState? = null,
    /** The app whose details dialog is open, or null. */
    val details: AppItemUiState? = null,
)

sealed interface AppsMessage {
    /** The host is no longer known. */
    data object HostUnknown : AppsMessage

    data object HostNotPaired : AppsMessage

    data object HostOffline : AppsMessage

    data object QuitDone : AppsMessage

    /** The quit failed; [message] is the localized text, or null for the generic text. */
    data class QuitFailed(val message: String?) : AppsMessage
}

sealed interface AppsEffect {
    /** The screen closes and shows [message]. */
    data class Close(val message: AppsMessage) : AppsEffect

    /** The screen hands [request] and its Activity to the stream launcher. */
    data class StartStream(val request: StreamRequest) : AppsEffect

    data class ShowMessage(val message: AppsMessage) : AppsEffect
}

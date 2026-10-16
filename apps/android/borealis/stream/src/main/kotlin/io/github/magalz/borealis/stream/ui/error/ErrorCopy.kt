package io.github.magalz.borealis.stream.ui.error

import androidx.annotation.StringRes
import io.github.magalz.borealis.host.failure.FailureKind
import io.github.magalz.borealis.stream.R

/** Which state action a failure offers besides Try again. */
internal enum class StateAction {
    WAKE_HOST,
    PAIR_AGAIN,
}

/**
 * The words of one failure kind. The title takes the host name, or the app name for
 * [FailureKind.GENERIC]; the state action is what the user can do about this kind, if anything.
 */
internal class ErrorCopy(
    @StringRes val title: Int,
    @StringRes val body: Int,
    val action: StateAction?,
)

internal fun errorCopy(kind: FailureKind): ErrorCopy =
    when (kind) {
        FailureKind.HOST_BUSY ->
            ErrorCopy(R.string.stream_error_busy_title, R.string.stream_error_busy_body, null)
        FailureKind.HOST_OFFLINE ->
            ErrorCopy(
                R.string.stream_error_offline_title,
                R.string.stream_error_offline_body,
                StateAction.WAKE_HOST,
            )
        FailureKind.CERTIFICATE_CHANGED ->
            ErrorCopy(
                R.string.stream_error_certificate_title,
                R.string.stream_error_certificate_body,
                StateAction.PAIR_AGAIN,
            )
        FailureKind.GENERIC ->
            ErrorCopy(R.string.stream_error_generic_title, R.string.stream_error_generic_body, null)
    }

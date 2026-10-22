package io.github.magalz.borealis.library.pairing

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import io.github.magalz.borealis.designsystem.components.AnimatedCheck
import io.github.magalz.borealis.designsystem.components.AuroraLoader
import io.github.magalz.borealis.designsystem.components.BackdropInk
import io.github.magalz.borealis.designsystem.components.BackdropText
import io.github.magalz.borealis.designsystem.components.BorealisBackButton
import io.github.magalz.borealis.designsystem.components.BorealisButton
import io.github.magalz.borealis.designsystem.components.BorealisButtonStyle
import io.github.magalz.borealis.designsystem.components.PinDisplay
import io.github.magalz.borealis.designsystem.focus.initialFocus
import io.github.magalz.borealis.designsystem.theme.BorealisTheme
import io.github.magalz.borealis.designsystem.theme.FormFactor
import io.github.magalz.borealis.designsystem.theme.LocalBorealisColors
import io.github.magalz.borealis.designsystem.theme.LocalBorealisSpacing
import io.github.magalz.borealis.designsystem.theme.LocalBorealisType
import io.github.magalz.borealis.library.R

private val SECTION_GAP = 16.dp
private val WIDE_COLUMN_GAP = 40.dp
private val STEP_GAP = 14.dp
private val STEP_NUMBER_SIZE = 24.dp
private val NOTE_PADDING = 12.dp
private val BUTTON_GAP = 12.dp
private val CHECK_SIZE = 64.dp
private val LOADER_SIZE = 64.dp

/** The note about a restart wraps at this width, so it does not widen the PIN column. */
private val NOTE_MAX_WIDTH = 280.dp

/**
 * The pairing screen (UC-001). The PIN is entered on the host, in the LuminalShine Web UI, so the
 * screen shows it large, says where to enter it and offers Cancel, and Get a new PIN once it
 * expired. [hostName] is null when the caller does not know it. Back cancels, except once paired. A
 * phone has the PIN above the steps; a TV or handheld puts the steps beside it. Provides its own
 * theme for [formFactor].
 */
@Composable
fun PairingScreen(
    state: PairingUiState,
    formFactor: FormFactor,
    onGetNewPin: () -> Unit,
    onCancel: () -> Unit,
    modifier: Modifier = Modifier,
    hostName: String? = null,
) {
    BorealisTheme(formFactor) {
        BackHandler(enabled = state !is PairingUiState.Paired, onBack = onCancel)
        val gutter = LocalBorealisSpacing.current.gutter
        Box(modifier.fillMaxSize().windowInsetsPadding(WindowInsets.safeDrawing)) {
            Column(Modifier.fillMaxSize().padding(vertical = SECTION_GAP)) {
                Header(
                    title =
                        if (hostName != null) {
                            stringResource(R.string.pairing_title, hostName)
                        } else {
                            stringResource(R.string.pairing_title_unknown_host)
                        },
                    showBack = formFactor == FormFactor.Phone && state !is PairingUiState.Paired,
                    onBack = onCancel,
                    modifier = Modifier.padding(horizontal = gutter),
                )
                Box(
                    Modifier.weight(1f)
                        .fillMaxWidth()
                        .verticalScroll(rememberScrollState())
                        .padding(horizontal = gutter)
                        .padding(top = SECTION_GAP)
                ) {
                    Content(state, formFactor != FormFactor.Phone, onGetNewPin, onCancel)
                }
            }
            // Paired has nothing to scroll: it is in the middle of the screen, not of what is left
            // under the title.
            if (state is PairingUiState.Paired) Paired(Modifier.align(Alignment.Center))
        }
    }
}

@Composable
private fun Header(title: String, showBack: Boolean, onBack: () -> Unit, modifier: Modifier) {
    Row(modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        if (showBack) BorealisBackButton(onBack, stringResource(R.string.pairing_back))
        BackdropText(
            title,
            Modifier.semantics { heading() },
            style = MaterialTheme.typography.titleLarge,
        )
    }
}

@Composable
private fun Content(
    state: PairingUiState,
    wide: Boolean,
    onGetNewPin: () -> Unit,
    onCancel: () -> Unit,
) {
    when (state) {
        PairingUiState.Preparing -> {
            val description = stringResource(R.string.pairing_preparing)
            AuroraLoader(Modifier.semantics { contentDescription = description }, LOADER_SIZE)
        }
        PairingUiState.HostUnknown ->
            Outcome(stringResource(R.string.pairing_host_unknown)) {
                FirstButton(
                    stringResource(R.string.pairing_close),
                    onCancel,
                    BorealisButtonStyle.Secondary,
                )
            }
        is PairingUiState.Waiting -> Waiting(state, wide, onCancel)
        PairingUiState.Paired -> Unit
        PairingUiState.Expired ->
            Outcome(stringResource(R.string.pairing_expired)) {
                Actions(stringResource(R.string.pairing_get_new_pin), onGetNewPin, onCancel)
            }
        is PairingUiState.Failed ->
            Outcome(
                when (state.reason) {
                    PairingFailure.PIN_WRONG -> stringResource(R.string.pairing_failed_pin_wrong)
                    PairingFailure.ALREADY_IN_PROGRESS ->
                        stringResource(R.string.pairing_failed_in_progress)
                    PairingFailure.HOST_OFFLINE -> stringResource(R.string.pairing_failed_offline)
                    PairingFailure.OTHER ->
                        state.message ?: stringResource(R.string.pairing_failed_other)
                }
            ) {
                Actions(stringResource(R.string.pairing_try_again), onGetNewPin, onCancel)
            }
    }
}

/**
 * The PIN with its note and validity, and the steps with Cancel. The PIN and the line under it are
 * centred: on the screen of a phone, where they sit above the steps, and in a column of their own
 * width on a wide screen, where the steps take the rest beside them.
 */
@Composable
private fun Waiting(state: PairingUiState.Waiting, wide: Boolean, onCancel: () -> Unit) {
    val pin: @Composable (Modifier) -> Unit = { modifier ->
        Column(
            modifier,
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(SECTION_GAP),
        ) {
            if (state.restarted) {
                Note(
                    stringResource(R.string.pairing_restarted),
                    Modifier.widthIn(max = NOTE_MAX_WIDTH),
                )
            }
            PinDisplay(state.pin)
            BackdropText(
                stringResource(R.string.pairing_pin_valid),
                style = MaterialTheme.typography.bodyMedium,
                ink = BackdropInk.Ink3,
                textAlign = TextAlign.Center,
            )
        }
    }
    val steps: @Composable ColumnScope.() -> Unit = {
        Steps(state)
        FirstButton(
            stringResource(R.string.pairing_cancel),
            onCancel,
            BorealisButtonStyle.Secondary,
        )
    }
    if (wide) {
        Row(horizontalArrangement = Arrangement.spacedBy(WIDE_COLUMN_GAP)) {
            pin(Modifier.width(IntrinsicSize.Max))
            Column(
                Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(SECTION_GAP),
                content = steps,
            )
        }
    } else {
        Column(verticalArrangement = Arrangement.spacedBy(SECTION_GAP)) {
            pin(Modifier.fillMaxWidth())
            steps()
        }
    }
}

@Composable
private fun Steps(state: PairingUiState.Waiting) {
    val steps =
        listOf(
            if (state.webUiUrl != null) {
                stringResource(R.string.pairing_step_web_ui_at) to state.webUiUrl
            } else {
                stringResource(R.string.pairing_step_web_ui_on_pc) to null
            },
            stringResource(R.string.pairing_step_clients) to null,
            stringResource(R.string.pairing_step_pin, state.deviceName) to null,
            stringResource(R.string.pairing_step_name_warning) to null,
        )
    Column(verticalArrangement = Arrangement.spacedBy(STEP_GAP)) {
        steps.forEachIndexed { index, (text, address) -> Step(index + 1, text, address) }
    }
}

/**
 * A numbered step, with the address it refers to under the text. A screen reader reads it as one
 * thing, the number, the text and the address, and not as three. The number is as large as the text
 * that it holds, so a large text size does not cut it.
 */
@Composable
private fun Step(number: Int, text: String, address: String?) {
    val colors = LocalBorealisColors.current
    val numberSize = STEP_NUMBER_SIZE * maxOf(1f, LocalDensity.current.fontScale)
    Row(
        Modifier.semantics(mergeDescendants = true) {},
        horizontalArrangement = Arrangement.spacedBy(STEP_GAP),
    ) {
        Box(
            Modifier.size(numberSize).border(1.dp, colors.lineStrong, CircleShape),
            contentAlignment = Alignment.Center,
        ) {
            BackdropText(
                number.toString(),
                style = LocalBorealisType.current.kicker,
                ink = BackdropInk.Ink3,
            )
        }
        Column(Modifier.weight(1f)) {
            BackdropText(text, style = MaterialTheme.typography.bodyMedium, ink = BackdropInk.Ink2)
            if (address != null) {
                BackdropText(
                    address,
                    style = LocalBorealisType.current.codes(MaterialTheme.typography.bodySmall),
                    ink = BackdropInk.Ink3,
                )
            }
        }
    }
}

@Composable
private fun Note(text: String, modifier: Modifier = Modifier) {
    val colors = LocalBorealisColors.current
    val shape = MaterialTheme.shapes.medium
    BackdropText(
        text,
        modifier
            .background(colors.accentTint, shape)
            .border(1.dp, colors.accentLine, shape)
            .padding(NOTE_PADDING),
        style = MaterialTheme.typography.bodyMedium,
    )
}

/** The check draws itself above Paired once, and the pair stays in the middle of the screen. */
@Composable
private fun Paired(modifier: Modifier) {
    Column(
        modifier.testTag("paired").semantics { liveRegion = LiveRegionMode.Polite },
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(SECTION_GAP),
    ) {
        AnimatedCheck(Modifier.size(CHECK_SIZE).testTag("paired-check"))
        BackdropText(
            stringResource(R.string.pairing_paired),
            style = MaterialTheme.typography.titleLarge,
        )
    }
}

/** A message with its buttons: the end of a pairing attempt. */
@Composable
private fun Outcome(message: String, buttons: @Composable () -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(SECTION_GAP)) {
        // What went wrong is announced when it appears, for focus stays where it was.
        BackdropText(
            message,
            Modifier.semantics { liveRegion = LiveRegionMode.Polite },
            style = MaterialTheme.typography.bodyLarge,
        )
        buttons()
    }
}

/** The main action, with focus on a TV or handheld, then Cancel. */
@Composable
private fun Actions(primaryLabel: String, onPrimary: () -> Unit, onCancel: () -> Unit) {
    Row(horizontalArrangement = Arrangement.spacedBy(BUTTON_GAP)) {
        FirstButton(primaryLabel, onPrimary, BorealisButtonStyle.Primary)
        BorealisButton(
            stringResource(R.string.pairing_cancel),
            onCancel,
            style = BorealisButtonStyle.Secondary,
        )
    }
}

/** The button that has focus when a TV or handheld shows the screen. */
@Composable
private fun FirstButton(label: String, onClick: () -> Unit, style: BorealisButtonStyle) {
    BorealisButton(
        label,
        onClick,
        modifier = Modifier.initialFocus(remember { FocusRequester() }),
        style = style,
    )
}

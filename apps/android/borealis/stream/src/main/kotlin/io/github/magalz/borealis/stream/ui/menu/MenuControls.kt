package io.github.magalz.borealis.stream.ui.menu

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import io.github.magalz.borealis.designsystem.components.BorealisIcons
import io.github.magalz.borealis.designsystem.components.Chip
import io.github.magalz.borealis.designsystem.components.MenuRow
import io.github.magalz.borealis.designsystem.components.SegmentedControl
import io.github.magalz.borealis.designsystem.components.Text
import io.github.magalz.borealis.designsystem.theme.BorealisDimens
import io.github.magalz.borealis.designsystem.theme.FormFactor
import io.github.magalz.borealis.designsystem.theme.LocalBorealisColors
import io.github.magalz.borealis.designsystem.theme.LocalBorealisType
import io.github.magalz.borealis.designsystem.theme.LocalFormFactor
import io.github.magalz.borealis.stream.OverlayMode
import io.github.magalz.borealis.stream.PerfSnapshot
import io.github.magalz.borealis.stream.PointerSpeed
import io.github.magalz.borealis.stream.R
import io.github.magalz.borealis.stream.SessionInfo
import io.github.magalz.borealis.stream.StreamIntents
import io.github.magalz.borealis.stream.StreamPhase
import io.github.magalz.borealis.stream.TouchMode
import io.github.magalz.borealis.stream.keys.Shortcuts
import io.github.magalz.borealis.stream.ui.overlay.rememberPerfFormat

private val CHIP_GAP_HORIZONTAL = 8.dp
private val INFO_PADDING_HORIZONTAL = 16.dp
private val INFO_GAP = 12.dp

/** Send keys: a row that opens a wrap of chips; a chip sends its chord and the menu stays open. */
@OptIn(ExperimentalLayoutApi::class)
@Composable
internal fun SendKeysRow(intents: StreamIntents) {
    var open by remember { mutableStateOf(false) }
    MenuRow(
        stringResource(R.string.stream_menu_send_keys),
        onClick = { open = !open },
        trailing = { ExpandMark(open) },
    )
    if (open) {
        // Chips are focus stops: the padding and the gap of a list keep their outlines whole.
        FlowRow(
            Modifier.fillMaxWidth().padding(horizontal = BorealisDimens.listPadding),
            horizontalArrangement = Arrangement.spacedBy(CHIP_GAP_HORIZONTAL),
            verticalArrangement = Arrangement.spacedBy(BorealisDimens.listGap),
        ) {
            for (key in Shortcuts.sendKeys) {
                Chip(stringResource(key.label), onClick = { intents.send(key.chord) })
            }
        }
    }
}

/**
 * How the pointer is driven: Touch on a phone, Gamepad mouse on a TV or handheld, and nothing when
 * the session offers no such choice (the value is null). While the gamepad mouse is on, a second
 * row sets its speed for this session.
 */
@Composable
internal fun PointerRow(phase: StreamPhase.Streaming, intents: StreamIntents) {
    val phone = LocalFormFactor.current == FormFactor.Phone
    val touchMode = phase.touchMode
    val gamepadMouse = phase.gamepadMouse
    val touchLabels = TouchMode.entries.associateWith { stringResource(touchLabel(it)) }
    val on = stringResource(R.string.stream_menu_on)
    val off = stringResource(R.string.stream_menu_off)
    if (phone && touchMode != null) {
        OptionRow(
            stringResource(R.string.stream_menu_touch),
            options = TouchMode.entries,
            selected = touchMode,
            label = touchLabels::getValue,
            onSelect = intents::setTouchMode,
        )
    } else if (!phone && gamepadMouse != null) {
        OptionRow(
            stringResource(R.string.stream_menu_gamepad_mouse),
            options = listOf(true, false),
            selected = gamepadMouse,
            label = { if (it) on else off },
            onSelect = intents::setGamepadMouse,
        )
        if (gamepadMouse) {
            val speedLabels = PointerSpeed.entries.associateWith { stringResource(speedLabel(it)) }
            OptionRow(
                stringResource(R.string.stream_menu_pointer_speed),
                options = PointerSpeed.entries,
                selected = phase.pointerSpeed,
                label = speedLabels::getValue,
                onSelect = intents::setPointerSpeed,
            )
        }
    }
}

/**
 * A row with a segmented control for [options]. OK, A or Enter on the row, or a tap on it, selects
 * the next option and wraps around from the last to the first, and the focus stays on the row. The
 * segments are not focus stops, so the keys never go into them; a tap on a segment selects it.
 */
@Composable
private fun <T> OptionRow(
    title: String,
    options: List<T>,
    selected: T,
    label: (T) -> String,
    onSelect: (T) -> Unit,
) {
    MenuRow(
        title,
        onClick = { onSelect(options[(options.indexOf(selected) + 1) % options.size]) },
        value = label(selected),
        onClickLabel = stringResource(R.string.stream_menu_change_option, title),
        trailing = {
            SegmentedControl(
                options = options,
                selected = selected,
                label = label,
                onSelect = onSelect,
                groupLabel = title,
                focusable = false,
            )
        },
    )
}

private fun speedLabel(speed: PointerSpeed) =
    when (speed) {
        PointerSpeed.SLOW -> R.string.stream_menu_pointer_speed_slow
        PointerSpeed.NORMAL -> R.string.stream_menu_pointer_speed_normal
        PointerSpeed.FAST -> R.string.stream_menu_pointer_speed_fast
    }

private fun touchLabel(mode: TouchMode) =
    when (mode) {
        TouchMode.TRACKPAD -> R.string.stream_menu_touch_trackpad
        TouchMode.DIRECT -> R.string.stream_menu_touch_direct
        TouchMode.OFF -> R.string.stream_menu_off
    }

/** The performance overlay: Off, Compact or Detailed. */
@Composable
internal fun OverlayRow(current: OverlayMode, intents: StreamIntents) {
    val labels = OverlayMode.entries.associateWith { stringResource(overlayLabel(it)) }
    OptionRow(
        stringResource(R.string.stream_menu_overlay),
        options = OverlayMode.entries,
        selected = current,
        label = labels::getValue,
        onSelect = intents::setOverlay,
    )
}

private fun overlayLabel(mode: OverlayMode) =
    when (mode) {
        OverlayMode.OFF -> R.string.stream_menu_off
        OverlayMode.COMPACT -> R.string.stream_menu_overlay_compact
        OverlayMode.DETAILED -> R.string.stream_menu_overlay_detailed
    }

/** Session info: a row that opens the figures of the negotiated stream. */
@Composable
internal fun SessionInfoRow(info: SessionInfo, perf: PerfSnapshot?) {
    var open by remember { mutableStateOf(false) }
    MenuRow(
        stringResource(R.string.stream_menu_session_info),
        onClick = { open = !open },
        trailing = { ExpandMark(open) },
    )
    if (open) {
        val format = rememberPerfFormat()
        val rows =
            listOfNotNull(
                R.string.stream_menu_info_video to format.video(info),
                R.string.stream_menu_info_codec to format.codec(info),
                info.decoder?.let { R.string.stream_menu_info_decoder to it },
                R.string.stream_menu_info_host to format.hostProcessing(perf),
                R.string.stream_menu_info_network to format.network(perf),
                R.string.stream_menu_info_bitrate to format.bitrate(perf, info),
            )
        for ((label, value) in rows) InfoLine(stringResource(label), value)
    }
}

@Composable
private fun InfoLine(label: String, value: String) {
    val colors = LocalBorealisColors.current
    Row(
        Modifier.fillMaxWidth().padding(horizontal = INFO_PADDING_HORIZONTAL),
        horizontalArrangement = Arrangement.spacedBy(INFO_GAP),
    ) {
        Text(label, color = colors.ink4, style = MaterialTheme.typography.bodySmall)
        Text(
            value,
            Modifier.weight(1f),
            color = colors.ink2,
            textAlign = TextAlign.End,
            style = LocalBorealisType.current.codes(MaterialTheme.typography.bodySmall),
        )
    }
}

private val EXPAND_MARK_SIZE = 20.dp

/**
 * The mark of a row that expands: a chevron down while it is [open], to the right while it is not.
 */
@Composable
private fun ExpandMark(open: Boolean) {
    Icon(
        if (open) BorealisIcons.ChevronDown else BorealisIcons.ChevronRight,
        contentDescription = null,
        modifier = Modifier.size(EXPAND_MARK_SIZE),
        tint = LocalBorealisColors.current.ink3,
    )
}

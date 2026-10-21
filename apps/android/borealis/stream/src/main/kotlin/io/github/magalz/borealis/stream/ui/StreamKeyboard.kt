package io.github.magalz.borealis.stream.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.focusGroup
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusProperties
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.paneTitle
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import io.github.magalz.borealis.designsystem.components.Chip
import io.github.magalz.borealis.designsystem.components.KeyboardKey
import io.github.magalz.borealis.designsystem.focus.InitialFocusState
import io.github.magalz.borealis.designsystem.focus.initialFocus
import io.github.magalz.borealis.designsystem.focus.rememberInitialFocusState
import io.github.magalz.borealis.designsystem.theme.BorealisDimens
import io.github.magalz.borealis.designsystem.theme.LocalBorealisColors
import io.github.magalz.borealis.stream.R
import io.github.magalz.borealis.stream.StreamIntents
import io.github.magalz.borealis.stream.keys.KeyChord
import io.github.magalz.borealis.stream.keys.KeyModifier
import io.github.magalz.borealis.stream.keys.KeyModifier.SHIFT
import io.github.magalz.borealis.stream.keys.NamedChord
import io.github.magalz.borealis.stream.keys.Shortcuts
import io.github.magalz.borealis.stream.ui.common.onBackKey
import io.github.magalz.borealis.stream.ui.common.onGamepadB
import io.github.magalz.borealis.stream.ui.keyboard.CharKey
import io.github.magalz.borealis.stream.ui.keyboard.FnKey
import io.github.magalz.borealis.stream.ui.keyboard.KeySpec
import io.github.magalz.borealis.stream.ui.keyboard.KeyboardLatch
import io.github.magalz.borealis.stream.ui.keyboard.KeyboardLayout
import io.github.magalz.borealis.stream.ui.keyboard.ModifierKey

private val PANEL_PADDING_HORIZONTAL = 10.dp
private val PANEL_PADDING_TOP = 8.dp
private val PANEL_PADDING_BOTTOM = 10.dp
private val KEY_GAP = 5.dp
private val CHIP_GAP = 6.dp

// Room for the focus outline of a chip inside a row that scrolls, which clips what it draws: the
// outline reaches 7dp when it is the thicker one, and the padding of a list is 8dp.
private val CHIP_ROW_PADDING = BorealisDimens.listPadding

private const val VK_BACKSPACE = 0x08

/**
 * The Borealis keyboard: a shortcut row, three letter rows and a bottom row, with Ctrl, Alt, Win
 * and Shift that latch until the next key. It sits at the bottom of its parent and takes only the
 * room it needs, so a [docked] keyboard leaves the stream visible above it. Keys are US virtual
 * keys; the System keyboard chip hands text in other languages to the Android keyboard.
 *
 * Not [docked] (a TV, a handheld, or a phone with a pad), the focus starts on Q and the D-pad moves
 * between the keys (Left and Right wrap around at the ends of a row): A types through the
 * platform's D-pad centre fallback, B closes and Y is Backspace. Docked, the panel takes no focus
 * and handles no physical key, so a hardware keyboard keeps typing into the PC.
 *
 * Nothing typed is logged: this code has no log calls.
 */
@Composable
fun StreamKeyboard(docked: Boolean, intents: StreamIntents) {
    var latch by remember { mutableStateOf(KeyboardLatch()) }
    var functionLayer by remember { mutableStateOf(false) }
    val initialFocus = rememberInitialFocusState()
    val firstKey = remember { FocusRequester() }

    fun type(vk: Int) {
        val typed = latch.type(vk)
        latch = typed.latch
        intents.send(typed.chord)
    }

    fun sendShortcut(chord: KeyChord) {
        latch = latch.cleared()
        intents.send(chord)
    }

    val colors = LocalBorealisColors.current
    val title = stringResource(R.string.stream_menu_keyboard)
    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.BottomCenter) {
        Column(
            Modifier.testTag("keyboard")
                .semantics { paneTitle = title }
                .fillMaxWidth()
                // A tap between two keys must not reach the stream below.
                .pointerInput(Unit) { detectTapGestures {} }
                .gamepadKeys(onClose = intents::closePanel, onBackspace = { type(VK_BACKSPACE) })
                .background(colors.surface)
                .drawBehind { drawRect(colors.lineStrong, size = Size(size.width, 1.dp.toPx())) }
                .padding(
                    start = PANEL_PADDING_HORIZONTAL,
                    top = PANEL_PADDING_TOP,
                    end = PANEL_PADDING_HORIZONTAL,
                    bottom = PANEL_PADDING_BOTTOM,
                )
                .focusGroup(),
            verticalArrangement = Arrangement.spacedBy(KEY_GAP),
        ) {
            ShortcutRow(
                shortcuts = if (docked) Shortcuts.keyboardRowPhone else Shortcuts.keyboardRow,
                onShortcut = ::sendShortcut,
                onClose = intents::closePanel,
                onSystemKeyboard = intents::openSystemKeyboard,
            )
            val rows = if (functionLayer) KeyboardLayout.functionRows else KeyboardLayout.letterRows
            for ((index, row) in (rows + listOf(KeyboardLayout.bottomRow)).withIndex()) {
                KeyRow(
                    keys = row,
                    actions =
                        KeyActions(
                            latch = latch,
                            functionLayer = functionLayer,
                            onType = ::type,
                            onToggle = { latch = latch.toggle(it) },
                            onFunctionLayer = { functionLayer = !functionLayer },
                        ),
                    firstKeyFocus =
                        if (!docked && !functionLayer && index == 0) {
                            FirstKeyFocus(firstKey, initialFocus)
                        } else {
                            null
                        },
                )
            }
        }
    }
}

/**
 * B closes by the shared gamepad B rule, Back closes at once (Compose would take it for a focus
 * exit from a key) and Y is Backspace; the events of all three are consumed.
 */
private fun Modifier.gamepadKeys(onClose: () -> Unit, onBackspace: () -> Unit): Modifier =
    onGamepadB(onClose).onBackKey(onClose).onKeyEvent { event ->
        // A held Y repeats, like a held Backspace.
        val isY = event.key == Key.ButtonY
        if (isY && event.type == KeyEventType.KeyDown) onBackspace()
        isY
    }

@Composable
private fun ShortcutRow(
    shortcuts: List<NamedChord>,
    onShortcut: (KeyChord) -> Unit,
    onClose: () -> Unit,
    onSystemKeyboard: () -> Unit,
) {
    Row(
        Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(CHIP_GAP, Alignment.CenterHorizontally),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Row(
            Modifier.weight(1f, fill = false)
                .horizontalScroll(rememberScrollState())
                .padding(CHIP_ROW_PADDING),
            horizontalArrangement = Arrangement.spacedBy(CHIP_GAP),
        ) {
            for (shortcut in shortcuts) {
                val label = stringResource(shortcut.label)
                Chip(
                    label,
                    Modifier.testTag("chip:$label"),
                    onClick = { onShortcut(shortcut.chord) },
                )
            }
        }
        Row(
            Modifier.padding(CHIP_ROW_PADDING),
            horizontalArrangement = Arrangement.spacedBy(CHIP_GAP),
        ) {
            val system = stringResource(R.string.stream_keyboard_system)
            val close = stringResource(R.string.stream_keyboard_close)
            Chip(system, Modifier.testTag("chip:$system"), onClick = onSystemKeyboard)
            Chip(close, Modifier.testTag("chip:$close"), onClick = onClose)
        }
    }
}

/** What the keys of a row show and do. */
private class KeyActions(
    val latch: KeyboardLatch,
    val functionLayer: Boolean,
    val onType: (Int) -> Unit,
    val onToggle: (KeyModifier) -> Unit,
    val onFunctionLayer: () -> Unit,
)

/** The first key of the row gives [requester] the focus the keyboard starts with. */
private class FirstKeyFocus(val requester: FocusRequester, val state: InitialFocusState)

/**
 * A row of keys. The D-pad wraps at the ends of the row, as on any on-screen keyboard: forward from
 * the last key it reaches the first key, and back from the first key it reaches the last.
 */
@Composable
private fun KeyRow(keys: List<KeySpec>, actions: KeyActions, firstKeyFocus: FirstKeyFocus?) {
    val rowStart = remember { FocusRequester() }
    val rowEnd = remember { FocusRequester() }
    // The keys of a row are as high as the highest: a caption that wraps makes its key grow.
    Row(
        Modifier.fillMaxWidth().height(IntrinsicSize.Min),
        horizontalArrangement = Arrangement.spacedBy(KEY_GAP, Alignment.CenterHorizontally),
    ) {
        for ((index, key) in keys.withIndex()) {
            val name = key.label.name()
            val caption = key.label.onCap(key.label.resolve(), SHIFT in actions.latch.latched)
            var modifier =
                Modifier.weight(key.style.weight, fill = false).fillMaxHeight().testTag("key:$name")
            // A symbol on the cap is read out as the name of the key.
            if (!name.equals(caption, ignoreCase = true))
                modifier = modifier.semantics { contentDescription = name }
            if (index == 0) {
                modifier = modifier.focusRequester(rowStart).focusProperties { start = rowEnd }
            }
            if (index == keys.lastIndex) {
                modifier = modifier.focusRequester(rowEnd).focusProperties { end = rowStart }
            }
            if (index == 0 && firstKeyFocus != null) {
                modifier = modifier.initialFocus(firstKeyFocus.requester, firstKeyFocus.state)
            }
            when (key) {
                is CharKey -> KeyboardKey(caption, { actions.onType(key.vk) }, modifier, key.style)
                is ModifierKey ->
                    KeyboardKey(
                        caption,
                        { actions.onToggle(key.modifier) },
                        modifier,
                        key.style,
                        latched = key.modifier in actions.latch.latched,
                    )
                FnKey ->
                    KeyboardKey(
                        caption,
                        actions.onFunctionLayer,
                        modifier,
                        key.style,
                        latched = actions.functionLayer,
                    )
            }
        }
    }
}

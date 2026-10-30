package io.github.magalz.borealis.stream.ui

import androidx.activity.OnBackPressedCallback
import androidx.activity.findViewTreeOnBackPressedDispatcherOwner
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.focusGroup
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusProperties
import androidx.compose.ui.input.InputMode
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalInputModeManager
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.paneTitle
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import io.github.magalz.borealis.designsystem.components.BorealisButton
import io.github.magalz.borealis.designsystem.components.BorealisButtonStyle
import io.github.magalz.borealis.designsystem.components.LocalTextHalo
import io.github.magalz.borealis.designsystem.components.STREAM_SCRIM_ALPHA
import io.github.magalz.borealis.designsystem.components.Text
import io.github.magalz.borealis.designsystem.focus.initialFocus
import io.github.magalz.borealis.designsystem.glass.rememberPanelGlass
import io.github.magalz.borealis.designsystem.theme.BorealisDimens
import io.github.magalz.borealis.designsystem.theme.DialogWidth
import io.github.magalz.borealis.designsystem.theme.FormFactor
import io.github.magalz.borealis.designsystem.theme.LocalBorealisColors
import io.github.magalz.borealis.designsystem.theme.LocalFormFactor
import io.github.magalz.borealis.designsystem.tokens.BorealisTokens
import io.github.magalz.borealis.stream.ui.common.onBackKey
import io.github.magalz.borealis.stream.ui.common.onGamepadB
import io.github.magalz.borealis.stream.ui.glass.LocalPopupOverGame
import io.github.magalz.borealis.stream.ui.glass.LocalVideoGlass
import io.github.magalz.borealis.stream.ui.glass.OnVideoGlass
import io.github.magalz.borealis.stream.ui.glass.POPUP_TINT_PERCENT
import io.github.magalz.borealis.stream.ui.glass.glassContentLayer
import io.github.magalz.borealis.stream.ui.glass.videoGlassSurface
import kotlinx.coroutines.flow.filter

private val DIALOG_GAP = 12.dp
private val BUTTON_GAP = 12.dp

/** A button of a [StreamDialog]: its [label], how it looks and what it does. */
internal class StreamDialogAction(
    val label: String,
    val style: BorealisButtonStyle,
    val onClick: () -> Unit,
)

/**
 * A dialog inside the stream chrome: a scrim over the whole layer and, centred on it, a panel with
 * the surface, the [width] (the compact one unless the dialog says otherwise) and the padding of a
 * `BorealisDialog`, the [title], the [content], the [details] and the [actions] as buttons. The
 * conflict dialog, the error popup and the Quit popup of the menu are made from it.
 *
 * It is not a `BorealisDialog`, on purpose. That one is a platform dialog window, and the stream
 * host owns the input: `StreamChromeHost.dispatchKeyEvent` routes the gamepad and the keys to the
 * chrome root, which a dialog window would bypass (gamepad B, the D-pad focus and the Back rule).
 *
 * The scrim takes the touches, so nothing under the dialog is reached, and a tap on it calls
 * [onDismiss], as Cancel does, unless [dismissOnScrimTap] is off and the tap does nothing (a dialog
 * that has no Cancel); a tap in the panel does not. The first of [actions] has the initial focus,
 * so it is the safe one (Cancel): a stray OK changes nothing. The focus stays in the dialog, Up,
 * Down, Left and Right move between the buttons and stop at the last. The gamepad B button, the
 * Back key and Back of the system call [onDismiss] and are used up here, so neither reaches what is
 * under the dialog nor the host's callback, which closes the open panel.
 *
 * Over the game ([LocalPopupOverGame]: the Quit popup of the menu, and the error popup over the
 * last frame of the stream) the scrim is the page at 35 percent, the one of the side panel of the
 * menu, so the game stays visible around the dialog, and the panel is the firm element: where it is
 * glass (the menu provides it, see `InGameMenu`, and the frozen frame does for the error popup) it
 * is [POPUP_TINT_PERCENT] of the surface over the blurred copy of the video, with the text colours
 * that keep 4.5:1 over it; where the rules make it solid it is the `surface` colour and the scrim
 * is the same. Over the background of the screens outside the game the dialog does what a dialog
 * window does in the Library (ADR 0057, decision 8): the background is blurred and held on its
 * still frame, the dialog draws no scrim, and the panel takes the glass tint and the glass text
 * colours; on API 28 to 30 the dialog scrim (the page at 72 percent) and the solid panel stay.
 * Elsewhere, and where the glass is solid, the panel is the `surface` colour.
 *
 * The panel is announced by its [title]. The title and the [content] are one polite live region, so
 * a screen reader reads the body with the title when the dialog opens; the title is the only
 * heading. The [details] come under it and are not read out when the dialog opens: a screen reader
 * reaches them as it moves on. Three buttons or more stack on a phone, where they do not fit side
 * by side; elsewhere the buttons go to the next row when they do not fit in one. [modifier] applies
 * to the panel.
 */
@Composable
internal fun StreamDialog(
    title: String,
    onDismiss: () -> Unit,
    actions: List<StreamDialogAction>,
    modifier: Modifier = Modifier,
    width: DialogWidth = DialogWidth.Compact,
    dismissOnScrimTap: Boolean = true,
    details: @Composable ColumnScope.() -> Unit = {},
    content: @Composable ColumnScope.() -> Unit,
) {
    require(actions.isNotEmpty()) { "A stream dialog has at least one action" }
    val colors = LocalBorealisColors.current
    val glass = LocalVideoGlass.current
    val overGame = LocalPopupOverGame.current
    // Over the background of the screens outside the game, the dialog opens the overlay of ADR
    // 0057, decision 8: no scrim, the tinted panel; elsewhere this is null.
    val overlay = rememberPanelGlass()
    val shape = MaterialTheme.shapes.extraLarge
    DismissOnSystemBack(onDismiss)
    BoxWithConstraints(
        Modifier.fillMaxSize()
            .then(
                when {
                    overGame -> Modifier.background(colors.page.copy(alpha = STREAM_SCRIM_ALPHA))
                    overlay == null -> Modifier.background(colors.scrim)
                    else -> Modifier
                }
            )
            // Before the insets: the scrim takes the tap over all it draws, the strips of the
            // system bars too, and nothing under it takes it.
            .pointerInput(onDismiss, dismissOnScrimTap) {
                detectTapGestures { if (dismissOnScrimTap) onDismiss() }
            }
            .windowInsetsPadding(WindowInsets.safeDrawing)
            .onGamepadB(onDismiss)
            .onBackKey(onDismiss)
            .keepFocusInside(),
        contentAlignment = Alignment.Center,
    ) {
        // The padding is inside the scroll area: the area clips, and a focus outline reaches
        // outside its button.
        Column(
            modifier
                .semantics { paneTitle = title }
                .width(BorealisDimens.dialogWidth(width, maxWidth))
                .then(
                    when {
                        glass != null ->
                            Modifier.videoGlassSurface(glass, shape, POPUP_TINT_PERCENT)
                        overlay != null ->
                            Modifier.background(colors.surface.copy(alpha = overlay.tint), shape)
                        else -> Modifier.background(colors.surface, shape)
                    }
                )
                .border(1.dp, colors.line, shape)
                .clip(shape)
                // A tap in the panel is not a tap on the scrim.
                .pointerInput(Unit) { detectTapGestures() }
                .verticalScroll(rememberScrollState())
                .padding(BorealisTokens.Space.DialogPadding),
            verticalArrangement = Arrangement.spacedBy(DIALOG_GAP),
        ) {
            // The same composition with and without the glass of the background.
            CompositionLocalProvider(LocalBorealisColors provides (overlay?.colors ?: colors)) {
                DialogContent(title, actions, glass != null, details, content)
            }
        }
    }
}

/**
 * The title, the body and the buttons of a [StreamDialog], in the text colours and with the halo of
 * the video glass where [onGlass], and the same composition without it.
 *
 * Over the glass the title and the body are on an offscreen layer of their own (see
 * [glassContentLayer]). The buttons are not: a focus outline reaches outside its button, Cancel is
 * the last child and it has the initial focus, and the layer would cut the outline at the edge of
 * the content. Two buttons drawn at each copy of the video cost nothing.
 */
@Composable
private fun DialogContent(
    title: String,
    actions: List<StreamDialogAction>,
    onGlass: Boolean,
    details: @Composable ColumnScope.() -> Unit,
    content: @Composable ColumnScope.() -> Unit,
) {
    OnVideoGlass(LocalVideoGlass.current) {
        Column(verticalArrangement = Arrangement.spacedBy(DIALOG_GAP)) {
            Column(
                Modifier.glassContentLayer(onGlass).semantics(mergeDescendants = true) {
                    liveRegion = LiveRegionMode.Polite
                },
                verticalArrangement = Arrangement.spacedBy(DIALOG_GAP),
            ) {
                Text(
                    title,
                    Modifier.semantics { heading() },
                    style = MaterialTheme.typography.titleLarge,
                )
                content()
            }
            details()
            Actions(actions)
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun Actions(actions: List<StreamDialogAction>) {
    val firstFocus = remember { FocusRequester() }
    FocusOnKeyboard(firstFocus)
    val stacked =
        actions.size > MAX_SIDE_BY_SIDE_ON_A_PHONE && LocalFormFactor.current == FormFactor.Phone
    val width = if (stacked) Modifier.fillMaxWidth() else Modifier
    val halo = LocalTextHalo.current
    val buttons: @Composable () -> Unit = {
        actions.forEachIndexed { index, action ->
            // The label of a filled button stands on the fill, not on the glass: a halo around it
            // would smudge it.
            CompositionLocalProvider(
                LocalTextHalo provides
                    if (action.style == BorealisButtonStyle.Primary) null else halo
            ) {
                BorealisButton(
                    action.label,
                    action.onClick,
                    if (index == 0) width.initialFocus(firstFocus) else width,
                    action.style,
                )
            }
        }
    }
    if (stacked) {
        Column(verticalArrangement = Arrangement.spacedBy(BUTTON_GAP)) { buttons() }
    } else {
        FlowRow(
            horizontalArrangement = Arrangement.spacedBy(BUTTON_GAP),
            verticalArrangement = Arrangement.spacedBy(BUTTON_GAP),
        ) {
            buttons()
        }
    }
}

private const val MAX_SIDE_BY_SIDE_ON_A_PHONE = 2

/**
 * Puts the focus on [first] when the window goes into the keyboard input mode. A phone opens the
 * dialog in touch mode, where a button cannot take the focus, so the initial focus does nothing
 * there; the first key of a D-pad, a pad or a keyboard leaves touch mode and the focus comes then,
 * to the safe button and not to whatever else the chrome has in reach.
 */
@Composable
private fun FocusOnKeyboard(first: FocusRequester) {
    val inputMode = LocalInputModeManager.current
    LaunchedEffect(inputMode) {
        snapshotFlow { inputMode.inputMode }
            .filter { it == InputMode.Keyboard }
            .collect { first.requestFocus() }
    }
}

/**
 * The focus does not leave the dialog in any direction, so the D-pad never reaches what is under
 * it.
 */
private fun Modifier.keepFocusInside(): Modifier = focusProperties {
    onExit = { cancelFocusChange() }
}
    .focusGroup()

/**
 * Back of the system (the gesture, or the key when nothing in the dialog has the focus, as on a
 * phone in touch mode) calls [onDismiss] while the dialog is shown. The callback is added after the
 * host's, so it is asked first, and it goes when the dialog does.
 */
@Composable
private fun DismissOnSystemBack(onDismiss: () -> Unit) {
    val owner =
        checkNotNull(LocalView.current.findViewTreeOnBackPressedDispatcherOwner()) {
            "A stream dialog needs the OnBackPressedDispatcherOwner of its activity"
        }
    val latest = rememberUpdatedState(onDismiss)
    DisposableEffect(owner) {
        val callback =
            object : OnBackPressedCallback(true) {
                override fun handleOnBackPressed() = latest.value()
            }
        owner.onBackPressedDispatcher.addCallback(callback)
        onDispose { callback.remove() }
    }
}

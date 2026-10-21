package io.github.magalz.borealis.designsystem.components

import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.width
import androidx.compose.material3.BottomSheetDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.minimumInteractiveComponentSize
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import io.github.magalz.borealis.designsystem.glass.OverlayOpen
import io.github.magalz.borealis.designsystem.glass.rememberOverlayGlass
import io.github.magalz.borealis.designsystem.theme.FormFactor
import io.github.magalz.borealis.designsystem.theme.LocalBorealisColors
import io.github.magalz.borealis.designsystem.theme.LocalBorealisShapes
import io.github.magalz.borealis.designsystem.theme.LocalFormFactor
import io.github.magalz.borealis.designsystem.theme.sheetWidth

/**
 * The shell of a modal list that opens over a screen, for the sheets of the design system: on a
 * phone a bottom sheet, on a TV or handheld a centred dialog of [sheetWidth]. Back, or a tap
 * outside it, calls [onDismiss]. [content] is the list, and is told whether the shell is the one
 * of a phone, for what the focus does differently there.
 *
 * Over a glass host that blurs its window the sheet draws no scrim, its surface is the tint of the
 * glass over the blurred window, and [content] has the glass text colours ([OverlayOpen],
 * [rememberOverlayGlass], [overlayPanel] and [OverlayTextColors] are the wiring).
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun OverlaySheet(onDismiss: () -> Unit, content: @Composable (onPhone: Boolean) -> Unit) {
    val glass = rememberOverlayGlass()
    OverlayOpen()
    if (LocalFormFactor.current == FormFactor.Phone) {
        val colors = LocalBorealisColors.current
        ModalBottomSheet(
            onDismissRequest = onDismiss,
            sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
            shape = LocalBorealisShapes.current.sheet,
            containerColor = colors.surface.copy(alpha = glass?.tint ?: 1f),
            contentColor = colors.ink,
            scrimColor = if (glass == null) colors.scrim else Color.Transparent,
            dragHandle = { SheetHandle() },
        ) {
            OverlayTextColors(glass) { content(true) }
        }
    } else {
        Dialog(
            onDismissRequest = onDismiss,
            properties = DialogProperties(usePlatformDefaultWidth = false),
        ) {
            // Over a blurred window there is no dim: the glass is the dimmer.
            if (glass != null) NoPlatformDim()
            val colors = LocalBorealisColors.current
            val shape = MaterialTheme.shapes.extraLarge
            Column(
                Modifier.width(sheetWidth())
                    .overlayPanel(shape, glass)
                    .border(1.dp, colors.line, shape)
                    .clip(shape)
            ) {
                OverlayTextColors(glass) { content(false) }
            }
        }
    }
}

/**
 * The handle of a bottom sheet, which a screen reader uses to expand and dismiss it. The default is
 * 32dp wide; this one has the 48dp that a finger needs (WCAG 2.5.8), around the bar and not in it.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun SheetHandle() {
    BottomSheetDefaults.DragHandle(Modifier.minimumInteractiveComponentSize())
}

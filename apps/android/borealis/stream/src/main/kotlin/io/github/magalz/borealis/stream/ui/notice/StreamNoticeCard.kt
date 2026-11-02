package io.github.magalz.borealis.stream.ui.notice

import android.os.Build
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import io.github.magalz.borealis.designsystem.components.Text
import io.github.magalz.borealis.designsystem.theme.LocalBorealisColors
import io.github.magalz.borealis.designsystem.theme.LocalBorealisLook
import io.github.magalz.borealis.designsystem.theme.rememberAnimationsRemoved
import io.github.magalz.borealis.stream.R
import io.github.magalz.borealis.stream.StreamNotice
import io.github.magalz.borealis.stream.SurroundLayout
import io.github.magalz.borealis.stream.ui.glass.POPUP_TINT_PERCENT
import io.github.magalz.borealis.stream.ui.glass.videoGlassSpec
import kotlinx.coroutines.delay

/** Marks the card, so a test can find it. */
internal const val NOTICE_CARD_TAG = "stream_notice_card"

private const val NOTICE_DURATION_MS = 4_000L
private const val PERCENT = 100f
private val PADDING_HORIZONTAL = 14.dp
private val PADDING_VERTICAL = 8.dp

/**
 * The card that tells the viewer [notice] over the stream, in one line, and goes by itself after
 * four seconds, when it calls [onShown] once with the notice. Nothing is drawn for a null notice.
 * The caller shows it where the stream is, one notice at a time ([StreamSession.notice]): a
 * different notice starts its own four seconds, and one that is not composed has none of them, so
 * the card waits out a panel that covers it and picture-in-picture.
 *
 * It takes no focus and no input: nothing on it is clickable or focusable, and a touch or a key
 * goes through to what is under it, so a notice never changes who has the input. Screen readers
 * hear it as a polite announcement. There is no animation, so Reduce motion has nothing to remove
 * here; where the glass of the stream would be solid (the transparency Off, high contrast, Reduce
 * motion: the rules of [videoGlassSpec]) the card is the solid surface, and else the surface at
 * the firm tint of a popup over the game ([POPUP_TINT_PERCENT]), with no blur and no live copy of
 * the video, which a card of four seconds is too light for. The text is ink, which that tint keeps
 * at 4.5:1 over the lightest frame, with no halo (`PopupGlassContrastTest` in
 * `design/accessibility`).
 */
@Composable
internal fun StreamNoticeCard(
    notice: StreamNotice?,
    onShown: (StreamNotice) -> Unit,
    modifier: Modifier = Modifier,
) {
    if (notice == null) return
    val shown by rememberUpdatedState(onShown)
    LaunchedEffect(notice) {
        delay(NOTICE_DURATION_MS)
        shown(notice)
    }
    val colors = LocalBorealisColors.current
    val shape = MaterialTheme.shapes.medium
    // The rules of the glass, without its API check: a tint needs no blur.
    val glass =
        videoGlassSpec(
            LocalBorealisLook.current,
            colors,
            Build.VERSION_CODES.S,
            batterySaver = false,
            animationsRemoved = rememberAnimationsRemoved(),
        )
    val surface =
        if (glass == null) colors.surface
        else colors.surface.copy(alpha = POPUP_TINT_PERCENT / PERCENT)
    Text(
        noticeText(notice),
        modifier
            .testTag(NOTICE_CARD_TAG)
            .semantics { liveRegion = LiveRegionMode.Polite }
            .background(surface, shape)
            .border(1.dp, colors.line, shape)
            .padding(PADDING_HORIZONTAL, PADDING_VERTICAL),
        color = colors.ink,
        style = MaterialTheme.typography.bodySmall,
    )
}

@Composable
private fun noticeText(notice: StreamNotice): String =
    when (notice) {
        StreamNotice.BluetoothOutput -> stringResource(R.string.stream_notice_bluetooth)
        is StreamNotice.StereoFallback ->
            stringResource(R.string.stream_notice_stereo_fallback, notice.asked.label())
    }

private fun SurroundLayout.label() =
    when (this) {
        SurroundLayout.SURROUND_5_1 -> "5.1"
        SurroundLayout.SURROUND_7_1 -> "7.1"
    }

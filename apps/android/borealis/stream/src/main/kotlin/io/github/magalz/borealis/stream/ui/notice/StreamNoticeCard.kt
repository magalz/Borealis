package io.github.magalz.borealis.stream.ui.notice

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import io.github.magalz.borealis.stream.StreamNotice

/** Marks the card, so a test can find it. */
internal const val NOTICE_CARD_TAG = "stream_notice_card"

/** The card that tells the viewer [notice]; it is not drawn yet. */
@Composable
internal fun StreamNoticeCard(
    notice: StreamNotice?,
    onShown: (StreamNotice) -> Unit,
    modifier: Modifier = Modifier,
) = Unit

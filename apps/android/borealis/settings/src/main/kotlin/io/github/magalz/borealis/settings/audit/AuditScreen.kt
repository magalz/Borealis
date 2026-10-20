package io.github.magalz.borealis.settings.audit

import androidx.activity.compose.BackHandler
import androidx.annotation.StringRes
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.focusable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import io.github.magalz.borealis.designsystem.components.BorealisPhoneBar
import io.github.magalz.borealis.designsystem.components.EmptyState
import io.github.magalz.borealis.designsystem.focus.borealisFocusIndication
import io.github.magalz.borealis.designsystem.focus.initialFocus
import io.github.magalz.borealis.designsystem.focus.rememberInitialFocusState
import io.github.magalz.borealis.designsystem.theme.BorealisDimens
import io.github.magalz.borealis.designsystem.theme.BorealisFonts
import io.github.magalz.borealis.designsystem.theme.BorealisTheme
import io.github.magalz.borealis.designsystem.theme.FormFactor
import io.github.magalz.borealis.designsystem.theme.LocalBorealisColors
import io.github.magalz.borealis.designsystem.theme.LocalBorealisSpacing
import io.github.magalz.borealis.designsystem.theme.LocalFormFactor
import io.github.magalz.borealis.logging.AuditType
import io.github.magalz.borealis.settings.R
import io.github.magalz.borealis.settings.ui.dpadScroll

private val ROW_PADDING_HORIZONTAL = 12.dp
private val ROW_PADDING_VERTICAL = 10.dp
private val COLUMN_GAP = 12.dp
private val PHONE_TIME_WIDTH = 72.dp
private val TV_TIME_WIDTH = 100.dp
private val HEADER_PADDING = 16.dp
private val CODE_GAP = 8.dp
private val FIELD_GAP = 16.dp
private val SUMMARY_GAP = 2.dp
private const val SUMMARY_MAX_LINES = 3

/**
 * The audit trail (UC-007): [AuditUiState.entries] as a list, newest first, and in its place the
 * [AuditUiState.detail] of the entry that the user opened. A tap or OK on an entry runs
 * [onEntrySelected] with its [AuditRow.key]. Back, and the arrow of the phone bar, run
 * [onDetailDismissed] while an entry is open and [onBack] otherwise; a TV or handheld has no arrow.
 * Provides its own theme for [formFactor].
 */
@Composable
fun AuditScreen(
    state: AuditUiState,
    formFactor: FormFactor,
    onEntrySelected: (key: String) -> Unit,
    onDetailDismissed: () -> Unit,
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
) {
    BorealisTheme(formFactor) {
        val close = if (state.detail != null) onDetailDismissed else onBack
        BackHandler(onBack = close)
        // The list is left while an entry is open, but its scroll position and the entry that was
        // open stay here, so the list comes back to that entry.
        val listState = rememberLazyListState()
        var lastOpened by rememberSaveable { mutableStateOf<String?>(null) }
        Column(modifier.fillMaxSize().windowInsetsPadding(WindowInsets.safeDrawing)) {
            if (formFactor == FormFactor.Phone) {
                BorealisPhoneBar(close, stringResource(R.string.audit_ui_back)) { Title() }
            } else {
                Header()
            }
            Box(Modifier.weight(1f).fillMaxWidth()) {
                when {
                    state.detail != null -> Detail(state.detail)
                    state.entries.isNotEmpty() ->
                        Entries(
                            state.entries,
                            listState,
                            lastOpened,
                            onSelected = {
                                lastOpened = it
                                onEntrySelected(it)
                            },
                        )
                    state.loading -> Unit
                    else ->
                        EmptyState(
                            stringResource(R.string.audit_ui_empty_title),
                            stringResource(R.string.audit_ui_empty_body),
                            Modifier.align(Alignment.Center),
                        )
                }
            }
        }
    }
}

@Composable
private fun Header() {
    val gutter = LocalBorealisSpacing.current.gutter
    Title(Modifier.fillMaxWidth().padding(horizontal = gutter, vertical = HEADER_PADDING))
}

/** The name of the screen with how long the entries are kept. */
@Composable
private fun Title(modifier: Modifier = Modifier) {
    Column(modifier, verticalArrangement = Arrangement.spacedBy(BorealisDimens.stackedTextGap)) {
        Text(
            stringResource(R.string.audit_ui_screen_title),
            style = MaterialTheme.typography.titleLarge,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
        Text(
            stringResource(R.string.audit_ui_retention),
            style = MaterialTheme.typography.bodySmall,
            color = LocalBorealisColors.current.ink3,
        )
    }
}

@Composable
private fun Entries(
    entries: List<AuditRow>,
    listState: LazyListState,
    lastOpened: String?,
    onSelected: (key: String) -> Unit,
) {
    val gutter = LocalBorealisSpacing.current.gutter
    val focusRequester = remember { FocusRequester() }
    val focusState = rememberInitialFocusState()
    // Focus starts on the entry that was open, or on the first one; entries that arrive later at
    // the head leave it where it is.
    val focusKey = remember { lastOpened?.takeIf { key -> entries.any { it.key == key } } }
    LaunchedEffect(Unit) {
        val index = entries.indexOfFirst { it.key == focusKey }
        // Wait for the first layout: the state still describes the list as it was left.
        withFrameNanos {}
        if (index >= 0 && listState.layoutInfo.visibleItemsInfo.none { it.index == index }) {
            listState.scrollToItem(index)
        }
    }
    LazyColumn(
        modifier = Modifier.widthIn(max = BorealisDimens.contentMaxWidth).fillMaxSize(),
        state = listState,
        contentPadding = PaddingValues(horizontal = gutter, vertical = BorealisDimens.listPadding),
        verticalArrangement = Arrangement.spacedBy(BorealisDimens.listGap),
    ) {
        itemsIndexed(entries, key = { _, row -> row.key }) { index, row ->
            val tag = Modifier.testTag("audit:entry:$index")
            val starts = if (focusKey == null) index == 0 else row.key == focusKey
            EntryRow(
                row,
                onClick = { onSelected(row.key) },
                modifier = if (starts) tag.initialFocus(focusRequester, focusState) else tag,
            )
        }
    }
}

/** One entry: its time, its title and event code, and its detail. */
@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun EntryRow(row: AuditRow, onClick: () -> Unit, modifier: Modifier) {
    val colors = LocalBorealisColors.current
    val shape = MaterialTheme.shapes.medium
    val timeWidth =
        if (LocalFormFactor.current == FormFactor.Phone) PHONE_TIME_WIDTH else TV_TIME_WIDTH
    Row(
        modifier
            .fillMaxWidth()
            .borealisFocusIndication(shape)
            .clip(shape)
            .clickable(
                interactionSource = null,
                indication = null,
                role = Role.Button,
                onClickLabel = stringResource(R.string.audit_ui_show_detail),
                onClick = onClick,
            )
            .padding(horizontal = ROW_PADDING_HORIZONTAL, vertical = ROW_PADDING_VERTICAL),
        horizontalArrangement = Arrangement.spacedBy(COLUMN_GAP),
    ) {
        Text(
            row.time,
            Modifier.widthIn(min = timeWidth),
            color = colors.ink3,
            style = MaterialTheme.typography.labelMedium.copy(fontFamily = BorealisFonts.Mono),
        )
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(SUMMARY_GAP)) {
            FlowRow(
                horizontalArrangement = Arrangement.spacedBy(CODE_GAP),
                verticalArrangement = Arrangement.Center,
            ) {
                Text(
                    stringResource(row.type.titleRes()),
                    color = titleColor(row.type),
                    style = MaterialTheme.typography.labelLarge,
                )
                Text(
                    row.type.name,
                    color = colors.ink3,
                    style =
                        MaterialTheme.typography.labelMedium.copy(fontFamily = BorealisFonts.Mono),
                )
            }
            if (row.summary != null) {
                Text(
                    row.summary,
                    color = colors.ink3,
                    style = MaterialTheme.typography.bodySmall,
                    maxLines = SUMMARY_MAX_LINES,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }
    }
}

/** The opened entry in full: what the list shortens, and its date, result and host. */
@Composable
private fun Detail(detail: AuditDetail) {
    val shape = MaterialTheme.shapes.medium
    val spacing = LocalBorealisSpacing.current
    val focusRequester = remember { FocusRequester() }
    val scroll = rememberScrollState()
    Column(
        Modifier.widthIn(max = BorealisDimens.contentMaxWidth)
            .fillMaxWidth()
            .padding(horizontal = spacing.gutter, vertical = BorealisDimens.listPadding)
            .initialFocus(focusRequester)
            .dpadScroll(scroll)
            .borealisFocusIndication(shape)
            .clip(shape)
            .background(LocalBorealisColors.current.surface, shape)
            // Before verticalScroll: a focusable after it takes the size of the whole entry, not
            // of the viewport, and the focus search does not find it where it is shown.
            .focusable()
            .verticalScroll(scroll)
            .padding(spacing.cardPadding),
        verticalArrangement = Arrangement.spacedBy(FIELD_GAP),
    ) {
        Text(
            stringResource(detail.type.titleRes()),
            color = titleColor(detail.type),
            style = MaterialTheme.typography.titleLarge,
        )
        Field(R.string.audit_ui_label_event, detail.type.name, mono = true)
        Field(R.string.audit_ui_label_time, detail.time)
        Field(R.string.audit_ui_label_result, stringResource(detail.outcome.labelRes()))
        if (!detail.host.isNullOrBlank()) Field(R.string.audit_ui_label_host, detail.host)
        if (!detail.detail.isNullOrBlank()) Field(R.string.audit_ui_label_detail, detail.detail)
    }
}

@Composable
private fun Field(@StringRes label: Int, value: String, mono: Boolean = false) {
    val colors = LocalBorealisColors.current
    val body = MaterialTheme.typography.bodyMedium
    Column(verticalArrangement = Arrangement.spacedBy(BorealisDimens.stackedTextGap)) {
        Text(stringResource(label), color = colors.ink3, style = MaterialTheme.typography.bodySmall)
        Text(
            value,
            color = colors.ink,
            style = if (mono) body.copy(fontFamily = BorealisFonts.Mono) else body,
        )
    }
}

/** A changed host certificate is the one event that asks the user to look twice. */
@Composable
private fun titleColor(type: AuditType): Color {
    val colors = LocalBorealisColors.current
    return if (type == AuditType.CERTIFICATE_CHANGED) colors.warningInk else colors.ink
}

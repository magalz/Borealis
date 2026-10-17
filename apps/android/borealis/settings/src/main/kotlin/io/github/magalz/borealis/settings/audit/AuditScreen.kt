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
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import io.github.magalz.borealis.designsystem.components.EmptyState
import io.github.magalz.borealis.designsystem.focus.borealisFocusIndication
import io.github.magalz.borealis.designsystem.focus.initialFocus
import io.github.magalz.borealis.designsystem.focus.rememberInitialFocusState
import io.github.magalz.borealis.designsystem.theme.BorealisFonts
import io.github.magalz.borealis.designsystem.theme.BorealisTheme
import io.github.magalz.borealis.designsystem.theme.FormFactor
import io.github.magalz.borealis.designsystem.theme.LocalBorealisColors
import io.github.magalz.borealis.designsystem.theme.LocalBorealisSpacing
import io.github.magalz.borealis.designsystem.theme.LocalFormFactor
import io.github.magalz.borealis.logging.AuditType
import io.github.magalz.borealis.settings.R

/** The content is as wide as a reading column; a TV leaves the rest empty. */
private val CONTENT_MAX_WIDTH = 720.dp

/** An outline sits 5dp outside a row, so the list keeps 8dp around it and 10dp between rows. */
private val LIST_PADDING = 8.dp
private val ROW_GAP = 10.dp
private val ROW_PADDING_HORIZONTAL = 12.dp
private val ROW_PADDING_VERTICAL = 10.dp
private val COLUMN_GAP = 12.dp
private val PHONE_TIME_WIDTH = 72.dp
private val TV_TIME_WIDTH = 100.dp
private val HEADER_GAP = 4.dp
private val HEADER_PADDING = 16.dp
private val BAR_PADDING = 4.dp
private val ICON_SIZE = 24.dp
private val ICON_STROKE = 2.dp
private val CODE_GAP = 8.dp
private val FIELD_GAP = 16.dp
private val SUMMARY_GAP = 2.dp
private const val SUMMARY_MAX_LINES = 3

/**
 * The audit trail (UC-007): [AuditUiState.entries] as a list, newest first, and in its place the
 * [AuditUiState.detail] of the entry that the user opened. A tap or OK on an entry runs
 * [onEntrySelected] with its position. Back, and the arrow of the phone bar, run
 * [onDetailDismissed] while an entry is open and [onBack] otherwise; a TV or handheld has no arrow.
 * Provides its own theme for [formFactor].
 */
@Composable
fun AuditScreen(
    state: AuditUiState,
    formFactor: FormFactor,
    onEntrySelected: (index: Int) -> Unit,
    onDetailDismissed: () -> Unit,
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
) {
    BorealisTheme(formFactor) {
        val close = if (state.detail != null) onDetailDismissed else onBack
        BackHandler(onBack = close)
        // The entry that was open takes the focus again when the list comes back.
        var lastOpened by rememberSaveable { mutableIntStateOf(0) }
        Column(
            modifier
                .fillMaxSize()
                .background(LocalBorealisColors.current.page)
                .windowInsetsPadding(WindowInsets.safeDrawing)
        ) {
            if (formFactor == FormFactor.Phone) PhoneBar(close) else Header()
            Box(Modifier.weight(1f).fillMaxWidth()) {
                when {
                    state.detail != null -> Detail(state.detail)
                    state.entries.isNotEmpty() ->
                        Entries(
                            state.entries,
                            focusIndex = lastOpened.coerceIn(0, state.entries.lastIndex),
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
private fun PhoneBar(onBack: () -> Unit) {
    val ink = LocalBorealisColors.current.ink
    val back = stringResource(R.string.audit_ui_back)
    Row(
        Modifier.fillMaxWidth().padding(horizontal = BAR_PADDING),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        IconButton(onClick = onBack) {
            Box(
                Modifier.size(ICON_SIZE)
                    .semantics { contentDescription = back }
                    .drawBehind {
                        val stroke =
                            Stroke(
                                ICON_STROKE.toPx(),
                                cap = StrokeCap.Round,
                                join = StrokeJoin.Round,
                            )
                        val arrow =
                            Path().apply {
                                moveTo(size.width * ARROW_TAIL, size.height * HALF)
                                lineTo(size.width * ARROW_TIP, size.height * HALF)
                                moveTo(size.width * ARROW_HEAD, size.height * ARROW_TOP)
                                lineTo(size.width * ARROW_TIP, size.height * HALF)
                                lineTo(size.width * ARROW_HEAD, size.height * ARROW_BOTTOM)
                            }
                        drawPath(arrow, ink, style = stroke)
                    }
            )
        }
        Title(Modifier.weight(1f))
    }
}

private const val HALF = 0.5f
private const val ARROW_TAIL = 0.79f
private const val ARROW_TIP = 0.21f
private const val ARROW_HEAD = 0.46f
private const val ARROW_TOP = 0.25f
private const val ARROW_BOTTOM = 0.75f

@Composable
private fun Header() {
    val gutter = LocalBorealisSpacing.current.gutter
    Title(Modifier.fillMaxWidth().padding(horizontal = gutter, vertical = HEADER_PADDING))
}

/** The name of the screen with how long the entries are kept. */
@Composable
private fun Title(modifier: Modifier) {
    Column(modifier, verticalArrangement = Arrangement.spacedBy(HEADER_GAP)) {
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
private fun Entries(entries: List<AuditRow>, focusIndex: Int, onSelected: (Int) -> Unit) {
    val gutter = LocalBorealisSpacing.current.gutter
    val focusRequester = remember { FocusRequester() }
    val focusState = rememberInitialFocusState()
    LazyColumn(
        modifier = Modifier.widthIn(max = CONTENT_MAX_WIDTH).fillMaxSize(),
        contentPadding = PaddingValues(horizontal = gutter, vertical = LIST_PADDING),
        verticalArrangement = Arrangement.spacedBy(ROW_GAP),
    ) {
        itemsIndexed(entries) { index, row ->
            val tag = Modifier.testTag("audit:entry:$index")
            EntryRow(
                row,
                onClick = { onSelected(index) },
                modifier =
                    if (index == focusIndex) tag.initialFocus(focusRequester, focusState) else tag,
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
    Column(
        Modifier.widthIn(max = CONTENT_MAX_WIDTH)
            .fillMaxWidth()
            .padding(horizontal = spacing.gutter, vertical = LIST_PADDING)
            .initialFocus(focusRequester)
            .borealisFocusIndication(shape)
            .clip(shape)
            .background(LocalBorealisColors.current.surface, shape)
            .verticalScroll(rememberScrollState())
            .focusable()
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
    Column(verticalArrangement = Arrangement.spacedBy(HEADER_GAP)) {
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

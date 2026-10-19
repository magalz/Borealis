package io.github.magalz.borealis.designsystem.components.settings

import androidx.compose.foundation.background
import androidx.compose.foundation.focusGroup
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.State
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.snapshotFlow
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusDirection
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusProperties
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp
import io.github.magalz.borealis.designsystem.components.liftedWhenFocused
import io.github.magalz.borealis.designsystem.focus.BorealisPressIndication
import io.github.magalz.borealis.designsystem.focus.borealisFocusIndication
import io.github.magalz.borealis.designsystem.theme.BorealisDimens
import io.github.magalz.borealis.designsystem.theme.LocalBorealisColors
import io.github.magalz.borealis.designsystem.theme.LocalFormFactor
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch

/** One group of a [SettingsTwoPane]: the [key] that identifies it and its [title]. */
class SettingsGroup<out K>(val key: K, val title: String)

private val PANE_GAP = 12.dp
/** An outline sits 5dp outside its item, so neighbours need 10dp between them. */
private val ITEM_GAP = 10.dp
private val AREA_PADDING = 8.dp
private val GROUP_PADDING_HORIZONTAL = 12.dp
private val GROUP_PADDING_VERTICAL = 10.dp

/** How long focus must rest on a group before the pane follows it. */
internal const val GROUP_SELECT_DELAY_MILLIS = 150L

/**
 * The settings of a TV or handheld in two panes: the [groups] by title in a list on the left, and
 * on the right whatever [content] shows for the group whose key is [selected]. The pane scrolls and
 * spaces its rows 10dp apart; [content] puts the rows of the group in it, with 8dp of padding
 * around them already given. A group for which [selfScrolling] is true brings its own scroll and
 * padding: its [content] fills the pane, which does not wrap it in a scroll that would give it an
 * unbounded height.
 *
 * Groups are identified by key, not by position, so a screen can hide a group on some devices
 * without moving the selection. A [selected] key that no group has falls back to the first group,
 * and with no groups the pane is empty.
 *
 * Selection follows focus once focus rests: the group that has focus looks selected at once, but
 * the pane, which costs more than a frame to build on a TV, changes and [onSelect] is called with
 * the key only after focus has stayed on the group for [GROUP_SELECT_DELAY_MILLIS], so moving over
 * several groups quickly selects the last one only. A tap or OK on a group selects it at once.
 * Right from any group selects it if the pane has not followed yet, and enters the pane at its
 * first row; Left from the pane, when no row uses the key, returns to the selected group, whichever
 * row it leaves.
 */
@Composable
fun <K> SettingsTwoPane(
    groups: List<SettingsGroup<K>>,
    selected: K,
    onSelect: (K) -> Unit,
    modifier: Modifier = Modifier,
    selfScrolling: (K) -> Boolean = { false },
    content: @Composable ColumnScope.(K) -> Unit,
) {
    val current = groups.firstOrNull { it.key == selected } ?: groups.firstOrNull()
    val selectedGroup = remember { FocusRequester() }
    val pane = remember { FocusRequester() }
    val focusManager = LocalFocusManager.current
    val scope = rememberCoroutineScope()
    val currentKey by rememberUpdatedState(current?.key)
    val select by rememberUpdatedState(onSelect)
    // The group that D-pad focus moved to and the pane has not followed yet.
    val resting = remember { mutableStateOf<K?>(null) }

    fun selectNow(key: K) {
        resting.value = null
        if (key != currentKey) select(key)
    }

    // A new group restarts the wait, so groups that focus passes over are never selected.
    LaunchedEffect(Unit) {
        snapshotFlow { resting.value }
            .collectLatest { key ->
                if (key != null) {
                    delay(GROUP_SELECT_DELAY_MILLIS)
                    selectNow(key)
                }
            }
    }

    fun focusPane() {
        // The search for a focus target to the right would pick the row nearest to the group.
        // With no focus to measure from, entering the pane picks its first row.
        focusManager.clearFocus()
        if (!pane.requestFocus()) selectedGroup.requestFocus()
    }

    fun enterPane() {
        val group = resting.value
        if (group == null) {
            focusPane()
        } else {
            selectNow(group)
            // The rows of the group are composed in the next frame and placed by the one after.
            scope.launch {
                repeat(2) { withFrameNanos {} }
                focusPane()
            }
        }
    }

    Row(modifier.fillMaxSize(), horizontalArrangement = Arrangement.spacedBy(PANE_GAP)) {
        GroupList(
            groups,
            current?.key,
            resting,
            onRest = { resting.value = if (it == currentKey) null else it },
            onSelect = ::selectNow,
            onEnterPane = ::enterPane,
            selectedGroup = selectedGroup,
        )
        Box(
            Modifier.weight(1f)
                .fillMaxHeight()
                .focusRequester(pane)
                .onKeyEvent { event ->
                    // No row used Left: go back to the selected group.
                    val left = event.key == Key.DirectionLeft
                    if (left && event.type == KeyEventType.KeyDown) selectedGroup.requestFocus()
                    left
                }
                .focusGroup()
        ) {
            if (current != null) {
                // Keyed, so that a group starts fresh. Its rows would otherwise reuse the nodes of
                // the group before, and a switch would slide to its state over some 17 frames and
                // the pane would keep the scroll of the old group.
                key(current.key) {
                    if (selfScrolling(current.key)) {
                        Column(Modifier.fillMaxSize()) { content(current.key) }
                    } else {
                        Column(
                            Modifier.fillMaxSize()
                                .verticalScroll(rememberScrollState())
                                .padding(AREA_PADDING),
                            verticalArrangement = Arrangement.spacedBy(ITEM_GAP),
                        ) {
                            content(current.key)
                        }
                    }
                }
            }
        }
    }
}

/**
 * The groups by title. The group that has focus and that the pane has not followed yet, [resting],
 * looks selected in place of [selected]. [onRest] is told when focus lands on a group, [onSelect]
 * when a tap or OK selects one, and Right calls [onEnterPane]. [selectedGroup] is where an entry of
 * the list by the D-pad lands.
 */
@OptIn(ExperimentalComposeUiApi::class)
@Composable
private fun <K> GroupList(
    groups: List<SettingsGroup<K>>,
    selected: K?,
    resting: State<K?>,
    onRest: (K) -> Unit,
    onSelect: (K) -> Unit,
    onEnterPane: () -> Unit,
    selectedGroup: FocusRequester,
) {
    // Read here, so that a focus move recomposes the list and not the pane.
    val shown = resting.value ?: selected
    Column(
        Modifier.width(BorealisDimens.navWidth(LocalFormFactor.current))
            .fillMaxHeight()
            .verticalScroll(rememberScrollState())
            .padding(AREA_PADDING)
            .selectableGroup()
            .onKeyEvent { event ->
                val right = event.key == Key.DirectionRight
                if (right && event.type == KeyEventType.KeyDown) onEnterPane()
                right
            }
            // Entering the list by the D-pad lands on the selected group, not the nearest. A
            // request for one group (a tap, an initial focus) is not an entry and stays on it.
            .focusProperties {
                enter = { direction ->
                    if (direction != FocusDirection.Enter) selectedGroup else FocusRequester.Default
                }
            }
            .focusGroup(),
        verticalArrangement = Arrangement.spacedBy(ITEM_GAP),
    ) {
        for (group in groups) {
            GroupEntry(
                group.title,
                selected = group.key == shown,
                onFocus = { onRest(group.key) },
                onSelect = { onSelect(group.key) },
                modifier =
                    if (group.key == selected) Modifier.focusRequester(selectedGroup) else Modifier,
            )
        }
    }
}

@Composable
private fun GroupEntry(
    title: String,
    selected: Boolean,
    onFocus: () -> Unit,
    onSelect: () -> Unit,
    modifier: Modifier,
) {
    val colors = LocalBorealisColors.current
    val shape = MaterialTheme.shapes.medium
    Box(
        modifier
            .fillMaxWidth()
            .liftedWhenFocused()
            .onFocusChanged { if (it.isFocused) onFocus() }
            .borealisFocusIndication(shape)
            .background(if (selected) colors.accentTint else Color.Transparent, shape)
            .clip(shape)
            .selectable(
                selected = selected,
                interactionSource = null,
                indication = BorealisPressIndication,
                role = Role.Tab,
                onClick = onSelect,
            )
            .padding(horizontal = GROUP_PADDING_HORIZONTAL, vertical = GROUP_PADDING_VERTICAL)
    ) {
        Text(
            title,
            color = if (selected) colors.accent else colors.ink2,
            style = MaterialTheme.typography.labelLarge,
        )
    }
}

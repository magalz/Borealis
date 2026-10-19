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
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
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
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch

/** One group of a [SettingsTwoPane]: the [key] that identifies it and its [title]. */
class SettingsGroup<out K>(val key: K, val title: String)

private val PANE_GAP = 12.dp
/** An outline sits 5dp outside its item, so neighbours need 10dp between them. */
internal val ITEM_GAP = 10.dp
internal val AREA_PADDING = 8.dp
private val GROUP_PADDING_HORIZONTAL = 12.dp
private val GROUP_PADDING_VERTICAL = 10.dp

/**
 * How long focus must rest on a group before the pane follows it. It is short: with the panes built
 * and stacked, showing one only changes which pane counts, so the wait only has to skip the groups
 * that a held key passes over.
 */
internal const val GROUP_SELECT_DELAY_MILLIS = 30L

/**
 * The settings of a TV or handheld in two panes: the [groups] by title in a list on the left, and
 * on the right whatever [content] shows for the group whose key is [selected]. The pane scrolls and
 * spaces its rows 10dp apart; [content] puts the rows of the group in it, with 8dp of padding
 * around them already given. A group for which [selfScrolling] is true brings its own scroll and
 * padding: its [content] fills the pane, which does not wrap it in a scroll that would give it an
 * unbounded height.
 *
 * Every group has a pane of its own, composed once and kept, and the panes are stacked: each is
 * laid out and drawn in the same bounds, and only the selected one is visible. Moving to a group
 * therefore composes, measures and records nothing; it changes which pane counts. The pane of the
 * selected group is composed at once, those of the others after the first frame, one in each frame
 * and the nearest to the selected group first. A pane that is not shown cannot be touched, takes no
 * focus (D-pad, Tab) and is left out of the semantics tree. A group for which [selfScrolling] is
 * true brings content and work of its own (the screen of Privacy, say), so its pane is composed
 * while the group is selected and not after. A pane opens at its top whenever it is shown.
 *
 * [selectDelayMillis] is how long focus must rest on a group before the pane follows it; a test
 * gives a longer wait, so that the time that its own steps take does not decide the outcome.
 *
 * Groups are identified by key, not by position, so a screen can hide a group on some devices
 * without moving the selection. A [selected] key that no group has falls back to the first group,
 * and with no groups the pane is empty.
 *
 * Selection follows focus once focus rests: the group that has focus looks selected at once, but
 * the pane, which costs more than a frame to build on a TV, changes and [onSelect] is called with
 * the key only after focus has stayed on the group for [GROUP_SELECT_DELAY_MILLIS], so moving over
 * several groups quickly selects the last one only. A tap or OK on a group selects it at once.
 * Focus that leaves the list for the pane drops a selection that was still waiting, so the pane
 * never changes under a focused row. Right from any group selects it if the pane has not followed
 * yet, and enters the pane at its first row once the group has arrived, however late [onSelect]
 * brings it; another key cancels that entry. Left from the pane, when no row uses the key, returns
 * to the selected group, whichever row it leaves.
 */
@Composable
fun <K> SettingsTwoPane(
    groups: List<SettingsGroup<K>>,
    selected: K,
    onSelect: (K) -> Unit,
    modifier: Modifier = Modifier,
    selfScrolling: (K) -> Boolean = { false },
    selectDelayMillis: Long = GROUP_SELECT_DELAY_MILLIS,
    content: @Composable ColumnScope.(K) -> Unit,
) {
    val current = groups.firstOrNull { it.key == selected } ?: groups.firstOrNull()
    val selectedGroup = remember { FocusRequester() }
    val pane = remember { FocusRequester() }
    val navigation =
        rememberGroupNavigation(current?.key, onSelect, selectDelayMillis, pane, selectedGroup)
    val composed = rememberComposedGroups(groups, current?.key, selfScrolling)
    Row(modifier.fillMaxSize(), horizontalArrangement = Arrangement.spacedBy(PANE_GAP)) {
        GroupList(groups, current?.key, navigation, selectedGroup)
        GroupPanes(
            groups,
            composed,
            current?.key,
            selfScrolling,
            pane,
            Modifier.weight(1f).fillMaxHeight().onKeyEvent { event ->
                // No row used Left: go back to the selected group.
                val left = event.key == Key.DirectionLeft
                if (left && event.type == KeyEventType.KeyDown) selectedGroup.requestFocus()
                left
            },
            content,
        )
    }
}

/**
 * What stands between focus on a group and the pane of a [SettingsTwoPane]: the group that focus
 * [resting] on and the pane has not followed yet, and the group that Right is [entering]. The
 * [currentKey] and [select] are those of the pane; [focusPane] puts focus into it.
 */
private class GroupNavigation<K>(
    private val currentKey: State<K?>,
    private val select: State<(K) -> Unit>,
    private val selectDelayMillis: Long,
    private val focusPane: () -> Unit,
) {
    /** The group that D-pad focus moved to and the pane has not followed yet. */
    val resting = mutableStateOf<K?>(null)

    /** The group whose pane Right asked to enter, while the caller has yet to bring it. */
    private val entering = mutableStateOf<K?>(null)

    /** Focus landed on the group [key]; the pane follows once focus stays. */
    fun onRest(key: K) {
        resting.value = if (key == currentKey.value) null else key
        entering.value = null
    }

    /** A tap or OK on the group [key], or the wait over: the pane follows at once. */
    fun selectNow(key: K) {
        resting.value = null
        entering.value = null
        if (key != currentKey.value) select.value(key)
    }

    /** A key other than Right: whatever it does, it is not entering the pane. */
    fun onOtherKey() {
        entering.value = null
    }

    /** Focus left the list, for the pane or elsewhere: a selection that waited is dropped. */
    fun onLeave() {
        resting.value = null
        entering.value = null
    }

    /** Right: enter the pane, after the group that focus rests on has become the selected one. */
    fun enterPane() {
        val group = resting.value
        when {
            group != null -> {
                selectNow(group)
                entering.value = group
            }
            // Right again, as a held key repeats it, while the group has yet to arrive.
            entering.value == null -> focusPane()
        }
    }

    /** Follows focus: selects a group that focus rested on, and enters a pane that has arrived. */
    suspend fun run(): Unit = coroutineScope {
        // A new group restarts the wait, so groups that focus passes over are never selected.
        launch {
            snapshotFlow { resting.value }
                .collectLatest { key ->
                    if (key != null) {
                        delay(selectDelayMillis)
                        selectNow(key)
                    }
                }
        }
        // Focus enters once the group has arrived, whenever the caller applies the selection. A
        // cancel of the entry, even during the frame, drops it.
        launch {
            snapshotFlow { entering.value != null && entering.value == currentKey.value }
                .collectLatest { arrived ->
                    if (arrived) {
                        // The rows are composed by now; the next frame has them placed.
                        withFrameNanos {}
                        entering.value = null
                        focusPane()
                    }
                }
        }
    }
}

@Composable
private fun <K> rememberGroupNavigation(
    currentKey: K?,
    onSelect: (K) -> Unit,
    selectDelayMillis: Long,
    pane: FocusRequester,
    selectedGroup: FocusRequester,
): GroupNavigation<K> {
    val focusManager = LocalFocusManager.current
    val currentKeyState = rememberUpdatedState(currentKey)
    val select = rememberUpdatedState(onSelect)
    val navigation =
        remember(focusManager, selectDelayMillis) {
            GroupNavigation(currentKeyState, select, selectDelayMillis) {
                // The search for a focus target to the right would pick the row nearest to the
                // group. With no focus to measure from, entering the pane picks its first row.
                focusManager.clearFocus()
                if (!pane.requestFocus()) selectedGroup.requestFocus()
            }
        }
    LaunchedEffect(navigation) { navigation.run() }
    return navigation
}

/**
 * The groups by title. The group that has focus and that the pane has not followed yet looks
 * selected in place of [selected]; [navigation] hears of focus, of the keys and of a selection.
 * [selectedGroup] is where an entry of the list by the D-pad lands.
 */
@OptIn(ExperimentalComposeUiApi::class)
@Composable
private fun <K> GroupList(
    groups: List<SettingsGroup<K>>,
    selected: K?,
    navigation: GroupNavigation<K>,
    selectedGroup: FocusRequester,
) {
    // Read here, so that a focus move recomposes the list and not the pane.
    val shown = navigation.resting.value ?: selected
    Column(
        Modifier.width(BorealisDimens.navWidth(LocalFormFactor.current))
            .fillMaxHeight()
            .verticalScroll(rememberScrollState())
            .padding(AREA_PADDING)
            .selectableGroup()
            .onKeyEvent { event ->
                val right = event.key == Key.DirectionRight
                if (event.type == KeyEventType.KeyDown) {
                    if (right) navigation.enterPane() else navigation.onOtherKey()
                }
                right
            }
            // Entering the list by the D-pad lands on the selected group, not the nearest. A
            // request for one group (a tap, an initial focus) is not an entry and stays on it.
            .focusProperties {
                enter = { direction ->
                    if (direction != FocusDirection.Enter) selectedGroup else FocusRequester.Default
                }
            }
            .onFocusChanged { if (!it.hasFocus) navigation.onLeave() }
            .focusGroup(),
        verticalArrangement = Arrangement.spacedBy(ITEM_GAP),
    ) {
        for (group in groups) {
            GroupEntry(
                group.title,
                selected = group.key == shown,
                onFocus = { navigation.onRest(group.key) },
                onSelect = { navigation.selectNow(group.key) },
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

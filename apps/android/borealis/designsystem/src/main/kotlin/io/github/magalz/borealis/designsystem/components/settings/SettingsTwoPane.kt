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
import androidx.compose.runtime.derivedStateOf
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
import io.github.magalz.borealis.designsystem.focus.initialFocus
import io.github.magalz.borealis.designsystem.focus.rememberInitialFocusState
import io.github.magalz.borealis.designsystem.theme.BorealisDimens
import io.github.magalz.borealis.designsystem.theme.LocalBorealisColors
import io.github.magalz.borealis.designsystem.theme.LocalFormFactor
import kotlinx.coroutines.flow.collectLatest

/** One group of a [SettingsTwoPane]: the [key] that identifies it and its [title]. */
class SettingsGroup<out K>(val key: K, val title: String)

private val PANE_GAP = 12.dp
/** An outline sits 5dp outside its item, so neighbours need 10dp between them. */
internal val ITEM_GAP = 10.dp
internal val AREA_PADDING = 8.dp
private val GROUP_PADDING_HORIZONTAL = 12.dp
/** Small enough that the ten groups of Settings, [ITEM_GAP] apart, fit a 540dp TV unscrolled. */
private val GROUP_PADDING_VERTICAL = 5.dp

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
 * With [focusOnOpen] the selected group takes focus when the panes open, once, and a TV or handheld
 * leaves touch mode, as the first item of Hosts does: opened from the rail, the screen takes focus
 * from the rail item that opened it.
 *
 * [selected] gives the key of the selected group. It is a function so that a caller that keeps the
 * selection in state does not read it while it composes, and so is not recomposed by a change.
 *
 * Groups are identified by key, not by position, so a screen can hide a group on some devices
 * without moving the selection. A [selected] key that no group has falls back to the first group,
 * and with no groups the pane is empty.
 *
 * Selection follows focus: [onSelect] is called with a group as soon as focus lands on it (not for
 * the group already selected), so the pane follows every step of a held key; a tap or OK on a group
 * selects it too. [onSelect] must apply the selection at once, as a state write: Right from a group
 * enters the pane at its first row one frame later, and another key before that cancels it. Left
 * from the pane, when no row uses the key, returns to the selected group, whichever row it leaves.
 */
@Composable
fun <K> SettingsTwoPane(
    groups: List<SettingsGroup<K>>,
    selected: () -> K,
    onSelect: (K) -> Unit,
    modifier: Modifier = Modifier,
    selfScrolling: (K) -> Boolean = { false },
    focusOnOpen: Boolean = false,
    content: @Composable ColumnScope.(K) -> Unit,
) {
    val selectedGroup = remember { FocusRequester() }
    val pane = remember { FocusRequester() }
    val groupsNow = rememberUpdatedState(groups)
    val selectedNow = rememberUpdatedState(selected)
    // The key of the group whose pane is current. It is state that the list, the layers and the
    // wrappers of the panes read where they need it, so that a switch recomposes none of this.
    val current = remember {
        derivedStateOf {
            val all = groupsNow.value
            all.firstOrNull { it.key == selectedNow.value() }?.key ?: all.firstOrNull()?.key
        }
    }
    val navigation = rememberGroupNavigation(current, onSelect, pane, selectedGroup)
    val composed = rememberComposedGroups(groups, current, selfScrolling)
    Row(modifier.fillMaxSize(), horizontalArrangement = Arrangement.spacedBy(PANE_GAP)) {
        GroupList(groups, current, navigation, selectedGroup, focusOnOpen)
        GroupPanes(
            groups,
            composed,
            current,
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
 * What stands between focus on a group and the pane of a [SettingsTwoPane]: a group that focus or a
 * tap reaches is [select]ed at once, and Right asks to enter the pane. The [currentKey] is that of
 * the pane; [focusPane] puts focus into it.
 */
private class GroupNavigation<K>(
    private val currentKey: State<K?>,
    private val onSelect: State<(K) -> Unit>,
    private val focusPane: () -> Unit,
) {
    /** Whether Right asked to enter the pane and the pane has yet to take focus. */
    private val entering = mutableStateOf(false)

    /** Focus landed on the group [key], or a tap or OK chose it: the pane follows at once. */
    fun select(key: K) {
        entering.value = false
        if (key != currentKey.value) onSelect.value(key)
    }

    /** A key other than Right: whatever it does, it is not entering the pane. */
    fun onOtherKey() {
        entering.value = false
    }

    /**
     * Right: enter the pane, a frame later. A selection that was just made has not recomposed the
     * panes yet, and the target of a request for the current pane is still on the old one, which
     * would take focus and clear it when it stops being current.
     */
    fun enterPane() {
        entering.value = true
    }

    /** Puts focus into the pane a frame after Right; a cancel, even during the frame, drops it. */
    suspend fun run() {
        snapshotFlow { entering.value }
            .collectLatest { enter ->
                if (enter) {
                    // The rows are composed by now; the next frame has them placed.
                    withFrameNanos {}
                    entering.value = false
                    focusPane()
                }
            }
    }
}

@Composable
private fun <K> rememberGroupNavigation(
    currentKey: State<K?>,
    onSelect: (K) -> Unit,
    pane: FocusRequester,
    selectedGroup: FocusRequester,
): GroupNavigation<K> {
    val focusManager = LocalFocusManager.current
    val select = rememberUpdatedState(onSelect)
    val navigation =
        remember(focusManager) {
            GroupNavigation(currentKey, select) {
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
 * The groups by title, the one that is [selected] marked. [navigation] hears of focus, of the keys
 * and of a tap. [selectedGroup] is where an entry of the list by the D-pad lands.
 */
@OptIn(ExperimentalComposeUiApi::class)
@Composable
private fun <K> GroupList(
    groups: List<SettingsGroup<K>>,
    selected: State<K?>,
    navigation: GroupNavigation<K>,
    selectedGroup: FocusRequester,
    focusOnOpen: Boolean,
) {
    val initialFocus = rememberInitialFocusState()
    // Read here, so that a switch recomposes the list and not the pane.
    val current = selected.value
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
            .focusGroup(),
        verticalArrangement = Arrangement.spacedBy(ITEM_GAP),
    ) {
        for (group in groups) {
            GroupEntry(
                group.title,
                selected = group.key == current,
                onSelect = { navigation.select(group.key) },
                modifier =
                    when {
                        group.key != current -> Modifier
                        focusOnOpen -> Modifier.initialFocus(selectedGroup, initialFocus)
                        else -> Modifier.focusRequester(selectedGroup)
                    },
            )
        }
    }
}

/** One group of the list. Focus on it selects it, as a tap or OK does. */
@Composable
private fun GroupEntry(title: String, selected: Boolean, onSelect: () -> Unit, modifier: Modifier) {
    val colors = LocalBorealisColors.current
    val shape = MaterialTheme.shapes.medium
    Box(
        modifier
            .fillMaxWidth()
            .liftedWhenFocused()
            .onFocusChanged { if (it.isFocused) onSelect() }
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

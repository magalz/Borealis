package io.github.magalz.borealis.designsystem.components.settings

import androidx.compose.foundation.ScrollState
import androidx.compose.foundation.focusGroup
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.Stable
import androidx.compose.runtime.State
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.runtime.snapshots.SnapshotStateList
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusDirection
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusProperties
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.Layout
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.semantics.clearAndSetSemantics

/**
 * The groups whose panes are composed: the [built] ones, which stay, and the [unbuilt] one that is
 * current and has no pane yet. Both are state, read by [GroupPanes] only, so that a pane that is
 * added recomposes the panes and not the screen above them.
 */
@Stable
internal class ComposedGroups<K>(
    private val built: SnapshotStateList<K>,
    private val unbuilt: State<K?>,
) {
    /** Whether the pane of the group [key] is composed. */
    operator fun contains(key: K): Boolean = key in built || key == unbuilt.value
}

/**
 * The groups whose panes are composed. The group that is [current] is composed at once. The others
 * follow after the first frame, one in each frame and the nearest to the current group first (the
 * next, the previous and outward), so that opening the screen does not wait for every pane and a
 * key pressed meanwhile is not held up by more than one. A pane that is composed stays composed,
 * except the pane of a group for which [selfScrolling] is true: it brings content and work of its
 * own (effects, a snackbar, dialogs) that must not run under another group, so it is composed while
 * it is current and not after.
 *
 * [current] is read as it changes, not while composing: this recomposes its caller only when the
 * current group has no pane yet, so a switch to a pane that is built recomposes nothing.
 */
@Composable
internal fun <K> rememberComposedGroups(
    groups: List<SettingsGroup<K>>,
    current: State<K?>,
    selfScrolling: (K) -> Boolean,
): ComposedGroups<K> {
    val composed = remember { mutableStateListOf<K>() }
    val selfScrollingNow by rememberUpdatedState(selfScrolling)
    // The current group with no pane yet: composed at once, then kept (or not, if it brings its
    // own content) by the effect below.
    val unbuilt = remember { derivedStateOf { current.value?.takeIf { it !in composed } } }
    LaunchedEffect(composed) {
        snapshotFlow { current.value }
            .collect { key ->
                if (key != null && !selfScrollingNow(key) && key !in composed) composed.add(key)
            }
    }
    LaunchedEffect(groups.map { it.key }) {
        withFrameNanos {}
        while (true) {
            val next = nearestToBuild(groups, current.value, composed, selfScrollingNow) ?: break
            composed.add(next)
            withFrameNanos {}
        }
    }
    return remember(composed) { ComposedGroups(composed, unbuilt) }
}

/**
 * The key of the group nearest to [current] whose pane is not built and may be: the next, the
 * previous, the one after the next, and so on. A group for which [selfScrolling] is true is not.
 */
private fun <K> nearestToBuild(
    groups: List<SettingsGroup<K>>,
    current: K?,
    built: List<K>,
    selfScrolling: (K) -> Boolean,
): K? {
    val from = groups.indexOfFirst { it.key == current }.coerceAtLeast(0)
    for (distance in 1 until groups.size) {
        for (index in intArrayOf(from + distance, from - distance)) {
            val group = groups.getOrNull(index) ?: continue
            if (group.key !in built && !selfScrolling(group.key)) return group.key
        }
    }
    return null
}

/**
 * The panes of the [composed] groups, stacked: each is measured, placed and drawn in the same
 * bounds. The pane of the [current] group is the one that can be seen; the others are invisible,
 * but laid out and recorded all the same. Which one is current is read only by the layers and the
 * small wrapper of each pane (see [GroupPane]), never while the panes compose, so a switch changes
 * the layer properties of two panes and recomposes their wrappers, and composes, measures and
 * records nothing else. A pane that is not current cannot be touched, has no focus target that a
 * key can reach and has no semantics node. [shownPane] is the target of a request for the current
 * pane, and is attached to it.
 */
@OptIn(ExperimentalComposeUiApi::class)
@Composable
internal fun <K> GroupPanes(
    groups: List<SettingsGroup<K>>,
    composed: ComposedGroups<K>,
    current: State<K?>,
    selfScrolling: (K) -> Boolean,
    shownPane: FocusRequester,
    modifier: Modifier,
    content: @Composable ColumnScope.(K) -> Unit,
) {
    Layout(
        content = {
            for (group in groups) {
                if (group.key in composed) {
                    key(group.key) {
                        GroupPane(group.key, current, selfScrolling(group.key), shownPane, content)
                    }
                }
            }
        },
        modifier =
            modifier
                .focusProperties {
                    // An entry by the D-pad or Tab lands in the current pane, whichever comes
                    // first. A request for one row is not an entry and stays on it.
                    enter = { direction ->
                        if (direction == FocusDirection.Enter) FocusRequester.Default else shownPane
                    }
                }
                .focusGroup(),
    ) { measurables, constraints ->
        val panes = measurables.map { it.measure(constraints) }
        layout(
            panes.maxOfOrNull { it.width } ?: constraints.minWidth,
            panes.maxOfOrNull { it.height } ?: constraints.minHeight,
        ) {
            for (pane in panes) pane.place(0, 0)
        }
    }
}

/**
 * The wrapper of the pane of one group. It is the only thing that follows which group is [current]
 * while composing, and it is small: modifiers and a call that is skipped, [PaneRows], so the rows
 * are not recomposed by a switch. While the pane is not current it is invisible and out of reach:
 * - it is moved out of the bounds of the panes, so that no touch lands on it; both this and the
 *   alpha are read in the layer, so they change without composing, measuring or recording;
 * - it has no semantics node, so a screen reader does not find it;
 * - focus cannot enter it, by the D-pad or Tab, and focus that it holds when it stops being
 *   current, by a tap on another group or a selection from outside, is cleared, so it gets no more
 *   keys;
 * - it goes back to its top, so that it opens there.
 *
 * Only the current pane takes [shownPane], the target of an entry into the panes.
 */
@OptIn(ExperimentalComposeUiApi::class)
@Composable
private fun <K> GroupPane(
    key: K,
    current: State<K?>,
    selfScrolling: Boolean,
    shownPane: FocusRequester,
    content: @Composable ColumnScope.(K) -> Unit,
) {
    val shown by remember(key) { derivedStateOf { current.value == key } }
    val scroll = remember { ScrollState(0) }
    LaunchedEffect(shown) { if (!shown) scroll.scrollTo(0) }
    var hasFocus by remember { mutableStateOf(false) }
    val focusManager = LocalFocusManager.current
    SideEffect { if (!shown && hasFocus) focusManager.clearFocus() }
    Box(
        Modifier.fillMaxSize()
            .graphicsLayer {
                val visible = current.value == key
                alpha = if (visible) 1f else 0f
                translationX = if (visible) 0f else -size.width * HIDDEN_OFFSET_WIDTHS
            }
            .onFocusChanged { hasFocus = it.hasFocus }
            .then(if (shown) Modifier.focusRequester(shownPane) else Modifier)
            .focusProperties { onEnter = { if (!shown) cancelFocusChange() } }
            .focusGroup()
            .then(if (shown) Modifier else Modifier.clearAndSetSemantics {})
    ) {
        PaneRows(key, selfScrolling, scroll, content)
    }
}

/**
 * The rows of one group. They scroll and are spaced, unless [selfScrolling] says that [content]
 * brings its own scroll and padding. It has nothing that changes with the selection, so a switch
 * skips it.
 */
@Composable
private fun <K> PaneRows(
    key: K,
    selfScrolling: Boolean,
    scroll: ScrollState,
    content: @Composable ColumnScope.(K) -> Unit,
) {
    if (selfScrolling) {
        Column(Modifier.fillMaxSize()) { content(key) }
    } else {
        Column(
            Modifier.fillMaxSize().verticalScroll(scroll).padding(AREA_PADDING),
            verticalArrangement = Arrangement.spacedBy(ITEM_GAP),
        ) {
            content(key)
        }
    }
}

/** How many widths of the panes a pane that is not shown is moved to the left, out of reach. */
private const val HIDDEN_OFFSET_WIDTHS = 2f

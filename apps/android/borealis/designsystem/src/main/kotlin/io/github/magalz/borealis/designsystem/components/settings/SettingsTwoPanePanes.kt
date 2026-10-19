package io.github.magalz.borealis.designsystem.components.settings

import androidx.compose.foundation.ScrollState
import androidx.compose.foundation.focusGroup
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
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
import androidx.compose.ui.layout.layoutId
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.semantics.clearAndSetSemantics

/** Tells the layout of the panes which child is the pane of a group, whatever the key is. */
private data class PaneId(val key: Any?)

/**
 * The keys of the groups whose panes are composed. The [selected] group is composed at once. The
 * others follow after the first frame, one in each frame and the nearest to the selected group
 * first (the next, the previous and outward), so that opening the screen does not wait for every
 * pane and a key pressed meanwhile is not held up by more than one. A pane that is composed stays
 * composed, except the pane of a group for which [selfScrolling] is true: it brings content and
 * work of its own (effects, a snackbar, dialogs) that must not run under another group, so it is
 * composed while it is selected and not after.
 */
@Composable
internal fun <K> rememberComposedGroups(
    groups: List<SettingsGroup<K>>,
    selected: K?,
    selfScrolling: (K) -> Boolean,
): List<K> {
    val composed = remember { mutableStateListOf<K>() }
    val selfScrollingNow by rememberUpdatedState(selfScrolling)
    val selectedNow by rememberUpdatedState(selected)
    // The list below composes the selected group at once; this keeps it once it is not selected.
    LaunchedEffect(selected) {
        if (selected != null && !selfScrollingNow(selected) && selected !in composed) {
            composed.add(selected)
        }
    }
    LaunchedEffect(groups.map { it.key }) {
        withFrameNanos {}
        while (true) {
            val next = nearestToBuild(groups, selectedNow, composed, selfScrollingNow) ?: break
            composed.add(next)
            withFrameNanos {}
        }
    }
    return if (selected == null || selected in composed) composed else composed + selected
}

/**
 * The key of the group nearest to [selected] whose pane is not built and may be: the next, the
 * previous, the one after the next, and so on. A group for which [selfScrolling] is true is not.
 */
private fun <K> nearestToBuild(
    groups: List<SettingsGroup<K>>,
    selected: K?,
    built: List<K>,
    selfScrolling: (K) -> Boolean,
): K? {
    val from = groups.indexOfFirst { it.key == selected }.coerceAtLeast(0)
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
 * bounds, with the pane of the [selected] group on top. The others are invisible, but laid out and
 * recorded all the same, so that showing one only changes which pane counts and composes, measures
 * and records nothing. A pane that is not shown cannot be touched, has no focus target that a key
 * can reach and has no semantics node; see [GroupPane]. [shownPane] is the target of a request for
 * the selected pane, and is attached to it.
 */
@OptIn(ExperimentalComposeUiApi::class)
@Composable
internal fun <K> GroupPanes(
    groups: List<SettingsGroup<K>>,
    composed: List<K>,
    selected: K?,
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
                        GroupPane(
                            group.key,
                            shown = group.key == selected,
                            selfScrolling = selfScrolling(group.key),
                            shownPane = shownPane,
                            content = content,
                        )
                    }
                }
            }
        },
        modifier =
            modifier
                .focusProperties {
                    // An entry by the D-pad or Tab lands in the selected pane, whichever comes
                    // first. A request for one row is not an entry and stays on it.
                    enter = { direction ->
                        if (direction == FocusDirection.Enter) FocusRequester.Default else shownPane
                    }
                }
                .focusGroup(),
    ) { measurables, constraints ->
        val panes = measurables.map { it.layoutId to it.measure(constraints) }
        layout(
            panes.maxOfOrNull { it.second.width } ?: constraints.minWidth,
            panes.maxOfOrNull { it.second.height } ?: constraints.minHeight,
        ) {
            for ((id, pane) in panes) pane.place(
                0,
                0,
                zIndex = if (id == PaneId(selected)) 1f else 0f,
            )
        }
    }
}

/**
 * The pane of one group. It scrolls and spaces its rows, unless [selfScrolling] says that its
 * [content] brings its own scroll and padding. While it is not [shown] it is invisible and taken
 * out of reach:
 * - it is moved out of the bounds of the panes, so that no touch lands on it;
 * - it has no semantics node, so a screen reader does not find it;
 * - focus cannot enter it, by the D-pad or Tab, and focus that it holds when it is hidden, by a tap
 *   on another group or a selection from outside, is cleared, so it gets no more keys;
 * - it goes back to its top, so that it opens there.
 *
 * Only the selected pane takes [shownPane], the target of an entry into the panes.
 */
@OptIn(ExperimentalComposeUiApi::class)
@Composable
private fun <K> GroupPane(
    key: K,
    shown: Boolean,
    selfScrolling: Boolean,
    shownPane: FocusRequester,
    content: @Composable ColumnScope.(K) -> Unit,
) {
    val scroll = remember { ScrollState(0) }
    LaunchedEffect(shown) { if (!shown) scroll.scrollTo(0) }
    var hasFocus by remember { mutableStateOf(false) }
    val focusManager = LocalFocusManager.current
    SideEffect { if (!shown && hasFocus) focusManager.clearFocus() }
    val pane =
        Modifier.layoutId(PaneId(key))
            .fillMaxSize()
            .graphicsLayer {
                alpha = if (shown) 1f else 0f
                translationX = if (shown) 0f else -size.width * HIDDEN_OFFSET_WIDTHS
            }
            .onFocusChanged { hasFocus = it.hasFocus }
            .then(if (shown) Modifier.focusRequester(shownPane) else Modifier)
            .focusProperties { onEnter = { if (!shown) cancelFocusChange() } }
            .focusGroup()
            .then(if (shown) Modifier else Modifier.clearAndSetSemantics {})
    if (selfScrolling) {
        Column(pane) { content(key) }
    } else {
        Column(
            pane.verticalScroll(scroll).padding(AREA_PADDING),
            verticalArrangement = Arrangement.spacedBy(ITEM_GAP),
        ) {
            content(key)
        }
    }
}

/** How many widths of the panes a pane that is not shown is moved to the left, out of reach. */
private const val HIDDEN_OFFSET_WIDTHS = 2f

package io.github.magalz.borealis.designsystem.components.settings

import androidx.compose.foundation.ScrollState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.Layout
import androidx.compose.ui.layout.layoutId
import androidx.compose.ui.semantics.clearAndSetSemantics

/** Tells the layout of the panes which child is the pane of a group, whatever the key is. */
private data class PaneId(val key: Any?)

/**
 * The keys of the groups whose panes are composed. The [selected] group is composed at once. The
 * others follow after the first frame, one in each frame, so that opening the screen does not wait
 * for every pane and a key pressed meanwhile is not held up by more than one. A group for which
 * [selfScrolling] is true brings content and work of its own, so it is composed only when it is
 * first selected. A pane that is composed stays composed.
 */
@Composable
internal fun <K> rememberComposedGroups(
    groups: List<SettingsGroup<K>>,
    selected: K?,
    selfScrolling: (K) -> Boolean,
): List<K> {
    val composed = remember { mutableStateListOf<K>() }
    val selfScrollingNow by rememberUpdatedState(selfScrolling)
    // The list below composes the selected group at once; this keeps it once it is not selected.
    LaunchedEffect(selected) {
        if (selected != null && selected !in composed) composed.add(selected)
    }
    LaunchedEffect(groups.map { it.key }) {
        withFrameNanos {}
        for (group in groups) {
            if (group.key !in composed && !selfScrollingNow(group.key)) {
                composed.add(group.key)
                withFrameNanos {}
            }
        }
    }
    return if (selected == null || selected in composed) composed else composed + selected
}

/**
 * The panes of the [composed] groups, one on top of the other, of which only the [selected] one is
 * measured, placed and drawn. The others stay composed, so that showing one costs a layout and a
 * draw and no composition, but they take no space and have no focus target and no semantics node to
 * be reached.
 */
@Composable
internal fun <K> GroupPanes(
    groups: List<SettingsGroup<K>>,
    composed: List<K>,
    selected: K?,
    selfScrolling: (K) -> Boolean,
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
                            content = content,
                        )
                    }
                }
            }
        },
        modifier = modifier,
    ) { measurables, constraints ->
        val shown =
            measurables.firstOrNull { it.layoutId == PaneId(selected) }?.measure(constraints)
        layout(shown?.width ?: constraints.minWidth, shown?.height ?: constraints.minHeight) {
            shown?.place(0, 0)
        }
    }
}

/**
 * The pane of one group. It scrolls and spaces its rows, unless [selfScrolling] says that its
 * [content] brings its own scroll and padding. It goes back to its top while it is hidden, so that
 * it opens there, and it has no semantics while it is hidden.
 */
@Composable
private fun <K> GroupPane(
    key: K,
    shown: Boolean,
    selfScrolling: Boolean,
    content: @Composable ColumnScope.(K) -> Unit,
) {
    val scroll = remember { ScrollState(0) }
    LaunchedEffect(shown) { if (!shown) scroll.scrollTo(0) }
    val pane = Modifier.layoutId(PaneId(key)).fillMaxSize()
    val semantics = if (shown) Modifier else Modifier.clearAndSetSemantics {}
    if (selfScrolling) {
        Column(pane.then(semantics)) { content(key) }
    } else {
        Column(
            pane.then(semantics).verticalScroll(scroll).padding(AREA_PADDING),
            verticalArrangement = Arrangement.spacedBy(ITEM_GAP),
        ) {
            content(key)
        }
    }
}

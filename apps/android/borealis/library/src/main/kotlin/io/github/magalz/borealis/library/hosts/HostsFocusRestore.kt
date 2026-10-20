package io.github.magalz.borealis.library.hosts

import androidx.compose.foundation.lazy.grid.LazyGridState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.saveable.Saver
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.onFocusChanged
import kotlinx.coroutines.flow.first

/**
 * The card of Hosts that had focus, or that a tap chose where a card cannot take focus: a [hostKey]
 * or a [continueKey]. It is a plain holder and not state, so that a D-pad move writes it without
 * recomposing a card; the screen reads it once, when its initial-focus guard starts. The navigation
 * takes Hosts out of the composition while Apps (or Add host) shows, so the key is saved with
 * [FocusedCardSaver].
 */
internal class FocusedCard(var key: String? = null)

internal val FocusedCardSaver =
    Saver<FocusedCard, String>(save = { it.key }, restore = { FocusedCard(it) })

internal fun hostKey(hostId: String) = "host:$hostId"

internal fun continueKey(hostId: String) = "continue:$hostId"

/** Keeps [key] in [card] while this element has focus. */
internal fun Modifier.keepsFocusIn(card: FocusedCard?, key: String): Modifier =
    if (card == null) this else onFocusChanged { if (it.isFocused) card.key = key }

/** The keys of the cards on the screen. */
internal fun HostsUiState.cardKeys(): List<String> =
    continueItems.map { continueKey(it.hostId) } + items.map { hostKey(it.id) }

/**
 * Scrolls [grid] to the card [key] once per [guard], when the card is not in view. A card that the
 * list moved out of view is not composed, so it could not take focus; one that is in view is left
 * where it is, so that the grid keeps the place it was left at.
 */
@Composable
internal fun ScrollToCard(grid: LazyGridState, state: HostsUiState, key: String?, guard: Any?) {
    LaunchedEffect(guard) {
        val card = key ?: return@LaunchedEffect
        val index = state.gridIndexOf(card)
        val shown = snapshotFlow { grid.layoutInfo.visibleItemsInfo }
        val inView = shown.first { it.isNotEmpty() }
        if (inView.none { it.index == index }) grid.scrollToItem(index)
    }
}

/**
 * The place of the card [key] in the grid, which has a section title before the Continue cards
 * (when there are any) and one before the hosts.
 */
private fun HostsUiState.gridIndexOf(key: String): Int {
    val continueIndex = continueItems.indexOfFirst { continueKey(it.hostId) == key }
    if (continueIndex >= 0) return 1 + continueIndex
    val hostsStart = if (continueItems.isEmpty()) 1 else continueItems.size + 2
    return hostsStart + items.indexOfFirst { hostKey(it.id) == key }
}

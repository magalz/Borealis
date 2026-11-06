package io.github.magalz.borealis.library.focus

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.focus.FocusRequester

/**
 * Asks for [focus], the requester of the card or tile of the host or app that opened the windows
 * over the Hosts or Apps screen, once the last of them has closed: the options, the Forget
 * confirmation or the details of a host; the actions, the quit confirmation inside them or the
 * details of an app. On the TV the focus was lost after Back (Sprint 14 UAT, U11); the tests assume
 * that the window took it from the screen and that Compose gave it back to the first card or tile,
 * not to the one that had it. Nothing happens when the opener is already gone ([openerListed] is
 * false), and nothing when the screen starts with a window already open: that is not a close. After
 * Forget or Hide the removal is asynchronous (the ViewModel launches it), so the opener may still
 * be listed when the window closes: the screen has made the opener the card or tile that takes its
 * place (`openerAfterForget`, `openerAfterHide`), so the one that asks for the focus is one that
 * stays. A card or tile that the grid does not compose (many hosts or apps, scrolled away while a
 * window was open) has no node for [focus]: Compose logs it and answers false, so nothing fails.
 */
@Composable
internal fun RestoreOpenerFocus(open: Boolean, openerListed: Boolean, focus: FocusRequester) {
    var wasOpen by remember { mutableStateOf(open) }
    LaunchedEffect(open) {
        if (wasOpen && !open && openerListed) focus.requestFocus()
        wasOpen = open
    }
}

/**
 * The element that takes the place of the first one that [matches] once it is gone: the next one in
 * the list, or the previous one when it was the last. It is null when nothing matches, and when no
 * other element is left (the focus then goes where Compose puts it).
 */
internal fun <T> List<T>.neighbourOf(matches: (T) -> Boolean): T? {
    val index = indexOfFirst(matches)
    return if (index < 0) null else getOrNull(index + 1) ?: getOrNull(index - 1)
}

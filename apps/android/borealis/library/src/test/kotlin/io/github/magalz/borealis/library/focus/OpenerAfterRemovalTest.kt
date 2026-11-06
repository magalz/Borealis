package io.github.magalz.borealis.library.focus

import com.google.common.truth.Truth.assertThat
import io.github.magalz.borealis.library.domain.Reachability
import io.github.magalz.borealis.library.hosts.HostAction
import io.github.magalz.borealis.library.hosts.HostItemUiState
import io.github.magalz.borealis.library.hosts.openerAfterForget
import org.junit.Test

/**
 * Which card or tile asks for the focus when the windows close after Forget or Hide. The screen
 * tests cover the opener that is removed (the next card, the previous one, none left); only here is
 * the opener that is not the one removed, which the screens never produce.
 */
class OpenerAfterRemovalTest {
    private val hosts = listOf("a", "b", "c").map(::host)

    @Test
    fun `the next card takes the focus when the removed card had it`() {
        assertThat(hosts.openerAfterForget("a", "a")).isEqualTo("b")
        assertThat(hosts.openerAfterForget("b", "b")).isEqualTo("c")
    }

    @Test
    fun `the previous card takes the focus when the removed card was the last`() {
        assertThat(hosts.openerAfterForget("c", "c")).isEqualTo("b")
    }

    @Test
    fun `another card that had the focus keeps it`() {
        assertThat(hosts.openerAfterForget("a", "c")).isEqualTo("c")
        assertThat(hosts.openerAfterForget("c", "a")).isEqualTo("a")
    }

    @Test
    fun `no card has the focus when none had it`() {
        assertThat(hosts.openerAfterForget("a", null)).isNull()
    }

    @Test
    fun `the opener stays when the removed card is not listed`() {
        assertThat(hosts.openerAfterForget("z", "z")).isEqualTo("z")
        assertThat(hosts.openerAfterForget("z", "b")).isEqualTo("b")
    }

    @Test
    fun `the opener stays when no other card is left`() {
        assertThat(listOf(host("a")).openerAfterForget("a", "a")).isEqualTo("a")
        assertThat(emptyList<HostItemUiState>().openerAfterForget("a", "a")).isEqualTo("a")
    }

    private fun host(id: String) =
        HostItemUiState(id, "Host $id", Reachability.ONLINE, true, null, HostAction.OPEN)
}

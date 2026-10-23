package io.github.magalz.borealis.hostmodel

import com.google.common.truth.Truth.assertThat
import com.limelight.computers.ComputerManagerListener
import com.limelight.nvstream.http.ComputerDetails
import org.junit.Test

/** Characterizes the listener of the inherited host model: Kotlin builds it as a lambda. */
class ComputerManagerListenerTest {
    @Test
    fun `a lambda is a listener that gets the updated details`() {
        val seen = mutableListOf<ComputerDetails>()
        val listener = ComputerManagerListener { details -> seen += details }
        val details = ComputerDetails().also { it.uuid = "uuid-1" }

        listener.notifyComputerUpdated(details)

        assertThat(seen).containsExactly(details)
        assertThat(seen.single()).isSameInstanceAs(details)
    }
}

package io.github.magalz.borealis.logging.testing

import com.google.common.truth.Truth.assertThat
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.StandardTestDispatcher
import org.junit.Assert.assertThrows
import org.junit.Rule
import org.junit.Test
import org.junit.runner.Description
import org.junit.runners.model.Statement

class MainDispatcherRuleTest {
    private val dispatcher = StandardTestDispatcher()

    @get:Rule val main = MainDispatcherRule(dispatcher)

    @Test
    fun `Main runs on the test dispatcher, so its work waits for the test to advance time`() {
        var ran = false

        CoroutineScope(Dispatchers.Main).launch { ran = true }

        assertThat(ran).isFalse()
        dispatcher.scheduler.runCurrent()
        assertThat(ran).isTrue()
    }

    @Test
    fun `the rule without a dispatcher runs Main on a standard one`() {
        var ran = false
        val body =
            object : Statement() {
                override fun evaluate() {
                    CoroutineScope(Dispatchers.Main).launch { ran = true }
                    assertThat(ran).isFalse()
                }
            }

        MainDispatcherRule().apply(body, Description.EMPTY).evaluate()
    }
}

class MainDispatcherRuleResetTest {
    @Test
    fun `Main is given up when the test ends`() {
        val body =
            object : Statement() {
                override fun evaluate() = Unit
            }

        MainDispatcherRule().apply(body, Description.EMPTY).evaluate()

        // Without the test dispatcher and without Android, there is no Main to run on.
        assertThrows(IllegalStateException::class.java) {
            CoroutineScope(Dispatchers.Main).launch {}
        }
    }
}

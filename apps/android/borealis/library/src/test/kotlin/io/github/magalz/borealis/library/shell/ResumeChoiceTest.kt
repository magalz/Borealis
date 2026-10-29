package io.github.magalz.borealis.library.shell

import com.google.common.truth.Truth.assertThat
import org.junit.Test

class ResumeChoiceTest {
    private val choice = ResumeChoice()

    @Test
    fun `nothing is chosen until the stream remembers a choice`() {
        assertThat(choice.take("desk", 7)).isFalse()
    }

    @Test
    fun `a remembered choice is taken once`() {
        choice.remember("desk", 7)

        assertThat(choice.take("desk", 7)).isTrue()
        assertThat(choice.take("desk", 7)).isFalse()
    }

    @Test
    fun `a choice for another app or host is not taken, and taking it ends the choice`() {
        choice.remember("desk", 7)
        assertThat(choice.take("desk", 8)).isFalse()
        assertThat(choice.take("desk", 7)).isFalse()

        choice.remember("desk", 7)
        assertThat(choice.take("den", 7)).isFalse()
        assertThat(choice.take("desk", 7)).isFalse()
    }

    @Test
    fun `a later choice replaces the earlier one`() {
        choice.remember("desk", 7)
        choice.remember("den", 9)

        assertThat(choice.take("desk", 7)).isFalse()
        choice.remember("den", 9)
        assertThat(choice.take("den", 9)).isTrue()
    }

    @Test
    fun `clearing forgets the choice`() {
        choice.remember("desk", 7)

        choice.clear()

        assertThat(choice.take("desk", 7)).isFalse()
    }
}

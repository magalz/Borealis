package io.github.magalz.borealis.host.apps

import com.google.common.truth.Truth.assertThat
import org.junit.Test

class DesktopAppTest {
    @Test
    fun `the title Desktop is Desktop`() {
        assertThat(isDesktop("Desktop")).isTrue()
    }

    @Test
    fun `case and the spaces around the title do not matter`() {
        assertThat(isDesktop(" desktop ")).isTrue()
        assertThat(isDesktop("DESKTOP")).isTrue()
        assertThat(isDesktop("\tDesktop\n")).isTrue()
    }

    @Test
    fun `a title that only contains Desktop is another app`() {
        assertThat(isDesktop("Desktop 2")).isFalse()
        assertThat(isDesktop("My Desktop")).isFalse()
        assertThat(isDesktop("Low Res Desktop")).isFalse()
        assertThat(isDesktop("Remote Desktop")).isFalse()
    }

    @Test
    fun `an unknown or blank title is not Desktop`() {
        assertThat(isDesktop(null)).isFalse()
        assertThat(isDesktop("")).isFalse()
        assertThat(isDesktop("   ")).isFalse()
    }
}

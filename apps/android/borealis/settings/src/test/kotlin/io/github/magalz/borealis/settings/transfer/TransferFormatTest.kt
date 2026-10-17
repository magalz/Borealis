package io.github.magalz.borealis.settings.transfer

import com.google.common.truth.Truth.assertThat
import org.junit.Test

class TransferFormatTest {
    @Test
    fun `the countdown rounds up to whole seconds as minutes and seconds`() {
        assertThat(formatCountdown(300_000)).isEqualTo("5:00")
        assertThat(formatCountdown(271_200)).isEqualTo("4:32")
        assertThat(formatCountdown(61_000)).isEqualTo("1:01")
        assertThat(formatCountdown(9_000)).isEqualTo("0:09")
        assertThat(formatCountdown(1)).isEqualTo("0:01")
        assertThat(formatCountdown(0)).isEqualTo("0:00")
    }

    @Test
    fun `a negative remaining time reads as zero`() {
        assertThat(formatCountdown(-500)).isEqualTo("0:00")
    }
}

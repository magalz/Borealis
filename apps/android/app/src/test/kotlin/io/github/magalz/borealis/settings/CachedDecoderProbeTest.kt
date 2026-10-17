package io.github.magalz.borealis.settings

import com.google.common.truth.Truth.assertThat
import io.github.magalz.borealis.settings.device.DecoderLimits
import org.junit.Test

class CachedDecoderProbeTest {
    private val asked = mutableListOf<String>()
    private val answers = mutableMapOf<String, DecoderLimits?>()
    private val cached = CachedDecoderProbe { mime ->
        asked += mime
        answers[mime]
    }

    @Test
    fun `a codec is probed once`() {
        answers[DecoderProbe.AVC] = DecoderLimits(4096, 2304)

        val first = cached.limits(DecoderProbe.AVC)
        val second = cached.limits(DecoderProbe.AVC)

        assertThat(first).isEqualTo(DecoderLimits(4096, 2304))
        assertThat(second).isEqualTo(first)
        assertThat(asked).containsExactly(DecoderProbe.AVC)
    }

    @Test
    fun `a codec with no decoder is probed once too`() {
        assertThat(cached.limits(DecoderProbe.HEVC)).isNull()
        assertThat(cached.limits(DecoderProbe.HEVC)).isNull()

        assertThat(asked).containsExactly(DecoderProbe.HEVC)
    }

    @Test
    fun `each codec has its own answer`() {
        answers[DecoderProbe.AVC] = DecoderLimits(1920, 1088)
        answers[DecoderProbe.HEVC] = DecoderLimits(8192, 4320)

        assertThat(cached.limits(DecoderProbe.AVC)).isEqualTo(DecoderLimits(1920, 1088))
        assertThat(cached.limits(DecoderProbe.HEVC)).isEqualTo(DecoderLimits(8192, 4320))
        assertThat(asked).containsExactly(DecoderProbe.AVC, DecoderProbe.HEVC)
    }
}

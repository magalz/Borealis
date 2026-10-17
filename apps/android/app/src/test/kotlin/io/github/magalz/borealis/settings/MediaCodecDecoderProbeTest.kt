package io.github.magalz.borealis.settings

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import com.google.common.truth.Truth.assertThat
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class MediaCodecDecoderProbeTest {
    private val context: Context = ApplicationProvider.getApplicationContext()

    @Test
    fun `a device with no decoder gives no limits`() {
        assertThat(MediaCodecDecoderProbe(context).limits(DecoderProbe.AVC)).isNull()
    }
}

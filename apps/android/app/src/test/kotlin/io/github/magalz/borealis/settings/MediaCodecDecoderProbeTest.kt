package io.github.magalz.borealis.settings

import android.content.Context
import android.media.MediaCodecInfo
import android.media.MediaFormat
import androidx.test.core.app.ApplicationProvider
import com.google.common.truth.Truth.assertThat
import io.github.magalz.borealis.settings.device.DecoderLimits
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.shadows.MediaCodecInfoBuilder
import org.robolectric.shadows.ShadowMediaCodecList

@RunWith(RobolectricTestRunner::class)
class MediaCodecDecoderProbeTest {
    private val context: Context = ApplicationProvider.getApplicationContext()
    private val probe = MediaCodecDecoderProbe(context)

    /**
     * The highest level of the codec, so that the framework does not cap the sizes at 384 pixels.
     */
    private fun highestLevel(mime: String) =
        MediaCodecInfo.CodecProfileLevel().apply {
            if (mime == "video/hevc") {
                profile = MediaCodecInfo.CodecProfileLevel.HEVCProfileMain
                level = MediaCodecInfo.CodecProfileLevel.HEVCMainTierLevel62
            } else {
                profile = MediaCodecInfo.CodecProfileLevel.AVCProfileHigh
                level = MediaCodecInfo.CodecProfileLevel.AVCLevel52
            }
        }

    /**
     * A hardware decoder of [mime] that reports [sizeRange], as `WxH-WxH` (smallest to largest).
     */
    private fun decoder(mime: String, sizeRange: String): MediaCodecInfo {
        val format =
            MediaFormat().apply {
                setString(MediaFormat.KEY_MIME, mime)
                setString("size-range", sizeRange)
                setString("block-count-range", "1-100000")
                setString("blocks-per-second-range", "1-10000000")
            }
        val capabilities =
            MediaCodecInfoBuilder.CodecCapabilitiesBuilder.newBuilder()
                .setMediaFormat(format)
                .setIsEncoder(false)
                .setProfileLevels(arrayOf(highestLevel(mime)))
                .setColorFormats(intArrayOf())
                .build()
        return MediaCodecInfoBuilder.newBuilder()
            .setName("c2.test.${mime.substringAfter('/')}.decoder")
            .setIsEncoder(false)
            .setIsSoftwareOnly(false)
            .setIsHardwareAccelerated(true)
            .setCapabilities(capabilities)
            .build()
    }

    @Test
    fun `a device with no decoder gives no limits`() {
        assertThat(probe.limits(DecoderProbe.AVC)).isNull()
    }

    @Test
    fun `the limits are the largest width and height that the decoder reports`() {
        ShadowMediaCodecList.addCodec(decoder("video/avc", "64x64-4096x2304"))

        assertThat(probe.limits(DecoderProbe.AVC)).isEqualTo(DecoderLimits(4096, 2304))
    }

    @Test
    fun `each codec has its own decoder`() {
        ShadowMediaCodecList.addCodec(decoder("video/avc", "64x64-1920x1088"))
        ShadowMediaCodecList.addCodec(decoder("video/hevc", "64x64-8192x4320"))

        assertThat(probe.limits(DecoderProbe.AVC)).isEqualTo(DecoderLimits(1920, 1088))
        assertThat(probe.limits(DecoderProbe.HEVC)).isEqualTo(DecoderLimits(8192, 4320))
    }

    @Test
    fun `a decoder whose smallest width is above 1280 is ignored`() {
        ShadowMediaCodecList.addCodec(decoder("video/avc", "1920x1080-4096x2304"))

        assertThat(probe.limits(DecoderProbe.AVC)).isNull()
    }

    @Test
    fun `a decoder whose smallest width is exactly 1280 counts`() {
        ShadowMediaCodecList.addCodec(decoder("video/avc", "1280x720-4096x2304"))

        assertThat(probe.limits(DecoderProbe.AVC)).isEqualTo(DecoderLimits(4096, 2304))
    }

    @Test
    fun `a decoder that cannot report its capabilities gives no limits`() {
        // The finder returns a decoder of another codec, so getCapabilitiesForType throws.
        val avcOnly = decoder("video/avc", "64x64-4096x2304")
        val probe = MediaCodecDecoderProbe { avcOnly }

        assertThat(probe.limits(DecoderProbe.HEVC)).isNull()
    }
}

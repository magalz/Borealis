package io.github.magalz.borealis.diagnostics.bundle

import android.content.Context
import android.hardware.display.DisplayManager
import android.media.MediaCodecInfo
import android.media.MediaCodecInfo.CodecProfileLevel as P
import android.media.MediaFormat
import android.view.Display
import android.view.Display.HdrCapabilities as Hdr
import androidx.test.core.app.ApplicationProvider
import com.google.common.truth.Truth.assertThat
import org.junit.After
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf
import org.robolectric.shadows.MediaCodecInfoBuilder
import org.robolectric.shadows.ShadowMediaCodecList

@RunWith(RobolectricTestRunner::class)
class SystemDecoderSourceTest {
    private val context: Context = ApplicationProvider.getApplicationContext()

    @After
    fun tearDown() {
        ShadowMediaCodecList.reset()
    }

    private fun codec(
        name: String,
        mime: String,
        encoder: Boolean,
        hardware: Boolean,
        vararg profiles: Int,
    ): MediaCodecInfo {
        val capabilities =
            MediaCodecInfoBuilder.CodecCapabilitiesBuilder.newBuilder()
                .setMediaFormat(MediaFormat.createVideoFormat(mime, 1920, 1080))
                .setIsEncoder(encoder)
                .setColorFormats(intArrayOf(MediaCodecInfo.CodecCapabilities.COLOR_FormatSurface))
                .setProfileLevels(
                    profiles
                        .map { profile -> P().apply { this.profile = profile; level = 0 } }
                        .toTypedArray()
                )
                .build()
        return MediaCodecInfoBuilder.newBuilder()
            .setName(name)
            .setIsEncoder(encoder)
            .setIsHardwareAccelerated(hardware)
            .setIsSoftwareOnly(!hardware)
            .setCapabilities(capabilities)
            .build()
    }

    @Test
    fun `lists the video decoders with their profiles and hardware flag, and not encoders`() {
        ShadowMediaCodecList.addCodec(
            codec("c2.acme.hevc.decoder", "video/hevc", false, true, P.HEVCProfileMain, P.HEVCProfileMain10)
        )
        ShadowMediaCodecList.addCodec(
            codec("c2.android.avc.decoder", "video/avc", false, false, P.AVCProfileHigh)
        )
        ShadowMediaCodecList.addCodec(
            codec("c2.acme.hevc.encoder", "video/hevc", true, true, P.HEVCProfileMain)
        )

        val decoders = SystemDecoderSource(context).decoders().associateBy { it.name }

        assertThat(decoders.keys).containsExactly("c2.acme.hevc.decoder", "c2.android.avc.decoder")
        assertThat(decoders.getValue("c2.acme.hevc.decoder"))
            .isEqualTo(
                VideoDecoder(
                    "c2.acme.hevc.decoder",
                    "video/hevc",
                    hardware = true,
                    profiles = listOf(P.HEVCProfileMain, P.HEVCProfileMain10),
                )
            )
        assertThat(decoders.getValue("c2.android.avc.decoder").hardware).isFalse()
    }

    @Test
    fun `a device without codecs gives an empty list`() {
        assertThat(SystemDecoderSource(context).decoders()).isEmpty()
    }

    @Test
    fun `reports the HDR types of the default display`() {
        val display =
            context.getSystemService(DisplayManager::class.java).getDisplay(Display.DEFAULT_DISPLAY)
        shadowOf(display)
            .setDisplayHdrCapabilities(0, 1000f, 500f, 0.1f, Hdr.HDR_TYPE_HDR10, Hdr.HDR_TYPE_HLG)

        assertThat(SystemDecoderSource(context).displayHdrTypes())
            .containsExactly(Hdr.HDR_TYPE_HDR10, Hdr.HDR_TYPE_HLG)
    }
}

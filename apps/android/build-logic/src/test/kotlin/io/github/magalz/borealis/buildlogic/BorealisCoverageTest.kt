package io.github.magalz.borealis.buildlogic

import com.google.common.truth.Truth.assertThat
import java.util.regex.Pattern
import org.junit.Test

class BorealisCoverageTest {
    /**
     * Kover 0.9.11 compiles every class filter with its own `wildcardsToRegex` (`*` becomes `.*`,
     * so it crosses dots; `?` becomes `.`; every other character is literal) and drops a class when
     * one of the patterns matches its whole dotted binary name (`Matcher.matches` in
     * `ClassNameUtil.matchesPatterns`). The test calls that function, so it uses the rule Kover
     * uses.
     */
    private val wildcardsToRegex =
        Class.forName("kotlinx.kover.features.jvm.impl.WildcardsKt")
            .getMethod("wildcardsToRegex", String::class.java)

    private val patterns =
        BorealisCoverage.EXCLUDED_CLASSES.map {
            Pattern.compile(wildcardsToRegex.invoke(null, it) as String)
        }

    private fun isExcluded(className: String) = patterns.any { it.matcher(className).matches() }

    @Test
    fun `the Java that sprint 14 converts is counted`() {
        val remainingJava =
            listOf(
                "com.limelight.Game",
                "com.limelight.Game\$1",
                "com.limelight.binding.video.MediaCodecDecoderRenderer",
                "com.limelight.binding.video.MediaCodecHelper",
                "com.limelight.binding.audio.AndroidAudioRenderer",
                "com.limelight.nvstream.jni.MoonBridge",
                "com.limelight.nvstream.jni.MoonBridge\$AudioConfiguration",
            )

        assertThat(remainingJava.filter(::isExcluded)).isEmpty()
    }

    @Test
    fun `no exclusion names a com limelight class`() {
        val limelightEntries =
            BorealisCoverage.EXCLUDED_CLASSES.filter { it.startsWith("com.limelight") }

        assertThat(limelightEntries).isEmpty()
    }

    @Test
    fun `the input code that sprint 13 converts is counted`() {
        val input =
            listOf(
                "com.limelight.binding.input.ControllerHandler",
                "com.limelight.binding.input.ControllerHandler\$InputDeviceContext",
                "com.limelight.binding.input.KeyboardTranslator",
                "com.limelight.binding.input.capture.InputCaptureManager",
                "com.limelight.binding.input.driver.UsbDriverService",
                "com.limelight.binding.input.touch.RelativeTouchContext",
                "com.limelight.binding.input.virtual_controller.VirtualController",
            )

        assertThat(input.filter(::isExcluded)).isEmpty()
    }

    @Test
    fun `the converted network, host and support code is counted`() {
        val converted =
            listOf(
                "com.limelight.nvstream.http.NvHTTP",
                "com.limelight.computers.ComputerManagerService",
                "com.limelight.ShortcutTrampoline",
                "com.limelight.preferences.PreferenceConfiguration",
                "com.limelight.LimeLog",
                "com.limelight.binding.PlatformBinding",
                "com.limelight.binding.crypto.AndroidCryptoProvider",
            )

        assertThat(converted.filter(::isExcluded)).isEmpty()
    }

    @Test
    fun `generated classes in com limelight stay out of the report`() {
        val generated =
            listOf(
                "com.limelight.BuildConfig",
                "com.limelight.R",
                "com.limelight.R\$string",
                "com.limelight.computers.Hilt_ComputerManagerService",
                "com.limelight.computers.ComputerManagerService_GeneratedInjector",
            )

        assertThat(generated.filterNot(::isExcluded)).isEmpty()
    }
}

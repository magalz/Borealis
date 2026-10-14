package io.github.magalz.borealis.host.launch

import com.google.common.truth.Truth.assertThat
import org.junit.Test

class LaunchFrameRateTest {
    /** The expression of upstream `NvHTTP.launchApp`, written the way Java evaluates it. */
    private fun upstream(isGfe: Boolean, launchFps: Int): Int =
        if (isGfe && launchFps > 60) 0 else launchFps

    @Test
    fun `without the native rules the upstream expression applies to every input`() {
        val unavailable = FakeHostRules()
        for (isGfe in listOf(true, false)) {
            for (launchFps in listOf(-1, 0, 1, 30, 59, 60, 61, 90, 120, 144, 240)) {
                for (streamFps in listOf(0, 59, 60, 120)) {
                    assertThat(LaunchFrameRate.forLaunch(unavailable, isGfe, launchFps, streamFps))
                        .isEqualTo(upstream(isGfe, launchFps))
                }
            }
        }
    }

    @Test
    fun `the rate of the native rules wins over the upstream expression`() {
        val rules = FakeHostRules(fps = { _, _, streamFps -> streamFps })

        assertThat(LaunchFrameRate.forLaunch(rules, isGfe = false, launchFps = 60, streamFps = 59))
            .isEqualTo(59)
    }

    @Test
    fun `the rules receive the host kind and both rates unchanged`() {
        val seen = mutableListOf<Triple<Boolean, Int, Int>>()
        val rules =
            FakeHostRules(
                fps = { gfe, launch, stream ->
                    seen += Triple(gfe, launch, stream)
                    7
                }
            )

        assertThat(LaunchFrameRate.forLaunch(rules, true, 120, 59)).isEqualTo(7)
        assertThat(seen).containsExactly(Triple(true, 120, 59))
    }

    @Test
    fun `the public entry point uses upstream behaviour when the native library cannot load`() {
        // Unit tests run without libborealis_jni.so, so NativeHostRules answers null.
        assertThat(LaunchFrameRate.forLaunch(true, 120, 59)).isEqualTo(0)
        assertThat(LaunchFrameRate.forLaunch(false, 120, 59)).isEqualTo(120)
    }
}

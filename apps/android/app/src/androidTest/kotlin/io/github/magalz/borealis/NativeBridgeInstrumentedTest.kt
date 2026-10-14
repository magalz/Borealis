package io.github.magalz.borealis

import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.google.common.truth.Truth.assertThat
import io.github.magalz.borealis.nativecore.BorealisCore
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class NativeBridgeInstrumentedTest {
    @Test
    fun ndkBuildBridgeSeesTheCoreProjectVersion() {
        val expected =
            InstrumentationRegistry.getArguments().getString("expectedCoreVersion").orEmpty()

        assertThat(expected).isNotEmpty()
        assertThat(NativeBridge.coreVersion()).isEqualTo(expected)
    }

    @Test
    fun bridgeAndModuleApiAgree() {
        assertThat(NativeBridge.coreVersion()).isNotNull()
        assertThat(NativeBridge.coreVersion()).isEqualTo(BorealisCore.version())
    }
}

package io.github.magalz.borealis.nativecore

import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.google.common.truth.Truth.assertThat
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class BorealisCoreInstrumentedTest {
    @Test
    fun versionIsTheCoreProjectVersion() {
        val expected =
            InstrumentationRegistry.getArguments().getString("expectedCoreVersion").orEmpty()

        assertThat(expected).isNotEmpty()
        assertThat(BorealisCore.version()).isEqualTo(expected)
    }
}

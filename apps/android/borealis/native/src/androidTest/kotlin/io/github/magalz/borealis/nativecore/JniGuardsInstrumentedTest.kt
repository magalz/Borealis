package io.github.magalz.borealis.nativecore

import androidx.test.ext.junit.runners.AndroidJUnit4
import com.google.common.truth.Truth.assertThat
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Calls [CoreJni] directly to exercise the C++ length guards that the Kotlin pre-checks normally
 * mask: an over-long input must come back as `BOREALIS_ERR_INPUT_TOO_LARGE` (-2), never as a
 * out-of-bounds copy.
 */
@RunWith(AndroidJUnit4::class)
class JniGuardsInstrumentedTest {
    @Before
    fun loadLibrary() {
        // BorealisCore.version() loads libborealis_jni.so through the shared NativeLibrary.
        assertThat(BorealisCore.version()).isNotNull()
    }

    @Test
    fun parseCapsRejectsAnAppVersionOver64Bytes() {
        val result = CoreJni.parseCaps(ByteArray(65), 0)

        assertThat(result).isNotNull()
        assertThat(result!![0]).isEqualTo(-2)
    }

    @Test
    fun decideLaunchRejectsAMessageOver256Bytes() {
        val result = CoreJni.decideLaunch(0, 409, ByteArray(257), 0)

        assertThat(result).isNotNull()
        assertThat(result!![0]).isEqualTo(-2)
    }

    @Test
    fun classifyPairingRejectsAMessageOver256Bytes() {
        val result = CoreJni.classifyPairing(400, ByteArray(257))

        assertThat(result).isNotNull()
        assertThat(result!![0]).isEqualTo(-2)
    }
}

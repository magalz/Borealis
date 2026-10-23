package io.github.magalz.borealis.crypto

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import com.google.common.truth.Truth.assertThat
import com.limelight.binding.PlatformBinding
import com.limelight.binding.crypto.AndroidCryptoProvider
import java.io.File
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

/** The seam that `Game` and the host connections use to get the client identity. */
@RunWith(RobolectricTestRunner::class)
class PlatformBindingTest {
    private val context: Context = ApplicationProvider.getApplicationContext()

    @Test
    fun `the binding gives a provider that holds the client identity of the app`() {
        val provider = PlatformBinding.getCryptoProvider(context)

        assertThat(provider).isInstanceOf(AndroidCryptoProvider::class.java)
        val certificate = provider.clientCertificate
        assertThat(certificate?.subjectX500Principal?.name).isEqualTo("CN=NVIDIA GameStream Client")
        assertThat(provider.clientPrivateKey).isNotNull()
        assertThat(provider.pemEncodedClientCertificate).isEqualTo(File(context.filesDir, "client.crt").readBytes())
        assertThat(File(context.filesDir, "client.key").exists()).isTrue()
    }

    @Test
    fun `every call gives a new provider over the same identity`() {
        val first = PlatformBinding.getCryptoProvider(context)
        val second = PlatformBinding.getCryptoProvider(context)

        assertThat(second).isNotSameInstanceAs(first)
        assertThat(second.clientCertificate?.encoded).isEqualTo(first.clientCertificate?.encoded)
    }
}

package io.github.magalz.borealis.crypto

import com.google.common.truth.Truth.assertThat
import com.limelight.nvstream.http.PairingManager.PairState
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

/**
 * The pairing reads the certificate of the host with the default `X.509` factory of the platform,
 * not with Bouncy Castle: the certificate that NvHTTP pins is compared with the one of the TLS
 * session, which comes from the platform too.
 */
@RunWith(RobolectricTestRunner::class)
class PairingProvidersTest {
    @Test
    fun `the certificate of the host is read by the platform and not by Bouncy Castle`() {
        FakePairingHost().use { host ->
            val rig = PairingRig(host)

            assertThat(rig.pair()).isEqualTo(PairState.PAIRED)

            assertThat(rig.pairedCert?.javaClass?.name).doesNotContain("bouncycastle")
        }
    }
}

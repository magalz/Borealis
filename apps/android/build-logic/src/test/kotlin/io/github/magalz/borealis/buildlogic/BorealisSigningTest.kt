package io.github.magalz.borealis.buildlogic

import com.google.common.truth.Truth.assertThat
import java.io.File
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

class BorealisSigningTest {
    @get:Rule val temp = TemporaryFolder()

    private val fullEnv =
        mapOf(
            "BOREALIS_KEYSTORE_FILE" to "release.jks",
            "BOREALIS_KEYSTORE_PASSWORD" to "store-secret",
            "BOREALIS_KEY_ALIAS" to "borealis",
            "BOREALIS_KEY_PASSWORD" to "key-secret",
        )

    @Test
    fun `nothing configured leaves release unsigned`() {
        assertThat(BorealisSigning.load(temp.root, emptyMap())).isNull()
    }

    @Test
    fun `environment variables are used and a relative keystore is resolved against the root`() {
        val signing = BorealisSigning.load(temp.root, fullEnv)

        assertThat(signing?.storeFile).isEqualTo(File(temp.root, "release.jks"))
        assertThat(signing?.keyAlias).isEqualTo("borealis")
    }

    @Test
    fun `keystore properties take precedence over the environment`() {
        File(temp.root, "keystore.properties")
            .writeText(
                """
                storeFile=other.jks
                storePassword=p1
                keyAlias=alias
                keyPassword=p2
                """
                    .trimIndent()
            )

        val signing = BorealisSigning.load(temp.root, fullEnv)

        assertThat(signing?.storeFile).isEqualTo(File(temp.root, "other.jks"))
        assertThat(signing?.storePassword).isEqualTo("p1")
    }

    @Test
    fun `incomplete configuration leaves release unsigned`() {
        assertThat(BorealisSigning.load(temp.root, fullEnv - "BOREALIS_KEY_PASSWORD")).isNull()
    }
}

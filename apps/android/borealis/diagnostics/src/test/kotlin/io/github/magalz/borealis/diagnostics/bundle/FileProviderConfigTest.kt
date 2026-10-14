package io.github.magalz.borealis.diagnostics.bundle

import android.content.Context
import android.content.pm.PackageManager
import androidx.test.core.app.ApplicationProvider
import com.google.common.truth.Truth.assertThat
import java.io.File
import org.junit.Assert.assertThrows
import org.junit.Assume
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

/** The authority the code uses is the one the merged manifest declares. */
@RunWith(RobolectricTestRunner::class)
class FileProviderConfigTest {
    private val context: Context = ApplicationProvider.getApplicationContext()

    @Test
    fun `the manifest provider answers to the authority of the code`() {
        val provider =
            context.packageManager.resolveContentProvider(
                fileProviderAuthority(context.packageName),
                PackageManager.GET_META_DATA,
            )

        assertThat(provider).isNotNull()
        assertThat(provider!!.name).isEqualTo("androidx.core.content.FileProvider")
        assertThat(provider.exported).isFalse()
        assertThat(provider.grantUriPermissions).isTrue()
    }

    // androidx FileProvider cannot map Windows paths under Robolectric, so this runs on Linux CI.
    @Test
    fun `the default uri maps the bundle directory and refuses other files`() {
        Assume.assumeFalse(System.getProperty("os.name").startsWith("Windows"))
        val dir = File(context.cacheDir, "diagnostics").apply { mkdirs() }
        val zip = File(dir, "x.zip").apply { writeText("zip") }
        val outside = File(context.cacheDir, "other.zip").apply { writeText("zip") }
        val uriFor = ExportDiagnosticsPreference(context).uriFor

        assertThat(uriFor(zip).toString())
            .isEqualTo(
                "content://${context.packageName}.diagnostics.fileprovider/diagnostics/x.zip"
            )
        assertThrows(IllegalArgumentException::class.java) { uriFor(outside) }
    }
}

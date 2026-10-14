package io.github.magalz.borealis.diagnostics.bundle

import android.content.Context
import android.content.pm.PackageManager
import androidx.test.core.app.ApplicationProvider
import com.google.common.truth.Truth.assertThat
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
}

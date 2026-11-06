package io.github.magalz.borealis.support

import android.content.Context
import android.net.Uri
import androidx.test.core.app.ApplicationProvider
import com.google.common.truth.Truth.assertThat
import com.limelight.PosterContentProvider
import org.junit.Assert.assertThrows
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner

/**
 * The order of the checks of `PosterContentProvider.openBoxArtFile`: the mode comes before the URI
 * (`PosterContentProviderCharacterizationTest` has the modes on a valid URI and the URIs on the
 * read mode).
 */
@RunWith(RobolectricTestRunner::class)
class PosterContentProviderOpenOrderCharacterizationTest {
    private val context = ApplicationProvider.getApplicationContext<Context>()
    private val provider =
        Robolectric.setupContentProvider(
            PosterContentProvider::class.java,
            PosterContentProvider.AUTHORITY,
        )

    private fun uri(path: String): Uri =
        Uri.parse("content://${PosterContentProvider.AUTHORITY}/$path")

    @Test
    fun `a write mode is refused before the number of segments of the URI is checked`() {
        for (path in listOf("", "boxart", "boxart/0a1b-22", "boxart/0a1b-22/7/extra")) {
            val error =
                assertThrows("path: $path", UnsupportedOperationException::class.java) {
                    context.contentResolver.openFileDescriptor(uri(path), "w")
                }
            assertThat(error).hasMessageThat().isEqualTo("This provider is only for read mode")
        }
    }

    @Test
    fun `no mode at all is not the read mode`() {
        val error =
            assertThrows(UnsupportedOperationException::class.java) {
                provider.openBoxArtFile(PosterContentProvider.createBoxArtUri("0a1b-22", "7"), null)
            }

        assertThat(error).hasMessageThat().isEqualTo("This provider is only for read mode")
    }
}

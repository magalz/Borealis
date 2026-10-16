package io.github.magalz.borealis.library

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import com.google.common.truth.Truth.assertThat
import com.limelight.utils.CacheHelper
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

/** The cache file of the computer manager service: `applist/<uuid>` in the app cache directory. */
@RunWith(RobolectricTestRunner::class)
class DiskAppListCacheTest {
    private val context = ApplicationProvider.getApplicationContext<Context>()
    private val cache = DiskAppListCache(context)

    private fun write(hostId: String, text: String) =
        CacheHelper.openCacheFileForOutput(context.cacheDir, "applist", hostId).use {
            CacheHelper.writeStringToOutputStream(it, text)
        }

    @Test
    fun `it reads the list the service cached for the host`() {
        write("host-1", APP_LIST)

        assertThat(cache.read("host-1")).isEqualTo(APP_LIST)
    }

    @Test
    fun `it keeps what the file says, accents included`() {
        write("host-2", "<root>Café</root>")

        assertThat(cache.read("host-2")).isEqualTo("<root>Café</root>")
    }

    @Test
    fun `a host without a cache file has no list`() {
        assertThat(cache.read("never-seen")).isNull()
    }

    @Test
    fun `an empty cache file is no list`() {
        write("host-3", "")

        assertThat(cache.read("host-3")).isNull()
    }
}

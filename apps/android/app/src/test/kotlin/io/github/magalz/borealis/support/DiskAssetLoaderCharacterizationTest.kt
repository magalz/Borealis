package io.github.magalz.borealis.support

import android.content.Context
import android.graphics.BitmapFactory
import androidx.test.core.app.ApplicationProvider
import com.google.common.truth.Truth.assertThat
import com.limelight.grid.assets.DiskAssetLoader
import com.limelight.grid.assets.DiskAssetLoader.LoaderTuple
import com.limelight.nvstream.http.ComputerDetails
import com.limelight.nvstream.http.NvApp
import java.io.File
import org.junit.Assert.assertThrows
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

/** The box art cache of 12.2: `cache/boxart/<host>/<app>.png`. */
@RunWith(RobolectricTestRunner::class)
class DiskAssetLoaderCharacterizationTest {
    private val context = ApplicationProvider.getApplicationContext<Context>()
    private val loader = DiskAssetLoader(context)
    private val art: File
        get() = File(context.cacheDir, "boxart")

    @Before
    fun emptyCache() {
        art.deleteRecursively()
    }

    private fun tuple(uuid: String?, appId: Int) =
        LoaderTuple(ComputerDetails().also { it.uuid = uuid }, NvApp("App", appId, false))

    private fun options(width: Int, height: Int) =
        BitmapFactory.Options().also {
            it.outWidth = width
            it.outHeight = height
        }

    @Test
    fun `the file of an app is under the boxart directory of its host and nothing is created`() {
        val file = loader.getFile("host-1", 7)

        assertThat(file).isEqualTo(File(context.cacheDir, "boxart/host-1/7.png"))
        assertThat(art.exists()).isFalse()
    }

    @Test
    fun `the cache holds an app when its file exists`() {
        val tuple = tuple("host-1", 7)
        assertThat(loader.checkCacheExists(tuple)).isFalse()

        loader.getFile("host-1", 7).also { it.parentFile.mkdirs() }.writeText("png")

        assertThat(loader.checkCacheExists(tuple)).isTrue()
        assertThat(loader.checkCacheExists(tuple("host-1", 8))).isFalse()
        assertThat(loader.checkCacheExists(tuple("host-2", 7))).isFalse()
    }

    @Test
    fun `deleting the assets of a host removes its files and keeps its directory and other hosts`() {
        loader.getFile("host-1", 7).also { it.parentFile.mkdirs() }.writeText("a")
        loader.getFile("host-1", 8).writeText("b")
        loader.getFile("host-2", 7).also { it.parentFile.mkdirs() }.writeText("c")

        loader.deleteAssetsForComputer("host-1")

        assertThat(loader.getFile("host-1", 7).exists()).isFalse()
        assertThat(loader.getFile("host-1", 8).exists()).isFalse()
        assertThat(File(art, "host-1").isDirectory).isTrue()
        assertThat(loader.getFile("host-2", 7).exists()).isTrue()
    }

    @Test
    fun `deleting the assets of a host leaves a directory that is not empty`() {
        val nested = File(art, "host-1/nested").also { it.mkdirs() }
        File(nested, "keep.png").writeText("x")

        loader.deleteAssetsForComputer("host-1")

        assertThat(File(nested, "keep.png").exists()).isTrue()
    }

    @Test
    fun `deleting the assets of a host that has none changes nothing`() {
        loader.deleteAssetsForComputer("host-1")

        assertThat(art.exists()).isFalse()
    }

    @Test
    fun `two tuples are equal when host and app ids are`() {
        assertThat(tuple("host-1", 7)).isEqualTo(tuple("host-1", 7))
        assertThat(tuple("host-1", 7)).isNotEqualTo(tuple("host-2", 7))
        assertThat(tuple("host-1", 7)).isNotEqualTo(tuple("host-1", 8))
    }

    @Test
    fun `a tuple is equal by the ids and not by the name of the app`() {
        val named =
            LoaderTuple(ComputerDetails().also { it.uuid = "host-1" }, NvApp("Other name", 7, true))

        assertThat(tuple("host-1", 7)).isEqualTo(named)
    }

    @Test
    fun `a tuple is not equal to null or to another type`() {
        val tuple = tuple("host-1", 7)

        val nothing: Any? = null
        assertThat(tuple.equals(nothing)).isFalse()
        assertThat(tuple.equals("(host-1, 7)")).isFalse()
        assertThat(tuple.equals(tuple)).isTrue()
    }

    @Test
    fun `a tuple whose host has no id cannot be compared`() {
        assertThrows(RuntimeException::class.java) { tuple(null, 7).equals(tuple("host-1", 7)) }
    }

    @Test
    fun `a tuple is not equal to one whose host has no id`() {
        // The id of this tuple is compared with the id of the other: a null one is just not equal.
        assertThat(tuple("host-1", 7).equals(tuple(null, 7))).isFalse()
    }

    @Test
    fun `a tuple does not override hashCode, so equal tuples have different hash codes`() {
        val a = tuple("host-1", 7)
        val b = tuple("host-1", 7)

        assertThat(a).isEqualTo(b)
        assertThat(a.hashCode()).isEqualTo(System.identityHashCode(a))
        assertThat(a.hashCode()).isNotEqualTo(b.hashCode())
        assertThrows(NoSuchMethodException::class.java) {
            LoaderTuple::class.java.getDeclaredMethod("hashCode")
        }
    }

    @Test
    fun `a tuple is not a data class`() {
        assertThat(LoaderTuple::class.java.declaredMethods.map { it.name })
            .containsNoneOf("copy", "component1", "component2")
    }

    @Test
    fun `a tuple prints its host and app ids`() {
        assertThat(tuple("host-1", 7).toString()).isEqualTo("(host-1, 7)")
    }

    @Test
    fun `a tuple exposes the computer and the app it was made with`() {
        val computer = ComputerDetails().also { it.uuid = "host-1" }
        val app = NvApp("App", 7, false)

        val tuple = LoaderTuple(computer, app)

        assertThat(tuple.computer).isSameInstanceAs(computer)
        assertThat(tuple.app).isSameInstanceAs(app)
    }

    @Test
    fun `art that fits is not sampled down`() {
        assertThat(DiskAssetLoader.calculateInSampleSize(options(300, 400), 300, 400)).isEqualTo(1)
        assertThat(DiskAssetLoader.calculateInSampleSize(options(100, 100), 300, 400)).isEqualTo(1)
    }

    @Test
    fun `art is sampled down by the largest power of two that keeps both sides above the request`() {
        val table =
            listOf(
                SampleCase(600, 800, 300, 400, 2),
                SampleCase(1200, 1600, 300, 400, 4),
                SampleCase(2400, 3200, 300, 400, 8),
                // Only the narrow side limits it.
                SampleCase(150, 1000, 300, 400, 1),
                SampleCase(1000, 300, 300, 400, 1),
                SampleCase(1200, 800, 300, 400, 2),
                // The halves are integer divisions: 801 and 601 halve to 400 and 300.
                SampleCase(601, 801, 300, 400, 2),
                SampleCase(599, 801, 300, 400, 1),
                SampleCase(601, 799, 300, 400, 1),
                // Larger than the request in one side only.
                SampleCase(301, 400, 300, 400, 1),
                SampleCase(300, 401, 300, 400, 1),
                SampleCase(4000, 3000, 1000, 1000, 2),
                SampleCase(8192, 8192, 300, 400, 16),
            )

        for (case in table) {
            assertThat(
                    DiskAssetLoader.calculateInSampleSize(
                        options(case.width, case.height),
                        case.requestedWidth,
                        case.requestedHeight,
                    )
                )
                .isEqualTo(case.inSampleSize)
        }
    }

    @Test
    fun `the sample size reads the dimensions of the options and leaves them as they are`() {
        val options = options(1200, 1600)

        DiskAssetLoader.calculateInSampleSize(options, 300, 400)

        assertThat(options.outWidth).isEqualTo(1200)
        assertThat(options.outHeight).isEqualTo(1600)
        assertThat(options.inSampleSize).isEqualTo(0)
    }

    /**
     * An image of [width] by [height] that is asked for at [requestedWidth] by [requestedHeight].
     */
    private class SampleCase(
        val width: Int,
        val height: Int,
        val requestedWidth: Int,
        val requestedHeight: Int,
        val inSampleSize: Int,
    )
}

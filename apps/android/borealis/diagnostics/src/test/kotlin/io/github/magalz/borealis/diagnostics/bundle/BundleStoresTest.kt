package io.github.magalz.borealis.diagnostics.bundle

import android.content.Context
import android.content.pm.PackageManager
import android.provider.MediaStore
import androidx.test.core.app.ApplicationProvider
import com.google.common.truth.Truth.assertThat
import java.io.File
import java.io.IOException
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.Shadows.shadowOf

@RunWith(RobolectricTestRunner::class)
class BundleStoresTest {
    @get:Rule val temp = TemporaryFolder()

    private val context: Context = ApplicationProvider.getApplicationContext()
    private lateinit var zip: File
    private lateinit var provider: FakeDownloadsProvider

    @Before
    fun setUp() {
        zip =
            File(temp.newFolder("cache"), "borealis-diagnostics-20261014-123456.zip").apply {
                writeText("zip bytes")
            }
        provider = FakeDownloadsProvider.install()
    }

    @Test
    fun `the downloads store inserts a Downloads entry with the name, type and path`() {
        val saved = DownloadsBundleStore(context).save(zip)

        val row = provider.rows.single()
        assertThat(row.inserted.getAsString(MediaStore.MediaColumns.DISPLAY_NAME))
            .isEqualTo("borealis-diagnostics-20261014-123456.zip")
        assertThat(row.inserted.getAsString(MediaStore.MediaColumns.MIME_TYPE))
            .isEqualTo("application/zip")
        assertThat(row.inserted.getAsString(MediaStore.MediaColumns.RELATIVE_PATH))
            .isEqualTo("Download")
        assertThat(row.file.readText()).isEqualTo("zip bytes")
        assertThat(saved.location).isEqualTo("Download/borealis-diagnostics-20261014-123456.zip")
    }

    @Test
    fun `the entry is pending while it is written and final afterwards`() {
        DownloadsBundleStore(context).save(zip)

        val row = provider.rows.single()
        assertThat(row.inserted.getAsInteger(MediaStore.MediaColumns.IS_PENDING)).isEqualTo(1)
        assertThat(row.current.getAsInteger(MediaStore.MediaColumns.IS_PENDING)).isEqualTo(0)
    }

    @Test
    fun `the adb command pulls the file from the Download directory`() {
        val saved = DownloadsBundleStore(context).save(zip)

        assertThat(saved.adbPullCommand)
            .isEqualTo("adb pull /sdcard/Download/borealis-diagnostics-20261014-123456.zip")
    }

    @Test
    fun `saving again keeps only the newest bundle and nothing else`() {
        val older = File(zip.parentFile, "borealis-diagnostics-20250101-000000.zip")
        older.writeText("old")
        DownloadsBundleStore(context).save(older)
        provider.rows += // an entry that is not ours (another app's file with a similar start)
            FakeDownloadsProvider.Row(
                99,
                android.content.ContentValues().apply {
                    put(MediaStore.MediaColumns.DISPLAY_NAME, "report.zip")
                },
                android.content.ContentValues().apply {
                    put(MediaStore.MediaColumns.DISPLAY_NAME, "report.zip")
                },
                File.createTempFile("other", ".bin"),
            )

        DownloadsBundleStore(context).save(zip)

        assertThat(
                provider.rows.map { it.current.getAsString(MediaStore.MediaColumns.DISPLAY_NAME) }
            )
            .containsExactly("report.zip", "borealis-diagnostics-20261014-123456.zip")
    }

    @Test
    fun `a failed write removes the half written entry`() {
        val missing = File(zip.parentFile, "borealis-diagnostics-20260101-000000.zip")

        val failure = runCatching { DownloadsBundleStore(context).save(missing) }.exceptionOrNull()

        assertThat(failure).isNotNull()
        assertThat(provider.rows).isEmpty()
    }

    @Test
    fun `the app files store copies into the external files directory and prunes`() {
        val external = File(temp.root, "external").apply { mkdirs() }
        File(external, "borealis-diagnostics-20250101-000000.zip").writeText("old")
        File(external, "keep.txt").writeText("not mine")

        val saved = AppFilesBundleStore("io.github.magalz.borealis") { external }.save(zip)

        assertThat(external.list()!!.toList())
            .containsExactly("borealis-diagnostics-20261014-123456.zip", "keep.txt")
        assertThat(File(external, zip.name).readText()).isEqualTo("zip bytes")
        assertThat(saved.location).isEqualTo(File(external, zip.name).absolutePath)
        assertThat(saved.adbPullCommand)
            .isEqualTo(
                "adb pull /sdcard/Android/data/io.github.magalz.borealis/files/diagnostics/" +
                    "borealis-diagnostics-20261014-123456.zip"
            )
    }

    @Test
    fun `the app files store fails without an external directory`() {
        val failure = runCatching {
            AppFilesBundleStore("io.github.magalz.borealis") { null }.save(zip)
        }
            .exceptionOrNull()

        assertThat(failure).isInstanceOf(IllegalStateException::class.java)
    }

    @Test
    fun `API 29 and later use the downloads store`() {
        // Robolectric runs these tests on API 35.
        assertThat(defaultBundleStore(context)).isInstanceOf(DownloadsBundleStore::class.java)
    }

    @Test
    fun `a handheld is not a television`() {
        assertThat(isTelevision(context)).isFalse()
    }

    @Test
    fun `the television UI mode is a television`() {
        RuntimeEnvironment.setQualifiers("+television")

        assertThat(isTelevision(ApplicationProvider.getApplicationContext())).isTrue()
    }

    @Test
    fun `the leanback feature is a television`() {
        shadowOf(context.packageManager).setSystemFeature(PackageManager.FEATURE_LEANBACK, true)

        assertThat(isTelevision(context)).isTrue()
    }

    @Test
    fun `an unreadable zip fails the app files store with an IO error`() {
        val external = File(temp.root, "external2")
        val missing = File(temp.root, "gone.zip")

        val failure = runCatching {
            AppFilesBundleStore("p") { external }.save(missing)
        }
            .exceptionOrNull()

        assertThat(failure).isInstanceOf(IOException::class.java)
    }
}

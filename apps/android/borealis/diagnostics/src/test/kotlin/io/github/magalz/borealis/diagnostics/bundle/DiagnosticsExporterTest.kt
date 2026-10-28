package io.github.magalz.borealis.diagnostics.bundle

import android.app.Activity
import android.content.Context
import android.content.Intent
import android.content.pm.ActivityInfo
import android.content.pm.ResolveInfo
import android.net.Uri
import androidx.test.core.app.ApplicationProvider
import com.google.common.truth.Truth.assertThat
import io.github.magalz.borealis.diagnostics.DiagnosticSection
import java.io.File
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.Json
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.Shadows.shadowOf

/** The public export API of the Privacy and diagnostics screen, through its real flow. */
@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(RobolectricTestRunner::class)
class DiagnosticsExporterTest {
    private val context: Context = ApplicationProvider.getApplicationContext()
    private val switches = FakeLogSwitches()
    private lateinit var provider: FakeDownloadsProvider

    @Before
    fun setUp() {
        File(context.cacheDir, "diagnostics").deleteRecursively()
        provider = FakeDownloadsProvider.install()
    }

    private fun exporter(
        sections: Set<DiagnosticSection> = emptySet(),
        context: Context = this.context,
    ) =
        DiagnosticsExporter(
            context = context,
            sections = { sections },
            switches = switches,
            ioDispatcher = UnconfinedTestDispatcher(),
            // androidx FileProvider cannot map Windows paths under Robolectric; the real one is
            // covered by FileProviderConfigTest (not on Windows) and ManifestXmlTest.
            uriFor = { Uri.parse("content://test.fileprovider/diagnostics/${it.name}") },
            flushLogs = {},
        )

    @Suppress("DEPRECATION")
    private fun allowSharing() {
        val send = Intent(Intent.ACTION_SEND).setType("application/zip")
        val resolved =
            ResolveInfo().apply {
                activityInfo =
                    ActivityInfo().apply {
                        packageName = "com.example.mail"
                        name = "com.example.mail.Send"
                    }
            }
        shadowOf(context.packageManager).addResolveInfoForIntent(send, resolved)
    }

    @Test
    fun `a phone with a receiver gets a shareable file with its name, size and uri`() = runTest {
        allowSharing()

        val result = exporter(setOf(TextSection("audit.jsonl", "line\n"))).export()

        assertThat(result).isInstanceOf(ExportResult.Share::class.java)
        val share = result as ExportResult.Share
        assertThat(share.fileName).startsWith("borealis-diagnostics-")
        assertThat(share.fileName).endsWith(".zip")
        assertThat(share.sizeBytes).isGreaterThan(0L)
        assertThat(share.sizeBytes).isEqualTo(share.bundle.length())
        assertThat(share.uri.toString())
            .isEqualTo("content://test.fileprovider/diagnostics/${share.fileName}")
        assertThat(share.chooser.action).isEqualTo(Intent.ACTION_CHOOSER)
        assertThat(unzip(share.bundle.readBytes()).keys).contains("audit.jsonl")
    }

    @Test
    fun `a TV saves to Downloads and gives the adb pull command`() = runTest {
        RuntimeEnvironment.setQualifiers("+television")
        val tv = Robolectric.buildActivity(Activity::class.java).setup().get()
        allowSharing()

        val result = exporter(context = tv).export()

        assertThat(result).isInstanceOf(ExportResult.Saved::class.java)
        val saved = result as ExportResult.Saved
        assertThat(saved.location).isEqualTo("Download/${saved.fileName}")
        assertThat(saved.adbPullCommand).isEqualTo("adb pull /sdcard/Download/${saved.fileName}")
        assertThat(saved.sizeBytes).isEqualTo(saved.bundle.length())
        assertThat(saved.sizeBytes).isGreaterThan(0L)
        assertThat(provider.rows).hasSize(1)
    }

    @Test
    fun `the manifest in the bundle reflects the log switches`() = runTest {
        allowSharing()
        switches.verboseLogging = true
        switches.maskAddresses = true

        val result = exporter().export()
        assertThat(result).isInstanceOf(ExportResult.Share::class.java)
        val share = result as ExportResult.Share

        val manifest =
            Json.decodeFromString<BundleManifest>(
                unzip(share.bundle.readBytes()).getValue("manifest.json")
            )
        assertThat(manifest.logSettings)
            .isEqualTo(LogSettingsInfo(verbose = true, maskAddresses = true))
    }

    @Test
    fun `a bundle that cannot be built is a Failed result and not an exception`() = runTest {
        File(context.cacheDir, "diagnostics").writeText("a file, not a directory")

        assertThat(exporter().export()).isEqualTo(ExportResult.Failed)
    }
}

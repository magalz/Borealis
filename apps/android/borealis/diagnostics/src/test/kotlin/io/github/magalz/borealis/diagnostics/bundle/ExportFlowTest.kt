package io.github.magalz.borealis.diagnostics.bundle

import android.content.Intent
import android.net.Uri
import com.google.common.truth.Truth.assertThat
import java.io.File
import java.io.IOException
import java.time.Instant
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class ExportFlowTest {
    @get:Rule val temp = TemporaryFolder()

    private lateinit var zip: File
    private val saved = mutableListOf<File>()

    /** The intents that the flow asked whether an app can receive. */
    private val asked = mutableListOf<Intent>()

    private val recordingStore = BundleStore { file ->
        saved += file
        SavedBundle("Download/${file.name}", "adb pull /sdcard/Download/${file.name}")
    }

    @Before
    fun setUp() {
        zip =
            File(temp.newFolder("cache"), "borealis-diagnostics-20261014-123456.zip").apply {
                writeText("zip bytes")
            }
    }

    private fun flow(
        canShare: Boolean = true,
        television: Boolean = false,
        build: () -> File = { zip },
        uriFor: (File) -> Uri = { Uri.parse("content://test.fileprovider/diagnostics/${it.name}") },
        store: BundleStore = recordingStore,
    ) =
        ExportFlow(
            chooserTitle = "Share diagnostics",
            buildBundle = build,
            uriFor = uriFor,
            canShare = { intent ->
                asked += intent
                canShare
            },
            isTelevision = television,
            store = store,
        )

    private fun sendOf(chooser: Intent): Intent {
        @Suppress("DEPRECATION")
        val send = chooser.getParcelableExtra<Intent>(Intent.EXTRA_INTENT)!!
        return send
    }

    @Test
    fun `a phone shares the bundle through a chooser when an app can receive it`() {
        val result = flow(canShare = true).run()

        val chooser = (result as ExportResult.Share).chooser
        assertThat(chooser.action).isEqualTo(Intent.ACTION_CHOOSER)
        assertThat(chooser.getCharSequenceExtra(Intent.EXTRA_TITLE).toString())
            .isEqualTo("Share diagnostics")
        val send = sendOf(chooser)
        assertThat(send.action).isEqualTo(Intent.ACTION_SEND)
        assertThat(send.type).isEqualTo("application/zip")
        @Suppress("DEPRECATION")
        assertThat(send.getParcelableExtra<Uri>(Intent.EXTRA_STREAM).toString())
            .isEqualTo(
                "content://test.fileprovider/diagnostics/borealis-diagnostics-20261014-123456.zip"
            )
        assertThat(send.flags and Intent.FLAG_GRANT_READ_URI_PERMISSION).isNotEqualTo(0)
        assertThat(chooser.flags and Intent.FLAG_GRANT_READ_URI_PERMISSION).isNotEqualTo(0)
        assertThat(asked.single().action).isEqualTo(Intent.ACTION_SEND)
        assertThat(saved).isEmpty()
    }

    @Test
    fun `a TV saves the bundle even when an app, such as a video player, can receive it`() {
        val result = flow(canShare = true, television = true).run()

        val savedResult = result as ExportResult.Saved
        assertThat(saved).containsExactly(zip)
        assertThat(savedResult.location).isEqualTo("Download/${zip.name}")
        assertThat(savedResult.adbPullCommand).isEqualTo("adb pull /sdcard/Download/${zip.name}")
        assertThat(savedResult.bundle).isEqualTo(zip)
    }

    @Test
    fun `a phone with no receiver saves the bundle`() {
        val result = flow(canShare = false).run()

        assertThat(result).isInstanceOf(ExportResult.Saved::class.java)
        assertThat(saved).containsExactly(zip)
    }

    @Test
    fun `a saved result still offers a chooser for the file provider uri`() {
        val result = flow(television = true).run() as ExportResult.Saved

        val chooser = result.chooser
        assertThat(chooser.action).isEqualTo(Intent.ACTION_CHOOSER)
        assertThat(chooser.flags and Intent.FLAG_GRANT_READ_URI_PERMISSION).isNotEqualTo(0)
        val send = sendOf(chooser)
        assertThat(send.type).isEqualTo("application/zip")
        assertThat(send.flags and Intent.FLAG_GRANT_READ_URI_PERMISSION).isNotEqualTo(0)
        @Suppress("DEPRECATION")
        assertThat(send.getParcelableExtra<Uri>(Intent.EXTRA_STREAM).toString())
            .isEqualTo(
                "content://test.fileprovider/diagnostics/borealis-diagnostics-20261014-123456.zip"
            )
    }

    @Test
    fun `fails instead of throwing when the bundle cannot be saved`() {
        val failing = BundleStore { throw IOException("no space") }

        assertThat(flow(television = true, store = failing).run()).isEqualTo(ExportResult.Failed)
    }

    @Test
    fun `fails instead of throwing when the bundle cannot be built`() {
        val result = flow(build = { throw IOException("disk full") }).run()

        assertThat(result).isEqualTo(ExportResult.Failed)
    }

    @Test
    fun `fails instead of throwing when the file cannot be shared`() {
        val result = flow(uriFor = { throw IllegalArgumentException("not covered") }).run()

        assertThat(result).isEqualTo(ExportResult.Failed)
    }

    @Test
    fun `bundle names carry the UTC time`() {
        assertThat(BundleFiles.newName(Instant.parse("2026-10-14T12:34:56Z")))
            .isEqualTo("borealis-diagnostics-20261014-123456.zip")
    }

    @Test
    fun `pruning removes bundles and unfinished bundles and nothing else`() {
        val dir = temp.newFolder("prune")
        File(dir, "borealis-diagnostics-20250101-000000.zip").writeText("a")
        File(dir, "borealis-diagnostics-20250102-000000.zip.part").writeText("b")
        File(dir, "other.zip").writeText("c")

        BundleFiles.pruneOld(dir)

        assertThat(dir.list()!!.toList()).containsExactly("other.zip")
    }

    @Test
    fun `pruning a missing directory is fine`() {
        BundleFiles.pruneOld(File(temp.root, "nope"))
    }
}

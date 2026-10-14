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
    private lateinit var external: File

    @Before
    fun setUp() {
        zip =
            File(temp.newFolder("cache"), "borealis-diagnostics-20261014-123456.zip").apply {
                writeText("zip bytes")
            }
        external = File(temp.root, "external")
    }

    private fun flow(
        canShare: Boolean = true,
        build: () -> File = { zip },
        uriFor: (File) -> Uri = { Uri.parse("content://test.fileprovider/diagnostics/${it.name}") },
        externalDir: File? = external,
        asked: MutableList<Intent> = mutableListOf(),
    ) =
        ExportFlow(
            packageName = "io.github.magalz.borealis",
            chooserTitle = "Share diagnostics",
            buildBundle = build,
            uriFor = uriFor,
            canShare = { intent ->
                asked += intent
                canShare
            },
            externalDir = { externalDir },
        )

    @Test
    fun `shares the bundle through a chooser when an app can receive it`() {
        val asked = mutableListOf<Intent>()

        val result = flow(canShare = true, asked = asked).run()

        val chooser = (result as ExportResult.Share).chooser
        assertThat(chooser.action).isEqualTo(Intent.ACTION_CHOOSER)
        assertThat(chooser.getCharSequenceExtra(Intent.EXTRA_TITLE).toString())
            .isEqualTo("Share diagnostics")
        @Suppress("DEPRECATION")
        val send = chooser.getParcelableExtra<Intent>(Intent.EXTRA_INTENT)!!
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
        assertThat(external.exists()).isFalse()
    }

    @Test
    fun `saves a copy and gives the adb command when nothing can receive the bundle`() {
        val result = flow(canShare = false).run()

        val saved = result as ExportResult.Saved
        assertThat(saved.file.parentFile).isEqualTo(external)
        assertThat(saved.file.name).isEqualTo(zip.name)
        assertThat(saved.file.readText()).isEqualTo("zip bytes")
        assertThat(saved.adbPullCommand)
            .isEqualTo(
                "adb pull /sdcard/Android/data/io.github.magalz.borealis/files/diagnostics/" +
                    "borealis-diagnostics-20261014-123456.zip"
            )
    }

    @Test
    fun `saving replaces earlier bundles in the external directory`() {
        external.mkdirs()
        File(external, "borealis-diagnostics-20250101-000000.zip").writeText("old")
        File(external, "keep.txt").writeText("not mine")

        flow(canShare = false).run()

        assertThat(external.list()!!.toList())
            .containsExactly("borealis-diagnostics-20261014-123456.zip", "keep.txt")
    }

    @Test
    fun `fails when there is no external directory to save to`() {
        assertThat(flow(canShare = false, externalDir = null).run()).isEqualTo(ExportResult.Failed)
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

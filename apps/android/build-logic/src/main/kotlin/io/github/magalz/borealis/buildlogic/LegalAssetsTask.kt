package io.github.magalz.borealis.buildlogic

import org.gradle.api.DefaultTask
import org.gradle.api.file.ConfigurableFileCollection
import org.gradle.api.file.DirectoryProperty
import org.gradle.api.tasks.InputFiles
import org.gradle.api.tasks.OutputDirectory
import org.gradle.api.tasks.PathSensitive
import org.gradle.api.tasks.PathSensitivity
import org.gradle.api.tasks.TaskAction

/**
 * Copies the repository's `NOTICE`, `LICENSE` and the licence of each bundled font into generated
 * assets under `legal/`, so the app can show them without a browser (ADR 0050, ADR 0058) and no
 * second copy is committed. Each entry is a repository-relative path. The copy keeps its file name,
 * except a font licence, which takes the name of its font folder (see [assetName]): most of them
 * are called `OFL.txt`.
 */
abstract class LegalAssetsTask : DefaultTask() {
    @get:InputFiles
    @get:PathSensitive(PathSensitivity.NAME_ONLY)
    abstract val sources: ConfigurableFileCollection

    @get:OutputDirectory abstract val outputDir: DirectoryProperty

    @TaskAction
    fun copy() {
        val legal = outputDir.get().asFile.resolve(ASSET_DIR)
        legal.deleteRecursively()
        legal.mkdirs()
        sources.files.forEach { it.copyTo(legal.resolve(assetName(it.invariantSeparatorsPath))) }
    }

    internal companion object {
        const val ASSET_DIR = "legal"
        val FILES =
            listOf(
                "NOTICE",
                "LICENSE",
                "design/fonts/ibm-plex/OFL.txt",
                "design/fonts/atkinson-next/OFL.txt",
                "design/fonts/atkinson-mono/OFL.txt",
                "design/fonts/luciole/LICENSE.txt",
                "design/fonts/lexend/OFL.txt",
                "design/fonts/andika/OFL.txt",
                "design/fonts/b612/OFL.txt",
            )

        private val FONT_LICENCE = Regex("design/fonts/([^/]+)/([^/]+)$")

        /**
         * The name that the asset of [path] has under `legal/`: the file name, or the folder of the
         * font and the file name for a licence in `design/fonts/<font>/` (`lexend-OFL.txt`).
         */
        fun assetName(path: String): String =
            FONT_LICENCE.find(path)?.let { "${it.groupValues[1]}-${it.groupValues[2]}" }
                ?: path.substringAfterLast('/')
    }
}

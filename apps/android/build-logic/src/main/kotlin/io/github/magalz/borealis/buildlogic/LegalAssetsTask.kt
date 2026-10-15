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
 * Copies the repository's `NOTICE`, `LICENSE` and the bundled font licence (`OFL.txt`) into
 * generated assets under `legal/`, so the app can show them without a browser (ADR 0050) and no
 * second copy is committed. Each entry is a repository-relative path; the copy keeps its file name.
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
        sources.files.forEach { it.copyTo(legal.resolve(it.name)) }
    }

    internal companion object {
        const val ASSET_DIR = "legal"
        val FILES = listOf("NOTICE", "LICENSE", "design/fonts/ibm-plex/OFL.txt")
    }
}

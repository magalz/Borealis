package io.github.magalz.borealis.buildlogic.tokens

import org.gradle.api.DefaultTask
import org.gradle.api.file.DirectoryProperty
import org.gradle.api.file.RegularFileProperty
import org.gradle.api.tasks.CacheableTask
import org.gradle.api.tasks.InputFile
import org.gradle.api.tasks.OutputDirectory
import org.gradle.api.tasks.PathSensitive
import org.gradle.api.tasks.PathSensitivity
import org.gradle.api.tasks.TaskAction

/**
 * Generates `BorealisTokens.kt` and `values/borealis_tokens.xml` from `design/tokens.json` (ADR
 * 0044). Nothing generated is committed.
 */
@CacheableTask
abstract class BorealisTokensTask : DefaultTask() {
    @get:InputFile
    @get:PathSensitive(PathSensitivity.RELATIVE)
    abstract val tokens: RegularFileProperty

    @get:OutputDirectory abstract val kotlinOutputDir: DirectoryProperty

    @get:OutputDirectory abstract val resOutputDir: DirectoryProperty

    @TaskAction
    fun generate() {
        val generated = TokenGenerator.generate(tokens.get().asFile.readText())

        val kotlin = kotlinOutputDir.get().asFile
        kotlin.deleteRecursively()
        val kotlinFile = kotlin.resolve(KOTLIN_FILE)
        kotlinFile.parentFile.mkdirs()
        kotlinFile.writeText(generated.kotlin)

        val res = resOutputDir.get().asFile
        res.deleteRecursively()
        val resFile = res.resolve(RES_FILE)
        resFile.parentFile.mkdirs()
        resFile.writeText(generated.colorsXml)
    }

    internal companion object {
        const val KOTLIN_FILE = "io/github/magalz/borealis/designsystem/tokens/BorealisTokens.kt"
        const val RES_FILE = "values/borealis_tokens.xml"
    }
}

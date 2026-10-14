package io.github.magalz.borealis.buildlogic

import java.io.File

/** Reads the borealis-core version from `core/CMakeLists.txt`, the single source of truth. */
internal object BorealisCoreVersion {
    /** The `VERSION` of the `project()` call in the given CMakeLists.txt text. */
    @Suppress(
        "UnusedParameter",
        "FunctionOnlyReturningConstant",
    ) // Stub until the tested implementation.
    fun parse(cmakeLists: String): String = "0.0.0"

    /** The core version of the repository whose root is [repoRoot]. */
    fun read(repoRoot: File): String = parse(File(repoRoot, "core/CMakeLists.txt").readText())
}

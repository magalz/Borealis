package io.github.magalz.borealis.buildlogic

import java.io.File

/** Reads the borealis-core version from `core/CMakeLists.txt`, the single source of truth. */
internal object BorealisCoreVersion {
    private val PROJECT_CALL = Regex("""\bproject\s*\(([^)]*)\)""", RegexOption.IGNORE_CASE)
    private val VERSION = Regex("""\bVERSION\s+(\d+\.\d+\.\d+)\b""")

    /** The `VERSION` of the `project()` call in the given CMakeLists.txt text. */
    fun parse(cmakeLists: String): String {
        val arguments = PROJECT_CALL.find(cmakeLists)?.groupValues?.get(1)
        val version = arguments?.let { VERSION.find(it)?.groupValues?.get(1) }
        return requireNotNull(version) {
            "core/CMakeLists.txt has no project(... VERSION MAJOR.MINOR.PATCH ...)"
        }
    }

    /** The core version of the repository whose root is [repoRoot]. */
    fun read(repoRoot: File): String = parse(File(repoRoot, "core/CMakeLists.txt").readText())
}

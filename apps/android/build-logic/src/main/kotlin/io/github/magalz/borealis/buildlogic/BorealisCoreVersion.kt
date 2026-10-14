package io.github.magalz.borealis.buildlogic

import java.io.File

/** Reads the borealis-core version from `core/CMakeLists.txt`, the single source of truth. */
internal object BorealisCoreVersion {
    private val PROJECT_CALL = Regex("""\bproject\s*\(""", RegexOption.IGNORE_CASE)
    private val VERSION = Regex("""\bVERSION\s+(\d+\.\d+\.\d+)\b""")

    /** The `VERSION` of the `project()` call in the given CMakeLists.txt text. */
    fun parse(cmakeLists: String): String {
        val arguments = projectArguments(withoutComments(cmakeLists))
        val version = arguments?.let { VERSION.find(it)?.groupValues?.get(1) }
        return requireNotNull(version) {
            "core/CMakeLists.txt has no project(... VERSION MAJOR.MINOR.PATCH ...)"
        }
    }

    /** The core version of the repository whose root is [repoRoot]. */
    fun read(repoRoot: File): String = parse(File(repoRoot, "core/CMakeLists.txt").readText())

    /** Drops `#` line comments; a `#` inside a quoted argument is text, not a comment. */
    private fun withoutComments(text: String): String = buildString {
        var quoted = false
        var comment = false
        var escaped = false
        for (c in text) {
            when {
                comment -> {
                    comment = c != '\n'
                    if (!comment) append(c)
                }
                quoted -> {
                    quoted = escaped || c != '"'
                    escaped = !escaped && c == '\\'
                    append(c)
                }
                c == '#' -> comment = true
                else -> {
                    quoted = c == '"'
                    append(c)
                }
            }
        }
    }

    /**
     * The arguments of the first `project(` call, up to its matching parenthesis, with quoted
     * strings (such as a `DESCRIPTION`) blanked out so nothing inside them is mistaken for syntax.
     */
    private fun projectArguments(text: String): String? {
        val start = PROJECT_CALL.find(text)?.range?.last?.plus(1) ?: return null
        val arguments = StringBuilder()
        var depth = 1
        var quoted = false
        var escaped = false
        for (c in text.substring(start)) {
            if (quoted) {
                quoted = escaped || c != '"'
                escaped = !escaped && c == '\\'
                continue
            }
            when (c) {
                '"' -> {
                    quoted = true
                    arguments.append(' ')
                }
                '(' -> {
                    depth++
                    arguments.append(c)
                }
                ')' -> {
                    depth--
                    if (depth == 0) return arguments.toString()
                    arguments.append(c)
                }
                else -> arguments.append(c)
            }
        }
        return null
    }
}

package io.github.magalz.borealis.buildlogic

/** Detection of the `clang-format` binary that Spotless uses for Borealis C/C++ sources. */
internal object ClangFormat {
    const val MISSING_MESSAGE =
        "clang-format 22.x not found on PATH (see docs/development/building.md)"

    private val VERSION_OUTPUT = Regex("""clang-format version (\d+(?:\.\d+)*)""")

    /**
     * The version in `clang-format --version` output, or null when there is none (not installed).
     */
    fun parseVersion(output: String?): String? = output?.let {
        VERSION_OUTPUT.find(it)?.groupValues?.get(1)
    }
}

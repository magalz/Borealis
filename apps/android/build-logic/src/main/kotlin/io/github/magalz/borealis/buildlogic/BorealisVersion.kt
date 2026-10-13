package io.github.magalz.borealis.buildlogic

/** App version derived from `android-vMAJOR.MINOR.PATCH` git tags. */
data class BorealisVersion(val name: String, val code: Int) {
    companion object {
        val DEV = BorealisVersion(name = "0.0.0-dev", code = 1)

        @Suppress("UnusedParameter") // Stub: real parsing follows the failing tests.
        /** Parses `git describe --tags --long` output; falls back to [DEV]. */
        fun fromGitDescribe(output: String?): BorealisVersion = DEV
    }
}

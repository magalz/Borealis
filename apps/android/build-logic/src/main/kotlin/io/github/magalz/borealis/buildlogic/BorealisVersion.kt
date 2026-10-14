package io.github.magalz.borealis.buildlogic

/** App version derived from `android-vMAJOR.MINOR.PATCH` git tags. */
internal data class BorealisVersion(val name: String, val code: Int) {
    companion object {
        val DEV = BorealisVersion(name = "0.0.0-dev", code = 1)

        /**
         * `git describe` arguments: newest plain release tag, pre-release tags (`-rc1`) skipped.
         */
        val GIT_DESCRIBE_ARGS =
            listOf(
                "describe",
                "--tags",
                "--long",
                "--match",
                "android-v[0-9]*.[0-9]*.[0-9]*",
                "--exclude",
                "android-v*-*",
            )

        private const val MAX_COMPONENT_DIGITS = 3
        private val DESCRIBE =
            Regex(
                """^android-v(?<major>\d+)\.(?<minor>\d+)\.(?<patch>\d+)-(?<ahead>\d+)-g(?<sha>[0-9a-f]+)$"""
            )

        /**
         * Parses `git describe --tags --long` output; falls back to [DEV] when there is no (valid)
         * tag. The code is `MAJOR * 1_000_000 + MINOR * 1_000 + PATCH` (at least 1); commits after
         * the tag only show up in the name. Components above 999 would overflow the code and fail
         * with [IllegalArgumentException].
         */
        fun fromGitDescribe(output: String?): BorealisVersion {
            val match = output?.trim()?.let(DESCRIBE::matchEntire) ?: return DEV
            fun part(name: String) = match.groups[name]!!.value
            val (major, minor, patch) = listOf(part("major"), part("minor"), part("patch"))
            val base = "$major.$minor.$patch"
            require(listOf(major, minor, patch).all { it.length <= MAX_COMPONENT_DIGITS }) {
                "Release tag android-v$base has a component above 999; version codes would overflow."
            }
            val code = major.toInt() * 1_000_000 + minor.toInt() * 1_000 + patch.toInt()
            val ahead = part("ahead")
            return BorealisVersion(
                name = if (ahead == "0") base else "$base-$ahead-g${part("sha")}",
                code = code.coerceAtLeast(1),
            )
        }
    }
}

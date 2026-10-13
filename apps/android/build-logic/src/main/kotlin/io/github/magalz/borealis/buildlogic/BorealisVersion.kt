package io.github.magalz.borealis.buildlogic

/** App version derived from `android-vMAJOR.MINOR.PATCH` git tags. */
data class BorealisVersion(val name: String, val code: Int) {
    companion object {
        val DEV = BorealisVersion(name = "0.0.0-dev", code = 1)

        private val DESCRIBE = Regex("""^android-v(\d{1,4})\.(\d{1,3})\.(\d{1,3})-(\d+)-g([0-9a-f]+)$""")

        /**
         * Parses `git describe --tags --long --match "android-v*"` output; falls back to [DEV] when
         * there is no matching tag. The code is `MAJOR * 1_000_000 + MINOR * 1_000 + PATCH` (at
         * least 1); commits after the tag only show up in the name.
         */
        fun fromGitDescribe(output: String?): BorealisVersion {
            val match = output?.trim()?.let(DESCRIBE::matchEntire) ?: return DEV
            val groups = match.groupValues
            val base = "${groups[1]}.${groups[2]}.${groups[3]}"
            val code = groups[1].toInt() * 1_000_000 + groups[2].toInt() * 1_000 + groups[3].toInt()
            return BorealisVersion(
                name = if (groups[4] == "0") base else "$base-${groups[4]}-g${groups[5]}",
                code = code.coerceAtLeast(1),
            )
        }
    }
}

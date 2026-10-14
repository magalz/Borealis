package io.github.magalz.borealis.host

import java.io.File

/** Shared host fixtures from `core/tests/fixtures`, located through `borealis.fixtures.dir`. */
object Fixtures {
    const val DIR_PROPERTY = "borealis.fixtures.dir"

    /** The fixtures directory; fails when the build did not set the property. */
    fun root(): File {
        val path =
            checkNotNull(System.getProperty(DIR_PROPERTY)) {
                "System property $DIR_PROPERTY is not set: unit-test tasks get it from the " +
                    "borealis.android.library convention plugin (build-logic)."
            }
        return File(path)
    }

    /** The fixture at [relativePath] below [root], for example `luminalshine/26.09.1/x.xml`. */
    fun file(relativePath: String): File {
        val file = root().resolve(relativePath)
        check(file.isFile) { "Fixture not found: $relativePath (looked in ${root()})" }
        return file
    }

    fun text(relativePath: String): String = file(relativePath).readText()
}

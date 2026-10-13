package io.github.magalz.borealis.buildlogic

import java.io.File
import java.util.Properties

/**
 * Release signing material, read from `keystore.properties` or `BOREALIS_*` environment variables.
 */
internal data class BorealisSigning(
    val storeFile: File,
    val storePassword: String,
    val keyAlias: String,
    val keyPassword: String,
) {
    companion object {
        private val ENV_NAMES =
            mapOf(
                "storeFile" to "BOREALIS_KEYSTORE_FILE",
                "storePassword" to "BOREALIS_KEYSTORE_PASSWORD",
                "keyAlias" to "BOREALIS_KEY_ALIAS",
                "keyPassword" to "BOREALIS_KEY_PASSWORD",
            )

        /** Returns null (release stays unsigned) unless all four values are available. */
        fun load(projectRoot: File, env: Map<String, String> = System.getenv()): BorealisSigning? {
            val props = Properties()
            val file = File(projectRoot, "keystore.properties")
            if (file.isFile) file.inputStream().use { props.load(it) }

            val values =
                ENV_NAMES.mapNotNull { (prop, envName) ->
                        (props.getProperty(prop) ?: env[envName])
                            ?.takeIf { it.isNotBlank() }
                            ?.let { prop to it }
                    }
                    .toMap()
            if (values.size != ENV_NAMES.size) return null

            val path = File(values.getValue("storeFile"))
            return BorealisSigning(
                storeFile = if (path.isAbsolute) path else File(projectRoot, path.path),
                storePassword = values.getValue("storePassword"),
                keyAlias = values.getValue("keyAlias"),
                keyPassword = values.getValue("keyPassword"),
            )
        }
    }
}

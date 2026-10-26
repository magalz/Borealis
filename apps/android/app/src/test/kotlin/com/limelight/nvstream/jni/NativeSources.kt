package com.limelight.nvstream.jni

import java.io.File

/**
 * The C side of `MoonBridge`, read as text: the C files that `moonlight-core` is built from, and
 * `Limelight.h` of moonlight-common-c, which defines the constants that `MoonBridge` repeats. The
 * tests of `:app` run in `apps/android/app`.
 */
internal object NativeSources {
    private const val JNI_ROOT = "src/main/jni"
    private const val LIMELIGHT_H = "../../../core/third_party/moonlight-common-c/src/Limelight.h"
    private const val HEX = 16

    private val BLOCK_COMMENT = Regex("/\\*.*?\\*/", RegexOption.DOT_MATCHES_ALL)
    private val LINE_COMMENT = Regex("//[^\\n]*")
    private val DEFINE = Regex("(?m)^#define\\s+(\\w+)[ \\t]+([^\\r\\n]*)$")
    private val NUMBER = Regex("\\(?\\s*(-?)\\s*(0[xX][0-9A-Fa-f]+|\\d+)\\s*[uUlL]*\\s*\\)?")

    /** The text of every C file under `src/main/jni`, without comments, by path. */
    val cFiles: Map<String, String> by lazy {
        val files =
            File(JNI_ROOT)
                .walkTopDown()
                .filter { it.isFile && it.extension == "c" }
                .associate { it.path to withoutComments(it.readText()) }
        check(files.isNotEmpty()) { "No C file under $JNI_ROOT" }
        files
    }

    /** The text of `Limelight.h`, without comments. */
    val limelightHeader: String by lazy { withoutComments(File(LIMELIGHT_H).readText()) }

    /** The object-like macros of `Limelight.h` whose value is a plain number, by name. */
    val numericDefines: Map<String, Long> by lazy {
        DEFINE.findAll(limelightHeader)
            .mapNotNull { match ->
                val (name, value) = match.destructured
                NUMBER.matchEntire(value.trim())?.let { number ->
                    val (sign, digits) = number.destructured
                    val magnitude =
                        if (digits.startsWith("0x", ignoreCase = true)) digits.drop(2).toLong(HEX)
                        else digits.toLong()
                    name to if (sign == "-") -magnitude else magnitude
                }
            }
            .toMap()
    }

    private fun withoutComments(text: String): String =
        text.replace(BLOCK_COMMENT, " ").replace(LINE_COMMENT, "")
}

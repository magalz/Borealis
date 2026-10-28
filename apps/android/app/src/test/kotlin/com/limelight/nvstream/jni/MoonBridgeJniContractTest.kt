package com.limelight.nvstream.jni

import com.google.common.truth.Truth.assertThat
import io.github.magalz.borealis.connection.ShadowRecordingBridge
import org.junit.Test
import org.objectweb.asm.Type

/**
 * The agreement between `MoonBridge` and the C code that `moonlight-core` is built from. The C code
 * finds every native by the JNI name `Java_com_limelight_nvstream_jni_MoonBridge_<name>` (there is
 * no `RegisterNatives`) and every callback by a `GetStaticMethodID` lookup with a name and a
 * descriptor; a mistake in either shows only when the library runs on a device. The test reads the
 * C sources and the class file of `MoonBridge` (see [NativeSources] and [MoonBridgeClassFile]), so
 * it needs neither the native library nor the Android runtime, and it never initializes
 * `MoonBridge`.
 *
 * It runs in plain JUnit on purpose: the Robolectric runner instruments the class and turns a
 * native method into a plain one, which would hide what is checked here.
 */
class MoonBridgeJniContractTest {
    /** One `Java_com_limelight_nvstream_jni_MoonBridge_*` function of the C code. */
    private data class Export(
        val name: String,
        val returnType: String,
        val params: List<String>,
        val file: String,
    )

    /** One `GetStaticMethodID(env, clazz, "<name>", "<descriptor>")` of the C code. */
    private data class Lookup(val name: String, val descriptor: String, val file: String)

    private val exports: Map<String, Export> by lazy {
        val found =
            NativeSources.cFiles.flatMap { (file, text) ->
                EXPORT.findAll(text).map { match ->
                    val (returnType, mangled, params) = match.destructured
                    Export(
                        name = mangled.replace("_1", "_"),
                        returnType = returnType,
                        params = params.split(',').map { it.trim().split(WHITESPACE).first() },
                        file = file,
                    )
                }
            }
        assertThat(found.map { it.name }).containsNoDuplicates()
        found.associateBy { it.name }
    }

    private val lookups: List<Lookup> by lazy {
        NativeSources.cFiles.flatMap { (file, text) ->
            LOOKUP.findAll(text).map { Lookup(it.groupValues[1], it.groupValues[2], file) }
        }
    }

    @Test
    fun `every export of the C code is a static native method of MoonBridge with the same descriptor`() {
        val problems =
            exports.values.mapNotNull { export ->
                val native = MoonBridgeClassFile.natives.firstOrNull { it.name == export.name }
                when {
                    native == null -> "${export.name} (${export.file}): MoonBridge has no native"
                    !native.isStatic -> "${export.name}: the native is not static"
                    !agrees(export, native.descriptor) ->
                        "${export.name}: C has (${export.params.drop(2).joinToString()}) " +
                            "${export.returnType}, the native is ${native.descriptor}"
                    else -> null
                }
            }

        assertThat(problems).isEmpty()
    }

    @Test
    fun `every native method of MoonBridge has an export in the C code`() {
        val problems =
            MoonBridgeClassFile.natives
                .filter { it.name !in exports }
                .map {
                    "${it.name}${it.descriptor}: no Java_com_limelight_nvstream_jni_MoonBridge_*"
                }

        assertThat(problems).isEmpty()
    }

    @Test
    fun `an export takes the environment and the class, as a static native does`() {
        val problems =
            exports.values
                .filter { it.params.take(2) != listOf("JNIEnv", "jclass") }
                .map {
                    "${it.name} (${it.file}): the first two parameters are ${it.params.take(2)}"
                }

        assertThat(problems).isEmpty()
    }

    @Test
    fun `every callback that the C code looks up is a static method with that descriptor`() {
        val problems = lookups.mapNotNull { lookup ->
            val method =
                MoonBridgeClassFile.methods.firstOrNull {
                    it.name == lookup.name && it.descriptor == lookup.descriptor
                }
            when {
                method == null ->
                    "${lookup.name}${lookup.descriptor} (${lookup.file}): MoonBridge has none"
                !method.isStatic -> "${lookup.name}: the callback is not static"
                method.isNative -> "${lookup.name}: the callback is native"
                else -> null
            }
        }

        assertThat(problems).isEmpty()
    }

    @Test
    fun `every bridge callback of MoonBridge is looked up by the C code`() {
        val looked = lookups.map { it.name }.toSet()
        val problems =
            MoonBridgeClassFile.methods
                .filter { it.name.startsWith("bridge") }
                .filter { it.name !in looked }
                .map { "${it.name}${it.descriptor}: the C code never looks it up" }

        assertThat(problems).isEmpty()
        // init() looks up 21 callbacks and borealis_log.c the log one: the parse must have seen
        // them.
        assertThat(looked).hasSize(CALLBACKS)
    }

    @Test
    fun `the constants that Limelight h defines have the same value in MoonBridge`() {
        val header = NativeSources.numericDefines

        val differing =
            MoonBridgeClassFile.constants.values
                .filter { constant ->
                    val expected = header[constant.name]
                    val mask = widthMask(constant.descriptor)
                    expected != null && (expected and mask) != (constant.value.toLong() and mask)
                }
                .map { it.name }

        // The only difference: Limelight.h counts the 4:4:4 formats as 10-bit ones, MoonBridge
        // never
        // asks for them (see the test after the next one).
        assertThat(differing).containsExactly("VIDEO_FORMAT_MASK_10BIT")
    }

    @Test
    fun `no constant of MoonBridge lacks a number in Limelight h`() {
        val without =
            MoonBridgeClassFile.constants.keys.filter { it !in NativeSources.numericDefines }

        // The three AUDIO_CONFIGURATION_* are objects, not numbers: MoonBridgeStaticInitializerTest
        // compares them with MAKE_AUDIO_CONFIGURATION.
        assertThat(without).isEmpty()
    }

    @Test
    fun `the 10-bit video mask of MoonBridge keeps the two 10-bit formats the client asks for`() {
        val java = MoonBridgeClassFile.constants.getValue("VIDEO_FORMAT_MASK_10BIT").value
        val header = NativeSources.numericDefines.getValue("VIDEO_FORMAT_MASK_10BIT")

        // H.265 Main10 (0x0200) and AV1 Main10 (0x2000); the header adds the 4:4:4 formats (0x8800)
        assertThat(java).isEqualTo(0x2200)
        assertThat(header).isEqualTo(0xAA00L)
    }

    @Test
    fun `the canonical shadow shadows every native`() {
        val shadowed =
            ShadowRecordingBridge::class
                .java
                .declaredMethods
                .map { it.name + Type.getMethodDescriptor(it) }
                .toSet()

        val missing =
            MoonBridgeClassFile.natives
                .filter { it.name + it.descriptor !in shadowed }
                .map { "${it.name}${it.descriptor}" }

        assertThat(missing).isEmpty()
    }

    /** Whether the C parameters and result [export] declares are those of the JVM [descriptor]. */
    private fun agrees(export: Export, descriptor: String): Boolean {
        val (parameters, result) = split(descriptor)
        val declared = export.params.drop(2)
        return declared.size == parameters.size &&
            declared.zip(parameters).all { (c, java) -> sameType(c, java) } &&
            sameType(export.returnType, result)
    }

    private fun sameType(c: String, java: String): Boolean =
        if (c == "jobject") java.startsWith("L") || java.startsWith("[")
        else (JNI_TYPES[c] ?: error("Unmapped JNI type $c: add it to JNI_TYPES")) == java

    /** The parameter descriptors and the result descriptor of a method descriptor. */
    private fun split(descriptor: String): Pair<List<String>, String> {
        val parameters = mutableListOf<String>()
        var at = 1
        while (descriptor[at] != ')') {
            val start = at
            while (descriptor[at] == '[') at++
            at = if (descriptor[at] == 'L') descriptor.indexOf(';', at) + 1 else at + 1
            parameters += descriptor.substring(start, at)
        }
        return parameters to descriptor.substring(at + 1)
    }

    private fun widthMask(descriptor: String): Long =
        when (descriptor) {
            "B" -> 0xFFL
            "S" -> 0xFFFFL
            "I" -> 0xFFFFFFFFL
            else -> error("Unexpected constant type $descriptor")
        }

    private companion object {
        const val CALLBACKS = 22

        val WHITESPACE = Regex("\\s+")
        val EXPORT =
            Regex(
                "JNIEXPORT\\s+(\\w+)\\s+JNICALL\\s+Java_com_limelight_nvstream_jni_MoonBridge_(\\w+)" +
                    "\\s*\\(([^)]*)\\)"
            )
        val LOOKUP =
            Regex(
                "GetStaticMethodID\\s*\\(\\s*[^,]+,\\s*[^,]+,\\s*\"([^\"]+)\"\\s*,\\s*\"([^\"]+)\"\\s*\\)"
            )

        val JNI_TYPES =
            mapOf(
                "void" to "V",
                "jboolean" to "Z",
                "jbyte" to "B",
                "jchar" to "C",
                "jshort" to "S",
                "jint" to "I",
                "jlong" to "J",
                "jfloat" to "F",
                "jdouble" to "D",
                "jstring" to "Ljava/lang/String;",
                "jbooleanArray" to "[Z",
                "jbyteArray" to "[B",
                "jcharArray" to "[C",
                "jshortArray" to "[S",
                "jintArray" to "[I",
                "jlongArray" to "[J",
                "jfloatArray" to "[F",
                "jdoubleArray" to "[D",
            )
    }
}

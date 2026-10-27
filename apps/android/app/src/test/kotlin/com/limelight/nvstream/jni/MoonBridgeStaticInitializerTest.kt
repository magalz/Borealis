package com.limelight.nvstream.jni

import com.google.common.truth.Truth.assertThat
import java.util.concurrent.CopyOnWriteArrayList
import org.junit.Before
import org.junit.Test
import org.objectweb.asm.ClassReader
import org.objectweb.asm.ClassVisitor
import org.objectweb.asm.ClassWriter
import org.objectweb.asm.MethodVisitor
import org.objectweb.asm.Opcodes

/**
 * Stands in for the two native calls of the static initializer of `MoonBridge`: the load of the
 * `moonlight-core` library and the native `init()`. [MoonBridgeStaticInitializerTest] points the
 * bytecode of the initializer at it.
 */
internal object StaticInitializerProbe {
    val events = CopyOnWriteArrayList<String>()

    @Suppress("unused")
    @JvmStatic
    fun loadLibrary(name: String) {
        events += "loadLibrary $name"
    }

    @Suppress("unused")
    @JvmStatic
    fun init() {
        events += "init"
    }
}

/**
 * What the static initializer of `MoonBridge` does. It cannot run in a unit test as it is: it loads
 * the native `moonlight-core` library and calls the native `init()`, which caches the callbacks for
 * the C code. This test loads a copy of the class whose initializer calls [StaticInitializerProbe]
 * instead of those two natives, runs it, and reads what it left: the library name, the order of the
 * two calls, and the three audio configurations. A wrong library name, a missing `init()` or a
 * wrong audio configuration would otherwise show only on a device.
 *
 * Plain JUnit, like [MoonBridgeJniContractTest]: the class is never initialized in this class loader.
 */
class MoonBridgeStaticInitializerTest {
    /**
     * Defines `MoonBridge` and its nested classes afresh, with the initializer rewritten; every other
     * class comes from the parent, the probe included.
     */
    private class InitializerLoader(parent: ClassLoader) : ClassLoader(parent) {
        override fun loadClass(name: String, resolve: Boolean): Class<*> {
            if (name != MoonBridgeClassFile.NAME && !name.startsWith(MoonBridgeClassFile.NAME + "$")) {
                return super.loadClass(name, resolve)
            }
            synchronized(this) {
                val defined =
                    findLoadedClass(name)
                        ?: MoonBridgeClassFile.bytes(name)
                            .let { if (name == MoonBridgeClassFile.NAME) withProbe(it) else it }
                            .let { defineClass(name, it, 0, it.size) }
                if (resolve) resolveClass(defined)
                return defined
            }
        }

        private fun withProbe(bytes: ByteArray): ByteArray {
            val writer = ClassWriter(0)
            ClassReader(bytes)
                .accept(
                    object : ClassVisitor(Opcodes.ASM9, writer) {
                        override fun visitMethod(
                            access: Int,
                            name: String,
                            descriptor: String,
                            signature: String?,
                            exceptions: Array<out String>?,
                        ): MethodVisitor {
                            val next = super.visitMethod(access, name, descriptor, signature, exceptions)
                            return if (name == "<clinit>") probed(next) else next
                        }
                    },
                    0,
                )
            return writer.toByteArray()
        }

        private fun probed(next: MethodVisitor) =
            object : MethodVisitor(Opcodes.ASM9, next) {
                override fun visitMethodInsn(
                    opcode: Int,
                    owner: String,
                    name: String,
                    descriptor: String,
                    isInterface: Boolean,
                ) {
                    val probe =
                        when {
                            owner == "java/lang/System" && name == "loadLibrary" -> "loadLibrary"
                            owner == MoonBridgeClassFile.INTERNAL_NAME && name == "init" -> "init"
                            else -> null
                        }
                    if (opcode == Opcodes.INVOKESTATIC && probe != null) {
                        super.visitMethodInsn(opcode, PROBE, probe, descriptor, false)
                    } else {
                        super.visitMethodInsn(opcode, owner, name, descriptor, isInterface)
                    }
                }
            }
    }

    @Before
    fun clearEvents() {
        StaticInitializerProbe.events.clear()
    }

    /** `MoonBridge` with its static initializer run, in a class loader of its own. */
    private fun initialized(): Class<*> {
        val loader = InitializerLoader(javaClass.classLoader)
        return Class.forName(MoonBridgeClassFile.NAME, true, loader)
    }

    /** The channel count and channel mask of the audio configuration in [field], and its int. */
    private fun audio(bridge: Class<*>, field: String): Triple<Int, Int, Int> {
        val value = checkNotNull(bridge.getField(field).get(null)) { "$field is null" }
        val type = value.javaClass
        return Triple(
            type.getField("channelCount").getInt(value),
            type.getField("channelMask").getInt(value),
            type.getMethod("toInt").invoke(value) as Int,
        )
    }

    @Test
    fun `the initializer loads moonlight-core and then initializes the native side, once each`() {
        initialized()

        assertThat(StaticInitializerProbe.events).containsExactly("loadLibrary moonlight-core", "init").inOrder()
    }

    @Test
    fun `the three audio configurations are stereo, 5-1 and 7-1 as in Limelight h`() {
        val bridge = initialized()

        assertThat(audio(bridge, "AUDIO_CONFIGURATION_STEREO"))
            .isEqualTo(Triple(2, 0x3, macro("STEREO")))
        assertThat(audio(bridge, "AUDIO_CONFIGURATION_51_SURROUND"))
            .isEqualTo(Triple(6, 0x3F, macro("51_SURROUND")))
        assertThat(audio(bridge, "AUDIO_CONFIGURATION_71_SURROUND"))
            .isEqualTo(Triple(8, 0x63F, macro("71_SURROUND")))
    }

    @Test
    fun `the audio configurations are the macro MAKE_AUDIO_CONFIGURATION of Limelight h`() {
        val header = NativeSources.limelightHeader

        // The formula that AudioConfiguration.toInt implements, and the three values it is applied to
        assertThat(header.replace(Regex("\\s+"), " "))
            .contains("(((channelMask) << 16) | (channelCount << 8) | 0xCA)")
        assertThat(macro("STEREO")).isEqualTo(0x000302CA)
        assertThat(macro("51_SURROUND")).isEqualTo(0x003F06CA)
        assertThat(macro("71_SURROUND")).isEqualTo(0x063F08CA)
    }

    /** The value of `AUDIO_CONFIGURATION_<name>` in Limelight.h, computed from its macro. */
    private fun macro(name: String): Int {
        val definition =
            Regex("#define AUDIO_CONFIGURATION_$name MAKE_AUDIO_CONFIGURATION\\((\\d+), 0x([0-9A-F]+)\\)")
                .find(NativeSources.limelightHeader)
        val (count, mask) = checkNotNull(definition) { name }.destructured
        return (mask.toInt(HEX) shl MASK_SHIFT) or (count.toInt() shl COUNT_SHIFT) or MAGIC
    }

    private companion object {
        const val PROBE = "com/limelight/nvstream/jni/StaticInitializerProbe"
        const val HEX = 16
        const val MASK_SHIFT = 16
        const val COUNT_SHIFT = 8
        const val MAGIC = 0xCA
    }
}

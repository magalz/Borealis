package com.limelight.nvstream.jni

import org.objectweb.asm.ClassReader
import org.objectweb.asm.ClassVisitor
import org.objectweb.asm.FieldVisitor
import org.objectweb.asm.MethodVisitor
import org.objectweb.asm.Opcodes

/**
 * The compiled `MoonBridge`, read as bytes. `MoonBridge` cannot be initialised in a unit test: its
 * static initialiser loads the native `moonlight-core` library. Reading the class file never loads
 * or initialises the class, and it sees the modifiers that the instrumentation of Robolectric
 * hides (it turns a native method into a plain one), so the tests of the JNI contract use it. The
 * ASM library comes with Robolectric.
 */
internal object MoonBridgeClassFile {
    const val NAME = "com.limelight.nvstream.jni.MoonBridge"
    const val INTERNAL_NAME = "com/limelight/nvstream/jni/MoonBridge"

    /** One method as the class file declares it: its modifiers, name and JVM descriptor. */
    data class Method(val access: Int, val name: String, val descriptor: String) {
        val isStatic: Boolean
            get() = access and Opcodes.ACC_STATIC != 0

        val isNative: Boolean
            get() = access and Opcodes.ACC_NATIVE != 0
    }

    /** One constant: a static final field with a value in the class file (`const val`). */
    data class Constant(val name: String, val descriptor: String, val value: Int)

    /** The bytes of the class [name], from the class path, without loading the class. */
    fun bytes(name: String = NAME): ByteArray {
        val resource = name.replace('.', '/') + ".class"
        val stream = checkNotNull(javaClass.classLoader.getResourceAsStream(resource)) { resource }
        return stream.use { it.readBytes() }
    }

    val methods: List<Method> by lazy {
        buildList {
            ClassReader(bytes())
                .accept(
                    object : ClassVisitor(Opcodes.ASM9) {
                        override fun visitMethod(
                            access: Int,
                            name: String,
                            descriptor: String,
                            signature: String?,
                            exceptions: Array<out String>?,
                        ): MethodVisitor? {
                            add(Method(access, name, descriptor))
                            return null
                        }
                    },
                    ClassReader.SKIP_CODE or ClassReader.SKIP_DEBUG or ClassReader.SKIP_FRAMES,
                )
        }
    }

    val natives: List<Method>
        get() = methods.filter { it.isNative }

    /** The public `Int`, `Short` and `Byte` constants, by name. */
    val constants: Map<String, Constant> by lazy {
        buildMap {
            ClassReader(bytes())
                .accept(
                    object : ClassVisitor(Opcodes.ASM9) {
                        override fun visitField(
                            access: Int,
                            name: String,
                            descriptor: String,
                            signature: String?,
                            value: Any?,
                        ): FieldVisitor? {
                            if (value is Int && access and Opcodes.ACC_PUBLIC != 0) {
                                put(name, Constant(name, descriptor, value))
                            }
                            return null
                        }
                    },
                    ClassReader.SKIP_CODE or ClassReader.SKIP_DEBUG or ClassReader.SKIP_FRAMES,
                )
        }
    }
}

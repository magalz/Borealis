package io.github.magalz.borealis.computerstore

import android.content.Context
import com.google.common.truth.Truth.assertThat
import com.limelight.computers.ComputerDatabaseManager
import com.limelight.computers.IdentityManager
import java.lang.reflect.Constructor
import java.lang.reflect.Method
import java.lang.reflect.Modifier
import org.junit.Test

/**
 * The JVM members that `ComputerManagerService` (Java) uses on the saved host list and on the
 * client id: they keep their names, their signatures and their `throws`, whatever language the
 * classes are written in.
 */
class ComputerStoreJavaVisibleApiTest {
    /** `[static] Return name(Parameters) [throws Exceptions]`, with simple class names. */
    private fun describe(method: Method): String =
        buildString {
            if (Modifier.isStatic(method.modifiers)) append("static ")
            append(method.returnType.simpleName).append(' ').append(method.name)
            append(method.parameterTypes.joinToString(prefix = "(", postfix = ")") { it.simpleName })
            if (method.exceptionTypes.isNotEmpty()) {
                append(" throws ").append(method.exceptionTypes.joinToString { it.simpleName })
            }
        }

    private fun publicMethods(type: Class<*>): List<String> =
        type.declaredMethods
            .filter { Modifier.isPublic(it.modifiers) && !it.isSynthetic }
            .map(::describe)

    private fun publicConstructors(type: Class<*>): List<String> =
        type.declaredConstructors
            .filter { Modifier.isPublic(it.modifiers) && !it.isSynthetic }
            .map { constructor: Constructor<*> ->
                constructor.parameterTypes.joinToString(prefix = "(", postfix = ")") { it.simpleName }
            }

    @Test
    fun `the saved host list keeps its public methods, its static JSON helpers and their throws`() {
        assertThat(publicMethods(ComputerDatabaseManager::class.java))
            .containsExactly(
                "void close()",
                "void deleteComputer(ComputerDetails)",
                "boolean updateComputer(ComputerDetails)",
                "List getAllComputers()",
                "ComputerDetails getComputerByName(String)",
                "ComputerDetails getComputerByUUID(String)",
                "static JSONObject tupleToJson(AddressTuple) throws JSONException",
                "static AddressTuple tupleFromJson(JSONObject, String) throws JSONException",
            )
    }

    @Test
    fun `the saved host list has one public constructor that takes a context`() {
        assertThat(publicConstructors(ComputerDatabaseManager::class.java)).containsExactly("(Context)")
        assertThat(Modifier.isPublic(ComputerDatabaseManager::class.java.modifiers)).isTrue()
        assertThat(
                ComputerDatabaseManager::class.java.getConstructor(Context::class.java).exceptionTypes.toList()
            )
            .isEmpty()
    }

    @Test
    fun `the client id has one public constructor that takes a context, and a getter`() {
        assertThat(publicConstructors(IdentityManager::class.java)).containsExactly("(Context)")
        assertThat(publicMethods(IdentityManager::class.java)).containsExactly("String getUniqueId()")
        assertThat(Modifier.isPublic(IdentityManager::class.java.modifiers)).isTrue()
    }
}

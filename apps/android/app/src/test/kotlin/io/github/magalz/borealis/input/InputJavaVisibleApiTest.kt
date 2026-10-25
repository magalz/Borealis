package io.github.magalz.borealis.input

import android.app.Activity
import android.hardware.input.InputManager
import android.view.MotionEvent
import android.view.View
import com.google.common.truth.Truth.assertThat
import com.limelight.binding.input.KeyboardTranslator
import com.limelight.binding.input.capture.AndroidNativePointerCaptureProvider
import com.limelight.binding.input.capture.AndroidPointerIconCaptureProvider
import com.limelight.binding.input.capture.InputCaptureManager
import com.limelight.binding.input.capture.InputCaptureProvider
import java.lang.reflect.Modifier
import org.junit.Test

/**
 * The JVM members that `Game` (Java) uses of the keyboard translator and the capture providers:
 * they keep their names, their signatures and their modifiers, whatever language the classes are
 * written in.
 */
class InputJavaVisibleApiTest {
    private fun Class<*>.publicMethod(name: String, vararg types: Class<*>) =
        getMethod(name, *types)

    @Test
    fun `the translator is a public class with a public constructor that listens to input devices`() {
        val translator = KeyboardTranslator::class.java

        assertThat(Modifier.isPublic(translator.modifiers)).isTrue()
        assertThat(Modifier.isAbstract(translator.modifiers)).isFalse()
        assertThat(translator.interfaces.toList())
            .containsExactly(InputManager.InputDeviceListener::class.java)
        assertThat(Modifier.isPublic(translator.getConstructor().modifiers)).isTrue()
    }

    @Test
    fun `the translator translates and asks about normalized keys with ints`() {
        val translate =
            KeyboardTranslator::class.java.publicMethod(
                "translate",
                Int::class.javaPrimitiveType!!,
                Int::class.javaPrimitiveType!!,
            )
        val normalized =
            KeyboardTranslator::class.java.publicMethod(
                "hasNormalizedMapping",
                Int::class.javaPrimitiveType!!,
                Int::class.javaPrimitiveType!!,
            )

        assertThat(translate.returnType).isEqualTo(Short::class.javaPrimitiveType)
        assertThat(normalized.returnType).isEqualTo(Boolean::class.javaPrimitiveType)
        assertThat(Modifier.isStatic(translate.modifiers)).isFalse()
        assertThat(Modifier.isStatic(normalized.modifiers)).isFalse()
        assertThat(translate.exceptionTypes).isEmpty()
    }

    @Test
    fun `the translator keeps the virtual-key constants that are public`() {
        val expected =
            mapOf(
                "VK_0" to 48,
                "VK_A" to 65,
                "VK_NUMPAD0" to 96,
                "VK_CAPS_LOCK" to 20,
                "VK_CLEAR" to 12,
                "VK_BACK_SPACE" to 8,
                "VK_ESCAPE" to 27,
                "VK_F1" to 112,
                "VK_F13" to 0x7C,
                "VK_END" to 35,
                "VK_HOME" to 36,
                "VK_NUM_LOCK" to 144,
                "VK_PAGE_UP" to 33,
                "VK_PAGE_DOWN" to 34,
                "VK_SCROLL_LOCK" to 145,
                "VK_SPACE" to 32,
                "VK_PRINTSCREEN" to 154,
                "VK_TAB" to 9,
                "VK_LEFT" to 37,
                "VK_RIGHT" to 39,
                "VK_UP" to 38,
                "VK_DOWN" to 40,
                "VK_BACK_QUOTE" to 192,
                "VK_PAUSE" to 19,
            )

        for ((name, value) in expected) {
            val field = KeyboardTranslator::class.java.getField(name)

            assertThat(Modifier.isStatic(field.modifiers)).isTrue()
            assertThat(Modifier.isFinal(field.modifiers)).isTrue()
            assertThat(field.type).isEqualTo(Int::class.javaPrimitiveType)
            assertThat(field.getInt(null)).isEqualTo(value)
        }
    }

    @Test
    fun `the manager has one static method that makes the provider of an activity`() {
        val method = InputCaptureManager::class.java.publicMethod("getInputCaptureProvider", Activity::class.java)

        assertThat(Modifier.isStatic(method.modifiers)).isTrue()
        assertThat(method.returnType).isEqualTo(InputCaptureProvider::class.java)
        assertThat(method.exceptionTypes).isEmpty()
    }

    @Test
    fun `the providers are public, extend one another, and the two that are extended are open`() {
        val base = InputCaptureProvider::class.java
        val icon = AndroidPointerIconCaptureProvider::class.java
        val nativeProvider = AndroidNativePointerCaptureProvider::class.java

        assertThat(Modifier.isAbstract(base.modifiers)).isTrue()
        for (type in listOf(base, icon, nativeProvider)) {
            assertThat(Modifier.isPublic(type.modifiers)).isTrue()
        }
        // Nothing extends the native provider, so it does not have to stay open.
        for (type in listOf(base, icon)) {
            assertThat(Modifier.isFinal(type.modifiers)).isFalse()
        }
        assertThat(icon.superclass).isEqualTo(base)
        assertThat(nativeProvider.superclass).isEqualTo(icon)
        assertThat(nativeProvider.interfaces.toList())
            .containsExactly(InputManager.InputDeviceListener::class.java)
        for (type in listOf(icon, nativeProvider)) {
            val constructor = type.getConstructor(Activity::class.java, View::class.java)
            assertThat(Modifier.isPublic(constructor.modifiers)).isTrue()
        }
    }

    @Test
    fun `the base provider has two protected flags and the public methods that Game calls`() {
        val base = InputCaptureProvider::class.java
        for (name in listOf("isCapturing", "isCursorVisible")) {
            val field = base.getDeclaredField(name)

            assertThat(Modifier.isProtected(field.modifiers)).isTrue()
            assertThat(Modifier.isStatic(field.modifiers)).isFalse()
            assertThat(Modifier.isFinal(field.modifiers)).isFalse()
            assertThat(field.type).isEqualTo(Boolean::class.javaPrimitiveType)
        }

        for (name in listOf("enableCapture", "disableCapture", "destroy", "showCursor", "hideCursor")) {
            val method = base.publicMethod(name)
            assertThat(method.returnType).isEqualTo(Void.TYPE)
        }
        assertThat(base.publicMethod("isCapturingActive").returnType)
            .isEqualTo(Boolean::class.javaPrimitiveType)
        assertThat(
                base.publicMethod("eventHasRelativeMouseAxes", MotionEvent::class.java).returnType
            )
            .isEqualTo(Boolean::class.javaPrimitiveType)
        for (name in listOf("getRelativeAxisX", "getRelativeAxisY")) {
            assertThat(base.publicMethod(name, MotionEvent::class.java).returnType)
                .isEqualTo(Float::class.javaPrimitiveType)
        }
        assertThat(base.publicMethod("onWindowFocusChanged", Boolean::class.javaPrimitiveType!!).returnType)
            .isEqualTo(Void.TYPE)
    }
}

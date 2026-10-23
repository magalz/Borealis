package io.github.magalz.borealis.support

import com.google.common.truth.Truth.assertThat
import com.limelight.utils.Vector2d
import java.lang.reflect.Modifier
import org.junit.Test

/**
 * The mutable vector that `ControllerHandler` (Java) reuses for the dead zone of the sticks: it
 * calls the constructor, `initialize`, `getMagnitude`, `getX` and `getY`. The magnitude is computed
 * in `double` from the two floats, and every setter computes it again.
 */
class Vector2dCharacterizationTest {
    private fun vector(x: Float, y: Float) = Vector2d().also { it.initialize(x, y) }

    @Test
    fun `a new vector is the origin`() {
        val vector = Vector2d()

        assertThat(vector.x).isEqualTo(0f)
        assertThat(vector.y).isEqualTo(0f)
        assertThat(vector.magnitude).isEqualTo(0.0)
    }

    @Test
    fun `initialize sets the two components and the magnitude`() {
        val vector = vector(3f, 4f)

        assertThat(vector.x).isEqualTo(3f)
        assertThat(vector.y).isEqualTo(4f)
        assertThat(vector.magnitude).isEqualTo(5.0)
    }

    @Test
    fun `initialize again replaces what was there`() {
        val vector = vector(3f, 4f)

        vector.initialize(6f, 8f)

        assertThat(vector.x).isEqualTo(6f)
        assertThat(vector.y).isEqualTo(8f)
        assertThat(vector.magnitude).isEqualTo(10.0)
    }

    @Test
    fun `the magnitude of negative components is positive`() {
        val vector = vector(-3f, -4f)

        assertThat(vector.x).isEqualTo(-3f)
        assertThat(vector.y).isEqualTo(-4f)
        assertThat(vector.magnitude).isEqualTo(5.0)
    }

    @Test
    fun `the magnitude is the square root of the sum of the squares, taken in double`() {
        assertThat(vector(1f, 1f).magnitude).isEqualTo(Math.sqrt(2.0))
        assertThat(vector(0f, -2f).magnitude).isEqualTo(2.0)
        // 0.1f is 0.10000000149011612 as a double: the squares are not taken in float.
        assertThat(vector(0.1f, 0f).magnitude).isWithin(1e-15).of(0.10000000149011612)
    }

    @Test
    fun `the components are kept as the floats that were given`() {
        val vector = vector(0.1f, -0.7f)

        assertThat(vector.x).isEqualTo(0.1f)
        assertThat(vector.y).isEqualTo(-0.7f)
    }

    @Test
    fun `a component that is not a number makes the magnitude not a number`() {
        assertThat(vector(Float.NaN, 1f).magnitude).isNaN()
        assertThat(vector(1f, Float.NaN).magnitude).isNaN()
    }

    @Test
    fun `an infinite component makes the magnitude infinite`() {
        assertThat(vector(Float.POSITIVE_INFINITY, 1f).magnitude).isPositiveInfinity()
        assertThat(vector(1f, Float.NEGATIVE_INFINITY).magnitude).isPositiveInfinity()
    }

    @Test
    fun `the squares of the largest floats do not overflow`() {
        assertThat(vector(Float.MAX_VALUE, 0f).magnitude).isEqualTo(Float.MAX_VALUE.toDouble())
    }

    @Test
    fun `a vector is normalized into the vector that is given, and itself stays as it is`() {
        val vector = vector(3f, 4f)
        val into = Vector2d()

        vector.getNormalized(into)

        assertThat(into.x).isEqualTo(0.6f)
        assertThat(into.y).isEqualTo(0.8f)
        assertThat(into.magnitude).isWithin(1e-6).of(1.0)
        assertThat(vector.x).isEqualTo(3f)
        assertThat(vector.y).isEqualTo(4f)
        assertThat(vector.magnitude).isEqualTo(5.0)
    }

    @Test
    fun `normalizing divides in double and narrows to float after`() {
        val into = Vector2d()

        vector(1f, 2f).getNormalized(into)

        // 1 / sqrt(5) and 2 / sqrt(5), narrowed from double.
        assertThat(into.x).isEqualTo((1.0 / Math.sqrt(5.0)).toFloat())
        assertThat(into.y).isEqualTo((2.0 / Math.sqrt(5.0)).toFloat())
    }

    @Test
    fun `a negative component stays negative when normalized`() {
        val into = Vector2d()

        vector(-3f, 4f).getNormalized(into)

        assertThat(into.x).isEqualTo(-0.6f)
        assertThat(into.y).isEqualTo(0.8f)
    }

    @Test
    fun `a vector can be normalized into itself`() {
        val vector = vector(3f, 4f)

        vector.getNormalized(vector)

        assertThat(vector.x).isEqualTo(0.6f)
        assertThat(vector.y).isEqualTo(0.8f)
        assertThat(vector.magnitude).isWithin(1e-6).of(1.0)
    }

    @Test
    fun `the zero vector normalizes to not a number, in both components and the magnitude`() {
        val into = vector(1f, 1f)

        Vector2d().getNormalized(into)

        assertThat(into.x).isNaN()
        assertThat(into.y).isNaN()
        assertThat(into.magnitude).isNaN()
    }

    @Test
    fun `a vector with an infinite component normalizes to not a number`() {
        val into = Vector2d()

        vector(Float.POSITIVE_INFINITY, 1f).getNormalized(into)

        assertThat(into.x).isNaN()
        assertThat(into.y).isEqualTo(0f)
    }

    @Test
    fun `a scalar multiplies both components and the magnitude follows`() {
        val vector = vector(3f, 4f)

        vector.scalarMultiply(2.0)

        assertThat(vector.x).isEqualTo(6f)
        assertThat(vector.y).isEqualTo(8f)
        assertThat(vector.magnitude).isEqualTo(10.0)
    }

    @Test
    fun `a negative scalar turns the vector around`() {
        val vector = vector(3f, -4f)

        vector.scalarMultiply(-1.0)

        assertThat(vector.x).isEqualTo(-3f)
        assertThat(vector.y).isEqualTo(4f)
        assertThat(vector.magnitude).isEqualTo(5.0)
    }

    @Test
    fun `the product is taken in double and narrowed to float`() {
        val vector = vector(1f, 3f)

        vector.scalarMultiply(0.1)

        assertThat(vector.x).isEqualTo((1.0f * 0.1).toFloat())
        assertThat(vector.y).isEqualTo((3.0f * 0.1).toFloat())
        assertThat(vector.x).isEqualTo(0.1f)
    }

    @Test
    fun `a product that does not fit a float becomes infinite`() {
        val vector = vector(1f, -1f)

        vector.scalarMultiply(1e300)

        assertThat(vector.x).isPositiveInfinity()
        assertThat(vector.y).isNegativeInfinity()
        assertThat(vector.magnitude).isPositiveInfinity()
    }

    @Test
    fun `a scalar that is not a number makes the vector not a number`() {
        val vector = vector(1f, 2f)

        vector.scalarMultiply(Double.NaN)

        assertThat(vector.x).isNaN()
        assertThat(vector.y).isNaN()
        assertThat(vector.magnitude).isNaN()
    }

    @Test
    fun `a scalar of zero gives the origin, and keeps the sign of a zero`() {
        val vector = vector(3f, -4f)

        vector.scalarMultiply(0.0)

        assertThat(vector.x).isEqualTo(0f)
        assertThat(vector.y).isEqualTo(-0f)
        assertThat(vector.magnitude).isEqualTo(0.0)
    }

    @Test
    fun `setting x keeps y and computes the magnitude again`() {
        val vector = vector(3f, 4f)

        vector.x = 6f

        assertThat(vector.x).isEqualTo(6f)
        assertThat(vector.y).isEqualTo(4f)
        assertThat(vector.magnitude).isEqualTo(Math.sqrt(52.0))
    }

    @Test
    fun `setting y keeps x and computes the magnitude again`() {
        val vector = vector(3f, 4f)

        vector.y = 0f

        assertThat(vector.x).isEqualTo(3f)
        assertThat(vector.y).isEqualTo(0f)
        assertThat(vector.magnitude).isEqualTo(3.0)
    }

    @Test
    fun `the zero vector is one instance that every caller shares`() {
        assertThat(Vector2d.ZERO).isSameInstanceAs(Vector2d.ZERO)
        assertThat(Vector2d.ZERO.x).isEqualTo(0f)
        assertThat(Vector2d.ZERO.y).isEqualTo(0f)
        assertThat(Vector2d.ZERO.magnitude).isEqualTo(0.0)
    }

    @Test
    fun `the zero vector can be changed, and then it is changed for everybody`() {
        val zero = Vector2d.ZERO
        try {
            zero.initialize(1f, 2f)

            assertThat(Vector2d.ZERO.x).isEqualTo(1f)
            assertThat(Vector2d.ZERO.y).isEqualTo(2f)
            assertThat(Vector2d.ZERO.magnitude).isEqualTo(Math.sqrt(5.0))
        } finally {
            zero.initialize(0f, 0f)
        }
        assertThat(Vector2d.ZERO.x).isEqualTo(0f)
    }

    @Test
    fun `the zero vector is a public static final field, as the Java callers read it`() {
        val field = Vector2d::class.java.getField("ZERO")

        assertThat(Modifier.isStatic(field.modifiers)).isTrue()
        assertThat(Modifier.isFinal(field.modifiers)).isTrue()
        assertThat(field.type).isEqualTo(Vector2d::class.java)
    }

    @Test
    fun `a vector has the members that the Java callers use, and no others`() {
        val methods =
            Vector2d::class
                .java
                .declaredMethods
                .filter { Modifier.isPublic(it.modifiers) && !it.isSynthetic }
                .map { it.name to it.parameterTypes.toList() + it.returnType }
                .toSet()

        val floatType = Float::class.javaPrimitiveType!!
        val doubleType = Double::class.javaPrimitiveType!!
        val voidType = Void.TYPE
        assertThat(methods)
            .containsExactly(
                "initialize" to listOf(floatType, floatType, voidType),
                "getMagnitude" to listOf(doubleType),
                "getNormalized" to listOf(Vector2d::class.java, voidType),
                "scalarMultiply" to listOf(doubleType, voidType),
                "setX" to listOf(floatType, voidType),
                "setY" to listOf(floatType, voidType),
                "getX" to listOf(floatType),
                "getY" to listOf(floatType),
            )
        assertThat(Vector2d::class.java.getConstructor().modifiers and Modifier.PUBLIC)
            .isEqualTo(Modifier.PUBLIC)
    }

    @Test
    fun `a vector is a plain class, compared and hashed by identity`() {
        val a = vector(1f, 2f)
        val b = vector(1f, 2f)

        assertThat(a.equals(b)).isFalse()
        assertThat(a.hashCode()).isEqualTo(System.identityHashCode(a))
        assertThat(a.toString()).startsWith("com.limelight.utils.Vector2d@")
        assertThat(Vector2d::class.java.declaredMethods.map { it.name })
            .containsNoneOf("copy", "component1", "component2", "equals", "hashCode", "toString")
    }
}

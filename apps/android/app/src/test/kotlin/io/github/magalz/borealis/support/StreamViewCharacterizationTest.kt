package io.github.magalz.borealis.support

import android.content.Context
import android.util.AttributeSet
import android.view.KeyEvent
import android.view.SurfaceView
import android.view.View.MeasureSpec
import androidx.test.core.app.ApplicationProvider
import com.google.common.truth.Truth.assertThat
import com.limelight.ui.StreamView
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner

/**
 * The surface of the stream: it keeps the aspect ratio of the video, and the IME cannot take keys.
 */
@RunWith(RobolectricTestRunner::class)
class StreamViewCharacterizationTest {
    private val context = ApplicationProvider.getApplicationContext<Context>()
    private val view = StreamView(context)

    private fun measure(
        width: Int,
        height: Int,
        widthMode: Int = MeasureSpec.EXACTLY,
        heightMode: Int = MeasureSpec.EXACTLY,
    ): Pair<Int, Int> {
        view.measure(
            MeasureSpec.makeMeasureSpec(width, widthMode),
            MeasureSpec.makeMeasureSpec(height, heightMode),
        )
        return view.measuredWidth to view.measuredHeight
    }

    @Test
    fun `without an aspect ratio the view measures as a plain surface view`() {
        assertThat(measure(800, 600)).isEqualTo(800 to 600)
        assertThat(measure(800, 600, MeasureSpec.AT_MOST, MeasureSpec.AT_MOST))
            .isEqualTo(800 to 600)
        // Nothing asks for a size and the surface has no content: it is empty.
        assertThat(measure(800, 600, MeasureSpec.UNSPECIFIED, MeasureSpec.UNSPECIFIED))
            .isEqualTo(0 to 0)
    }

    @Test
    fun `an aspect ratio of zero is the same as none, and is never divided by`() {
        view.setDesiredAspectRatio(0.0)

        assertThat(measure(1000, 500)).isEqualTo(1000 to 500)
    }

    @Test
    fun `setting the aspect ratio back to zero measures as a plain surface view again`() {
        view.setDesiredAspectRatio(2.0)
        assertThat(measure(1000, 1000)).isEqualTo(1000 to 500)

        view.setDesiredAspectRatio(0.0)

        assertThat(measure(1000, 1000)).isEqualTo(1000 to 1000)
    }

    @Test
    fun `a view that is wider than the ratio takes the height and the width that follows`() {
        view.setDesiredAspectRatio(2.0)

        assertThat(measure(1000, 300)).isEqualTo(600 to 300)
    }

    @Test
    fun `a view that is taller than the ratio takes the width and the height that follows`() {
        view.setDesiredAspectRatio(2.0)

        assertThat(measure(1000, 1000)).isEqualTo(1000 to 500)
    }

    @Test
    fun `a view that has exactly the ratio keeps its size`() {
        view.setDesiredAspectRatio(2.0)

        assertThat(measure(600, 300)).isEqualTo(600 to 300)
    }

    @Test
    fun `the dimension that follows is truncated, not rounded`() {
        view.setDesiredAspectRatio(16.0 / 9.0)
        // 1000 / 1.7777... is 562.5.
        assertThat(measure(1000, 1000)).isEqualTo(1000 to 562)

        view.setDesiredAspectRatio(1.5)
        // 33 * 1.5 is 49.5.
        assertThat(measure(100, 33)).isEqualTo(49 to 33)
    }

    @Test
    fun `a portrait ratio narrows the view`() {
        view.setDesiredAspectRatio(0.75)

        assertThat(measure(1000, 400)).isEqualTo(300 to 400)
        assertThat(measure(300, 1000)).isEqualTo(300 to 400)
    }

    @Test
    fun `only the size of the specs counts, not their mode`() {
        view.setDesiredAspectRatio(2.0)

        assertThat(measure(1000, 1000, MeasureSpec.AT_MOST, MeasureSpec.AT_MOST))
            .isEqualTo(1000 to 500)
        assertThat(measure(1000, 1000, MeasureSpec.UNSPECIFIED, MeasureSpec.UNSPECIFIED))
            .isEqualTo(1000 to 500)
    }

    @Test
    fun `a view with no room measures empty`() {
        view.setDesiredAspectRatio(2.0)

        assertThat(measure(0, 0)).isEqualTo(0 to 0)
        assertThat(measure(0, 1000)).isEqualTo(0 to 0)
        assertThat(measure(1000, 0)).isEqualTo(0 to 0)
    }

    @Test
    fun `a ratio that is not a number leaves the full width and no height`() {
        view.setDesiredAspectRatio(Double.NaN)

        assertThat(measure(1000, 1000)).isEqualTo(1000 to 0)
    }

    @Test
    fun `an infinite ratio leaves the full width and no height`() {
        view.setDesiredAspectRatio(Double.POSITIVE_INFINITY)

        assertThat(measure(1000, 1000)).isEqualTo(1000 to 0)
    }

    @Test
    fun `a ratio so small that the width truncates to zero measures a sliver`() {
        view.setDesiredAspectRatio(1e-12)

        assertThat(measure(1000, 1000)).isEqualTo(0 to 1000)
    }

    @Test
    fun `a key before the IME goes to the callbacks, and the IME does not see it when they take it`() {
        val callbacks = RecordingCallbacks(takesDown = true, takesUp = true)
        view.setInputCallbacks(callbacks)

        val down = KeyEvent(KeyEvent.ACTION_DOWN, KeyEvent.KEYCODE_SPACE)
        val up = KeyEvent(KeyEvent.ACTION_UP, KeyEvent.KEYCODE_SPACE)

        assertThat(view.dispatchKeyEventPreIme(down)).isTrue()
        assertThat(view.dispatchKeyEventPreIme(up)).isTrue()
        assertThat(callbacks.events).containsExactly("down", "up").inOrder()
        assertThat(callbacks.keyEvents).containsExactly(down, up).inOrder()
    }

    @Test
    fun `a key that the callbacks refuse is left to the view, which does not take it`() {
        val callbacks = RecordingCallbacks(takesDown = false, takesUp = false)
        view.setInputCallbacks(callbacks)

        assertThat(view.dispatchKeyEventPreIme(KeyEvent(KeyEvent.ACTION_DOWN, KeyEvent.KEYCODE_A)))
            .isFalse()
        assertThat(view.dispatchKeyEventPreIme(KeyEvent(KeyEvent.ACTION_UP, KeyEvent.KEYCODE_A)))
            .isFalse()
        assertThat(callbacks.events).containsExactly("down", "up").inOrder()
    }

    @Test
    fun `down and up are decided by their own callback`() {
        val callbacks = RecordingCallbacks(takesDown = true, takesUp = false)
        view.setInputCallbacks(callbacks)

        assertThat(view.dispatchKeyEventPreIme(KeyEvent(KeyEvent.ACTION_DOWN, KeyEvent.KEYCODE_A)))
            .isTrue()
        assertThat(view.dispatchKeyEventPreIme(KeyEvent(KeyEvent.ACTION_UP, KeyEvent.KEYCODE_A)))
            .isFalse()
    }

    @Test
    fun `an action that is neither down nor up does not reach the callbacks`() {
        val callbacks = RecordingCallbacks(takesDown = true, takesUp = true)
        view.setInputCallbacks(callbacks)

        val multiple = KeyEvent(KeyEvent.ACTION_MULTIPLE, KeyEvent.KEYCODE_A)

        assertThat(view.dispatchKeyEventPreIme(multiple)).isFalse()
        assertThat(callbacks.events).isEmpty()
    }

    @Test
    fun `without callbacks no key is taken`() {
        assertThat(view.dispatchKeyEventPreIme(KeyEvent(KeyEvent.ACTION_DOWN, KeyEvent.KEYCODE_A)))
            .isFalse()
    }

    @Test
    fun `callbacks that were removed are not asked`() {
        val callbacks = RecordingCallbacks(takesDown = true, takesUp = true)
        view.setInputCallbacks(callbacks)
        view.setInputCallbacks(null)

        assertThat(view.dispatchKeyEventPreIme(KeyEvent(KeyEvent.ACTION_DOWN, KeyEvent.KEYCODE_A)))
            .isFalse()
        assertThat(callbacks.events).isEmpty()
    }

    @Test
    fun `the view is a surface view with the four constructors of a view`() {
        assertThat(SurfaceView::class.java.isAssignableFrom(StreamView::class.java)).isTrue()
        assertThat(StreamView::class.java.constructors.map { it.parameterTypes.toList() })
            .containsExactly(
                listOf<Class<*>>(Context::class.java),
                listOf<Class<*>>(Context::class.java, AttributeSet::class.java),
                listOf<Class<*>>(Context::class.java, AttributeSet::class.java, Int::class.java),
                listOf<Class<*>>(
                    Context::class.java,
                    AttributeSet::class.java,
                    Int::class.java,
                    Int::class.java,
                ),
            )
    }

    @Test
    fun `the constructors that the layout inflater and code use build a view`() {
        val attributes = Robolectric.buildAttributeSet().build()

        assertThat(StreamView(context, attributes)).isNotNull()
        assertThat(StreamView(context, attributes, 0)).isNotNull()
        assertThat(StreamView(context, attributes, 0, 0)).isNotNull()
    }

    private class RecordingCallbacks(private val takesDown: Boolean, private val takesUp: Boolean) :
        StreamView.InputCallbacks {
        val events = mutableListOf<String>()
        val keyEvents = mutableListOf<KeyEvent>()

        override fun handleKeyUp(event: KeyEvent): Boolean {
            events += "up"
            keyEvents += event
            return takesUp
        }

        override fun handleKeyDown(event: KeyEvent): Boolean {
            events += "down"
            keyEvents += event
            return takesDown
        }
    }
}

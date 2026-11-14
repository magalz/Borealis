package io.github.magalz.borealis.stream.game.motion

import android.app.Application
import android.view.MotionEvent
import androidx.test.core.app.ApplicationProvider
import com.google.common.truth.Truth.assertThat
import com.limelight.nvstream.input.MouseButtonPacket
import io.github.magalz.borealis.stream.Panel
import io.github.magalz.borealis.stream.StreamPhase
import io.github.magalz.borealis.stream.TouchMode
import io.github.magalz.borealis.stream.game.phase
import io.github.magalz.borealis.stream.game.session
import io.github.magalz.borealis.testing.ANDROID_9
import org.junit.Before
import org.junit.Test
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config

/**
 * The lift of a finger that Android cancelled, on an Android older than 13: the system does not
 * mark such a lift there, so `Game` takes a flagged lift for a normal one. It counts as the end of
 * a tap of three fingers, and it is a touch up, not a cancel. The other motion tests run at the
 * level of `robolectric.properties`, where the two sides of these `SDK_INT` checks are the new
 * ones.
 */
@Config(sdk = [ANDROID_9])
internal class FingerTouchOldAndroidTest : MotionTestBase() {
    private val left = MouseButtonPacket.BUTTON_LEFT

    @Before
    fun grantTheReceiverPermission() {
        val application = ApplicationProvider.getApplicationContext<Application>()
        shadowOf(application)
            .grantPermissions("${application.packageName}.DYNAMIC_RECEIVER_NOT_EXPORTED_PERMISSION")
    }

    private fun cancelledUp(x: Float, y: Float, at: Long) =
        finger(MotionEvent.ACTION_UP, x, y, at) { flags = MotionEvent.FLAG_CANCELED }

    private fun panel(): Panel = (rig.phase as StreamPhase.Streaming).panel

    @Test
    fun `a lift that is flagged as cancelled still ends a tap of three fingers`() {
        streaming()
        val three = listOf(10f to 10f, 20f to 20f, 30f to 30f)
        touch(
            backgroundView,
            fingerEvent(MotionEvent.ACTION_POINTER_DOWN, three, index = 2) { eventTime = TIME },
        )

        assertThat(touch(backgroundView, cancelledUp(30f, 30f, TIME + 100))).isTrue()

        assertThat(panel()).isEqualTo(Panel.KEYBOARD)
        assertThat(sent()).isEmpty()
    }

    @Test
    fun `a touch that ends with a flagged lift is a tap, as the lift is a touch up`() {
        streaming()
        rig.session.setTouchMode(TouchMode.DIRECT)
        touch(streamView, finger(MotionEvent.ACTION_DOWN, 100f, 50f, TIME))

        assertThat(touch(streamView, cancelledUp(100f, 50f, TIME + 50))).isTrue()

        assertThat(sent()).containsExactly(position(100, 50), press(left)).inOrder()
    }

    @Test
    fun `the finger that is left after a flagged lift takes its place as the finger that moves`() {
        streaming()
        touch(streamView, finger(MotionEvent.ACTION_DOWN, 100f, 100f, TIME))
        val both = listOf(100f to 100f, 200f to 200f)
        touch(
            streamView,
            fingerEvent(MotionEvent.ACTION_POINTER_DOWN, both, index = 1) { eventTime = TIME + 10 },
        )
        val lift =
            fingerEvent(MotionEvent.ACTION_POINTER_UP, both, index = 0) {
                eventTime = TIME + 20
                flags = MotionEvent.FLAG_CANCELED
            }
        assertThat(touch(streamView, lift)).isTrue()

        // The lift was a touch up, so the second finger took the place of the first.
        touch(streamView, finger(MotionEvent.ACTION_MOVE, 210f, 200f, TIME + 30))

        assertThat(sent()).containsExactly(moveBy(40, 0))
    }
}

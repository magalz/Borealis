package io.github.magalz.borealis.stream.host

import androidx.test.core.app.ApplicationProvider
import com.google.common.truth.Truth.assertThat
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
class StreamChromeHostPhoneTest {
    private val context = ApplicationProvider.getApplicationContext<android.content.Context>()

    @Test
    fun `a touch screen of phone width gets the docked keyboard`() {
        assertThat(StreamChromeHost.isPhone(context)).isTrue()
    }

    @Test
    @Config(qualifiers = "w900dp-h500dp-notouch-dpad")
    fun `a handheld with a D-pad and no touch screen does not`() {
        assertThat(StreamChromeHost.isPhone(context)).isFalse()
    }
}

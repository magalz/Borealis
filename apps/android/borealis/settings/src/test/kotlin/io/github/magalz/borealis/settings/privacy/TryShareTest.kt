package io.github.magalz.borealis.settings.privacy

import android.content.ActivityNotFoundException
import android.content.Intent
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.google.common.truth.Truth.assertThat
import org.junit.Assert.assertThrows
import org.junit.Test
import org.junit.runner.RunWith

/** A share that the system refuses is reported; any other failure is a bug and is not hidden. */
@RunWith(AndroidJUnit4::class)
class TryShareTest {
    private val chooser = Intent.createChooser(Intent(Intent.ACTION_SEND), "Share diagnostics")

    @Test
    fun `a share that starts is reported as started`() {
        val started = mutableListOf<Intent>()

        val result = tryShare({ started += it }, chooser)

        assertThat(result).isTrue()
        assertThat(started).containsExactly(chooser)
    }

    @Test
    fun `no activity to handle the share is reported as refused`() {
        val result = tryShare({ throw ActivityNotFoundException("no activity") }, chooser)

        assertThat(result).isFalse()
    }

    @Test
    fun `a share the system does not allow is reported as refused`() {
        val result = tryShare({ throw SecurityException("not allowed") }, chooser)

        assertThat(result).isFalse()
    }

    @Test
    fun `a failure that is not the system refusing the share is not swallowed`() {
        assertThrows(IllegalStateException::class.java) {
            tryShare({ throw IllegalStateException("a bug") }, chooser)
        }
    }
}

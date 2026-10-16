package io.github.magalz.borealis.library

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import com.google.common.truth.Truth.assertThat
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class HiddenAppsTest {
    private val context = ApplicationProvider.getApplicationContext<Context>()
    private val prefs = context.getSharedPreferences(FILE, Context.MODE_PRIVATE)
    private lateinit var hidden: HiddenApps

    @Before
    fun setUp() {
        prefs.edit().clear().commit()
        hidden = HiddenApps(context)
    }

    @Test
    fun `nothing is hidden at first`() {
        assertThat(hidden.read("host-1")).isEmpty()
    }

    @Test
    fun `hiding an app writes the string set that the 12 2 app list reads`() {
        hidden.set("host-1", 7, hidden = true)
        hidden.set("host-1", 8, hidden = true)

        assertThat(prefs.getStringSet("host-1", null)).containsExactly("7", "8")
        assertThat(hidden.read("host-1")).containsExactly(7, 8)
    }

    @Test
    fun `an app hidden in the 12 2 app list is hidden here`() {
        prefs.edit().putStringSet("host-1", setOf("3", "12")).commit()

        assertThat(hidden.read("host-1")).containsExactly(3, 12)
    }

    @Test
    fun `showing an app removes it from the set and leaves the others`() {
        prefs.edit().putStringSet("host-1", setOf("3", "12")).commit()

        hidden.set("host-1", 3, hidden = false)

        assertThat(prefs.getStringSet("host-1", null)).containsExactly("12")
    }

    @Test
    fun `an entry that is not a number is ignored`() {
        prefs.edit().putStringSet("host-1", setOf("3", "oops")).commit()

        assertThat(hidden.read("host-1")).containsExactly(3)
    }

    @Test
    fun `the hosts do not share their apps`() {
        hidden.set("host-1", 7, hidden = true)

        assertThat(hidden.read("host-2")).isEmpty()
    }

    @Test
    fun `clearing a host removes its entry`() {
        hidden.set("host-1", 7, hidden = true)
        hidden.set("host-2", 8, hidden = true)

        hidden.clear("host-1")

        assertThat(prefs.contains("host-1")).isFalse()
        assertThat(hidden.read("host-2")).containsExactly(8)
    }

    @Test
    fun `every write changes the revision`() {
        val before = hidden.revision.value

        hidden.set("host-1", 7, hidden = true)
        val afterSet = hidden.revision.value
        hidden.clear("host-1")

        assertThat(afterSet).isGreaterThan(before)
        assertThat(hidden.revision.value).isGreaterThan(afterSet)
    }

    private companion object {
        /** The preference file of the 12.2 app list. */
        const val FILE = "HiddenApps"
    }
}

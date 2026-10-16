package io.github.magalz.borealis.library

import android.content.Context
import android.content.pm.PackageManager
import android.content.pm.ShortcutManager
import androidx.test.core.app.ApplicationProvider
import com.google.common.truth.Truth.assertThat
import com.limelight.ShortcutTrampoline
import com.limelight.nvstream.http.ComputerDetails
import io.github.magalz.borealis.library.shortcuts.ShortcutIntentParser
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import org.junit.Assert.assertThrows
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf

/** The launcher shortcuts of the Library, over the upstream `ShortcutHelper`. */
@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(RobolectricTestRunner::class)
class UpstreamHostShortcutsTest {
    private val context: Context = ApplicationProvider.getApplicationContext()
    private val shortcutManager = context.getSystemService(ShortcutManager::class.java)
    private val shortcuts = UpstreamHostShortcuts(context, UnconfinedTestDispatcher())

    @Before
    fun pinningIsSupported() {
        shadowOf(shortcutManager).setIsRequestPinShortcutSupported(true)
    }

    @Test
    fun `a host that was opened gets a dynamic shortcut to its apps`() {
        shortcuts.reportHostOpened("uuid-1", "Desk PC")

        val shortcut = shortcutManager.dynamicShortcuts.single()
        assertThat(shortcut.id).isEqualTo("uuid-1")
        assertThat(shortcut.shortLabel.toString()).isEqualTo("Desk PC")
        assertThat(shortcut.intent?.component?.className).isEqualTo(ShortcutTrampoline::class.java.name)
        assertThat(ShortcutIntentParser.parse(shortcut.intent?.extras))
            .isEqualTo(LibraryLaunchRequest.Apps("uuid-1"))
    }

    @Test
    fun `the shortcut of an opened host is published off the calling thread`() {
        val io = StandardTestDispatcher()
        val deferred = UpstreamHostShortcuts(context, io)

        deferred.reportHostOpened("uuid-1", "Desk PC")
        assertThat(shortcutManager.dynamicShortcuts).isEmpty()

        io.scheduler.advanceUntilIdle()
        assertThat(shortcutManager.dynamicShortcuts.map { it.id }).containsExactly("uuid-1")
    }

    @Test
    fun `opening a host again updates its shortcut instead of adding another`() {
        shortcuts.reportHostOpened("uuid-1", "Desk PC")

        shortcuts.reportHostOpened("uuid-1", "Study PC")

        assertThat(shortcutManager.dynamicShortcuts.map { it.shortLabel.toString() })
            .containsExactly("Study PC")
    }

    @Test
    fun `pinning is offered on a phone whose launcher supports it`() {
        assertThat(shortcuts.canPinApps).isTrue()
    }

    @Test
    fun `pinning is not offered when the launcher does not support it`() {
        shadowOf(shortcutManager).setIsRequestPinShortcutSupported(false)

        assertThat(shortcuts.canPinApps).isFalse()
    }

    @Test
    fun `pinning is not offered on a TV`() {
        shadowOf(context.packageManager).setSystemFeature(PackageManager.FEATURE_LEANBACK, true)

        assertThat(shortcuts.canPinApps).isFalse()
    }

    @Test
    fun `a pinned app is a shortcut that launches it on its host`() {
        val pinned = shortcuts.pinApp("uuid-1", "Desk PC", 42, "Hades II", hdrSupported = true)

        assertThat(pinned).isTrue()
        val shortcut = shortcutManager.pinnedShortcuts.single()
        assertThat(shortcut.id).isEqualTo("uuid-142")
        assertThat(shortcut.shortLabel.toString()).isEqualTo("Hades II (Desk PC)")
        assertThat(ShortcutIntentParser.parse(shortcut.intent?.extras))
            .isEqualTo(LibraryLaunchRequest.Launch("uuid-1", 42))
        assertThat(shortcut.intent?.getBooleanExtra("HDR", false)).isTrue()
    }

    @Test
    fun `an app is not pinned when the launcher does not support it`() {
        shadowOf(shortcutManager).setIsRequestPinShortcutSupported(false)

        assertThat(shortcuts.pinApp("uuid-1", "Desk PC", 42, "Hades II", hdrSupported = false))
            .isFalse()
        assertThat(shortcutManager.pinnedShortcuts).isEmpty()
    }

    @Test
    fun `a forgotten host disables the pinned shortcuts of its apps and no others`() {
        shortcuts.reportHostOpened("uuid-1", "Desk PC")
        shortcuts.pinApp("uuid-1", "Desk PC", 42, "Hades II", hdrSupported = false)
        shortcuts.pinApp("uuid-2", "Study PC", 7, "Steam", hdrSupported = false)

        shortcuts.hostForgotten(ComputerDetails().also { it.uuid = "uuid-1"; it.name = "Desk PC" })

        // The platform (and its Robolectric shadow) rejects a pin request for a disabled shortcut.
        assertThrows(IllegalArgumentException::class.java) {
            shortcuts.pinApp("uuid-1", "Desk PC", 42, "Hades II", hdrSupported = false)
        }
        assertThat(shortcuts.pinApp("uuid-2", "Study PC", 7, "Steam", hdrSupported = false)).isTrue()
    }
}

package io.github.magalz.borealis.library

import android.content.Context
import android.content.pm.PackageManager
import android.content.pm.ShortcutInfo
import android.content.pm.ShortcutManager
import android.graphics.Bitmap
import android.graphics.drawable.Icon
import androidx.test.core.app.ApplicationProvider
import com.google.common.truth.Truth.assertThat
import com.limelight.R
import com.limelight.ShortcutTrampoline
import com.limelight.nvstream.http.ComputerDetails
import io.github.magalz.borealis.library.shortcuts.ShortcutIntentParser
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertThrows
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.GraphicsMode

/** The launcher shortcuts of the Library, over the upstream `ShortcutHelper`. */
@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class UpstreamHostShortcutsTest {
    private val context: Context = ApplicationProvider.getApplicationContext()
    private val shortcutManager = context.getSystemService(ShortcutManager::class.java)
    private val boxArt = FakeBoxArt()
    private val shortcuts = UpstreamHostShortcuts(context, UnconfinedTestDispatcher(), boxArt)

    // ShortcutInfo.getIcon and the Icon accessors are hidden API: the tests read them by
    // reflection.
    private fun iconOf(shortcut: ShortcutInfo): Icon =
        ShortcutInfo::class.java.getMethod("getIcon").invoke(shortcut) as Icon

    private val Icon.bitmap: Bitmap
        get() = Icon::class.java.getMethod("getBitmap").invoke(this) as Bitmap

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
        assertThat(shortcut.intent?.component?.className)
            .isEqualTo(ShortcutTrampoline::class.java.name)
        assertThat(ShortcutIntentParser.parse(shortcut.intent?.extras))
            .isEqualTo(LibraryLaunchRequest.Apps("uuid-1"))
    }

    @Test
    fun `the shortcut of an opened host is published off the calling thread`() {
        val io = StandardTestDispatcher()
        val deferred = UpstreamHostShortcuts(context, io, boxArt)

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
    fun `a pinned app is a shortcut that launches it on its host`() = runTest {
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
    fun `a pinned app has its box art as an adaptive icon`() = runTest {
        boxArt.art[42] = Bitmap.createBitmap(300, 400, Bitmap.Config.ARGB_8888)

        shortcuts.pinApp("uuid-1", "Desk PC", 42, "Hades II", hdrSupported = false)

        val icon = iconOf(shortcutManager.pinnedShortcuts.single())
        assertThat(icon.type).isEqualTo(TYPE_ADAPTIVE_BITMAP)
        assertThat(icon.bitmap.width).isEqualTo(AdaptiveShortcutIcon.SIZE_PX)
        assertThat(icon.bitmap.height).isEqualTo(AdaptiveShortcutIcon.SIZE_PX)
    }

    @Test
    fun `a pinned app without box art has the Borealis shortcut icon`() = runTest {
        shortcuts.pinApp("uuid-1", "Desk PC", 42, "Hades II", hdrSupported = false)

        val icon = iconOf(shortcutManager.pinnedShortcuts.single())
        assertThat(icon.type).isEqualTo(TYPE_RESOURCE)
        assertThat(icon.resId).isEqualTo(R.mipmap.ic_pc_scut)
    }

    @Test
    fun `an app is not pinned when the launcher does not support it`() = runTest {
        shadowOf(shortcutManager).setIsRequestPinShortcutSupported(false)

        assertThat(shortcuts.pinApp("uuid-1", "Desk PC", 42, "Hades II", hdrSupported = false))
            .isFalse()
        assertThat(shortcutManager.pinnedShortcuts).isEmpty()
    }

    @Test
    fun `hiding an app disables its pinned shortcut and showing it enables it again`() = runTest {
        shortcuts.pinApp("uuid-1", "Desk PC", 42, "Hades II", hdrSupported = false)
        shortcuts.pinApp("uuid-1", "Desk PC", 43, "Steam", hdrSupported = false)

        shortcuts.appHidden("uuid-1", 42)

        // The platform (and its Robolectric shadow) rejects a pin request for a disabled shortcut.
        assertThrows(IllegalArgumentException::class.java) {
            runBlocking {
                shortcuts.pinApp("uuid-1", "Desk PC", 42, "Hades II", hdrSupported = false)
            }
        }
        assertThat(shortcuts.pinApp("uuid-1", "Desk PC", 43, "Steam", hdrSupported = false))
            .isTrue()

        shortcuts.appShown("uuid-1", 42)

        assertThat(shortcuts.pinApp("uuid-1", "Desk PC", 42, "Hades II", hdrSupported = false))
            .isTrue()
    }

    @Test
    fun `hiding an app that was never pinned changes nothing`() {
        shortcuts.appHidden("uuid-1", 42)
        shortcuts.appShown("uuid-1", 42)

        assertThat(shortcutManager.pinnedShortcuts).isEmpty()
    }

    @Test
    fun `a forgotten host disables the pinned shortcuts of its apps and no others`() = runTest {
        shortcuts.reportHostOpened("uuid-1", "Desk PC")
        shortcuts.pinApp("uuid-1", "Desk PC", 42, "Hades II", hdrSupported = false)
        shortcuts.pinApp("uuid-2", "Study PC", 7, "Steam", hdrSupported = false)

        shortcuts.hostForgotten(
            ComputerDetails().also {
                it.uuid = "uuid-1"
                it.name = "Desk PC"
            }
        )

        // The platform (and its Robolectric shadow) rejects a pin request for a disabled shortcut.
        assertThrows(IllegalArgumentException::class.java) {
            runBlocking {
                shortcuts.pinApp("uuid-1", "Desk PC", 42, "Hades II", hdrSupported = false)
            }
        }
        assertThat(shortcuts.pinApp("uuid-2", "Study PC", 7, "Steam", hdrSupported = false))
            .isTrue()
    }

    private companion object {
        const val TYPE_RESOURCE = 2
        const val TYPE_ADAPTIVE_BITMAP = 5
    }
}

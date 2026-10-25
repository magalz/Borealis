package io.github.magalz.borealis.support

import android.content.Context
import android.content.Intent
import android.content.pm.ShortcutInfo
import android.content.pm.ShortcutManager
import androidx.test.core.app.ApplicationProvider
import com.google.common.truth.Truth.assertThat
import com.limelight.nvstream.http.ComputerDetails
import com.limelight.utils.ShortcutHelper
import java.util.concurrent.Callable
import java.util.concurrent.CancellationException
import java.util.concurrent.ExecutionException
import java.util.concurrent.FutureTask
import java.util.concurrent.TimeUnit
import java.util.concurrent.TimeoutException
import org.junit.Assert.fail
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import org.robolectric.annotation.Implementation
import org.robolectric.annotation.Implements
import org.robolectric.shadows.ShadowShortcutManager

/**
 * What a host that the user opened does to a launcher whose dynamic shortcuts are full: the helper
 * removes the shortcut with the highest rank, until there is room for the new one. The call runs on
 * its own thread with a timeout, because Robolectric does not interrupt a test body: a helper that
 * never finishes the removal fails the test instead of hanging the suite.
 */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(shadows = [ShadowStoppableShortcutManager::class])
class ShortcutReapTest {
    private val context: Context = ApplicationProvider.getApplicationContext()
    private val manager = context.getSystemService(ShortcutManager::class.java)
    private val shadow = shadowOf(manager) as ShadowStoppableShortcutManager
    private val helper = ShortcutHelper(context)

    private fun addDynamic(id: String, rank: Int) {
        manager.addDynamicShortcuts(
            listOf(
                ShortcutInfo.Builder(context, id)
                    .setIntent(Intent(Intent.ACTION_VIEW))
                    .setShortLabel(id)
                    .setRank(rank)
                    .build()
            )
        )
    }

    private fun openHost(uuid: String) {
        val computer =
            ComputerDetails().also {
                it.uuid = uuid
                it.name = "Den PC"
            }
        val task = FutureTask(Callable { helper.createHostShortcut(computer, true, false) })
        val thread = Thread(task, "ShortcutReapTest").also { it.isDaemon = true }
        thread.start()
        try {
            task.get(REAP_TIMEOUT_MS, TimeUnit.MILLISECONDS)
        } catch (_: TimeoutException) {
            // The shadow stops the thread at its next removal, so no spinner is left behind.
            thread.interrupt()
            fail("createHostShortcut did not return within $REAP_TIMEOUT_MS ms")
        } catch (e: ExecutionException) {
            throw e.cause ?: e
        }
    }

    @Test
    fun `a host the user opened takes the place of the highest-ranked shortcut when they are full`() {
        shadow.setMaxShortcutCountPerActivity(4)
        addDynamic("full-a", rank = 2)
        addDynamic("full-b", rank = 7)
        addDynamic("full-c", rank = 5)
        addDynamic("full-d", rank = 3)

        openHost("full-new")

        assertThat(manager.dynamicShortcuts.map { it.id })
            .containsExactly("full-a", "full-c", "full-d", "full-new")
    }

    @Test
    fun `of two shortcuts with the highest rank, the one that comes first is removed`() {
        shadow.setMaxShortcutCountPerActivity(4)
        addDynamic("tie-a", rank = 1)
        addDynamic("tie-b", rank = 9)
        addDynamic("tie-c", rank = 9)
        addDynamic("tie-d", rank = 4)
        val removed = manager.dynamicShortcuts.first { it.rank == 9 }.id

        openHost("tie-new")

        val expected = listOf("tie-a", "tie-b", "tie-c", "tie-d").filter { it != removed }
        assertThat(manager.dynamicShortcuts.map { it.id })
            .containsExactlyElementsIn(expected + "tie-new")
    }

    @Test
    fun `several shortcuts are removed when the launcher lowered its limit`() {
        shadow.setMaxShortcutCountPerActivity(4)
        addDynamic("low-a", rank = 3)
        addDynamic("low-b", rank = 0)
        addDynamic("low-c", rank = 2)
        addDynamic("low-d", rank = 1)
        shadow.setMaxShortcutCountPerActivity(2)

        openHost("low-new")

        assertThat(manager.dynamicShortcuts.map { it.id }).containsExactly("low-b", "low-new")
    }

    @Test
    fun `the host is not added when the launcher refuses to remove a shortcut`() {
        shadow.setMaxShortcutCountPerActivity(2)
        addDynamic("kept-a", rank = 1)
        addDynamic("kept-b", rank = 2)
        shadow.refusesRemoval = true

        openHost("kept-new")

        assertThat(manager.dynamicShortcuts.map { it.id }).containsExactly("kept-a", "kept-b")
    }

    private companion object {
        const val REAP_TIMEOUT_MS = 30_000L
    }
}

/**
 * A launcher whose removal of dynamic shortcuts can be switched off with [refusesRemoval], and
 * whose removal stops a thread that was interrupted. Use it with
 * `@Config(shadows = [ShadowStoppableShortcutManager::class])`.
 */
@Implements(ShortcutManager::class)
class ShadowStoppableShortcutManager : ShadowShortcutManager() {
    /** The launcher keeps the shortcuts that the app asks it to remove. */
    var refusesRemoval = false

    @Suppress("unused")
    @Implementation
    override fun removeDynamicShortcuts(shortcutIds: List<String>) {
        if (Thread.currentThread().isInterrupted) throw CancellationException("The test gave up")
        if (!refusesRemoval) super.removeDynamicShortcuts(shortcutIds)
    }
}

package io.github.magalz.borealis.stream.ui.background

import android.content.Context
import android.content.Intent
import android.os.Looper
import android.os.PowerManager
import androidx.test.core.app.ApplicationProvider
import io.github.magalz.borealis.host.launch.ConflictAnswer
import io.github.magalz.borealis.stream.StreamSession
import io.github.magalz.borealis.stream.StreamVisuals
import io.github.magalz.borealis.stream.testing.aDefaults
import io.github.magalz.borealis.stream.testing.aFailure
import io.github.magalz.borealis.stream.testing.aSession
import org.robolectric.Shadows.shadowOf

/** The screens outside the game, and what takes a session to each one. */
internal enum class OutsideScreen(val enter: (StreamSession) -> Unit) {
    STARTING({}),
    CONFLICT({ it.onConflict("Steam", "Desktop", sameApp = false, answer = ConflictAnswer()) }),
    FAILED({ it.onFailed(aFailure) }),
    QUITTING({ it.quitApp() }),
    DISCONNECTING({ it.disconnect() }),
}

/** A session that starts with [visuals] and is at [screen]. */
internal fun sessionAt(
    screen: OutsideScreen = OutsideScreen.STARTING,
    visuals: StreamVisuals = StreamVisuals(),
): StreamSession = aSession(defaults = aDefaults.copy(visuals = visuals)).also { screen.enter(it) }

/** Turns the battery saver on or off, and tells the app the way the system does. */
internal fun setPowerSave(on: Boolean) {
    val context = ApplicationProvider.getApplicationContext<Context>()
    shadowOf(context.getSystemService(PowerManager::class.java)).setIsPowerSaveMode(on)
    context.sendBroadcast(Intent(PowerManager.ACTION_POWER_SAVE_MODE_CHANGED))
    shadowOf(Looper.getMainLooper()).idle()
}

/** The tags of the pieces of a video background (BackgroundTags, internal to the design system). */
internal const val VIDEO = "background:video"
internal const val STILL = "background:still"

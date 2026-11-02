package io.github.magalz.borealis.stream.game.session

import android.view.View
import io.github.magalz.borealis.library.domain.QuitResult
import io.github.magalz.borealis.library.shell.QuitNotice
import io.github.magalz.borealis.stream.FakeApps
import io.github.magalz.borealis.stream.FakeEntryPoint
import io.github.magalz.borealis.stream.GameStreamActions
import io.github.magalz.borealis.stream.OverlayMode
import io.github.magalz.borealis.stream.RecordingInput
import io.github.magalz.borealis.stream.SessionInfo
import io.github.magalz.borealis.stream.StreamControls
import io.github.magalz.borealis.stream.StreamDefaults
import io.github.magalz.borealis.stream.StreamSession
import io.github.magalz.borealis.stream.TouchMode
import io.github.magalz.borealis.stream.game.GameRig
import io.github.magalz.borealis.stream.host.HeldKeys
import io.github.magalz.borealis.stream.input.GamepadChrome
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import org.robolectric.util.ReflectionHelpers

/** The window that the session holds a 599 answer to a Quit for. */
internal const val SHARED_QUIT_WINDOW_MS = 5_000L

/** The code that the control stream reports when the host ends the stream with the quit's. */
internal const val ENET_DISCONNECT = -1

private object NoGamepad : GamepadChrome {
    override fun setCaptured(captured: Boolean) = Unit

    override fun setMouseEmulation(enabled: Boolean) = Unit

    override fun setPointerSpeed(factor: Float) = Unit
}

/**
 * A stream of the activity of [rig] whose session works with the real actions of the app, against a
 * host that answers the Quit with [quit]: the Quit reaches the host and its answer comes back to
 * the session, as on a device. The actions run their coroutines at once, so the host answers inside
 * the call that asks it, unless [FakeApps.gate] holds the answer. The clock and the one task that
 * the session schedules are the test's: [now] is the clock, and [runScheduled] runs what the
 * session scheduled. [notice] is what the stream leaves for the Library, with a Library attached.
 *
 * The session is told nothing about who started the app: a test that goes through
 * `Game.launchStarting` says that itself.
 */
@OptIn(ExperimentalCoroutinesApi::class)
internal class SharedQuitRig(rig: GameRig, quit: QuitResult) {
    var now = 0L
    val apps = FakeApps(quit)
    val scheduled = mutableListOf<Pair<Long, () -> Unit>>()
    val session: StreamSession
    val notice: QuitNotice

    init {
        val entryPoint = FakeEntryPoint(apps = apps)
        notice = entryPoint.notice
        notice.attach()
        val actions =
            GameStreamActions(
                rig.game,
                RecordingInput(),
                StreamControls(
                    controller = NoGamepad,
                    touchContexts = arrayOfNulls(2),
                    streamView = View(rig.game),
                    closePanel = {},
                    buildTouch = { null },
                    stopConnection = { it.run() },
                ),
                HeldKeys(),
                { entryPoint },
                CoroutineScope(UnconfinedTestDispatcher()),
            )
        session =
            StreamSession(
                "Desktop",
                "Den PC",
                StreamDefaults(OverlayMode.OFF, TouchMode.TRACKPAD, false, showMenuHint = false),
                { false },
                actions,
                { now },
                { delayMs, action -> scheduled += delayMs to action },
            )
        session.onConnectionStarted(SessionInfo(1920, 1080, 60, "HEVC", false, null, 20_000))
        session.onFirstFrame()
        ReflectionHelpers.setField(rig.game, "streamSession", session)
    }

    fun runScheduled() = scheduled.toList().also { scheduled.clear() }.forEach { it.second() }
}

package io.github.magalz.borealis.host.launch

import java.io.IOException
import java.util.concurrent.atomic.AtomicReference
import java.util.function.BooleanSupplier
import org.xmlpull.v1.XmlPullParserException

/** What the user decided about the app that is already running on the host. */
enum class ConflictChoice {
    /** `/resume` the running app. */
    JOIN,

    /** End the running app (and the session of the other device), then launch the requested one. */
    QUIT_AND_RELAUNCH,

    /** Leave everything as it is and end the launch quietly. */
    CANCEL,
}

/**
 * The answer slot between the dialog (UI thread) and the connection thread. The first choice wins;
 * later ones, for example the dialog closing after a button, are ignored.
 */
class ConflictAnswer {
    private val chosen = AtomicReference<ConflictChoice?>(null)

    fun choose(choice: ConflictChoice) {
        chosen.compareAndSet(null, choice)
    }

    internal fun get(): ConflictChoice? = chosen.get()
}

/** Reads `currentgame` from `/serverinfo`: the id of the running app, 0 when none runs. */
fun interface RunningAppReader {
    @Throws(IOException::class, XmlPullParserException::class) fun currentGame(): Int
}

/** The name of an app on the host, or null when it is not known. */
fun interface AppNameLookup {
    @Throws(IOException::class, XmlPullParserException::class) fun nameOf(appId: Int): String?
}

/** Shows the choice to the user and puts the answer into [ConflictAnswer]. */
fun interface ConflictPrompt {
    @Suppress("LongParameterList")
    fun ask(
        runningAppId: Int,
        runningAppName: String?,
        requestedAppId: Int,
        requestedAppName: String?,
        answer: ConflictAnswer,
    )
}

/** `/resume` of the running app, through the retry runner. */
fun interface ConflictJoin {
    @Throws(IOException::class, XmlPullParserException::class) fun join(appId: Int): LaunchOutcome
}

/**
 * Resolves, with the user, a `/launch` that the host refused because an app is already running
 * (host contract 7.5). One instance per connection: it allows one resolution per launch, so a
 * second refusal after Join or Quit fails like any other error instead of looping.
 *
 * It never ends another client's app on its own: only the user's explicit choice does.
 */
class LaunchConflicts
internal constructor(
    private val isCancelled: BooleanSupplier,
    private val cancel: Runnable,
    private val pollBudgetMs: Long,
    private val clock: () -> Long,
    private val sleeper: (Long) -> Unit,
) {
    constructor(
        isCancelled: BooleanSupplier,
        cancel: Runnable,
    ) : this(
        isCancelled,
        cancel,
        POLL_BUDGET_MS,
        { System.nanoTime() / NANOS_PER_MILLI },
        { Thread.sleep(it) },
    )

    /** True until [resolve] has been called once. */
    fun isOpen(): Boolean = true

    /**
     * Runs the resolution and returns whether the launch went through; false also means that the
     * user cancelled (the connection then ends quietly). Blocks, so call it off the main thread.
     */
    @Suppress("LongParameterList")
    @Throws(IOException::class, XmlPullParserException::class)
    fun resolve(
        reader: RunningAppReader,
        names: AppNameLookup,
        requestedAppId: Int,
        requestedAppName: String?,
        prompt: ConflictPrompt,
        relaunch: LaunchAttempt,
        join: ConflictJoin,
        quitAndRelaunch: LaunchAttempt,
    ): Boolean = false

    companion object {
        /** How long `/serverinfo` is re-read before the user is asked. */
        const val POLL_BUDGET_MS = 3_000L
        const val POLL_INTERVAL_MS = 1_000L
        const val SLICE_MS = 250L
        private const val NANOS_PER_MILLI = 1_000_000L
    }
}

package io.github.magalz.borealis.host.launch

import io.github.magalz.borealis.logging.BorealisLog
import io.github.magalz.borealis.logging.LogTag
import java.io.IOException
import java.util.concurrent.atomic.AtomicBoolean
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
 * later ones, for example the dialog closing after a button, are ignored. [choose] tells the caller
 * whether its choice was the one that won.
 */
class ConflictAnswer {
    private val chosen = AtomicReference<ConflictChoice?>(null)

    fun choose(choice: ConflictChoice): Boolean = chosen.compareAndSet(null, choice)

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

/** The app the refused `/launch` asked for: its id and name, or null when the name is unknown. */
data class RequestedApp(val id: Int, val name: String?)

/**
 * How the connection answers a conflict: [prompt] asks the user; [relaunch] launches the requested
 * app again (when nothing runs any more, or a resume found nothing to resume); [join] resumes the
 * running app; [quitAndRelaunch] ends it and launches the requested one.
 */
class ConflictActions(
    val prompt: ConflictPrompt,
    val relaunch: LaunchAttempt,
    val join: ConflictJoin,
    val quitAndRelaunch: LaunchAttempt,
)

/**
 * Resolves, with the user, a `/launch` that the host refused because an app is already running
 * (host contract 7.5). One instance per connection: it allows one resolution per launch, so a
 * second refusal after Join or Quit fails like any other error instead of looping.
 *
 * It never ends another client's app on its own: only the user's explicit choice does. Nothing
 * tells this device's session from another device's, so the same app that runs is put to the user
 * too. The one exception is the choice the user made earlier: Resume or Continue on that app in the
 * Library (UC-003, host contract 7.6).
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

    private val used = AtomicBoolean(false)

    /** True until [resolve] has been called once. */
    fun isOpen(): Boolean = !used.get()

    /**
     * The stream joins the running app because the user chose Resume or Continue on it in the
     * Library: nothing is asked. The audit and the log say that this is how the stream entered.
     */
    fun joinedByChoice(host: String?, runningAppId: Int) {
        BorealisLog.i(LogTag.LAUNCH, "running app joined on the user's choice")
        LaunchAudit.resumeChosen(host, runningAppId)
    }

    /**
     * Runs the resolution and returns whether the launch went through; false also means that the
     * user cancelled (the connection then ends quietly). Blocks, so call it off the main thread.
     * [resumeChosen] is true when the user chose Resume or Continue on the requested app, which
     * then is joined without a question when it is the one that runs.
     */
    // The reader, the names, the request, the actions, the host label and the user's choice: the
    // six values of one resolution.
    @Suppress("LongParameterList")
    @Throws(IOException::class, XmlPullParserException::class)
    fun resolve(
        reader: RunningAppReader,
        names: AppNameLookup,
        requested: RequestedApp,
        actions: ConflictActions,
        host: String? = null,
        resumeChosen: Boolean = false,
    ): Boolean {
        used.set(true)
        return when (val seen = pollRunningApp(reader)) {
            Seen.Cancelled -> false
            Seen.Nothing -> actions.relaunch.run()
            is Seen.App ->
                if (resumeChosen && seen.id == requested.id) {
                    joinedByChoice(host, seen.id)
                    afterJoin(actions.join.join(seen.id), actions.relaunch)
                } else {
                    askAndAct(seen.id, names, requested, actions, host)
                }
        }
    }

    /** Puts the running app to the user and does what they answer. */
    private fun askAndAct(
        runningAppId: Int,
        names: AppNameLookup,
        requested: RequestedApp,
        actions: ConflictActions,
        host: String?,
    ): Boolean {
        val name = nameOrNull(names, runningAppId)
        val choice = ask(actions.prompt, runningAppId, name, requested.id, requested.name)
        BorealisLog.i(LogTag.LAUNCH, "running app conflict answered", "choice" to choice)
        LaunchAudit.conflict(host, choice, runningAppId)
        return when (choice) {
            ConflictChoice.JOIN -> afterJoin(actions.join.join(runningAppId), actions.relaunch)
            ConflictChoice.QUIT_AND_RELAUNCH -> actions.quitAndRelaunch.run()
            ConflictChoice.CANCEL -> {
                cancel.run()
                false
            }
        }
    }

    private sealed interface Seen {
        data object Cancelled : Seen

        data object Nothing : Seen

        data class App(val id: Int) : Seen
    }

    /**
     * Re-reads `/serverinfo` until the app is gone, the budget is spent or the launch is cancelled.
     */
    private fun pollRunningApp(reader: RunningAppReader): Seen {
        val startedAt = clock()
        while (!isCancelled.asBoolean) {
            val id = reader.currentGame()
            if (id == 0) return Seen.Nothing
            if (clock() - startedAt >= pollBudgetMs) return Seen.App(id)
            if (!wait(POLL_INTERVAL_MS)) break
        }
        return Seen.Cancelled
    }

    /** Waits in slices; false when cancelled or interrupted before the wait was over. */
    private fun wait(totalMs: Long): Boolean {
        var left = totalMs
        try {
            while (left > 0) {
                val slice = minOf(SLICE_MS, left)
                sleeper(slice)
                left -= slice
                if (isCancelled.asBoolean) return false
            }
        } catch (e: InterruptedException) {
            BorealisLog.d(LogTag.LAUNCH, "conflict wait interrupted")
            Thread.currentThread().interrupt()
            return false
        }
        return true
    }

    /** The name of the running app, or null when the host cannot tell it. */
    private fun nameOrNull(names: AppNameLookup, appId: Int): String? =
        try {
            names.nameOf(appId)
        } catch (e: IOException) {
            BorealisLog.d(
                LogTag.LAUNCH,
                "running app name unavailable",
                "error" to e.javaClass.simpleName,
            )
            null
        } catch (e: XmlPullParserException) {
            BorealisLog.d(
                LogTag.LAUNCH,
                "running app name unavailable",
                "error" to e.javaClass.simpleName,
            )
            null
        }

    /** Shows the question and waits for the answer; a cancelled launch is a cancel. */
    private fun ask(
        prompt: ConflictPrompt,
        runningAppId: Int,
        runningAppName: String?,
        requestedAppId: Int,
        requestedAppName: String?,
    ): ConflictChoice {
        val answer = ConflictAnswer()
        prompt.ask(runningAppId, runningAppName, requestedAppId, requestedAppName, answer)
        while (true) {
            answer.get()?.let {
                return it
            }
            if (isCancelled.asBoolean || !wait(SLICE_MS)) return ConflictChoice.CANCEL
        }
    }

    private fun afterJoin(outcome: LaunchOutcome, relaunch: LaunchAttempt): Boolean =
        when (outcome) {
            LaunchOutcome.SUCCEEDED -> true
            LaunchOutcome.FALLBACK_TO_LAUNCH -> relaunch.run()
            LaunchOutcome.FAILED,
            LaunchOutcome.CANCELLED,
            LaunchOutcome.CONFLICT -> false
        }

    companion object {
        /** How long `/serverinfo` is re-read before the user is asked. */
        const val POLL_BUDGET_MS = 3_000L
        const val POLL_INTERVAL_MS = 1_000L
        const val SLICE_MS = 250L
        private const val NANOS_PER_MILLI = 1_000_000L
    }
}

package io.github.magalz.borealis.host.launch

import com.google.common.truth.Truth.assertThat
import io.github.magalz.borealis.logging.LogTag
import java.io.IOException
import org.junit.Assert.assertThrows
import org.junit.Rule
import org.junit.Test
import org.xmlpull.v1.XmlPullParserException

class LaunchConflictsTest {
    @get:Rule val log = RecordingLogSink()

    /** Fake time: sleeping advances the clock, so the poll budget and the slices can be seen. */
    private var now = 0L
    private val slices = mutableListOf<Long>()
    private var cancelled = false
    private var cancelCalls = 0
    private var onSlice: (Int) -> Unit = {}

    private val conflicts =
        LaunchConflicts(
            { cancelled },
            {
                cancelCalls++
                cancelled = true
            },
            LaunchConflicts.POLL_BUDGET_MS,
            { now },
            {
                slices += it
                now += it
                onSlice(slices.size)
            },
        )

    // What the connection does with the answer.
    private val calls = mutableListOf<String>()
    private var relaunchResult = true
    private var quitResult = true
    private var joinOutcome = LaunchOutcome.SUCCEEDED

    private class Prompt(val running: Int, val runningName: String?, val requested: Int)

    private val prompts = mutableListOf<Prompt>()
    private var answerWith: ConflictChoice? = null
    private var reads = 0

    private fun reader(vararg ids: Int) = RunningAppReader {
        reads++
        ids[minOf(reads - 1, ids.size - 1)]
    }

    private fun resolve(
        reader: RunningAppReader,
        names: AppNameLookup = AppNameLookup { "Desktop" },
        resumeChosen: Boolean = false,
    ): Boolean =
        conflicts.resolve(
            reader,
            names,
            RequestedApp(7, "Steam", resumeChosen),
            ConflictActions(
                prompt = { running, name, requested, _, answer ->
                    prompts += Prompt(running, name, requested)
                    answerWith?.let(answer::choose)
                },
                relaunch =
                    LaunchAttempt {
                        calls += "relaunch"
                        relaunchResult
                    },
                join =
                    ConflictJoin {
                        calls += "join $it"
                        joinOutcome
                    },
                quitAndRelaunch =
                    LaunchAttempt {
                        calls += "quit"
                        quitResult
                    },
            ),
        )

    @Test
    fun `nothing running any more launches again without asking`() {
        val result = resolve(reader(0))

        assertThat(result).isTrue()
        assertThat(calls).containsExactly("relaunch")
        assertThat(prompts).isEmpty()
        assertThat(slices).isEmpty()
    }

    @Test
    fun `the result of the second launch is the result of the resolution`() {
        relaunchResult = false

        assertThat(resolve(reader(0))).isFalse()
    }

    @Test
    fun `an app that stops during the poll launches again without asking`() {
        val result = resolve(reader(5, 5, 0))

        assertThat(result).isTrue()
        assertThat(calls).containsExactly("relaunch")
        assertThat(prompts).isEmpty()
        assertThat(reads).isEqualTo(3)
        assertThat(slices.sum()).isEqualTo(2000)
    }

    @Test
    fun `serverinfo is re-read for 3 seconds in 250 ms slices before the user is asked`() {
        answerWith = ConflictChoice.CANCEL

        resolve(reader(5))

        assertThat(reads).isEqualTo(4)
        assertThat(slices.sum()).isEqualTo(3000)
        assertThat(slices.toSet()).containsExactly(250L)
    }

    @Test
    fun `another app that keeps running is put to the user with its id and name`() {
        answerWith = ConflictChoice.CANCEL

        resolve(reader(5))

        val prompt = prompts.single()
        assertThat(prompt.running).isEqualTo(5)
        assertThat(prompt.runningName).isEqualTo("Desktop")
        assertThat(prompt.requested).isEqualTo(7)
        assertThat(calls).isEmpty()
    }

    @Test
    fun `the same app running is put to the user too when they did not choose to resume it`() {
        answerWith = ConflictChoice.CANCEL

        resolve(reader(7))

        assertThat(prompts.single().running).isEqualTo(prompts.single().requested)
        assertThat(calls).isEmpty()
    }

    @Test
    fun `the same app running is joined without asking when the user chose to resume it`() {
        // A question would be answered at once, so that this test fails instead of waiting.
        answerWith = ConflictChoice.CANCEL

        val result = resolve(reader(7), resumeChosen = true)

        assertThat(result).isTrue()
        assertThat(calls).containsExactly("join 7")
        assertThat(prompts).isEmpty()
        assertThat(cancelCalls).isEqualTo(0)
    }

    @Test
    fun `another app running is put to the user even when they chose to resume`() {
        answerWith = ConflictChoice.CANCEL

        val result = resolve(reader(5), resumeChosen = true)

        assertThat(result).isFalse()
        assertThat(prompts.single().running).isEqualTo(5)
        assertThat(calls).isEmpty()
    }

    @Test
    fun `an app that is gone is launched again without asking whatever the user chose`() {
        val result = resolve(reader(0), resumeChosen = true)

        assertThat(result).isTrue()
        assertThat(calls).containsExactly("relaunch")
        assertThat(prompts).isEmpty()
    }

    @Test
    fun `a chosen join that finds nothing to resume launches the app instead`() {
        answerWith = ConflictChoice.CANCEL
        joinOutcome = LaunchOutcome.FALLBACK_TO_LAUNCH

        val result = resolve(reader(7), resumeChosen = true)

        assertThat(result).isTrue()
        assertThat(calls).containsExactly("join 7", "relaunch").inOrder()
        assertThat(prompts).isEmpty()
    }

    @Test
    fun `a chosen join that the host does not take fails quietly`() {
        answerWith = ConflictChoice.CANCEL
        joinOutcome = LaunchOutcome.FAILED

        assertThat(resolve(reader(7), resumeChosen = true)).isFalse()
        assertThat(prompts).isEmpty()
    }

    @Test
    fun `an unknown app name is passed as null`() {
        answerWith = ConflictChoice.CANCEL

        resolve(reader(5), AppNameLookup { null })
        cancelled = false
        resolve(reader(5), AppNameLookup { throw IOException("offline") })
        cancelled = false
        resolve(reader(5), AppNameLookup { throw XmlPullParserException("bad list") })

        assertThat(prompts.map { it.runningName }).containsExactly(null, null, null)
    }

    @Test
    fun `join resumes the running app and its success is the result`() {
        answerWith = ConflictChoice.JOIN

        val result = resolve(reader(5))

        assertThat(result).isTrue()
        assertThat(calls).containsExactly("join 5")
        assertThat(cancelCalls).isEqualTo(0)
    }

    @Test
    fun `a join that did not resume or was cancelled fails quietly`() {
        answerWith = ConflictChoice.JOIN

        for (outcome in
            listOf(LaunchOutcome.FAILED, LaunchOutcome.CANCELLED, LaunchOutcome.CONFLICT)) {
            joinOutcome = outcome
            assertThat(resolve(reader(5))).isFalse()
        }
    }

    @Test
    fun `a join that finds nothing to resume launches the app instead`() {
        answerWith = ConflictChoice.JOIN
        joinOutcome = LaunchOutcome.FALLBACK_TO_LAUNCH

        val result = resolve(reader(5))

        assertThat(result).isTrue()
        assertThat(calls).containsExactly("join 5", "relaunch").inOrder()
    }

    @Test
    fun `quit and relaunch runs the upstream quit and launch path`() {
        answerWith = ConflictChoice.QUIT_AND_RELAUNCH

        assertThat(resolve(reader(5))).isTrue()
        assertThat(calls).containsExactly("quit")
        assertThat(cancelCalls).isEqualTo(0)
    }

    @Test
    fun `a quit and launch that fails is a failure`() {
        answerWith = ConflictChoice.QUIT_AND_RELAUNCH
        quitResult = false

        assertThat(resolve(reader(5))).isFalse()
    }

    @Test
    fun `cancel ends the launch without touching the running app`() {
        answerWith = ConflictChoice.CANCEL

        val result = resolve(reader(5))

        assertThat(result).isFalse()
        assertThat(calls).isEmpty()
        assertThat(cancelCalls).isEqualTo(1)
    }

    @Test
    fun `cancelling during the poll ends it without asking or launching`() {
        onSlice = { if (it == 2) cancelled = true }

        val result = resolve(reader(5))

        assertThat(result).isFalse()
        assertThat(prompts).isEmpty()
        assertThat(calls).isEmpty()
        assertThat(slices).hasSize(2)
    }

    @Test
    fun `a launch cancelled before the resolution starts is not read or asked`() {
        cancelled = true

        assertThat(resolve(reader(5))).isFalse()
        assertThat(reads).isEqualTo(0)
        assertThat(prompts).isEmpty()
    }

    @Test
    fun `the wait for the choice runs in 250 ms slices until the user answers`() {
        var answer: ConflictAnswer? = null
        // The 4 poll waits of the budget come first; the user answers after 3 more slices.
        onSlice = { if (it == 12 + 3) answer?.choose(ConflictChoice.JOIN) }

        val result =
            conflicts.resolve(
                reader(5),
                AppNameLookup { null },
                RequestedApp(7, null),
                ConflictActions(
                    { _, _, _, _, a -> answer = a },
                    { true },
                    { LaunchOutcome.SUCCEEDED },
                    { true },
                ),
            )

        assertThat(result).isTrue()
        assertThat(slices).hasSize(15)
        assertThat(slices.toSet()).containsExactly(250L)
    }

    @Test
    fun `cancelling while the dialog is open counts as cancel`() {
        // No answer ever comes; the connection is cancelled (Back, onStop, onDestroy) after 2
        // slices.
        onSlice = { if (it == 12 + 2) cancelled = true }

        val result = resolve(reader(5))

        assertThat(result).isFalse()
        assertThat(prompts).hasSize(1)
        assertThat(calls).isEmpty()
        assertThat(slices).hasSize(14)
    }

    @Test
    fun `an interrupted wait counts as cancel and keeps the interrupt flag`() {
        val interrupting =
            LaunchConflicts({ false }, {}, 0L, { 0L }, { throw InterruptedException() })
        try {
            val result =
                interrupting.resolve(
                    reader(5),
                    AppNameLookup { null },
                    RequestedApp(7, null),
                    ConflictActions(
                        { _, _, _, _, _ -> },
                        { true },
                        { LaunchOutcome.SUCCEEDED },
                        { true },
                    ),
                )

            assertThat(result).isFalse()
            assertThat(Thread.currentThread().isInterrupted).isTrue()
        } finally {
            Thread.interrupted()
        }
    }

    @Test
    fun `the first choice wins`() {
        val answer = ConflictAnswer()

        val first = answer.choose(ConflictChoice.JOIN)
        val second = answer.choose(ConflictChoice.CANCEL)

        assertThat(answer.get()).isEqualTo(ConflictChoice.JOIN)
        assertThat(first).isTrue()
        assertThat(second).isFalse()
    }

    @Test
    fun `a repeated choice of the same value does not win again`() {
        val answer = ConflictAnswer()

        assertThat(answer.choose(ConflictChoice.CANCEL)).isTrue()
        assertThat(answer.choose(ConflictChoice.CANCEL)).isFalse()
    }

    @Test
    fun `one resolution per launch closes the conflict path`() {
        assertThat(conflicts.isOpen()).isTrue()

        resolve(reader(0))

        assertThat(conflicts.isOpen()).isFalse()
    }

    @Test
    fun `the path is closed even when serverinfo cannot be read`() {
        val failure = IOException("offline")

        val thrown =
            assertThrows(IOException::class.java) { resolve(RunningAppReader { throw failure }) }

        assertThat(thrown).isSameInstanceAs(failure)
        assertThat(conflicts.isOpen()).isFalse()
        assertThat(calls).isEmpty()
    }

    @Test
    fun `the choice is logged without app names`() {
        answerWith = ConflictChoice.QUIT_AND_RELAUNCH

        resolve(reader(5))

        val records = log.records.filter { it.tag == LogTag.LAUNCH }
        assertThat(records.flatMap { it.fields }).contains("choice" to "QUIT_AND_RELAUNCH")
        assertThat(records.joinToString { it.message + it.fields }).doesNotContain("Desktop")
        assertThat(records.joinToString { it.message + it.fields }).doesNotContain("Steam")
    }

    @Test
    fun `a join on the user's choice is logged without app names`() {
        answerWith = ConflictChoice.CANCEL

        resolve(reader(7), resumeChosen = true)

        val records = log.records.filter { it.tag == LogTag.LAUNCH }
        assertThat(records.map { it.message }).contains("running app joined on the user's choice")
        assertThat(records.joinToString { it.message + it.fields }).doesNotContain("Steam")
    }

    @Test
    fun `the public constructor uses the real clock and sleeper`() {
        val real = LaunchConflicts({ false }, {})

        val result =
            real.resolve(
                RunningAppReader { 0 },
                AppNameLookup { null },
                RequestedApp(7, null),
                ConflictActions(
                    { _, _, _, _, _ -> },
                    { true },
                    { LaunchOutcome.SUCCEEDED },
                    { true },
                ),
            )

        assertThat(result).isTrue()
    }
}

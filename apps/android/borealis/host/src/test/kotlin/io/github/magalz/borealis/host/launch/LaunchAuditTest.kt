package io.github.magalz.borealis.host.launch

import com.google.common.truth.Truth.assertThat
import io.github.magalz.borealis.host.RecordingAudit
import io.github.magalz.borealis.logging.AuditOutcome
import io.github.magalz.borealis.logging.AuditType
import java.io.IOException
import java.util.function.BooleanSupplier
import org.junit.Assert.assertThrows
import org.junit.Rule
import org.junit.Test

class LaunchAuditTest {
    @get:Rule val audit = RecordingAudit()
    @get:Rule val log = RecordingLogSink()

    private val host = "192.168.1.5"
    private val runner = LaunchRetryRunner(FakeHostRules(), clock = { 0L }, sleeper = {})
    private val extractor = HostErrorExtractor { (it as? HostAnswer)?.error }
    private val notCancelled = BooleanSupplier { false }

    private fun raising(vararg failures: Throwable, last: Boolean = true): LaunchAttempt {
        val queue = ArrayDeque(failures.toList())
        return LaunchAttempt { queue.removeFirstOrNull()?.let { throw it } ?: last }
    }

    private fun run(
        attempt: LaunchAttempt,
        verb: String = "launch",
        isGfe: Boolean = false,
        cancelled: BooleanSupplier = notCancelled,
        conflictOpen: Boolean = true,
    ) =
        LaunchRetries.run(
            runner,
            LaunchRequest(isGfe, verb, host),
            attempt,
            LaunchHooks(extractor, cancelled, { _, _, _, _ -> }, { conflictOpen }),
        )

    private fun answer(code: Int, message: String? = null) = HostAnswer(HostError(code, message))

    @Test
    fun `an accepted launch is recorded as a success with the verb`() {
        run(raising())
        run(raising(), verb = "resume")

        assertThat(audit.events.map { Triple(it.type, it.outcome, it.detail) })
            .containsExactly(
                Triple(
                    AuditType.LAUNCH_ATTEMPT,
                    AuditOutcome.SUCCESS,
                    "verb=launch result=succeeded",
                ),
                Triple(
                    AuditType.LAUNCH_ATTEMPT,
                    AuditOutcome.SUCCESS,
                    "verb=resume result=succeeded",
                ),
            )
            .inOrder()
        assertThat(audit.events.map { it.host }.toSet()).containsExactly(host)
    }

    @Test
    fun `each retry is recorded with its category and attempt before the final result`() {
        run(raising(answer(409, "Another client launch is already pending"), answer(409)))

        assertThat(audit.events.map { Triple(it.type, it.outcome, it.detail) })
            .containsExactly(
                Triple(
                    AuditType.LAUNCH_RETRY,
                    AuditOutcome.FAILURE,
                    "verb=launch category=PENDING attempt=1/4 delay_ms=1000",
                ),
                Triple(
                    AuditType.LAUNCH_RETRY,
                    AuditOutcome.FAILURE,
                    "verb=launch category=PENDING attempt=2/4 delay_ms=2000",
                ),
                Triple(
                    AuditType.LAUNCH_ATTEMPT,
                    AuditOutcome.SUCCESS,
                    "verb=launch result=succeeded",
                ),
            )
            .inOrder()
    }

    @Test
    fun `a failure that is rethrown is recorded with the host status and not its message`() {
        val failure = answer(500, "secret=hunter2 pin=1234")

        assertThrows(HostAnswer::class.java) { run(raising(failure)) }

        val event = audit.events.single()
        assertThat(event.type).isEqualTo(AuditType.LAUNCH_ATTEMPT)
        assertThat(event.outcome).isEqualTo(AuditOutcome.FAILURE)
        assertThat(event.detail).isEqualTo("verb=launch result=failed status=500")
    }

    @Test
    fun `an exception that is not a host answer is recorded by class`() {
        assertThrows(IOException::class.java) { run(raising(IOException("pin=1234 broken"))) }

        assertThat(audit.events.single().detail)
            .isEqualTo("verb=launch result=failed error=IOException")
    }

    @Test
    fun `a host that answers 200 without starting the app is a failure`() {
        run(raising(last = false))

        val event = audit.events.single()
        assertThat(event.outcome).isEqualTo(AuditOutcome.FAILURE)
        assertThat(event.detail).isEqualTo("verb=launch result=refused")
    }

    @Test
    fun `a cancelled launch is recorded as cancelled`() {
        run(raising(), cancelled = { true })

        val event = audit.events.single()
        assertThat(event.outcome).isEqualTo(AuditOutcome.CANCELLED)
        assertThat(event.detail).isEqualTo("verb=launch result=cancelled")
    }

    @Test
    fun `a resume with nothing to resume and a running app are recorded`() {
        run(raising(answer(503, "No running app to resume")), verb = "resume")
        run(raising(answer(400, "An app is already running")))

        assertThat(audit.events.map { it.detail })
            .containsExactly(
                "verb=resume result=no_app_to_resume",
                "verb=launch result=app_running",
            )
            .inOrder()
        assertThat(audit.events.map { it.outcome }.toSet()).containsExactly(AuditOutcome.FAILURE)
    }

    @Test
    fun `a GFE host is recorded too`() {
        run(raising(), isGfe = true)
        run(raising(last = false), isGfe = true)

        assertThat(audit.events.map { it.detail })
            .containsExactly("verb=launch result=succeeded", "verb=launch result=refused")
            .inOrder()
    }

    // --- the user's choice in the running-app conflict ---

    private fun resolve(
        choice: ConflictChoice,
        running: Int = 5,
        resumeChosen: Boolean = false,
    ): Boolean {
        val conflicts = LaunchConflicts({ false }, {}, 0L, { 0L }, {})
        return conflicts.resolve(
            { running },
            { null },
            RequestedApp(7, "Steam", resumeChosen),
            ConflictActions(
                prompt = { _, _, _, _, answer -> answer.choose(choice) },
                relaunch = { true },
                join = { LaunchOutcome.SUCCEEDED },
                quitAndRelaunch = { true },
            ),
            host = host,
        )
    }

    @Test
    fun `each conflict choice is recorded with the id of the running app`() {
        resolve(ConflictChoice.JOIN)
        resolve(ConflictChoice.QUIT_AND_RELAUNCH)
        resolve(ConflictChoice.CANCEL)

        assertThat(audit.events.map { Triple(it.type, it.outcome, it.detail) })
            .containsExactly(
                Triple(
                    AuditType.LAUNCH_CONFLICT,
                    AuditOutcome.SUCCESS,
                    "choice=join running_app=5 entry=asked",
                ),
                Triple(
                    AuditType.LAUNCH_CONFLICT,
                    AuditOutcome.SUCCESS,
                    "choice=quit running_app=5 entry=asked",
                ),
                Triple(
                    AuditType.LAUNCH_CONFLICT,
                    AuditOutcome.CANCELLED,
                    "choice=cancel running_app=5 entry=asked",
                ),
            )
            .inOrder()
        assertThat(audit.events.map { it.host }.toSet()).containsExactly(host)
    }

    @Test
    fun `a resume that the user chose is recorded as a join that was not asked`() {
        resolve(ConflictChoice.CANCEL, running = 7, resumeChosen = true)

        val event = audit.events.single()
        assertThat(event.type).isEqualTo(AuditType.LAUNCH_CONFLICT)
        assertThat(event.outcome).isEqualTo(AuditOutcome.SUCCESS)
        assertThat(event.host).isEqualTo(host)
        assertThat(event.detail).isEqualTo("choice=join running_app=7 entry=chosen")
    }

    @Test
    fun `a join on the choice of the user on the serverinfo path is recorded the same way`() {
        LaunchConflicts({ false }, {}).joinedByChoice(host, 7)

        val event = audit.events.single()
        assertThat(event.type).isEqualTo(AuditType.LAUNCH_CONFLICT)
        assertThat(event.outcome).isEqualTo(AuditOutcome.SUCCESS)
        assertThat(event.host).isEqualTo(host)
        assertThat(event.detail).isEqualTo("choice=join running_app=7 entry=chosen")
    }

    @Test
    fun `another app than the chosen one is asked about, and recorded as asked`() {
        resolve(ConflictChoice.QUIT_AND_RELAUNCH, running = 5, resumeChosen = true)

        assertThat(audit.events.map { it.detail })
            .containsExactly("choice=quit running_app=5 entry=asked")
    }

    @Test
    fun `no conflict to resolve records nothing`() {
        resolve(ConflictChoice.JOIN, running = 0)

        assertThat(audit.events).isEmpty()
    }

    @Test
    fun `a restart after RTSP failed following a resume is a retry with its reason`() {
        LaunchAudit.resumeRestarted(host, -1, 3_000)

        val event = audit.events.single()
        assertThat(event.type).isEqualTo(AuditType.LAUNCH_RETRY)
        assertThat(event.outcome).isEqualTo(AuditOutcome.FAILURE)
        assertThat(event.host).isEqualTo(host)
        assertThat(event.detail)
            .isEqualTo("verb=resume reason=rtsp_after_resume error=-1 attempt=1/1 delay_ms=3000")
    }

    @Test
    fun `giving up after the restart is a failed resume attempt with the RTSP error`() {
        LaunchAudit.resumeGaveUp(host, -1)

        val event = audit.events.single()
        assertThat(event.type).isEqualTo(AuditType.LAUNCH_ATTEMPT)
        assertThat(event.outcome).isEqualTo(AuditOutcome.FAILURE)
        assertThat(event.host).isEqualTo(host)
        assertThat(event.detail).isEqualTo("verb=resume result=rtsp_failed error=-1")
    }

    @Test
    fun `refusing to quit another app after the restart is a failed resume attempt`() {
        LaunchAudit.resumeRefusedQuit(host)

        val event = audit.events.single()
        assertThat(event.type).isEqualTo(AuditType.LAUNCH_ATTEMPT)
        assertThat(event.outcome).isEqualTo(AuditOutcome.FAILURE)
        assertThat(event.host).isEqualTo(host)
        assertThat(event.detail).isEqualTo("verb=resume result=other_app_running")
    }
}

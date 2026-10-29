package io.github.magalz.borealis.host.launch

import com.google.common.truth.Truth.assertThat
import io.github.magalz.borealis.host.RecordingAudit
import io.github.magalz.borealis.logging.AuditOutcome
import io.github.magalz.borealis.logging.AuditType
import org.junit.Rule
import org.junit.Test

/**
 * A start that finds a different app running (UC-003): the connection asks first, and only the
 * user's explicit choice ends the other device's app.
 */
class DifferentAppStartTest {
    @get:Rule val log = RecordingLogSink()
    @get:Rule val audit = RecordingAudit()

    private val host = "192.168.1.5"
    private var now = 0L
    private var cancelled = false
    private var cancelCalls = 0

    private val collisions =
        ResumeCollisions({ cancelled }, { _, _, _, _ -> }, { host }, { now }, {})
    private val conflicts =
        LaunchConflicts(
            { cancelled },
            {
                cancelCalls++
                cancelled = true
            },
            LaunchConflicts.POLL_BUDGET_MS,
            { now },
            { now += it },
        )

    // Every request that changes the host.
    private val requests = mutableListOf<String>()
    private val prompts = mutableListOf<Pair<Int, Int>>()

    /** The connection's different-app branch: the conflict flow, with the user's answer. */
    private fun start(answer: ConflictChoice): Boolean = collisions.differentAppRunning {
        conflicts.resolve(
            reader = { 5 },
            names = { "Desktop" },
            requested = RequestedApp(7, "Steam"),
            actions =
                ConflictActions(
                    prompt = { running, _, requested, _, slot ->
                        prompts += running to requested
                        slot.choose(answer)
                    },
                    relaunch = {
                        requests += "launch"
                        true
                    },
                    join = {
                        requests += "resume $it"
                        LaunchOutcome.SUCCEEDED
                    },
                    quitAndRelaunch = {
                        requests += "quit"
                        requests += "launch"
                        true
                    },
                ),
            host = host,
        )
    }

    private val choices
        get() = audit.events.filter { it.type == AuditType.LAUNCH_CONFLICT }

    @Test
    fun `a different running app asks the user before anything changes on the host`() {
        start(ConflictChoice.CANCEL)

        assertThat(prompts).containsExactly(5 to 7)
        assertThat(requests).isEmpty()
    }

    @Test
    fun `cancel changes nothing on the host and is audited as cancel`() {
        val result = start(ConflictChoice.CANCEL)

        assertThat(result).isFalse()
        assertThat(requests).isEmpty()
        assertThat(cancelCalls).isEqualTo(1)
        assertThat(choices.single().outcome).isEqualTo(AuditOutcome.CANCELLED)
        assertThat(choices.single().detail).isEqualTo("choice=cancel running_app=5 entry=asked")
    }

    @Test
    fun `quit and start quits once, launches once and is audited as quit`() {
        val result = start(ConflictChoice.QUIT_AND_RELAUNCH)

        assertThat(result).isTrue()
        assertThat(requests).containsExactly("quit", "launch").inOrder()
        assertThat(choices.single().detail).isEqualTo("choice=quit running_app=5 entry=asked")
    }

    @Test
    fun `a restart still refuses without asking or quitting`() {
        // A start that was resumed and then failed at RTSP is restarted once.
        collisions.readServerInfo { "<root/>" }
        collisions.settleBeforeResume()
        collisions.resumeAccepted(false)
        collisions.onStageFailed(true, ResumeCollisions.RTSP_UNREADABLE_REPLY)
        assertThat(collisions.restartAfterFailure()).isTrue()

        val result = start(ConflictChoice.QUIT_AND_RELAUNCH)

        assertThat(result).isFalse()
        assertThat(prompts).isEmpty()
        assertThat(requests).isEmpty()
        assertThat(collisions.collided()).isTrue()
    }
}

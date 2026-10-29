package io.github.magalz.borealis.connection

import com.google.common.truth.Truth.assertThat
import com.limelight.nvstream.NvConnection
import io.github.magalz.borealis.host.failure.CauseKind
import io.github.magalz.borealis.host.launch.ConflictChoice
import io.github.magalz.borealis.library.RecordingAudit
import io.github.magalz.borealis.logging.AuditOutcome
import io.github.magalz.borealis.logging.AuditType
import io.github.magalz.borealis.nvhttp.HostReply
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * The app that the start asks for already runs on the host. Nothing tells this device's session
 * from another device's, so the start asks, unless the user chose Resume or Continue on that app:
 * then it joins at once (StartResumeTest). This holds when `/serverinfo` shows the app and when
 * `/launch` is refused because it runs.
 */
@RunWith(RobolectricTestRunner::class)
@Config(
    shadows =
        [
            ShadowRecordingBridge::class,
            ShadowMonkeyActivityManager::class,
            ShadowLoadedNativeLibrary::class,
            ShadowHostRules::class,
        ]
)
class StartSameAppTest {
    @get:Rule val rig = ConnectionRig()
    @get:Rule val audit = RecordingAudit()

    private val listener
        get() = rig.listener

    private val conflict =
        "launchConflict running=$DESKTOP_ID/$DESKTOP_NAME requested=$DESKTOP_ID/$DESKTOP_NAME"

    /** The requests that are no `/serverinfo`, which the connection reads again and again. */
    private fun steps(): List<String> = rig.paths().filter { it != "/serverinfo" }

    /**
     * Starts [connection] and waits until it asked, got through or failed, and its thread ended.
     */
    private fun startAndEnd(connection: NvConnection = rig.connection()) {
        rig.start(connection)
        listener.awaitAny("launchConflict", "stageComplete", "stageFailed")
        listener.awaitEnded()
    }

    /** What the native handshake does when its RTSP stage fails the first time. */
    private fun rtspFailsOnce(): (BridgeCall) -> Int = {
        if (ShadowRecordingBridge.named("startConnection").size == 1) {
            checkNotNull(ShadowRecordingBridge.listener)
                .stageFailed(
                    ShadowRecordingBridge.getStageName(ShadowRecordingBridge.RTSP_STAGE),
                    RTSP_PORTS,
                    -1,
                )
            -1
        } else {
            0
        }
    }

    /**
     * Waits until the start failed, or came through twice (a restart), and its threads ended. The
     * restart goes on in a new thread, which [RecordingListener.awaitEnded] also waits for.
     */
    private fun awaitRestartOrFailure() {
        val limit = System.currentTimeMillis() + WAIT_MS
        while (
            listener.events("stageFailed").isEmpty() && listener.events("stageComplete").size < 2
        ) {
            check(System.currentTimeMillis() < limit) {
                "Timed out waiting for a failure or a restart; events: ${listener.events}"
            }
            Thread.sleep(POLL_MS)
        }
        listener.awaitEnded()
    }

    /** Stamps the question and the start of the resume, for the time between them. */
    private fun stampQuestionAndResume(): Map<String, Long> {
        val stamps = mutableMapOf<String, Long>()
        listener.onEvent = { event ->
            if (event.startsWith("launchConflict") || event == "launchStarting resume=true") {
                stamps[event.substringBefore(' ')] = System.nanoTime()
            }
        }
        return stamps
    }

    private fun auditDetails() =
        audit.events.filter { it.type == AuditType.LAUNCH_CONFLICT }.map { it.detail }

    @Test
    fun `an app that runs is asked about when the user did not choose Resume`() {
        rig.script.running(DESKTOP_ID)
        val connection = rig.connection()

        startAndEnd(connection)

        assertThat(listener.events)
            .containsExactly("stageStarting $DESKTOP_NAME", conflict)
            .inOrder()
        assertThat(rig.paths()).doesNotContain("/resume")
        assertThat(ShadowRecordingBridge.calls).isEmpty()
        assertThat(connection.lastFailureCause()).isEqualTo(CauseKind.NONE)
        assertThat(connection.endedByResumeCollision()).isFalse()
    }

    @Test
    fun `join resumes the app that runs after the question`() {
        rig.script.running(DESKTOP_ID)
        listener.conflictChoice = ConflictChoice.JOIN

        startAndEnd()

        assertThat(listener.events)
            .containsExactly(
                "stageStarting $DESKTOP_NAME",
                conflict,
                "launchStarting resume=true",
                "stageComplete $DESKTOP_NAME",
            )
            .inOrder()
        assertThat(steps()).containsExactly("/applist", "/resume").inOrder()
        assertThat(rig.request("/resume").value("appid")).isEqualTo("$DESKTOP_ID")
    }

    @Test
    fun `quit and relaunch ends the app that runs, then launches it again`() {
        rig.script.running(DESKTOP_ID)
        listener.conflictChoice = ConflictChoice.QUIT_AND_RELAUNCH

        startAndEnd()

        assertThat(listener.events)
            .containsExactly(
                "stageStarting $DESKTOP_NAME",
                conflict,
                "launchStarting resume=false",
                "stageComplete $DESKTOP_NAME",
            )
            .inOrder()
        assertThat(steps()).containsExactly("/applist", "/cancel", "/launch").inOrder()
    }

    @Test
    fun `a start that is cancelled while it waits for the answer ends without a word`() {
        rig.script.running(DESKTOP_ID)
        listener.conflictChoice = null
        val connection = rig.connection()

        rig.start(connection)
        listener.awaitAny("launchConflict", "stageComplete", "stageFailed")
        connection.cancelLaunch()
        listener.awaitEnded()

        assertThat(listener.events)
            .containsExactly("stageStarting $DESKTOP_NAME", conflict)
            .inOrder()
        assertThat(steps()).containsExactly("/applist")
    }

    @Test
    fun `the answer of the user is audited as asked`() {
        rig.script.running(DESKTOP_ID)
        listener.conflictChoice = ConflictChoice.JOIN

        startAndEnd()

        assertThat(auditDetails())
            .containsExactly("choice=join running_app=$DESKTOP_ID entry=asked")
    }

    @Test
    fun `an app that runs is joined without asking when the user chose Resume, and audited so`() {
        rig.script.running(DESKTOP_ID)

        startAndEnd(rig.resumingConnection())

        assertThat(listener.events("launchConflict")).isEmpty()
        assertThat(rig.paths()).containsExactly("/serverinfo", "/resume").inOrder()
        assertThat(auditDetails())
            .containsExactly("choice=join running_app=$DESKTOP_ID entry=chosen")
        assertThat(audit.events.single { it.type == AuditType.LAUNCH_CONFLICT }.outcome)
            .isEqualTo(AuditOutcome.SUCCESS)
    }

    @Test
    fun `another app that runs is asked about even when the user chose Resume`() {
        rig.script.running(OTHER_ID)

        startAndEnd(rig.resumingConnection())

        assertThat(listener.events("launchConflict"))
            .containsExactly(
                "launchConflict running=$OTHER_ID/$OTHER_NAME " +
                    "requested=$DESKTOP_ID/$DESKTOP_NAME"
            )
        assertThat(rig.paths()).doesNotContain("/resume")
    }

    @Test
    fun `a launch refused because the app runs is asked about when the user did not choose Resume`() {
        raceWithDesktop()

        startAndEnd()

        assertThat(listener.events)
            .containsExactly(
                "stageStarting $DESKTOP_NAME",
                "launchStarting resume=false",
                conflict,
            )
            .inOrder()
        assertThat(rig.paths()).doesNotContain("/resume")
    }

    @Test
    fun `a launch refused because the app runs joins it without asking when the user chose Resume`() {
        raceWithDesktop()

        startAndEnd(rig.resumingConnection())

        assertThat(listener.events)
            .containsExactly(
                "stageStarting $DESKTOP_NAME",
                "launchStarting resume=false",
                "launchStarting resume=true",
                "stageComplete $DESKTOP_NAME",
            )
            .inOrder()
        assertThat(steps()).containsExactly("/launch", "/resume").inOrder()
        assertThat(auditDetails())
            .containsExactly("choice=join running_app=$DESKTOP_ID entry=chosen")
    }

    @Test
    fun `the question about the app that runs does not read serverinfo again first`() {
        rig.script.running(DESKTOP_ID)

        startAndEnd()

        assertThat(rig.paths().count { it == "/serverinfo" }).isEqualTo(1)
        assertThat(listener.events("launchConflict")).containsExactly(conflict)
    }

    @Test
    fun `cancel ends the start of an app that runs without a word and changes nothing on the host`() {
        rig.script.running(DESKTOP_ID)
        listener.conflictChoice = ConflictChoice.CANCEL
        val connection = rig.connection()

        startAndEnd(connection)

        assertThat(listener.events)
            .containsExactly("stageStarting $DESKTOP_NAME", conflict)
            .inOrder()
        assertThat(rig.paths()).containsExactly("/serverinfo", "/applist")
        assertThat(ShadowRecordingBridge.calls).isEmpty()
        assertThat(connection.lastFailureCause()).isEqualTo(CauseKind.NONE)
        assertThat(connection.endedByResumeCollision()).isFalse()
        assertThat(audit.events.filter { it.type == AuditType.LAUNCH_CONFLICT }.map { it.outcome })
            .containsExactly(AuditOutcome.CANCELLED)
        assertThat(auditDetails())
            .containsExactly("choice=cancel running_app=$DESKTOP_ID entry=asked")
    }

    @Test
    fun `a join of the app that runs and a failed handshake restart the start once, and ask once`() {
        rig.script.running(DESKTOP_ID)
        listener.conflictChoice = ConflictChoice.JOIN
        ShadowRecordingBridge.onStartConnection = rtspFailsOnce()
        val connection = rig.connection()

        rig.start(connection)
        awaitRestartOrFailure()

        assertThat(listener.events)
            .containsExactly(
                "stageStarting $DESKTOP_NAME",
                conflict,
                "launchStarting resume=true",
                "stageComplete $DESKTOP_NAME",
                "stageStarting $DESKTOP_NAME",
                "launchRetrying category=1 attempt=1 max=1 delay=3000",
                "launchStarting resume=true",
                "stageComplete $DESKTOP_NAME",
            )
            .inOrder()
        assertThat(ShadowRecordingBridge.named("startConnection")).hasSize(2)
        assertThat(connection.endedByResumeCollision()).isFalse()
        assertThat(auditDetails())
            .containsExactly("choice=join running_app=$DESKTOP_ID entry=asked")
    }

    @Test
    fun `a launch refused because the app runs, a Join and a failed handshake restart once and ask once`() {
        raceWithDesktop()
        listener.conflictChoice = ConflictChoice.JOIN
        ShadowRecordingBridge.onStartConnection = rtspFailsOnce()
        val connection = rig.connection()

        rig.start(connection)
        awaitRestartOrFailure()

        assertThat(listener.events)
            .containsExactly(
                "stageStarting $DESKTOP_NAME",
                "launchStarting resume=false",
                conflict,
                "launchStarting resume=true",
                "stageComplete $DESKTOP_NAME",
                "stageStarting $DESKTOP_NAME",
                "launchRetrying category=1 attempt=1 max=1 delay=3000",
                "launchStarting resume=true",
                "stageComplete $DESKTOP_NAME",
            )
            .inOrder()
        assertThat(ShadowRecordingBridge.named("startConnection")).hasSize(2)
        assertThat(connection.endedByResumeCollision()).isFalse()
        assertThat(auditDetails())
            .containsExactly("choice=join running_app=$DESKTOP_ID entry=asked")
    }

    @Test
    fun `a chosen resume and a failed handshake restart the start once and record the choice once`() {
        rig.script.running(DESKTOP_ID)
        ShadowRecordingBridge.onStartConnection = rtspFailsOnce()

        rig.start(rig.resumingConnection())
        awaitRestartOrFailure()

        assertThat(listener.events("stageComplete")).hasSize(2)
        assertThat(listener.events("launchConflict")).isEmpty()
        assertThat(auditDetails())
            .containsExactly("choice=join running_app=$DESKTOP_ID entry=chosen")
    }

    @Test
    fun `a join of the app that runs waits before the resume when the host was slow to answer`() {
        rig.script.running(DESKTOP_ID)
        rig.script.onServerInfo = { count -> if (count == 1) Thread.sleep(SLOW_SERVERINFO_MS) }
        listener.conflictChoice = ConflictChoice.JOIN
        val stamps = stampQuestionAndResume()

        startAndEnd()

        val waitedMs =
            (stamps.getValue("launchStarting") - stamps.getValue("launchConflict")) / NANOS_PER_MS
        assertThat(waitedMs).isAtLeast(PAUSE_MS - SLACK_MS)
        assertThat(rig.paths()).containsExactly("/serverinfo", "/applist", "/resume").inOrder()
    }

    @Test
    fun `a start that is cancelled while it waits for a busy host before the join ends without a word`() {
        rig.script.running(DESKTOP_ID)
        rig.script.onServerInfo = { count -> if (count == 1) Thread.sleep(SLOW_SERVERINFO_MS) }
        listener.conflictChoice = ConflictChoice.JOIN
        val connection = rig.connection()
        listener.onEvent = { if (it.startsWith("launchConflict")) connection.cancelLaunch() }

        startAndEnd(connection)

        assertThat(listener.events)
            .containsExactly("stageStarting $DESKTOP_NAME", conflict)
            .inOrder()
        assertThat(rig.paths()).doesNotContain("/resume")
        assertThat(ShadowRecordingBridge.calls).isEmpty()
    }

    @Test
    fun `a join of another app does not wait for a host that was slow to answer`() {
        rig.script.running(OTHER_ID)
        rig.script.onServerInfo = { count -> if (count == 1) Thread.sleep(SLOW_SERVERINFO_MS) }
        listener.conflictChoice = ConflictChoice.JOIN
        val stamps = stampQuestionAndResume()

        startAndEnd()

        val waitedMs =
            (stamps.getValue("launchStarting") - stamps.getValue("launchConflict")) / NANOS_PER_MS
        assertThat(waitedMs).isLessThan(PAUSE_MS - SLACK_MS)
    }

    /** `/serverinfo` shows no app, and the host refuses `/launch`: the app started meanwhile. */
    private fun raceWithDesktop() {
        rig.script.launchReplies +=
            HostReply.failure("400", "An app is already running on this host")
        rig.script.onLaunch = { rig.script.running(DESKTOP_ID) }
    }

    private companion object {
        const val WAIT_MS = 30_000L
        const val POLL_MS = 10L
        const val RTSP_PORTS = 5

        /** A `/serverinfo` answer this slow means that the host was busy with another launch. */
        const val SLOW_SERVERINFO_MS = 3_100L
        const val PAUSE_MS = 3_000L
        const val SLACK_MS = 500L
        const val NANOS_PER_MS = 1_000_000L
    }
}

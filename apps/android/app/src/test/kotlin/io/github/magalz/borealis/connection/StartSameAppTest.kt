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

    /** `/serverinfo` shows no app, and the host refuses `/launch`: the app started meanwhile. */
    private fun raceWithDesktop() {
        rig.script.launchReplies +=
            HostReply.failure("400", "An app is already running on this host")
        rig.script.onLaunch = { rig.script.running(DESKTOP_ID) }
    }
}

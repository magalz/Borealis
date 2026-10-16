package io.github.magalz.borealis.host.launch

import com.google.common.truth.Truth.assertThat
import io.github.magalz.borealis.host.RecordingAudit
import io.github.magalz.borealis.logging.AuditOutcome
import io.github.magalz.borealis.logging.AuditType
import org.junit.After
import org.junit.Before
import org.junit.Rule
import org.junit.Test

class SessionAuditTest {
    @get:Rule val audit = RecordingAudit()

    private var nowMillis = 0L

    @Before
    fun setUp() {
        SessionAudit.reset()
        SessionAudit.clock = { nowMillis }
    }

    @After fun tearDown() = SessionAudit.reset()

    private fun start(format: Int = HEVC, hostName: String? = "Den PC") =
        SessionAudit.started(hostName, "192.168.1.5", format, 1920, 1080, 60)

    @Test
    fun `the start records codec, resolution, frame rate and HDR`() {
        start()

        val event = audit.events.single()
        assertThat(event.type).isEqualTo(AuditType.SESSION_START)
        assertThat(event.outcome).isEqualTo(AuditOutcome.SUCCESS)
        assertThat(event.host).isEqualTo("Den PC (192.168.1.5)")
        assertThat(event.detail).isEqualTo("codec=HEVC hdr=false resolution=1920x1080 fps=60")
    }

    @Test
    fun `the codec and HDR come from the negotiated video format`() {
        for ((format, expected) in
            listOf(
                0x0001 to "codec=H.264 hdr=false",
                0x0100 to "codec=HEVC hdr=false",
                0x0200 to "codec=HEVC hdr=true",
                0x1000 to "codec=AV1 hdr=false",
                0x2000 to "codec=AV1 hdr=true",
                0x0000 to "codec=unknown hdr=false",
            )) {
            audit.events.clear()
            SessionAudit.reset()

            start(format)

            assertThat(audit.events.single().detail).startsWith(expected)
        }
    }

    @Test
    fun `a stop after a start records the duration and a user stop`() {
        start()
        nowMillis = 125_900L

        SessionAudit.stopped()

        val end = audit.events.last()
        assertThat(end.type).isEqualTo(AuditType.SESSION_END)
        assertThat(end.outcome).isEqualTo(AuditOutcome.SUCCESS)
        assertThat(end.host).isEqualTo("Den PC (192.168.1.5)")
        assertThat(end.detail).isEqualTo("reason=user duration_s=125")
    }

    @Test
    fun `a graceful termination is a success and an error code is a failure`() {
        start()
        nowMillis = 60_000L
        SessionAudit.terminated(0)
        start()
        nowMillis = 90_000L
        SessionAudit.terminated(-100)

        val ends = audit.events.filter { it.type == AuditType.SESSION_END }
        assertThat(ends.map { it.outcome })
            .containsExactly(AuditOutcome.SUCCESS, AuditOutcome.FAILURE)
            .inOrder()
        assertThat(ends.map { it.detail })
            .containsExactly(
                "reason=host_ended duration_s=60",
                "reason=error code=-100 duration_s=30",
            )
            .inOrder()
    }

    @Test
    fun `a session ends once however many paths report the end`() {
        start()

        SessionAudit.terminated(-100)
        SessionAudit.stopped()
        SessionAudit.terminated(0)

        assertThat(audit.types())
            .containsExactly(AuditType.SESSION_START, AuditType.SESSION_END)
            .inOrder()
    }

    @Test
    fun `an end without a start records nothing`() {
        SessionAudit.stopped()
        SessionAudit.terminated(-1)

        assertThat(audit.events).isEmpty()
    }

    @Test
    fun `the next session after an end is recorded again`() {
        start()
        SessionAudit.stopped()
        start()
        SessionAudit.stopped()

        assertThat(audit.types().count { it == AuditType.SESSION_END }).isEqualTo(2)
    }

    @Test
    fun `a disconnect records the duration and its own reason`() {
        start()
        nowMillis = 42_000L

        SessionAudit.disconnected()

        val end = audit.events.last()
        assertThat(end.type).isEqualTo(AuditType.SESSION_END)
        assertThat(end.outcome).isEqualTo(AuditOutcome.SUCCESS)
        assertThat(end.detail).isEqualTo("reason=disconnect duration_s=42")
    }

    @Test
    fun `a quit records the duration and its own reason`() {
        start()
        nowMillis = 7_000L

        SessionAudit.quit()

        val end = audit.events.last()
        assertThat(end.type).isEqualTo(AuditType.SESSION_END)
        assertThat(end.outcome).isEqualTo(AuditOutcome.SUCCESS)
        assertThat(end.detail).isEqualTo("reason=quit duration_s=7")
    }

    @Test
    fun `the reason of the first end stays when the stop of the activity follows`() {
        start()

        SessionAudit.disconnected()
        SessionAudit.stopped()
        start()
        SessionAudit.quit()
        SessionAudit.stopped()

        val ends = audit.events.filter { it.type == AuditType.SESSION_END }
        assertThat(ends.map { it.detail })
            .containsExactly("reason=disconnect duration_s=0", "reason=quit duration_s=0")
            .inOrder()
    }

    @Test
    fun `the host ending the stream while a quit is under way is the quit`() {
        start()
        SessionAudit.quitRequested()

        SessionAudit.terminated(0)

        assertThat(audit.events.last().detail).isEqualTo("reason=quit duration_s=0")
    }

    @Test
    fun `an error code while a quit is under way is the quit too`() {
        // LuminalShine ends the stream with -1 as soon as it gets the cancel, then answers it.
        start()
        SessionAudit.quitRequested()

        SessionAudit.terminated(-1)

        assertThat(audit.events.last().outcome).isEqualTo(AuditOutcome.SUCCESS)
        assertThat(audit.events.last().detail).isEqualTo("reason=quit duration_s=0")
    }

    @Test
    fun `an error code without a quit is still an error`() {
        start()

        SessionAudit.terminated(-1)

        assertThat(audit.events.last().outcome).isEqualTo(AuditOutcome.FAILURE)
        assertThat(audit.events.last().detail).isEqualTo("reason=error code=-1 duration_s=0")
    }

    @Test
    fun `a quit that failed leaves the host ending the stream as host_ended`() {
        start()
        SessionAudit.quitRequested()
        SessionAudit.quitFailed()

        SessionAudit.terminated(0)

        assertThat(audit.events.last().detail).isEqualTo("reason=host_ended duration_s=0")
    }

    @Test
    fun `a quit request does not carry over to the next session`() {
        start()
        SessionAudit.quitRequested()
        SessionAudit.stopped()
        start()

        SessionAudit.terminated(0)

        assertThat(audit.events.last().detail).isEqualTo("reason=host_ended duration_s=0")
    }

    @Test
    fun `an end without a start records nothing for a disconnect or a quit`() {
        SessionAudit.disconnected()
        SessionAudit.quit()

        assertThat(audit.events).isEmpty()
    }

    @Test
    fun `a host without a name is labelled by its address`() {
        start(hostName = null)

        assertThat(audit.events.single().host).isEqualTo("192.168.1.5")
    }

    private companion object {
        const val HEVC = 0x0100
    }
}

package io.github.magalz.borealis.host

import io.github.magalz.borealis.logging.AuditEvent
import io.github.magalz.borealis.logging.AuditSink
import io.github.magalz.borealis.logging.AuditType
import io.github.magalz.borealis.logging.BorealisAudit
import io.github.magalz.borealis.logging.Redactor
import java.util.concurrent.CopyOnWriteArrayList
import org.junit.rules.ExternalResource

/** Routes [BorealisAudit] into [events] for one test and uninstalls the facade afterwards. */
class RecordingAudit : ExternalResource(), AuditSink {
    val events = CopyOnWriteArrayList<AuditEvent>()

    override fun before() {
        BorealisAudit.install(this, Redactor(), "test", clock = { 1_700_000_000_000L })
    }

    override fun after() {
        BorealisAudit.uninstall()
    }

    override fun write(event: AuditEvent) {
        events += event
    }

    fun types(): List<AuditType> = events.map { it.type }
}

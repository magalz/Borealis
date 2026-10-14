package io.github.magalz.borealis.diagnostics.audit

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.google.common.truth.Truth.assertThat
import io.github.magalz.borealis.diagnostics.DiagnosticSection
import io.github.magalz.borealis.logging.AuditOutcome
import io.github.magalz.borealis.logging.AuditType
import io.github.magalz.borealis.logging.BorealisAudit
import io.github.magalz.borealis.logging.Redactor
import java.io.ByteArrayOutputStream
import java.io.OutputStream
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestCoroutineScheduler
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import org.junit.After
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class AuditSectionTest {
    private val scheduler = TestCoroutineScheduler()
    private val dispatcher = StandardTestDispatcher(scheduler)
    private val scope = CoroutineScope(SupervisorJob() + dispatcher)
    private val redactor = Redactor()
    private var now = 1_700_000_000_000L
    private lateinit var db: AuditDatabase
    private lateinit var store: AuditStore
    private lateinit var section: AuditSection

    @Before
    fun setUp() {
        db =
            Room.inMemoryDatabaseBuilder(
                    ApplicationProvider.getApplicationContext<Context>(),
                    AuditDatabase::class.java,
                )
                .allowMainThreadQueries()
                .build()
        store = AuditStore(db.dao(), scope, dispatcher)
        section = AuditSection(store)
        BorealisAudit.install(store, redactor, "1.0.0", clock = { now })
    }

    @After
    fun tearDown() {
        BorealisAudit.uninstall()
        scope.cancel()
        db.close()
    }

    private fun export(): String {
        scheduler.advanceUntilIdle()
        val out = ByteArrayOutputStream()
        section.writeTo(out)
        return out.toString(Charsets.UTF_8)
    }

    private fun lines(text: String): List<JsonObject> =
        text.lines().filter { it.isNotEmpty() }.map { Json.parseToJsonElement(it).jsonObject }

    private fun record(
        type: AuditType = AuditType.PAIRING_START,
        outcome: AuditOutcome = AuditOutcome.SUCCESS,
        host: String? = "den-pc",
        detail: String? = "detail",
    ) = BorealisAudit.record(type, outcome, host, detail)

    @Test
    fun `the section is the audit jsonl file`() {
        assertThat(section.fileName).isEqualTo("audit.jsonl")
        assertThat(DiagnosticSection.isSafeFileName(section.fileName)).isTrue()
    }

    @Test
    fun `an empty trail writes nothing`() {
        assertThat(export()).isEmpty()
    }

    @Test
    fun `each entry is one json object per line with every field`() {
        record(
            AuditType.LAUNCH_RETRY,
            AuditOutcome.FAILURE,
            "den-pc",
            "category=PENDING attempt=1/4",
        )

        val text = export()

        assertThat(text).endsWith("\n")
        val entry = lines(text).single()
        assertThat(entry.keys)
            .containsExactly("timestamp", "type", "outcome", "host", "detail", "appVersion")
        assertThat(entry["timestamp"]!!.jsonPrimitive.content).isEqualTo("2023-11-14T22:13:20Z")
        assertThat(entry["type"]!!.jsonPrimitive.content).isEqualTo("LAUNCH_RETRY")
        assertThat(entry["outcome"]!!.jsonPrimitive.content).isEqualTo("FAILURE")
        assertThat(entry["host"]!!.jsonPrimitive.content).isEqualTo("den-pc")
        assertThat(entry["detail"]!!.jsonPrimitive.content)
            .isEqualTo("category=PENDING attempt=1/4")
        assertThat(entry["appVersion"]!!.jsonPrimitive.content).isEqualTo("1.0.0")
    }

    @Test
    fun `a missing host or detail is a json null`() {
        record(host = null, detail = null)

        val entry = lines(export()).single()

        assertThat(entry["host"]).isEqualTo(JsonNull)
        assertThat(entry["detail"]).isEqualTo(JsonNull)
    }

    @Test
    fun `entries are written oldest first`() {
        for (i in 1..3) {
            now += 20 * MINUTE
            record(detail = "d$i")
        }

        val details = lines(export()).map { it["detail"]!!.jsonPrimitive.content }

        assertThat(details).containsExactly("d1", "d2", "d3").inOrder()
    }

    @Test
    fun `a newline or a quote in the detail stays on one line`() {
        record(detail = "first\nsecond \"quoted\"")

        val text = export()

        assertThat(text.trimEnd('\n').lines()).hasSize(1)
        assertThat(lines(text).single()["detail"]!!.jsonPrimitive.content)
            .isEqualTo("first\nsecond \"quoted\"")
    }

    @Test
    fun `the export masks an entry that was written while masking was off`() {
        record(host = "Den PC (192.168.1.5)", detail = "to 192.168.1.5")
        redactor.maskAddresses = true

        val entry = lines(export()).single()

        assertThat(entry["host"]!!.jsonPrimitive.content).isEqualTo("[HOST]")
        assertThat(entry["detail"]!!.jsonPrimitive.content).isEqualTo("to [IP]")
    }

    @Test
    fun `the section leaves the stream open`() {
        record()
        scheduler.advanceUntilIdle()
        val out =
            object : ByteArrayOutputStream() {
                var closed = false

                override fun close() {
                    closed = true
                }
            }

        section.writeTo(out as OutputStream)

        assertThat(out.closed).isFalse()
        assertThat(out.size()).isGreaterThan(0)
    }

    @Test
    fun `no canary secret reaches the stored entries or the export`() {
        val canaries = RedactionCorpus.canaries()
        assertThat(canaries.size).isAtLeast(30)

        for (masking in listOf(false, true)) {
            db.dao().deleteAll()
            now += 11 * MINUTE
            redactor.maskAddresses = masking
            for (canary in canaries) {
                record(AuditType.PAIRING_FAILURE, AuditOutcome.FAILURE, canary.input, canary.input)
            }
            val text = export()

            val stored = db.dao().all()
            assertThat(stored).isNotEmpty()
            for (canary in canaries) {
                assertThat(text).doesNotContain(canary.secret)
                for (row in stored) {
                    assertThat(row.host).doesNotContain(canary.secret)
                    assertThat(row.detail).doesNotContain(canary.secret)
                }
            }
        }
    }
}

package io.github.magalz.borealis.diagnostics.audit

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.google.common.truth.Truth.assertThat
import io.github.magalz.borealis.logging.AuditOutcome
import io.github.magalz.borealis.logging.AuditType
import io.github.magalz.borealis.logging.BorealisAudit
import io.github.magalz.borealis.logging.Redactor
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import org.junit.After
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class AuditTrailTest {
    @get:Rule val workers = WorkerDispatcherRule()

    private val scope = CoroutineScope(SupervisorJob() + workers.dispatcher)
    private lateinit var db: AuditDatabase
    private lateinit var store: AuditStore
    private lateinit var trail: AuditTrail

    @Before
    fun setUp() {
        db =
            Room.inMemoryDatabaseBuilder(
                    ApplicationProvider.getApplicationContext<Context>(),
                    AuditDatabase::class.java,
                )
                .allowMainThreadQueries()
                .build()
        store = AuditStore(db.dao(), scope, workers.dispatcher)
        trail = AuditModule.trail(db, store)
    }

    @After
    fun tearDown() {
        BorealisAudit.uninstall()
        scope.cancel()
        db.close()
    }

    private fun insert(at: Long, detail: String) =
        db.dao().insertAndPrune(AuditEntity.of(event(at = at, detail = detail)), 0, 10)

    private fun <T> await(block: suspend () -> T): T = runBlocking {
        withTimeout(TIMEOUT) { block() }
    }

    private fun collectInto(emissions: Channel<List<AuditEntry>>) {
        scope.launch { trail.observe().collect { emissions.send(it) } }
    }

    private fun entry(host: String?, detail: String?) =
        AuditEntry(5, AuditType.SESSION_START, AuditOutcome.SUCCESS, host, detail)

    @Test
    fun `an empty trail emits an empty list`() {
        assertThat(await { trail.observe().first() }).isEmpty()
    }

    @Test
    fun `entries come newest first`() {
        insert(10, "first")
        insert(20, "second")
        insert(30, "third")

        val entries = await { trail.observe().first() }

        assertThat(entries.map { it.detail }).containsExactly("third", "second", "first").inOrder()
    }

    @Test
    fun `the order is the insertion order, not the timestamp`() {
        insert(30, "inserted first")
        insert(10, "inserted last")

        val entries = await { trail.observe().first() }

        assertThat(entries.map { it.detail })
            .containsExactly("inserted last", "inserted first")
            .inOrder()
    }

    @Test
    fun `an entry carries its time, code, outcome, host and detail`() {
        val stored =
            event(
                at = 1_700_000_123_456L,
                type = AuditType.CERTIFICATE_CHANGED,
                outcome = AuditOutcome.FAILURE,
                host = "Den PC (10.0.0.7)",
                detail = "category=PINNED",
            )
        db.dao().insertAndPrune(AuditEntity.of(stored), 0, 10)

        val shown = await { trail.observe().first() }.single()

        assertThat(shown)
            .isEqualTo(
                AuditEntry(
                    timestampMillis = 1_700_000_123_456L,
                    type = AuditType.CERTIFICATE_CHANGED,
                    outcome = AuditOutcome.FAILURE,
                    host = "Den PC (10.0.0.7)",
                    detail = "category=PINNED",
                )
            )
    }

    @Test
    fun `a row of an unknown type is left out`() {
        insert(10, "known")
        val future = AuditEntity.of(event(at = 20, detail = "future"))
        db.dao().insertAndPrune(future.copy(type = "FROM_A_FUTURE_VERSION"), 0, 10)

        assertThat(await { trail.observe().first() }.map { it.detail }).containsExactly("known")
    }

    @Test
    fun `the flow emits again after an insert`() {
        val emissions = Channel<List<AuditEntry>>(Channel.UNLIMITED)
        collectInto(emissions)

        assertThat(await { emissions.receive() }).isEmpty()
        insert(10, "added")

        assertThat(await { emissions.receive() }.map { it.detail }).containsExactly("added")
    }

    @Test
    fun `erasing empties the flow and succeeds`() {
        insert(10, "a")
        insert(20, "b")
        val emissions = Channel<List<AuditEntry>>(Channel.UNLIMITED)
        collectInto(emissions)
        assertThat(await { emissions.receive() }).hasSize(2)

        val erased = await { trail.erase() }

        assertThat(erased).isTrue()
        assertThat(await { emissions.receive() }).isEmpty()
        assertThat(db.dao().count()).isEqualTo(0)
    }

    @Test
    fun `a failing erase returns false and leaves the entries`() {
        insert(10, "kept")
        db.openHelper.writableDatabase.execSQL(
            "CREATE TRIGGER refuse BEFORE DELETE ON audit_events " +
                "BEGIN SELECT RAISE(ABORT, 'refused'); END"
        )

        val erased = await { trail.erase() }

        assertThat(erased).isFalse()
        assertThat(db.dao().count()).isEqualTo(1)
    }

    @Test
    fun `masking hides the address in the host and the detail like the export does`() {
        BorealisAudit.install(store, Redactor(maskAddresses = true), "1.0.0")
        val shown = entry(host = "Den PC (192.168.1.5)", detail = "to 192.168.1.5")

        val masked = shown.masked()

        assertThat(masked.host).isEqualTo("[HOST]")
        assertThat(masked.detail).isEqualTo("to [IP]")
        assertThat(masked.copy(host = shown.host, detail = shown.detail)).isEqualTo(shown)
    }

    @Test
    fun `masking keeps the host when masking is off and a missing host stays missing`() {
        BorealisAudit.install(store, Redactor(maskAddresses = false), "1.0.0")

        assertThat(entry(host = null, detail = null).masked()).isEqualTo(entry(null, null))
        assertThat(entry(host = "Den PC (10.0.0.7)", detail = null).masked().host)
            .isEqualTo("Den PC (10.0.0.7)")
    }

    @Test
    fun `masking redacts a secret that reached the detail`() {
        assertThat(entry(host = null, detail = "pin=1234").masked().detail).doesNotContain("1234")
    }

    @Test
    fun `an entry has only the stored fields, nothing that could hold a secret`() {
        val fields =
            AuditEntry::class.java.declaredFields.filterNot { it.isSynthetic }.map { it.name }

        assertThat(fields).containsExactly("timestampMillis", "type", "outcome", "host", "detail")
    }

    private companion object {
        const val TIMEOUT = 5_000L
    }
}

package io.github.magalz.borealis.computerstore

import android.content.Context
import android.content.ContextWrapper
import android.database.Cursor
import android.database.sqlite.SQLiteConstraintException
import android.database.sqlite.SQLiteDatabase
import android.database.sqlite.SQLiteException
import androidx.test.core.app.ApplicationProvider
import com.google.common.truth.Truth.assertThat
import com.limelight.computers.ComputerDatabaseManager
import com.limelight.nvstream.http.ComputerDetails
import com.limelight.nvstream.http.ComputerDetails.AddressTuple
import com.limelight.nvstream.http.NvHTTP
import org.json.JSONException
import org.json.JSONObject
import org.junit.After
import org.junit.Assert.assertThrows
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

/**
 * The saved host list of the inherited `ComputerDatabaseManager`: what it reads from the database
 * file that the Java build wrote (`computerstore/computers4.db`, see [FIXTURE_HOSTS]), what it
 * writes, and what it does with a row or a file that is not what it expects.
 *
 * Characterization: these tests pass on the Java class and on its Kotlin conversion.
 */
@RunWith(RobolectricTestRunner::class)
class ComputerDatabaseManagerCharacterizationTest {
    private val context: Context = ApplicationProvider.getApplicationContext()
    private val managers = mutableListOf<ComputerDatabaseManager>()

    @After
    fun closeManagers() {
        managers.forEach { it.close() }
    }

    private fun open(on: Context = context): ComputerDatabaseManager =
        ComputerDatabaseManager(on).also { managers += it }

    private fun openFixture(): ComputerDatabaseManager {
        installFixture(DATABASE_NAME, context.computerDatabase())
        return open()
    }

    /** Runs [block] on a second connection to the database file, to see the rows themselves. */
    private fun <T> raw(block: (SQLiteDatabase) -> T): T =
        SQLiteDatabase.openDatabase(
                context.computerDatabase().path,
                null,
                SQLiteDatabase.OPEN_READWRITE,
            )
            .use(block)

    private fun SQLiteDatabase.rows(sql: String): List<List<Any?>> =
        rawQuery(sql, null).use { cursor ->
            buildList {
                while (cursor.moveToNext()) {
                    add((0 until cursor.columnCount).map { column -> cursor.valueAt(column) })
                }
            }
        }

    private fun Cursor.valueAt(column: Int): Any? =
        when (getType(column)) {
            Cursor.FIELD_TYPE_NULL -> null
            Cursor.FIELD_TYPE_INTEGER -> getLong(column)
            Cursor.FIELD_TYPE_BLOB -> getBlob(column).toList()
            else -> getString(column)
        }

    /** The rows of the table as the three columns that hold a host's text, plus its certificate. */
    private fun SQLiteDatabase.hostRows() =
        rows(
            "SELECT UUID, ComputerName, Addresses, MacAddress, typeof(ServerCert), ServerCert " +
                "FROM Computers ORDER BY rowid"
        )

    private fun insertRaw(
        uuid: String?,
        name: String?,
        addresses: String?,
        mac: String? = null,
        certificate: ByteArray? = null,
    ) {
        raw { database ->
            database.execSQL(
                "INSERT INTO Computers(UUID, ComputerName, Addresses, MacAddress, ServerCert) " +
                    "VALUES (?, ?, ?, ?, ?)",
                arrayOf(uuid, name, addresses, mac, certificate),
            )
        }
    }

    private fun details(
        uuid: String? = "00000000-0000-0000-0000-0000000000a1",
        name: String? = "LUMINALSHINE-HOST-A",
        local: AddressTuple? = null,
        remote: AddressTuple? = null,
        manual: AddressTuple? = null,
        ipv6: AddressTuple? = null,
        mac: String? = null,
    ): ComputerDetails =
        ComputerDetails().also {
            it.uuid = uuid
            it.name = name
            it.localAddress = local
            it.remoteAddress = remote
            it.manualAddress = manual
            it.ipv6Address = ipv6
            it.macAddress = mac
        }

    private fun assertStored(details: ComputerDetails, host: FixtureHost) {
        assertThat(details.uuid).isEqualTo(host.uuid)
        assertThat(details.name).isEqualTo(host.name)
        assertThat(details.localAddress).isEqualTo(host.local)
        assertThat(details.remoteAddress).isEqualTo(host.remote)
        assertThat(details.manualAddress).isEqualTo(host.manual)
        assertThat(details.ipv6Address).isEqualTo(host.ipv6)
        assertThat(details.macAddress).isEqualTo(host.mac)
        assertThat(details.serverCert).isEqualTo(host.certificate?.let(::fixtureCertificate))
        // The external port is not a column: it follows the remote address.
        assertThat(details.externalPort).isEqualTo(host.remote?.port ?: NvHTTP.DEFAULT_HTTP_PORT)
        // The list keeps no dynamic state.
        assertThat(details.state).isEqualTo(ComputerDetails.State.UNKNOWN)
        assertThat(details.activeAddress).isNull()
        assertThat(details.httpsPort).isEqualTo(0)
        assertThat(details.pairState).isNull()
        assertThat(details.runningGameId).isEqualTo(0)
        assertThat(details.rawAppList).isNull()
        assertThat(details.nvidiaServer).isFalse()
        assertThat(details.borealis).isNull()
    }

    // The database file that the Java build wrote.

    @Test
    fun `the list that the Java build saved is read back field by field, in saving order`() {
        val computers = openFixture().getAllComputers()

        assertThat(computers.map { it.uuid }).containsExactlyElementsIn(FIXTURE_HOSTS.map { it.uuid }).inOrder()
        computers.zip(FIXTURE_HOSTS).forEach { (details, host) -> assertStored(details, host) }
    }

    @Test
    fun `a host that the Java build saved is found by its uuid`() {
        val manager = openFixture()

        FIXTURE_HOSTS.forEach { host -> assertStored(manager.getComputerByUUID(host.uuid), host) }
    }

    @Test
    fun `a host that the Java build saved is found by its name`() {
        val manager = openFixture()

        FIXTURE_HOSTS.forEach { host -> assertStored(manager.getComputerByName(host.name), host) }
    }

    @Test
    fun `opening the list that the Java build saved changes none of its rows`() {
        installFixture(DATABASE_NAME, context.computerDatabase())
        val before = raw { it.hostRows() }

        open()

        assertThat(raw { it.hostRows() }).isEqualTo(before)
    }

    @Test
    fun `writing the fixture hosts now gives the rows that the Java build wrote`() {
        installFixture(DATABASE_NAME, context.computerDatabase())
        val written = raw { it.hostRows() }
        context.deleteDatabase(DATABASE_NAME)
        val manager = open()

        FIXTURE_HOSTS.forEach { assertThat(manager.updateComputer(it.toDetails())).isTrue() }

        assertThat(raw { it.hostRows() }).isEqualTo(written)
    }

    @Test
    fun `the fixture rows hold the expected addresses JSON, text, and certificate types`() {
        installFixture(DATABASE_NAME, context.computerDatabase())

        val rows = raw { it.hostRows() }

        assertThat(rows.map { it[2] })
            .containsExactly(
                """{"local":{"address":"192.0.2.10","port":47989},""" +
                    """"remote":{"address":"192.0.2.20","port":47991},""" +
                    """"manual":{"address":"luminalshine-host-1.test","port":47992},""" +
                    """"ipv6":{"address":"2001:db8::10","port":47993}}""",
                """{"local":{"address":"192.0.2.30","port":47989}}""",
                """{"remote":{"address":"192.0.2.40","port":48000},""" +
                    """"ipv6":{"address":"2001:db8::30","port":47989}}""",
                "{}",
                """{"manual":{"address":"luminalshine-host-5.test","port":47989}}""",
            )
            .inOrder()
        assertThat(rows.map { it[4] }).containsExactly("blob", "null", "null", "blob", "null").inOrder()
        assertThat(rows.map { it[3] })
            .containsExactly("02:00:00:00:00:01", null, "02:00:00:00:00:03", null, null)
            .inOrder()
        assertThat(rows[4][1]).isEqualTo("Büro LUMINALSHINE-HOST-5")
    }

    // The schema and the file.

    @Test
    fun `a new list is the file computers4 db with the table that the Java build created`() {
        open()

        assertThat(context.databaseList().toList()).contains(DATABASE_NAME)
        assertThat(context.computerDatabase().isFile).isTrue()
        raw { database ->
            assertThat(database.rows("PRAGMA table_info(Computers)"))
                .containsExactly(
                    listOf(0L, "UUID", "TEXT", 0L, null, 1L),
                    listOf(1L, "ComputerName", "TEXT", 1L, null, 0L),
                    listOf(2L, "Addresses", "TEXT", 1L, null, 0L),
                    listOf(3L, "MacAddress", "TEXT", 0L, null, 0L),
                    listOf(4L, "ServerCert", "TEXT", 0L, null, 0L),
                )
                .inOrder()
            assertThat(database.rows("SELECT sql FROM sqlite_master WHERE name = 'Computers'"))
                .containsExactly(
                    listOf(
                        "CREATE TABLE Computers(UUID TEXT PRIMARY KEY, ComputerName TEXT NOT NULL, " +
                            "Addresses TEXT NOT NULL, MacAddress TEXT, ServerCert TEXT)"
                    )
                )
        }
    }

    @Test
    fun `opening the list again keeps the table and its rows`() {
        open().updateComputer(details(local = AddressTuple("192.0.2.1", 47989)))
        managers.forEach { it.close() }

        val computers = open().getAllComputers()

        assertThat(computers.map { it.uuid }).containsExactly("00000000-0000-0000-0000-0000000000a1")
    }

    @Test
    fun `a new list is empty`() {
        assertThat(open().getAllComputers()).isEmpty()
    }

    // What it writes.

    @Test
    fun `the addresses are written as JSON in the order local, remote, manual, ipv6`() {
        val manager = open()

        manager.updateComputer(
            details(
                local = AddressTuple("192.0.2.1", 47989),
                remote = AddressTuple("192.0.2.2", 47990),
                manual = AddressTuple("host.test", 47991),
                ipv6 = AddressTuple("[2001:db8::1]", 47992),
            )
        )

        assertThat(raw { it.rows("SELECT Addresses FROM Computers") })
            .containsExactly(
                listOf(
                    """{"local":{"address":"192.0.2.1","port":47989},""" +
                        """"remote":{"address":"192.0.2.2","port":47990},""" +
                        """"manual":{"address":"host.test","port":47991},""" +
                        """"ipv6":{"address":"2001:db8::1","port":47992}}"""
                )
            )
    }

    @Test
    fun `a missing address is left out of the JSON, and no address at all is an empty object`() {
        val manager = open()

        manager.updateComputer(
            details(uuid = "u-1", remote = AddressTuple("192.0.2.2", 47990), ipv6 = AddressTuple("2001:db8::1", 1))
        )
        manager.updateComputer(details(uuid = "u-2"))

        assertThat(raw { it.rows("SELECT Addresses FROM Computers ORDER BY rowid") })
            .containsExactly(
                listOf("""{"remote":{"address":"192.0.2.2","port":47990},"ipv6":{"address":"2001:db8::1","port":1}}"""),
                listOf("{}"),
            )
            .inOrder()
    }

    @Test
    fun `the certificate is written as its encoding in a blob, and a missing one as null`() {
        val manager = open()
        val certificate = fixtureCertificate("host-ec.pem")

        manager.updateComputer(details(uuid = "with").also { it.serverCert = certificate })
        manager.updateComputer(details(uuid = "without"))

        assertThat(raw { it.rows("SELECT UUID, typeof(ServerCert), ServerCert FROM Computers ORDER BY rowid") })
            .containsExactly(
                listOf("with", "blob", certificate.encoded.toList()),
                listOf("without", "null", null),
            )
            .inOrder()
    }

    @Test
    fun `the mac address and the name are written as text, and a missing mac address as null`() {
        val manager = open()

        manager.updateComputer(details(uuid = "with", name = "N 1", mac = "02:00:00:00:00:09"))
        manager.updateComputer(details(uuid = "without", name = "N 2"))

        assertThat(raw { it.rows("SELECT UUID, ComputerName, MacAddress FROM Computers ORDER BY rowid") })
            .containsExactly(listOf("with", "N 1", "02:00:00:00:00:09"), listOf("without", "N 2", null))
            .inOrder()
    }

    @Test
    fun `the dynamic state and the external port of the details are not saved`() {
        val manager = open()
        val saved =
            details(local = AddressTuple("192.0.2.1", 47989)).also {
                it.externalPort = 41000
                it.httpsPort = 47984
                it.state = ComputerDetails.State.ONLINE
                it.runningGameId = 7
                it.rawAppList = "<apps/>"
                it.activeAddress = AddressTuple("192.0.2.1", 47989)
            }

        manager.updateComputer(saved)
        val read = manager.getAllComputers().single()

        assertThat(read.externalPort).isEqualTo(NvHTTP.DEFAULT_HTTP_PORT)
        assertThat(read.httpsPort).isEqualTo(0)
        assertThat(read.state).isEqualTo(ComputerDetails.State.UNKNOWN)
        assertThat(read.runningGameId).isEqualTo(0)
        assertThat(read.rawAppList).isNull()
        assertThat(read.activeAddress).isNull()
    }

    // Insert, update, delete, lookup.

    @Test
    fun `updating a new host inserts it and says so`() {
        val manager = open()

        assertThat(manager.updateComputer(details(uuid = "u-1", name = "First"))).isTrue()
        assertThat(manager.updateComputer(details(uuid = "u-2", name = "Second"))).isTrue()

        assertThat(manager.getAllComputers().map { it.name }).containsExactly("First", "Second").inOrder()
    }

    @Test
    fun `updating a known host replaces every column of its row and moves it to the end`() {
        val manager = open()
        manager.updateComputer(
            details(uuid = "u-1", name = "Old", local = AddressTuple("192.0.2.1", 47989), mac = "02:00:00:00:00:01")
                .also { it.serverCert = fixtureCertificate("host-ec.pem") }
        )
        manager.updateComputer(details(uuid = "u-2", name = "Other"))

        assertThat(
                manager.updateComputer(
                    details(uuid = "u-1", name = "New", remote = AddressTuple("192.0.2.9", 48000))
                )
            )
            .isTrue()

        val computers = manager.getAllComputers()
        assertThat(computers.map { it.uuid }).containsExactly("u-2", "u-1").inOrder()
        val replaced = computers.last()
        assertThat(replaced.name).isEqualTo("New")
        assertThat(replaced.localAddress).isNull()
        assertThat(replaced.remoteAddress).isEqualTo(AddressTuple("192.0.2.9", 48000))
        assertThat(replaced.macAddress).isNull()
        assertThat(replaced.serverCert).isNull()
        assertThat(raw { it.rows("SELECT COUNT(*) FROM Computers") }).containsExactly(listOf(2L))
    }

    @Test
    fun `the external port is the port of the remote address, or the default one without it`() {
        val manager = open()
        manager.updateComputer(details(uuid = "remote", remote = AddressTuple("192.0.2.2", 41234)))
        manager.updateComputer(
            details(uuid = "local", local = AddressTuple("192.0.2.1", 41235), manual = AddressTuple("h.test", 41236))
        )

        assertThat(manager.getComputerByUUID("remote").externalPort).isEqualTo(41234)
        assertThat(manager.getComputerByUUID("local").externalPort).isEqualTo(NvHTTP.DEFAULT_HTTP_PORT)
        assertThat(NvHTTP.DEFAULT_HTTP_PORT).isEqualTo(47989)
    }

    @Test
    fun `a host without a name makes the update throw, and leaves the list as it was`() {
        val manager = open()
        manager.updateComputer(details(uuid = "u-1", name = "Kept"))

        assertThrows(SQLiteConstraintException::class.java) {
            manager.updateComputer(details(uuid = "u-1", name = null))
        }

        assertThat(manager.getAllComputers().map { it.name }).containsExactly("Kept")
    }

    @Test
    fun `a host without a uuid is stored with a null uuid, as SQLite allows it`() {
        val manager = open()

        assertThat(manager.updateComputer(details(uuid = null, name = "No id"))).isTrue()

        val stored = manager.getAllComputers().single()
        assertThat(stored.uuid).isNull()
        assertThat(stored.name).isEqualTo("No id")
    }

    @Test
    fun `a certificate that cannot be encoded is saved as null and the host is still saved`() {
        val manager = open()

        val saved =
            manager.updateComputer(details(uuid = "u-1").also { it.serverCert = UnencodableCertificate() })

        assertThat(saved).isTrue()
        assertThat(raw { it.rows("SELECT typeof(ServerCert) FROM Computers") }).containsExactly(listOf("null"))
        assertThat(manager.getComputerByUUID("u-1").serverCert).isNull()
    }

    @Test
    fun `deleting a host removes the row of its uuid and no other`() {
        val manager = openFixture()
        val doomed = manager.getComputerByUUID(FIXTURE_HOSTS[1].uuid)

        manager.deleteComputer(doomed)

        assertThat(manager.getAllComputers().map { it.uuid })
            .containsExactlyElementsIn((FIXTURE_HOSTS - FIXTURE_HOSTS[1]).map { it.uuid })
            .inOrder()
    }

    @Test
    fun `deleting a host that is not there changes nothing`() {
        val manager = openFixture()

        manager.deleteComputer(details(uuid = "no-such-uuid"))

        assertThat(manager.getAllComputers()).hasSize(FIXTURE_HOSTS.size)
    }

    @Test
    fun `deleting by a name that other hosts share uses the uuid only`() {
        val manager = open()
        manager.updateComputer(details(uuid = "u-1", name = "Same"))
        manager.updateComputer(details(uuid = "u-2", name = "Same"))

        manager.deleteComputer(details(uuid = "u-1", name = "Different"))

        assertThat(manager.getAllComputers().map { it.uuid }).containsExactly("u-2")
    }

    @Test
    fun `lookup by uuid returns null for an unknown uuid`() {
        assertThat(openFixture().getComputerByUUID("no-such-uuid")).isNull()
    }

    @Test
    fun `lookup by name returns null for an unknown name, and matches the whole name only`() {
        val manager = openFixture()

        assertThat(manager.getComputerByName("no-such-name")).isNull()
        assertThat(manager.getComputerByName("LUMINALSHINE-HOST")).isNull()
        assertThat(manager.getComputerByName("luminalshine-host-1")).isNull()
    }

    @Test
    fun `lookup by a name that several hosts share returns the first one saved`() {
        val manager = open()
        manager.updateComputer(details(uuid = "u-1", name = "Same"))
        manager.updateComputer(details(uuid = "u-2", name = "Same"))

        assertThat(manager.getComputerByName("Same").uuid).isEqualTo("u-1")
    }

    @Test
    fun `a null uuid or name in a lookup is handed to the database, which refuses it`() {
        val manager = open()

        assertThrows(IllegalArgumentException::class.java) { manager.getComputerByUUID(null) }
        assertThrows(IllegalArgumentException::class.java) { manager.getComputerByName(null) }
    }

    @Test
    fun `deleting a host without a uuid deletes nothing, not even a row with a null uuid`() {
        val manager = open()
        manager.updateComputer(details(uuid = null, name = "No id"))
        manager.updateComputer(details(uuid = "u-1", name = "Kept"))

        manager.deleteComputer(details(uuid = null))

        assertThat(manager.getAllComputers().map { it.name }).containsExactly("No id", "Kept").inOrder()
    }

    @Test
    fun `after close the list can no longer be used`() {
        val manager = open()

        manager.close()

        assertThrows(IllegalStateException::class.java) { manager.getAllComputers() }
        assertThrows(IllegalStateException::class.java) { manager.updateComputer(details()) }
    }

    // Rows that are not what the code expects.

    @Test
    fun `a bad certificate in a row is skipped and the host is returned without one`() {
        val manager = open()
        insertRaw("u-1", "Bad cert", "{}", certificate = byteArrayOf(1, 2, 3))
        insertRaw("u-2", "Good", "{}")

        val computers = manager.getAllComputers()

        assertThat(computers.map { it.uuid }).containsExactly("u-1", "u-2").inOrder()
        assertThat(computers.map { it.serverCert }).containsExactly(null, null)
    }

    @Test
    fun `addresses that are not JSON make reading throw a runtime exception caused by the JSON one`() {
        val manager = open()
        insertRaw("u-1", "Bad JSON", "not json")

        val all = assertThrows(RuntimeException::class.java) { manager.getAllComputers() }
        val one = assertThrows(RuntimeException::class.java) { manager.getComputerByUUID("u-1") }

        assertThat(all).hasCauseThat().isInstanceOf(JSONException::class.java)
        assertThat(one).hasCauseThat().isInstanceOf(JSONException::class.java)
    }

    @Test
    fun `one bad row makes the whole list throw`() {
        val manager = open()
        insertRaw("u-1", "Good", "{}")
        insertRaw("u-2", "Bad JSON", "{")
        insertRaw("u-3", "Good too", "{}")

        assertThrows(RuntimeException::class.java) { manager.getAllComputers() }
        assertThat(manager.getComputerByUUID("u-1").name).isEqualTo("Good")
    }

    @Test
    fun `an address that is malformed makes reading throw a runtime exception caused by the JSON one`() {
        val manager = open()
        for ((uuid, addresses) in
            listOf(
                "no-address" to """{"local":{"port":47989}}""",
                "no-port" to """{"local":{"address":"192.0.2.1"}}""",
                "not-an-object" to """{"remote":5}""",
                "json-null" to """{"manual":null}""",
                "port-as-text" to """{"local":{"address":"192.0.2.1","port":"x"}}""",
            )) {
            insertRaw(uuid, uuid, addresses)

            val failure = assertThrows(uuid, RuntimeException::class.java) { manager.getComputerByUUID(uuid) }

            assertThat(failure).hasCauseThat().isInstanceOf(JSONException::class.java)
        }
    }

    @Test
    fun `a port that is not positive makes reading throw the address exception`() {
        val manager = open()
        insertRaw("u-1", "Port zero", """{"local":{"address":"192.0.2.1","port":0}}""")

        assertThrows(IllegalArgumentException::class.java) { manager.getAllComputers() }
    }

    @Test
    fun `a port in text form that looks like a number is read as a number`() {
        val manager = open()
        insertRaw("u-1", "Text port", """{"local":{"address":"192.0.2.1","port":"47990"}}""")

        assertThat(manager.getComputerByUUID("u-1").localAddress).isEqualTo(AddressTuple("192.0.2.1", 47990))
    }

    @Test
    fun `an address in brackets is read without them`() {
        val manager = open()
        insertRaw("u-1", "Bracketed", """{"ipv6":{"address":"[2001:db8::2]","port":47989}}""")

        assertThat(manager.getComputerByUUID("u-1").ipv6Address).isEqualTo(AddressTuple("2001:db8::2", 47989))
    }

    @Test
    fun `an unknown key in the addresses is ignored`() {
        val manager = open()
        insertRaw(
            "u-1",
            "Extra",
            """{"other":{"address":"192.0.2.1","port":1},""" +
                """"local":{"address":"192.0.2.2","port":2}}""",
        )

        val read = manager.getComputerByUUID("u-1")

        assertThat(read.localAddress).isEqualTo(AddressTuple("192.0.2.2", 2))
        assertThat(read.remoteAddress).isNull()
    }

    // The JSON helpers that are public.

    @Test
    fun `a tuple becomes a JSON object with its address and its port`() {
        val json = ComputerDatabaseManager.tupleToJson(AddressTuple("192.0.2.1", 47989))

        assertThat(json.toString()).isEqualTo("""{"address":"192.0.2.1","port":47989}""")
        assertThat(json.get("port")).isInstanceOf(Int::class.javaObjectType)
    }

    @Test
    fun `no tuple becomes no JSON object`() {
        assertThat(ComputerDatabaseManager.tupleToJson(null)).isNull()
    }

    @Test
    fun `a tuple is read back from the JSON object under the given key`() {
        val json = JSONObject("""{"a":{"address":"192.0.2.1","port":47989},"b":{"address":"h.test","port":2}}""")

        assertThat(ComputerDatabaseManager.tupleFromJson(json, "a")).isEqualTo(AddressTuple("192.0.2.1", 47989))
        assertThat(ComputerDatabaseManager.tupleFromJson(json, "b")).isEqualTo(AddressTuple("h.test", 2))
    }

    @Test
    fun `a key that is not in the JSON object gives no tuple`() {
        assertThat(ComputerDatabaseManager.tupleFromJson(JSONObject("{}"), "a")).isNull()
        assertThat(ComputerDatabaseManager.tupleFromJson(JSONObject("""{"b":{}}"""), "a")).isNull()
    }

    @Test
    fun `a tuple that cannot be read throws the JSON exception`() {
        for (text in listOf("""{"a":null}""", """{"a":1}""", """{"a":{}}""", """{"a":{"address":"h"}}""")) {
            assertThrows(text, JSONException::class.java) {
                ComputerDatabaseManager.tupleFromJson(JSONObject(text), "a")
            }
        }
    }

    // The database that cannot be opened.

    /** Lets the real context do the work and notes which database calls reach it, in order. */
    private class RecordingContext(base: Context, private val failOpens: Int = 0) : ContextWrapper(base) {
        val calls = mutableListOf<String>()
        private var opens = 0

        override fun openOrCreateDatabase(
            name: String,
            mode: Int,
            factory: SQLiteDatabase.CursorFactory?,
        ): SQLiteDatabase {
            if (name == DATABASE_NAME) {
                calls += "open $name $mode ${factory ?: "no factory"}"
                if (opens++ < failOpens) throw SQLiteException("cannot open $name")
            }
            return super.openOrCreateDatabase(name, mode, factory)
        }

        override fun deleteDatabase(name: String): Boolean {
            // The inherited code also deletes the three databases of the old Moonlight versions
            // (computers.db, computers2.db and computers3.db) after it opens its own; they are
            // not what these tests look at.
            if (name == DATABASE_NAME) calls += "delete $name"
            return super.deleteDatabase(name)
        }
    }

    @Test
    fun `opening the list opens computers4 db for the app alone, with no cursor factory`() {
        val recording = RecordingContext(context)

        open(recording)

        assertThat(recording.calls).containsExactly("open computers4.db 0 no factory")
    }

    @Test
    fun `a database that cannot be opened is deleted and opened again`() {
        val recording = RecordingContext(context, failOpens = 1)
        installFixture(DATABASE_NAME, context.computerDatabase())

        val manager = open(recording)

        assertThat(recording.calls)
            .containsExactly(
                "open computers4.db 0 no factory",
                "delete computers4.db",
                "open computers4.db 0 no factory",
            )
            .inOrder()
        // The delete took the saved hosts with it: the list starts again, and works.
        assertThat(manager.getAllComputers()).isEmpty()
        assertThat(manager.updateComputer(details())).isTrue()
        assertThat(manager.getAllComputers()).hasSize(1)
    }

    @Test
    fun `when the second open fails too the exception is thrown`() {
        val recording = RecordingContext(context, failOpens = 2)

        val failure = assertThrows(SQLiteException::class.java) { ComputerDatabaseManager(recording) }

        assertThat(failure).hasMessageThat().contains("cannot open computers4.db")
        assertThat(recording.calls)
            .containsExactly(
                "open computers4.db 0 no factory",
                "delete computers4.db",
                "open computers4.db 0 no factory",
            )
            .inOrder()
    }

    @Test
    fun `a file that is not a database is replaced by the framework, which makes the retry unneeded`() {
        context.computerDatabase().apply {
            parentFile?.mkdirs()
            writeBytes(ByteArray(GARBAGE_BYTES) { 'x'.code.toByte() })
        }
        val recording = RecordingContext(context)

        val manager = open(recording)

        assertThat(manager.getAllComputers()).isEmpty()
        assertThat(manager.updateComputer(details())).isTrue()
        assertThat(manager.getAllComputers()).hasSize(1)
        // The framework deletes a corrupt file and opens it again by itself: no exception reaches the
        // constructor, so it opens the database once and deletes nothing.
        assertThat(recording.calls).containsExactly("open computers4.db 0 no factory")
    }

    private companion object {
        const val GARBAGE_BYTES = 4096
    }
}

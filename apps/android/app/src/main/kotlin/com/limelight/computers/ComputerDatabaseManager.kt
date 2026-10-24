package com.limelight.computers

import android.content.ContentValues
import android.content.Context
import android.database.Cursor
import android.database.sqlite.SQLiteDatabase
import android.database.sqlite.SQLiteException
import com.limelight.nvstream.http.ComputerDetails
import com.limelight.nvstream.http.NvHTTP
import java.io.ByteArrayInputStream
import java.security.cert.CertificateEncodingException
import java.security.cert.CertificateException
import java.security.cert.CertificateFactory
import java.security.cert.X509Certificate
import java.util.LinkedList
import java.util.Locale
import org.json.JSONException
import org.json.JSONObject

class ComputerDatabaseManager(c: Context) {
    private object AddressFields {
        const val LOCAL = "local"
        const val REMOTE = "remote"
        const val MANUAL = "manual"
        const val IPv6 = "ipv6"

        const val ADDRESS = "address"
        const val PORT = "port"
    }

    private val computerDb: SQLiteDatabase

    init {
        computerDb =
            try {
                // Create or open an existing DB
                c.openOrCreateDatabase(COMPUTER_DB_NAME, 0, null)
            } catch (ignored: SQLiteException) {
                // Delete the DB and try again
                c.deleteDatabase(COMPUTER_DB_NAME)
                c.openOrCreateDatabase(COMPUTER_DB_NAME, 0, null)
            }
        initializeDb()
    }

    fun close() {
        computerDb.close()
    }

    private fun initializeDb() {
        // Create tables if they aren't already there
        computerDb.execSQL(
            String.format(
                null as Locale?,
                "CREATE TABLE IF NOT EXISTS %s(%s TEXT PRIMARY KEY, %s TEXT NOT NULL, " +
                    "%s TEXT NOT NULL, %s TEXT, %s TEXT)",
                COMPUTER_TABLE_NAME,
                COMPUTER_UUID_COLUMN_NAME,
                COMPUTER_NAME_COLUMN_NAME,
                ADDRESSES_COLUMN_NAME,
                MAC_ADDRESS_COLUMN_NAME,
                SERVER_CERT_COLUMN_NAME,
            )
        )
    }

    fun deleteComputer(details: ComputerDetails) {
        computerDb.delete(
            COMPUTER_TABLE_NAME,
            "$COMPUTER_UUID_COLUMN_NAME=?",
            arrayOf(details.uuid),
        )
    }

    // Converted as is from the inherited Java; refactor in a later sprint (Sprint 12 backlog).
    // The JSON failure is still rethrown as a RuntimeException, and the stack trace of a
    // certificate that cannot be encoded still goes to the standard error stream. Logging it
    // instead is a behaviour change.
    @Suppress("TooGenericExceptionThrown", "PrintStackTrace")
    fun updateComputer(details: ComputerDetails): Boolean {
        val values = ContentValues()
        values.put(COMPUTER_UUID_COLUMN_NAME, details.uuid)
        values.put(COMPUTER_NAME_COLUMN_NAME, details.name)

        try {
            val addresses = JSONObject()
            addresses.put(AddressFields.LOCAL, tupleToJson(details.localAddress))
            addresses.put(AddressFields.REMOTE, tupleToJson(details.remoteAddress))
            addresses.put(AddressFields.MANUAL, tupleToJson(details.manualAddress))
            addresses.put(AddressFields.IPv6, tupleToJson(details.ipv6Address))
            values.put(ADDRESSES_COLUMN_NAME, addresses.toString())
        } catch (e: JSONException) {
            throw RuntimeException(e)
        }

        values.put(MAC_ADDRESS_COLUMN_NAME, details.macAddress)
        try {
            val serverCert = details.serverCert
            if (serverCert != null) {
                values.put(SERVER_CERT_COLUMN_NAME, serverCert.encoded)
            } else {
                values.put(SERVER_CERT_COLUMN_NAME, null as ByteArray?)
            }
        } catch (e: CertificateEncodingException) {
            values.put(SERVER_CERT_COLUMN_NAME, null as ByteArray?)
            e.printStackTrace()
        }
        return -1L !=
            computerDb.insertWithOnConflict(
                COMPUTER_TABLE_NAME,
                null,
                values,
                SQLiteDatabase.CONFLICT_REPLACE,
            )
    }

    // Converted as is from the inherited Java; refactor in a later sprint (Sprint 12 backlog).
    // A row whose JSON is bad still makes the read throw a RuntimeException, and the stack trace
    // of a bad certificate still goes to the standard error stream (the host is returned without
    // it).
    @Suppress("TooGenericExceptionThrown", "PrintStackTrace")
    private fun getComputerFromCursor(c: Cursor): ComputerDetails {
        val details = ComputerDetails()

        details.uuid = c.getString(UUID_COLUMN_INDEX)
        details.name = c.getString(NAME_COLUMN_INDEX)
        try {
            val addresses = JSONObject(c.getString(ADDRESSES_COLUMN_INDEX))
            details.localAddress = tupleFromJson(addresses, AddressFields.LOCAL)
            details.remoteAddress = tupleFromJson(addresses, AddressFields.REMOTE)
            details.manualAddress = tupleFromJson(addresses, AddressFields.MANUAL)
            details.ipv6Address = tupleFromJson(addresses, AddressFields.IPv6)
        } catch (e: JSONException) {
            throw RuntimeException(e)
        }

        // External port is persisted in the remote address field
        val remoteAddress = details.remoteAddress
        if (remoteAddress != null) {
            details.externalPort = remoteAddress.port
        } else {
            details.externalPort = NvHTTP.DEFAULT_HTTP_PORT
        }

        details.macAddress = c.getString(MAC_ADDRESS_COLUMN_INDEX)

        try {
            val derCertData: ByteArray? = c.getBlob(SERVER_CERT_COLUMN_INDEX)

            if (derCertData != null) {
                details.serverCert =
                    CertificateFactory.getInstance("X.509")
                        .generateCertificate(ByteArrayInputStream(derCertData)) as X509Certificate
            }
        } catch (e: CertificateException) {
            e.printStackTrace()
        }

        // This signifies we don't have dynamic state (like pair state)
        details.state = ComputerDetails.State.UNKNOWN

        return details
    }

    fun getAllComputers(): List<ComputerDetails> =
        computerDb.rawQuery("SELECT * FROM $COMPUTER_TABLE_NAME", null).use { c ->
            val computerList = LinkedList<ComputerDetails>()
            while (c.moveToNext()) {
                computerList.add(getComputerFromCursor(c))
            }
            computerList
        }

    /**
     * Get a computer by name NOTE: It is perfectly valid for multiple computers to have the same
     * name, this function will only return the first one it finds. Consider using getComputerByUUID
     * instead.
     *
     * @param name The name of the computer
     * @return The computer details, or null if no computer with that name exists
     * @see ComputerDatabaseManager.getComputerByUUID for alternative.
     */
    fun getComputerByName(name: String?): ComputerDetails? =
        computerDb
            .query(
                COMPUTER_TABLE_NAME,
                null,
                "$COMPUTER_NAME_COLUMN_NAME=?",
                arrayOf(name),
                null,
                null,
                null,
            )
            .use { c ->
                // No matching computer when there is no first row
                if (c.moveToFirst()) getComputerFromCursor(c) else null
            }

    /**
     * Get a computer by UUID
     *
     * @param uuid The UUID of the computer
     * @return The computer details, or null if no computer with that UUID exists
     * @see ComputerDatabaseManager.getComputerByName for alternative.
     */
    fun getComputerByUUID(uuid: String?): ComputerDetails? =
        computerDb
            .query(
                COMPUTER_TABLE_NAME,
                null,
                "$COMPUTER_UUID_COLUMN_NAME=?",
                arrayOf(uuid),
                null,
                null,
                null,
            )
            .use { c ->
                // No matching computer when there is no first row
                if (c.moveToFirst()) getComputerFromCursor(c) else null
            }

    companion object {
        private const val COMPUTER_DB_NAME = "computers4.db"
        private const val COMPUTER_TABLE_NAME = "Computers"
        private const val COMPUTER_UUID_COLUMN_NAME = "UUID"
        private const val COMPUTER_NAME_COLUMN_NAME = "ComputerName"
        private const val ADDRESSES_COLUMN_NAME = "Addresses"

        private const val MAC_ADDRESS_COLUMN_NAME = "MacAddress"
        private const val SERVER_CERT_COLUMN_NAME = "ServerCert"

        // The positions of the columns in the CREATE TABLE statement above
        private const val UUID_COLUMN_INDEX = 0
        private const val NAME_COLUMN_INDEX = 1
        private const val ADDRESSES_COLUMN_INDEX = 2
        private const val MAC_ADDRESS_COLUMN_INDEX = 3
        private const val SERVER_CERT_COLUMN_INDEX = 4

        @JvmStatic
        @Throws(JSONException::class)
        fun tupleToJson(tuple: ComputerDetails.AddressTuple?): JSONObject? {
            if (tuple == null) {
                return null
            }

            val json = JSONObject()
            json.put(AddressFields.ADDRESS, tuple.address)
            json.put(AddressFields.PORT, tuple.port)

            return json
        }

        @JvmStatic
        @Throws(JSONException::class)
        fun tupleFromJson(json: JSONObject, name: String): ComputerDetails.AddressTuple? {
            if (!json.has(name)) {
                return null
            }

            val address = json.getJSONObject(name)
            return ComputerDetails.AddressTuple(
                address.getString(AddressFields.ADDRESS),
                address.getInt(AddressFields.PORT),
            )
        }
    }
}

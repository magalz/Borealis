package io.github.magalz.borealis.computerstore

import android.content.Context
import com.limelight.nvstream.http.ComputerDetails
import com.limelight.nvstream.http.ComputerDetails.AddressTuple
import java.io.ByteArrayInputStream
import java.io.File
import java.security.cert.CertificateFactory
import java.security.cert.X509Certificate

/** The database of the saved host list, as the Java build names it. */
internal const val DATABASE_NAME = "computers4.db"

/** The file of the client id, as the Java build names it. */
internal const val UNIQUE_ID_FILE = "uniqueid"

/** The resource folder of the fixtures that the Java build wrote. */
private const val FIXTURE_FOLDER = "/computerstore/"

/**
 * One saved host of the fixture database. The fixture was written by the Java
 * `ComputerDatabaseManager.updateComputer` under Robolectric, from these values, and it is
 * committed as `computerstore/computers4.db`. The values are placeholders: documentation addresses
 * (RFC 5737 and RFC 3849), reserved host names and made-up ids and MAC addresses.
 */
internal data class FixtureHost(
    val uuid: String,
    val name: String,
    val local: AddressTuple? = null,
    val remote: AddressTuple? = null,
    val manual: AddressTuple? = null,
    val ipv6: AddressTuple? = null,
    val mac: String? = null,
    /** The resource of the PEM certificate that the host carries, or null for none. */
    val certificate: String? = null,
) {
    /** The details that the Java build was asked to save. */
    fun toDetails(): ComputerDetails =
        ComputerDetails().also {
            it.uuid = uuid
            it.name = name
            it.localAddress = local
            it.remoteAddress = remote
            it.manualAddress = manual
            it.ipv6Address = ipv6
            it.macAddress = mac
            it.serverCert = certificate?.let(::fixtureCertificate)
        }
}

/** The hosts of `computers4.db`, in the order in which the Java build saved them. */
internal val FIXTURE_HOSTS =
    listOf(
        FixtureHost(
            uuid = "00000000-0000-0000-0000-000000000001",
            name = "LUMINALSHINE-HOST-1",
            local = AddressTuple("192.0.2.10", 47989),
            remote = AddressTuple("192.0.2.20", 47991),
            manual = AddressTuple("luminalshine-host-1.test", 47992),
            ipv6 = AddressTuple("2001:db8::10", 47993),
            mac = "02:00:00:00:00:01",
            certificate = "host-ec.pem",
        ),
        FixtureHost(
            uuid = "00000000-0000-0000-0000-000000000002",
            name = "LUMINALSHINE-HOST-2",
            local = AddressTuple("192.0.2.30", 47989),
        ),
        FixtureHost(
            uuid = "00000000-0000-0000-0000-000000000003",
            name = "LUMINALSHINE-HOST-3",
            remote = AddressTuple("192.0.2.40", 48000),
            ipv6 = AddressTuple("2001:db8::30", 47989),
            mac = "02:00:00:00:00:03",
        ),
        FixtureHost(
            uuid = "00000000-0000-0000-0000-000000000004",
            name = "LUMINALSHINE-HOST-4",
            certificate = "host-rsa.pem",
        ),
        FixtureHost(
            uuid = "00000000-0000-0000-0000-000000000005",
            name = "Büro LUMINALSHINE-HOST-5",
            manual = AddressTuple("luminalshine-host-5.test", 47989),
        ),
    )

/** The client id in the fixture `uniqueid` file. */
internal const val FIXTURE_UNIQUE_ID = "d4a53d64582eb640"

internal fun fixtureBytes(name: String): ByteArray =
    checkNotNull(FixtureHost::class.java.getResourceAsStream(FIXTURE_FOLDER + name)) {
            "missing fixture $name"
        }
        .use { it.readBytes() }

/** A public certificate of the fixtures, as parsed from its PEM file. */
internal fun fixtureCertificate(name: String): X509Certificate =
    CertificateFactory.getInstance("X.509")
        .generateCertificate(ByteArrayInputStream(fixtureBytes(name))) as X509Certificate

/** Puts a fixture file where the app keeps [target], as it was before the app first ran. */
internal fun installFixture(name: String, target: File) {
    target.parentFile?.mkdirs()
    target.writeBytes(fixtureBytes(name))
}

/** Where the database of the saved host list lives for [context]. */
internal fun Context.computerDatabase(): File = getDatabasePath(DATABASE_NAME)

package io.github.magalz.borealis.support

import android.app.Activity
import android.app.Application
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.os.Looper
import android.widget.Toast
import androidx.test.core.app.ApplicationProvider
import com.google.common.truth.Truth.assertThat
import com.limelight.Game
import com.limelight.ShortcutTrampoline
import com.limelight.computers.ComputerManagerService
import com.limelight.discovery.DiscoveryService
import com.limelight.nvstream.http.ComputerDetails
import com.limelight.nvstream.http.ComputerDetails.AddressTuple
import com.limelight.nvstream.http.NvApp
import com.limelight.utils.ServerHelper
import io.github.magalz.borealis.library.LibraryLaunchRequest
import io.github.magalz.borealis.library.shortcuts.ShortcutIntentParser
import io.github.magalz.borealis.library.testCertificate
import java.io.IOException
import java.math.BigInteger
import java.security.Principal
import java.security.PublicKey
import java.security.cert.CertificateEncodingException
import java.security.cert.X509Certificate
import java.util.Date
import org.junit.Assert.assertThrows
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf
import org.robolectric.shadows.ShadowToast

/** The intents that start a stream and that the launcher shortcuts carry, and the offline check. */
@Suppress("DEPRECATION")
@RunWith(RobolectricTestRunner::class)
class ServerHelperCharacterizationTest {
    private val context = ApplicationProvider.getApplicationContext<Context>()
    private val activity: Activity = Robolectric.buildActivity(Activity::class.java).create().get()

    private fun computer(
        uuid: String? = "uuid-1",
        name: String? = "Den PC",
        state: ComputerDetails.State? = ComputerDetails.State.ONLINE,
        address: AddressTuple? = AddressTuple("192.168.1.5", 47989),
    ) =
        ComputerDetails().also {
            it.uuid = uuid
            it.name = name
            it.state = state
            it.activeAddress = address
            it.httpsPort = 47984
        }

    // The real binder of the real service, which binds the discovery service when it is created.
    private fun binder(): ComputerManagerService.ComputerManagerBinder {
        val app = ApplicationProvider.getApplicationContext<Application>()
        val discovery = Robolectric.buildService(DiscoveryService::class.java).create().get()
        shadowOf(app)
            .setComponentNameAndServiceForBindService(
                ComponentName(app, DiscoveryService::class.java),
                discovery.onBind(Intent()),
            )
        val service = Robolectric.buildService(ComputerManagerService::class.java).create().get()
        shadowOf(Looper.getMainLooper()).idle()
        return service.onBind(Intent()) as ComputerManagerService.ComputerManagerBinder
    }

    @Test
    fun `the connectivity test server is the one of Moonlight`() {
        assertThat(ServerHelper.CONNECTION_TEST_SERVER)
            .isEqualTo("android.conntest.moonlight-stream.org")
    }

    @Test
    fun `the current address is the active address of the computer, as the same object`() {
        val active = AddressTuple("192.168.1.5", 47989)

        assertThat(ServerHelper.getCurrentAddressFromComputer(computer(address = active)))
            .isSameInstanceAs(active)
    }

    @Test
    fun `a computer without an active address has no current address`() {
        val error =
            assertThrows(IOException::class.java) {
                ServerHelper.getCurrentAddressFromComputer(computer(address = null))
            }

        assertThat(error.javaClass).isEqualTo(IOException::class.java)
        assertThat(error).hasMessageThat().isEqualTo("No active address for Den PC")
    }

    @Test
    fun `the error of a computer that has no name and no address says null`() {
        val error =
            assertThrows(IOException::class.java) {
                ServerHelper.getCurrentAddressFromComputer(computer(name = null, address = null))
            }

        assertThat(error).hasMessageThat().isEqualTo("No active address for null")
    }

    @Test
    fun `the shortcut of a PC opens the shortcut trampoline with the name and the id of the PC`() {
        val intent = ServerHelper.createPcShortcutIntent(context, computer())

        assertThat(intent.component)
            .isEqualTo(ComponentName(context, ShortcutTrampoline::class.java))
        assertThat(intent.action).isEqualTo(Intent.ACTION_DEFAULT)
        assertThat(intent.flags).isEqualTo(0)
        assertThat(intent.extras?.keySet()).containsExactly("Name", "UUID")
        assertThat(intent.getStringExtra("Name")).isEqualTo("Den PC")
        assertThat(intent.getStringExtra("UUID")).isEqualTo("uuid-1")
        assertThat(ShortcutIntentParser.parse(intent.extras))
            .isEqualTo(LibraryLaunchRequest.Apps("uuid-1"))
    }

    @Test
    fun `the shortcut of a PC that has no name or id keeps the extras, empty`() {
        val intent =
            ServerHelper.createPcShortcutIntent(context, computer(uuid = null, name = null))

        assertThat(intent.extras?.keySet()).containsExactly("Name", "UUID")
        assertThat(intent.getStringExtra("Name")).isNull()
        assertThat(intent.getStringExtra("UUID")).isNull()
    }

    @Test
    fun `the shortcut of an app adds its name, its id as a string and its HDR flag`() {
        val intent =
            ServerHelper.createAppShortcutIntent(context, computer(), NvApp("Hades II", 42, true))

        assertThat(intent.component)
            .isEqualTo(ComponentName(context, ShortcutTrampoline::class.java))
        assertThat(intent.action).isEqualTo(Intent.ACTION_DEFAULT)
        assertThat(intent.flags).isEqualTo(0)
        assertThat(intent.extras?.keySet())
            .containsExactly("Name", "UUID", "AppName", "AppId", "HDR")
        assertThat(intent.getStringExtra("Name")).isEqualTo("Den PC")
        assertThat(intent.getStringExtra("UUID")).isEqualTo("uuid-1")
        assertThat(intent.getStringExtra("AppName")).isEqualTo("Hades II")
        assertThat(intent.extras?.get("AppId")).isEqualTo("42")
        assertThat(intent.extras?.get("HDR")).isEqualTo(true)
        assertThat(ShortcutIntentParser.parse(intent.extras))
            .isEqualTo(LibraryLaunchRequest.Launch("uuid-1", 42))
    }

    @Test
    fun `the shortcut of an app without HDR says so`() {
        val intent =
            ServerHelper.createAppShortcutIntent(context, computer(), NvApp("Steam", 7, false))

        assertThat(intent.extras?.get("HDR")).isEqualTo(false)
        assertThat(intent.extras?.get("AppId")).isEqualTo("7")
    }

    @Test
    fun `the shortcut of an app with a negative id writes it with its sign`() {
        val intent =
            ServerHelper.createAppShortcutIntent(context, computer(), NvApp("X", -5, false))

        assertThat(intent.extras?.get("AppId")).isEqualTo("-5")
    }

    @Test
    fun `the start intent opens the game with everything that it needs to connect`() {
        val computer = computer().also { it.serverCert = testCertificate() }
        val binder = binder()

        val intent =
            ServerHelper.createStartIntent(activity, NvApp("Hades II", 42, true), computer, binder)

        assertThat(intent.component).isEqualTo(ComponentName(activity, Game::class.java))
        assertThat(intent.action).isNull()
        assertThat(intent.flags).isEqualTo(0)
        assertThat(intent.extras?.keySet())
            .containsExactly(
                "Host",
                "Port",
                "HttpsPort",
                "AppName",
                "AppId",
                "HDR",
                "UniqueId",
                "UUID",
                "PcName",
                "ServerCert",
            )
        val extras = checkNotNull(intent.extras)
        assertThat(extras.get("Host")).isEqualTo("192.168.1.5")
        assertThat(extras.get("Port")).isEqualTo(47989)
        assertThat(extras.get("HttpsPort")).isEqualTo(47984)
        assertThat(extras.get("AppName")).isEqualTo("Hades II")
        assertThat(extras.get("AppId")).isEqualTo(42)
        assertThat(extras.get("HDR")).isEqualTo(true)
        assertThat(extras.get("UniqueId")).isEqualTo(binder.uniqueId)
        assertThat(extras.get("UUID")).isEqualTo("uuid-1")
        assertThat(extras.get("PcName")).isEqualTo("Den PC")
        assertThat(extras.getByteArray("ServerCert")).isEqualTo(testCertificate().encoded)
    }

    @Test
    fun `the extras of the start intent are the ones that the game reads`() {
        val intent =
            ServerHelper.createStartIntent(
                activity,
                NvApp("Hades II", 42, false),
                computer().also { it.serverCert = testCertificate() },
                binder(),
            )

        assertThat(intent.extras?.keySet())
            .containsExactly(
                Game.EXTRA_HOST,
                Game.EXTRA_PORT,
                Game.EXTRA_HTTPS_PORT,
                Game.EXTRA_APP_NAME,
                Game.EXTRA_APP_ID,
                Game.EXTRA_APP_HDR,
                Game.EXTRA_UNIQUEID,
                Game.EXTRA_PC_UUID,
                Game.EXTRA_PC_NAME,
                Game.EXTRA_SERVER_CERT,
            )
    }

    @Test
    fun `the start intent has no certificate when the computer has none`() {
        val intent =
            ServerHelper.createStartIntent(activity, NvApp("X", 1, false), computer(), binder())

        assertThat(intent.extras?.keySet()).doesNotContain("ServerCert")
        assertThat(intent.extras?.keySet()).hasSize(9)
    }

    @Test
    fun `the start intent has no certificate when the computer's cannot be encoded, and does not fail`() {
        val computer = computer().also { it.serverCert = UnencodableCertificate() }

        val intent =
            ServerHelper.createStartIntent(activity, NvApp("X", 1, false), computer, binder())

        assertThat(intent.extras?.keySet()).doesNotContain("ServerCert")
        assertThat(intent.extras?.keySet()).hasSize(9)
        assertThat(intent.getStringExtra("Host")).isEqualTo("192.168.1.5")
    }

    @Test
    fun `an IPv6 address is the host of the start intent without brackets`() {
        val computer = computer(address = AddressTuple("[fe80::1]", 47989))

        val intent =
            ServerHelper.createStartIntent(activity, NvApp("X", 1, false), computer, binder())

        assertThat(intent.getStringExtra("Host")).isEqualTo("fe80::1")
    }

    @Test
    fun `a computer that has no name has a null name in the start intent`() {
        val intent =
            ServerHelper.createStartIntent(
                activity,
                NvApp("X", 1, false),
                computer(name = null),
                binder(),
            )

        assertThat(intent.extras?.keySet()).contains("PcName")
        assertThat(intent.getStringExtra("PcName")).isNull()
    }

    @Test
    fun `a start intent for a computer without an active address cannot be made`() {
        assertThrows(RuntimeException::class.java) {
            ServerHelper.createStartIntent(
                activity,
                NvApp("X", 1, false),
                computer(address = null),
                binder(),
            )
        }
    }

    @Test
    fun `starting a stream opens the game`() {
        val binder = binder()

        ServerHelper.doStart(activity, NvApp("Hades II", 42, true), computer(), binder)

        val started = shadowOf(activity).nextStartedActivity
        assertThat(started.component?.className).isEqualTo(Game::class.java.name)
        assertThat(started.getStringExtra("Host")).isEqualTo("192.168.1.5")
        assertThat(started.getIntExtra("AppId", -1)).isEqualTo(42)
        assertThat(started.getStringExtra("UniqueId")).isEqualTo(binder.uniqueId)
        assertThat(ShadowToast.getLatestToast()).isNull()
    }

    @Test
    fun `starting a stream on a computer whose state is unknown or not set opens the game`() {
        ServerHelper.doStart(
            activity,
            NvApp("X", 1, false),
            computer(state = ComputerDetails.State.UNKNOWN),
            binder(),
        )
        ServerHelper.doStart(activity, NvApp("X", 1, false), computer(state = null), binder())

        assertThat(shadowOf(activity).nextStartedActivity).isNotNull()
        assertThat(shadowOf(activity).nextStartedActivity).isNotNull()
    }

    @Test
    fun `starting a stream on an offline computer shows a short toast and starts nothing`() {
        ServerHelper.doStart(
            activity,
            NvApp("X", 1, false),
            computer(state = ComputerDetails.State.OFFLINE),
            binder(),
        )

        assertThat(shadowOf(activity).nextStartedActivity).isNull()
        assertThat(ShadowToast.getTextOfLatestToast()).isEqualTo("Computer is offline")
        assertThat(ShadowToast.getLatestToast().duration).isEqualTo(Toast.LENGTH_SHORT)
    }

    @Test
    fun `starting a stream on a computer without an active address shows the toast and starts nothing`() {
        ServerHelper.doStart(activity, NvApp("X", 1, false), computer(address = null), binder())

        assertThat(shadowOf(activity).nextStartedActivity).isNull()
        assertThat(ShadowToast.getTextOfLatestToast()).isEqualTo("Computer is offline")
    }

    /** A certificate that cannot be encoded, as a broken provider could hand over. */
    private class UnencodableCertificate : X509Certificate() {
        override fun getEncoded(): ByteArray = throw CertificateEncodingException("test")

        override fun checkValidity() {
            unused()
        }

        override fun checkValidity(date: Date?) {
            unused()
        }

        override fun getVersion(): Int = unused()

        override fun getSerialNumber(): BigInteger = unused()

        override fun getIssuerDN(): Principal = unused()

        override fun getSubjectDN(): Principal = unused()

        override fun getNotBefore(): Date = unused()

        override fun getNotAfter(): Date = unused()

        override fun getTBSCertificate(): ByteArray = unused()

        override fun getSignature(): ByteArray = unused()

        override fun getSigAlgName(): String = unused()

        override fun getSigAlgOID(): String = unused()

        override fun getSigAlgParams(): ByteArray = unused()

        override fun getIssuerUniqueID(): BooleanArray = unused()

        override fun getSubjectUniqueID(): BooleanArray = unused()

        override fun getKeyUsage(): BooleanArray = unused()

        override fun getBasicConstraints(): Int = unused()

        override fun verify(key: PublicKey?) {
            unused()
        }

        override fun verify(key: PublicKey?, sigProvider: String?) {
            unused()
        }

        override fun toString(): String = "UnencodableCertificate"

        override fun getPublicKey(): PublicKey = unused()

        override fun hasUnsupportedCriticalExtension(): Boolean = unused()

        override fun getCriticalExtensionOIDs(): MutableSet<String> = unused()

        override fun getNonCriticalExtensionOIDs(): MutableSet<String> = unused()

        override fun getExtensionValue(oid: String?): ByteArray = unused()

        private fun unused(): Nothing = throw UnsupportedOperationException()
    }
}

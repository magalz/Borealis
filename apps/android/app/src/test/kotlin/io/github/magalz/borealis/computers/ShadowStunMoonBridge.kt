package io.github.magalz.borealis.computers

import android.content.Context
import android.net.ConnectivityManager
import android.net.Network
import androidx.test.core.app.ApplicationProvider
import com.limelight.nvstream.jni.MoonBridge
import java.util.concurrent.CopyOnWriteArrayList
import org.robolectric.annotation.Implementation
import org.robolectric.annotation.Implements
import org.robolectric.util.ReflectionHelpers

/**
 * Keeps the STUN request of the computer manager off the network. The service asks `MoonBridge`,
 * whose native library is not there off a device, for the external address of the device. This
 * shadow answers with [answer] and records the request and the network that the process was bound
 * to when it was made. Call [reset] before each test.
 *
 * Use it with `@Config(shadows = [ShadowStunMoonBridge::class])`.
 */
@Implements(MoonBridge::class)
object ShadowStunMoonBridge {
    /** One request: the STUN server and the network that the process was bound to. */
    class Request(val host: String?, val port: Int, val boundNetwork: Network?)

    val requests = CopyOnWriteArrayList<Request>()

    /** What the STUN server tells the device: the address that it came from, or null. */
    @Volatile var answer: String? = null

    fun reset() {
        requests.clear()
        answer = null
    }

    /**
     * Skips the native library. The class is initialised once for all the tests that share the
     * sandbox, whichever comes first, so this sets the three audio configurations that the other
     * shadows of `MoonBridge` set and that the settings and the launch tests read.
     */
    @Suppress("unused")
    @Implementation
    @JvmStatic
    fun __staticInitializer__() {
        set("AUDIO_CONFIGURATION_STEREO", MoonBridge.AudioConfiguration(2, 0x3))
        set("AUDIO_CONFIGURATION_51_SURROUND", MoonBridge.AudioConfiguration(6, 0x3F))
        set("AUDIO_CONFIGURATION_71_SURROUND", MoonBridge.AudioConfiguration(8, 0x63F))
    }

    @Suppress("unused")
    @Implementation
    @JvmStatic
    fun findExternalAddressIP4(stunHostName: String?, stunPort: Int): String? {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val bound = context.getSystemService(ConnectivityManager::class.java).boundNetworkForProcess
        requests += Request(stunHostName, stunPort, bound)
        return answer
    }

    private fun set(name: String, value: MoonBridge.AudioConfiguration) {
        ReflectionHelpers.setStaticField(MoonBridge::class.java, name, value)
    }
}

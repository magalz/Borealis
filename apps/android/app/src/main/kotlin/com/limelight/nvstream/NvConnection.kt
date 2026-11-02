package com.limelight.nvstream

import android.app.ActivityManager
import android.content.Context
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import android.net.RouteInfo
import android.os.Build
import com.limelight.LimeLog
import com.limelight.nvstream.av.audio.AudioRenderer
import com.limelight.nvstream.av.video.VideoDecoderRenderer
import com.limelight.nvstream.http.ComputerDetails
import com.limelight.nvstream.http.HostHttpResponseException
import com.limelight.nvstream.http.LimelightCryptoProvider
import com.limelight.nvstream.http.NvApp
import com.limelight.nvstream.http.NvHTTP
import com.limelight.nvstream.http.PairingManager
import com.limelight.nvstream.input.MouseButtonPacket
import com.limelight.nvstream.jni.MoonBridge
import io.github.magalz.borealis.HeldStageFailures
import io.github.magalz.borealis.host.failure.CauseKind
import io.github.magalz.borealis.host.failure.FailureCauses
import io.github.magalz.borealis.host.failure.StartSteps
import io.github.magalz.borealis.host.launch.AppNameLookup
import io.github.magalz.borealis.host.launch.ConflictActions
import io.github.magalz.borealis.host.launch.ConflictJoin
import io.github.magalz.borealis.host.launch.ConflictPrompt
import io.github.magalz.borealis.host.launch.HostError
import io.github.magalz.borealis.host.launch.HostErrorExtractor
import io.github.magalz.borealis.host.launch.LaunchAttempt
import io.github.magalz.borealis.host.launch.LaunchConflicts
import io.github.magalz.borealis.host.launch.LaunchHooks
import io.github.magalz.borealis.host.launch.LaunchOutcome
import io.github.magalz.borealis.host.launch.LaunchRequest
import io.github.magalz.borealis.host.launch.LaunchRetries
import io.github.magalz.borealis.host.launch.RequestedApp
import io.github.magalz.borealis.host.launch.ResumeCollisions
import io.github.magalz.borealis.host.launch.RetryProgress
import io.github.magalz.borealis.host.launch.RunningAppReader
import io.github.magalz.borealis.host.launch.ServerInfoReader
import io.github.magalz.borealis.host.pairing.CertificateMismatchException
import io.github.magalz.borealis.logging.BorealisLog
import io.github.magalz.borealis.logging.LogTag
import io.github.magalz.borealis.stream.StartFailureLog
import io.github.magalz.borealis.stream.Utf8Text
import java.io.IOException
import java.net.InetAddress
import java.net.InetSocketAddress
import java.net.Socket
import java.nio.ByteBuffer
import java.security.NoSuchAlgorithmException
import java.security.SecureRandom
import java.security.cert.X509Certificate
import java.util.concurrent.Semaphore
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicLong
import java.util.function.BooleanSupplier
import java.util.function.Supplier
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import org.xmlpull.v1.XmlPullParserException

// The markers of the import groups of the Java, which ktfmt sorts together:
// BOREALIS: standard UTF-8 for typed text (stream.keyframe)
// BOREALIS: typed failure causes (host.failure)
// BOREALIS: LuminalShine launch retries (host.launch)

// Converted as is from the inherited Java; refactor in a later sprint (Sprint 12 backlog).
// The stack traces that the Java prints to stderr and the RuntimeException that wraps the cause of
// a missing AES provider stay as they were: PrintStackTrace and TooGenericExceptionThrown are
// suppressed for that. The constructor has the seven parameters of the Java.
@Suppress("TooManyFunctions", "LargeClass", "LongParameterList")
open class NvConnection(
    // Context parameters
    private val appContext: Context,
    host: ComputerDetails.AddressTuple?,
    httpsPort: Int,
    private val uniqueId: String?,
    config: StreamConfiguration?,
    private val cryptoProvider: LimelightCryptoProvider?,
    serverCert: X509Certificate?,
) {
    private val context = ConnectionContext()
    private val isMonkey: Boolean

    // BOREALIS: set by cancelLaunch() when the user leaves before the stream started, and by stop()
    // too. Nothing resets it: a stopped NvConnection is never started again, one serves one stream
    // (host.launch)
    @Volatile private var launchCancelled = false

    // BOREALIS: one resolution of "an app is already running" per launch (host.launch)
    private val conflicts =
        LaunchConflicts(BooleanSupplier { launchCancelled }, Runnable { cancelLaunch() })

    // BOREALIS: wait, then restart once, when a /resume collides with another device's launch
    // (host.launch)
    private val collisions =
        ResumeCollisions(
            BooleanSupplier { launchCancelled },
            RetryProgress { c, a, m, d -> context.listener.launchRetrying(c, a, m, d) },
            Supplier<String?> { checkNotNull(context.serverAddress).address },
        )

    // BOREALIS: why the last start failed, for the connection error screen (host.failure)
    @Volatile private var lastFailureCause = CauseKind.NONE
    @Volatile private var lastHttpCode = 0
    @Volatile private var launchRetried = false

    // BOREALIS: the HTTP step of the start that is under way, the stage word of a failure's
    // raw code (host.failure)
    @Volatile private var startStep: String = StartSteps.SERVER_INFO

    // BOREALIS: true only between a started connection and stop(); written under the MoonBridge
    // lock (stream.keyframe)
    @Volatile private var connected = false

    // BOREALIS: true from the moment this connection takes the bridge until it gave it back: the
    // native calls are process-global, so stop() makes them only while this holds, and never for a
    // connection that did not set the bridge up (or whose bridge another connection may own by
    // now). Written under the MoonBridge monitor and under bridgeFlagLock (host.launch)
    @Volatile private var bridgeSetUp = false

    // BOREALIS: orders the read of bridgeSetUp and the interrupt that stop() makes on it against
    // every write of the flag. The interrupt flag of the native library is process-global and the
    // next start clears it: an interrupt that was decided while this connection had the bridge must
    // be made before the flag goes false and the place goes back, or it lands on the start of the
    // next connection. Held only for those steps, never across a native call that can block, so a
    // stop can still interrupt a start that runs; taken inside the MoonBridge monitor, never the
    // other way round (host.launch)
    private val bridgeFlagLock = Any()

    // BOREALIS: true while this connection holds the place of connectionAllowed, from the acquire
    // of its start until stop(), or a start that ends without a stream, gives it back (host.launch)
    private val holdsPlace = AtomicBoolean(false)

    // BOREALIS: the client of the /launch that the host accepted, until the native stream started:
    // what a leave before the stream asks to /cancel. Only for a launch (a resume may join the app
    // of another device, and a refused launch started nothing) and not for a GFE host (host.launch)
    @Volatile private var acceptedLaunch: NvHTTP? = null

    // BOREALIS: the launch generation that the accepted /launch took, meaningful while
    // acceptedLaunch is set (host.launch)
    @Volatile private var acceptedGeneration = 0L

    // BOREALIS: the /cancel of an abandoned launch is sent once per connection (host.launch)
    private val launchCancelSent = AtomicBoolean(false)

    // BOREALIS: the app that this start asks for: the configured one, or the one that the lookup by
    // name found when the configuration came without an id; set by startApp, on the start thread
    // (host.launch)
    private lateinit var appToStart: NvApp

    init {
        context.serverAddress = host
        context.httpsPort = httpsPort
        context.streamConfig = config
        context.serverCert = serverCert

        // This is unique per connection
        context.riKey = generateRiAesKey()
        context.riKeyId = generateRiKeyId()

        isMonkey = ActivityManager.isUserAMonkey()
    }

    /** The listener of a start that is under way: [start] sets it before anything uses it. */
    private val ConnectionContext.listener: NvConnectionListener
        get() = checkNotNull(connListener)

    fun stop() {
        // BOREALIS: first, so that a start still on its way ends without a stream that nobody
        // would stop: the start thread checks it after it got the place and again under the bridge
        // monitor, which the stop below takes after this (host.launch)
        cancelLaunch()
        connected = false // BOREALIS: no new keyframe request from here on (stream.keyframe)
        // Interrupt any pending connection. This is thread-safe.
        // BOREALIS: only a connection that set the bridge up. The cancel above is written before
        // this read, and the start thread writes the flag before it reads the cancel: either it
        // sees the cancel and never begins, or this sees the flag and interrupts. The opposite
        // order on the start side would let a start begin after a stop that skipped the interrupt
        // (host.launch)
        synchronized(bridgeFlagLock) {
            if (bridgeSetUp) {
                MoonBridge.interruptConnection()
            }
        }

        // Moonlight-core is not thread-safe with respect to connection start and stop, so
        // we must not invoke that functionality in parallel.
        synchronized(MoonBridge::class.java) {
            // BOREALIS: the flag is consumed, so that a second stop makes no native call either:
            // by then another connection may own the bridge (host.launch)
            val setUp =
                synchronized(bridgeFlagLock) {
                    bridgeSetUp.also { bridgeSetUp = false }
                }
            if (setUp) {
                MoonBridge.stopConnection()
            }
            // BOREALIS: the start thread may have set it again before it saw the interrupt
            // (stream.keyframe)
            connected = false
            if (setUp) {
                MoonBridge.cleanupBridge()
            }
        }

        // Now a pending connection can be processed
        releasePlace()
    }

    // BOREALIS: gives the place back once, and only when this connection holds it: stop() of a
    // connection that never took one, or a second stop(), must not add a place (host.launch)
    private fun releasePlace() {
        if (holdsPlace.compareAndSet(true, false)) {
            connectionAllowed.release()
        }
    }

    // BOREALIS: ends the retry wait of start() without stop(): a stream's place is only given back
    // by stop() (host.launch)
    fun cancelLaunch() {
        launchCancelled = true
    }

    // BOREALIS: the start failed because another device was starting its stream (host.launch)
    open fun endedByResumeCollision(): Boolean = collisions.collided()

    open fun lastFailureCause(): CauseKind = lastFailureCause

    // BOREALIS: the status of the host's answer when lastFailureCause() is HTTP, otherwise 0
    // (host.failure)
    open fun lastHttpCode(): Int = lastHttpCode

    // BOREALIS: the host kept asking to retry /launch or /resume (409 or 503) until the client gave
    // up (host.failure)
    open fun launchRetriesExhausted(): Boolean =
        FailureCauses.retriesExhausted(
            launchRetried,
            if (lastFailureCause == CauseKind.HTTP) lastHttpCode else null,
        )

    // BOREALIS: records why the start failed (host.failure)
    private fun recordStartFailure(e: Throwable) {
        val httpCode: Int? = if (e is HostHttpResponseException) e.errorCode else null
        lastHttpCode = httpCode ?: 0
        lastFailureCause = FailureCauses.of(e, httpCode)
    }

    // BOREALIS: asks the host for a new keyframe; does nothing before the stream started or
    // after it stopped (stream.keyframe)
    fun requestKeyframe() {
        if (connected) {
            MoonBridge.requestIdrFrame()
        }
    }

    // BOREALIS: /launch and /resume through the host.launch retry runner (host.launch)
    @Throws(IOException::class, XmlPullParserException::class)
    private fun launchWithRetry(h: NvHTTP, verb: String, appId: Int): LaunchOutcome {
        startStep = verb // BOREALIS: host.failure
        context.listener.launchStarting("resume" == verb) // BOREALIS: host.failure
        // BOREALIS: a stop or a cancel during /serverinfo, /applist or the connection-type
        // detection must keep the first request from being sent. Only a GFE host needs it here: its
        // request is made at once, while the runner below checks the cancel before the first
        // request to any other host, and records it in the audit trail, which this return would
        // skip. The callers take CANCELLED as "the user left, nothing to report" (host.launch)
        if (context.isNvidiaServerSoftware && launchCancelled) {
            return LaunchOutcome.CANCELLED
        }
        return LaunchRetries.run(
            LaunchRequest(
                context.isNvidiaServerSoftware,
                verb,
                checkNotNull(context.serverAddress).address,
            ),
            LaunchAttempt {
                // BOREALIS: right before the request goes out, so that a cancel that is late can
                // tell that another connection asked the host for a session after this one
                // (host.launch)
                val generation = launchGeneration.incrementAndGet()
                val accepted = h.launchApp(context, verb, appId, context.negotiatedHdr)
                // BOREALIS: what a leave before the stream cancels (host.launch)
                if (accepted && verb == "launch" && !context.isNvidiaServerSoftware) {
                    acceptedGeneration = generation
                    acceptedLaunch = h
                }
                accepted
            },
            LaunchHooks(
                HostErrorExtractor { e ->
                    if (e is HostHttpResponseException) HostError(e.errorCode, e.errorMessage)
                    else null
                },
                BooleanSupplier { launchCancelled },
                RetryProgress { c, a, m, d ->
                    launchRetried = true
                    context.listener.launchRetrying(c, a, m, d)
                },
                BooleanSupplier { conflicts.isOpen() },
            ),
        )
    }

    // BOREALIS: Join: /resume of the running app, with upstream's message when the host answers 200
    // without resuming. When it is the app that this start asked for, it waits for a busy host and
    // lets a failed RTSP handshake right after it restart the start, as the chosen resume does
    // (host.launch)
    @Throws(IOException::class, XmlPullParserException::class)
    private fun joinRunning(h: NvHTTP, context: ConnectionContext, appId: Int): LaunchOutcome {
        val askedFor = appId == appToStart.appId
        if (askedFor && !collisions.settleBeforeResume()) {
            return LaunchOutcome.CANCELLED
        }
        val joined = launchWithRetry(h, "resume", appId)
        if (joined == LaunchOutcome.FAILED) {
            context.listener.displayMessage("Failed to resume existing session")
        }
        if (joined == LaunchOutcome.SUCCEEDED && askedFor) {
            collisions.resumeAccepted(context.isNvidiaServerSoftware)
        }
        return joined
    }

    // BOREALIS: what the user can answer about the app that is running: Join, Quit and relaunch or
    // Cancel (host.launch)
    private fun conflictActions(h: NvHTTP, context: ConnectionContext): ConflictActions {
        val listener = context.listener
        return ConflictActions(
            ConflictPrompt { runningId, runningName, requestedId, requestedName, answer ->
                listener.launchConflict(runningId, runningName, requestedId, requestedName, answer)
            },
            LaunchAttempt { launchNotRunningApp(h, context) },
            ConflictJoin { id -> joinRunning(h, context, id) },
            LaunchAttempt { quitAndLaunch(h, context) },
        )
    }

    private fun requestedApp(context: ConnectionContext): RequestedApp {
        val streamConfig = checkNotNull(context.streamConfig)
        return RequestedApp(appToStart.appId, appToStart.appName, streamConfig.resumeChosen)
    }

    // BOREALIS: another app is running although /serverinfo said none, or a launch was refused
    // because an app runs: wait for it to end, then ask, once (host.launch)
    @Throws(IOException::class, XmlPullParserException::class)
    private fun resolveConflict(h: NvHTTP, context: ConnectionContext): Boolean =
        conflicts.resolve(
            RunningAppReader { h.getCurrentGame(h.getServerInfo(true)) },
            AppNameLookup { id -> h.getAppById(id)?.appName },
            requestedApp(context),
            conflictActions(h, context),
            checkNotNull(context.serverAddress).address,
        )

    // BOREALIS: /serverinfo has just shown the app that this start asked for, and the user did not
    // choose to resume it: ask at once, once (host.launch)
    @Throws(IOException::class, XmlPullParserException::class)
    private fun askAboutRunning(h: NvHTTP, context: ConnectionContext, runningAppId: Int): Boolean =
        conflicts.askAbout(
            runningAppId,
            AppNameLookup { id -> h.getAppById(id)?.appName },
            requestedApp(context),
            conflictActions(h, context),
            checkNotNull(context.serverAddress).address,
        )

    @Suppress("PrintStackTrace")
    @Throws(IOException::class)
    private fun resolveServerAddress(): InetAddress {
        val serverAddress = checkNotNull(context.serverAddress)
        // Try to find an address that works for this host
        val addrs = InetAddress.getAllByName(serverAddress.address)
        for (addr in addrs) {
            try {
                Socket().use { s ->
                    s.setSoLinger(true, 0)
                    s.connect(InetSocketAddress(addr, serverAddress.port), CONNECT_TIMEOUT_MS)
                    return addr
                }
            } catch (e: IOException) {
                e.printStackTrace()
            }
        }

        // If we made it here, we didn't manage to find a working address. If DNS returned any
        // address, we'll use the first available address and hope for the best.
        if (addrs.isNotEmpty()) {
            return addrs[0]
        } else {
            throw IOException("No addresses found for $serverAddress")
        }
    }

    // Converted as is from the inherited Java; refactor in a later sprint (Sprint 12 backlog).
    @Suppress("CyclomaticComplexMethod", "NestedBlockDepth", "ReturnCount", "PrintStackTrace")
    private fun detectServerConnectionType(): Int {
        val connMgr =
            appContext.getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager
        val activeNetwork = connMgr.activeNetwork
        if (activeNetwork != null) {
            val netCaps = connMgr.getNetworkCapabilities(activeNetwork)
            if (netCaps != null) {
                if (
                    netCaps.hasTransport(NetworkCapabilities.TRANSPORT_VPN) ||
                        !netCaps.hasCapability(NetworkCapabilities.NET_CAPABILITY_NOT_VPN)
                ) {
                    // VPNs are treated as remote connections
                    return StreamConfiguration.STREAM_CFG_REMOTE
                } else if (netCaps.hasTransport(NetworkCapabilities.TRANSPORT_CELLULAR)) {
                    // Cellular is always treated as remote to avoid any possible
                    // issues with 464XLAT or similar technologies.
                    return StreamConfiguration.STREAM_CFG_REMOTE
                }
            }

            // Check if the server address is on-link
            val linkProperties = connMgr.getLinkProperties(activeNetwork)
            if (linkProperties != null) {
                val serverAddress: InetAddress
                try {
                    serverAddress = resolveServerAddress()
                } catch (e: IOException) {
                    e.printStackTrace()

                    // We can't decide without being able to resolve the server address
                    return StreamConfiguration.STREAM_CFG_AUTO
                }

                // If the address is in the NAT64 prefix, always treat it as remote
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                    val nat64Prefix = linkProperties.nat64Prefix
                    if (nat64Prefix != null && nat64Prefix.contains(serverAddress)) {
                        return StreamConfiguration.STREAM_CFG_REMOTE
                    }
                }

                for (route in linkProperties.routes) {
                    // Skip non-unicast routes (which are all we get prior to Android 13)
                    if (
                        Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
                            route.type != RouteInfo.RTN_UNICAST
                    ) {
                        continue
                    }

                    // Find the first route that matches this address
                    if (route.matches(serverAddress)) {
                        // If there's no gateway, this is an on-link destination
                        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                            // We want to use hasGateway() because getGateway() doesn't adhere
                            // to documented behavior of returning null for on-link addresses.
                            if (!route.hasGateway()) {
                                return StreamConfiguration.STREAM_CFG_LOCAL
                            }
                        } else {
                            // getGateway() is documented to return null for on-link destinations,
                            // but it actually returns the unspecified address (0.0.0.0 or ::).
                            val gateway = route.gateway
                            if (gateway == null || gateway.isAnyLocalAddress) {
                                return StreamConfiguration.STREAM_CFG_LOCAL
                            }
                        }

                        // We _should_ stop after the first matching route, but for some reason
                        // Android doesn't always report IPv6 routes in descending order of
                        // specificity and metric. To handle that case, we enumerate all matching
                        // routes, assuming that an on-link route will always be preferred.
                    }
                }
            }
        }

        // If we can't determine the connection type, let moonlight-common-c decide.
        return StreamConfiguration.STREAM_CFG_AUTO
    }

    // Converted as is from the inherited Java; refactor in a later sprint (Sprint 12 backlog).
    @Suppress(
        "ComplexCondition",
        "CyclomaticComplexMethod",
        "LongMethod",
        "ReturnCount",
        "ThrowsCount",
    )
    @Throws(XmlPullParserException::class, IOException::class)
    private fun startApp(): Boolean {
        val h =
            NvHTTP(
                checkNotNull(context.serverAddress),
                context.httpsPort,
                uniqueId,
                context.serverCert,
                checkNotNull(cryptoProvider),
            )

        val serverInfo: String =
            try {
                // BOREALIS: retry a timed-out /serverinfo (host.launch)
                collisions.readServerInfo(ServerInfoReader { h.getServerInfo(true) })
            } catch (e: IOException) {
                // BOREALIS: the HTTP fallback after a mismatch can fail too; the mismatch is the
                // cause (host.failure)
                if (h.sawCertificateMismatch()) {
                    throw CertificateMismatchException(e)
                }
                throw e
            } catch (e: XmlPullParserException) {
                if (h.sawCertificateMismatch()) {
                    throw CertificateMismatchException(e)
                }
                throw e
            }
        // BOREALIS: the fallback to HTTP would only say "not paired" (host.failure)
        if (h.sawCertificateMismatch()) {
            throw CertificateMismatchException(null)
        }

        context.serverAppVersion = h.getServerVersion(serverInfo)
        if (context.serverAppVersion == null) {
            context.listener.displayMessage("Server version malformed")
            return false
        }

        val details = h.getComputerDetails(serverInfo)
        context.isNvidiaServerSoftware = details.nvidiaServer

        // May be missing for older servers
        context.serverGfeVersion = h.getGfeVersion(serverInfo)

        if (h.getPairState(serverInfo) != PairingManager.PairState.PAIRED) {
            context.listener.displayMessage("Device not paired with computer")
            return false
        }

        context.serverCodecModeSupport = h.getServerCodecModeSupport(serverInfo).toInt()

        val streamConfig = checkNotNull(context.streamConfig)
        context.negotiatedHdr =
            (streamConfig.supportedVideoFormats and MoonBridge.VIDEO_FORMAT_MASK_10BIT) != 0
        if ((context.serverCodecModeSupport and HDR_CODEC_MODES) == 0 && context.negotiatedHdr) {
            context.listener.displayTransientMessage(
                "Your PC GPU does not support streaming HDR. The stream will be SDR."
            )
            context.negotiatedHdr = false
        }

        //
        // Decide on negotiated stream parameters now
        //

        // Check for a supported stream resolution
        if (
            (streamConfig.width > MAX_H264_SIDE || streamConfig.height > MAX_H264_SIDE) &&
                (h.getServerCodecModeSupport(serverInfo) and CODEC_MODE_ABOVE_4K) == 0L &&
                context.isNvidiaServerSoftware
        ) {
            context.listener.displayMessage(
                "Your host PC does not support streaming at resolutions above 4K."
            )
            return false
        } else if (
            (streamConfig.width > MAX_H264_SIDE || streamConfig.height > MAX_H264_SIDE) &&
                (streamConfig.supportedVideoFormats and MoonBridge.VIDEO_FORMAT_MASK_H264.inv()) ==
                    0
        ) {
            context.listener.displayMessage(
                "Your streaming device must support HEVC or AV1 to stream at resolutions above 4K."
            )
            return false
        } else if (streamConfig.height >= HEIGHT_4K && !h.supports4K(serverInfo)) {
            // Client wants 4K but the server can't do it
            context.listener.displayTransientMessage(
                "You must update GeForce Experience to stream in 4K. The stream will be 1080p."
            )

            // Lower resolution to 1080p
            context.negotiatedWidth = WIDTH_1080P
            context.negotiatedHeight = HEIGHT_1080P
        } else {
            // Take what the client wanted
            context.negotiatedWidth = streamConfig.width
            context.negotiatedHeight = streamConfig.height
        }

        // We will perform some connection type detection if the caller asked for it
        if (streamConfig.remote == StreamConfiguration.STREAM_CFG_AUTO) {
            context.negotiatedRemoteStreaming = detectServerConnectionType()
            context.negotiatedPacketSize =
                if (context.negotiatedRemoteStreaming == StreamConfiguration.STREAM_CFG_REMOTE) {
                    REMOTE_PACKET_SIZE
                } else {
                    streamConfig.maxPacketSize
                }
        } else {
            context.negotiatedRemoteStreaming = streamConfig.remote
            context.negotiatedPacketSize = streamConfig.maxPacketSize
        }

        //
        // Video stream format will be decided during the RTSP handshake
        //

        val configuredApp = checkNotNull(streamConfig.app)
        var app: NvApp = configuredApp

        // If the client did not provide an exact app ID, do a lookup with the applist
        if (!configuredApp.isInitialized) {
            LimeLog.info(
                "Using deprecated app lookup method - " +
                    "Please specify an app ID in your StreamConfiguration instead"
            )
            val found = h.getAppByName(configuredApp.appName)
            if (found == null) {
                context.listener.displayMessage(
                    "The app " + configuredApp.appName + " is not in GFE app list"
                )
                return false
            }
            app = found
        }
        appToStart = app // BOREALIS: launch and ask with the id that was found

        // If there's a game running, resume it
        if (h.getCurrentGame(serverInfo) != 0) {
            // BOREALIS: kept outside the try so the fallback launch is not handled as a resume
            // error
            var resumed = LaunchOutcome.SUCCEEDED
            try {
                if (h.getCurrentGame(serverInfo) == app.appId) {
                    // BOREALIS: nothing tells this session from another device's: ask, unless the
                    // user chose Resume, Continue or Try again for this app (host contract 7.6). A
                    // restart went through that already
                    val restart = collisions.isRestart()
                    if (!restart && !streamConfig.resumeChosen) {
                        return askAboutRunning(h, context, app.appId)
                    }
                    if (!restart) {
                        conflicts.joinedByChoice(
                            checkNotNull(context.serverAddress).address,
                            app.appId,
                        )
                    }
                    // BOREALIS: wait while the host is busy; retries, cancellation and the no-app
                    // fallback
                    resumed =
                        if (collisions.settleBeforeResume()) {
                            launchWithRetry(h, "resume", app.appId)
                        } else {
                            LaunchOutcome.CANCELLED
                        }
                    if (resumed == LaunchOutcome.CANCELLED) { // BOREALIS
                        return false
                    }
                    // BOREALIS: was !h.launchApp(context, "resume", ...)
                    if (resumed == LaunchOutcome.FAILED) {
                        context.listener.displayMessage("Failed to resume existing session")
                        return false
                    }
                } else {
                    // BOREALIS: ask before quitting another device's app; a restart never quits it
                    return collisions.differentAppRunning(
                        LaunchAttempt { resolveConflict(h, context) }
                    )
                }
            } catch (e: HostHttpResponseException) {
                if (e.errorCode == STATUS_NOT_YOUR_SESSION) {
                    // This is the error you get when you try to resume a session that's not yours.
                    // Because this is fairly common, we'll display a more detailed message.
                    context.listener.displayMessage(
                        "This session wasn't started by this device," +
                            " so it cannot be resumed. End streaming on the original " +
                            "device or the PC itself and try again. (Error code: " +
                            e.errorCode +
                            ")"
                    )
                    return false
                } else if (e.errorCode == STATUS_MINIMIZED) {
                    context.listener.displayMessage(
                        "The application is minimized. Resume it on the PC manually or " +
                            "quit the session and start streaming again."
                    )
                    return false
                } else {
                    throw e
                }
            }
            // BOREALIS: nothing to resume, launch instead
            if (resumed == LaunchOutcome.FALLBACK_TO_LAUNCH) {
                return launchNotRunningApp(h, context)
            }

            collisions.resumeAccepted(context.isNvidiaServerSoftware) // BOREALIS
            LimeLog.info("Resumed existing game session")
            return true
        } else {
            return launchNotRunningApp(h, context)
        }
    }

    @Suppress("ReturnCount")
    @Throws(IOException::class, XmlPullParserException::class)
    protected open fun quitAndLaunch(h: NvHTTP, context: ConnectionContext): Boolean {
        // BOREALIS: the user left, possibly with the answer Quit: the host's app, which may belong
        // to another device, is not ended for a stream that nobody watches (host.launch)
        if (launchCancelled) {
            return false
        }
        startStep = StartSteps.QUIT // BOREALIS: host.failure
        try {
            if (!h.quitApp()) {
                context.listener.displayMessage(
                    "Failed to quit previous session! You must quit it manually"
                )
                return false
            }
        } catch (e: HostHttpResponseException) {
            if (e.errorCode == STATUS_NOT_YOUR_SESSION_TO_QUIT) {
                context.listener.displayMessage(
                    "This session wasn't started by this device," +
                        " so it cannot be quit. End streaming on the original " +
                        "device or the PC itself. (Error code: " +
                        e.errorCode +
                        ")"
                )
                return false
            } else {
                throw e
            }
        }

        // BOREALIS: left while the host ended the app: no launch, and no word to the listener
        // (host.launch)
        if (launchCancelled) {
            return false
        }
        return launchNotRunningApp(h, context)
    }

    // Converted as is from the inherited Java; refactor in a later sprint (Sprint 12 backlog).
    @Suppress("ReturnCount")
    @Throws(IOException::class, XmlPullParserException::class)
    private fun launchNotRunningApp(h: NvHTTP, context: ConnectionContext): Boolean {
        // Launch the app since it's not running
        // BOREALIS: retries and cancellation
        val launched = launchWithRetry(h, "launch", appToStart.appId)
        if (launched == LaunchOutcome.CANCELLED) { // BOREALIS
            return false
        }
        // BOREALIS: ask the user once
        if (launched == LaunchOutcome.CONFLICT) {
            return resolveConflict(h, context)
        }
        // BOREALIS: was !h.launchApp(context, "launch", ...)
        if (launched != LaunchOutcome.SUCCEEDED) {
            context.listener.displayMessage("Failed to launch application")
            return false
        }

        LimeLog.info("Launched new game session")

        return true
    }

    fun start(
        audioRenderer: AudioRenderer?,
        videoDecoderRenderer: VideoDecoderRenderer?,
        connectionListener: NvConnectionListener?,
    ) {
        Thread {
            runStart(audioRenderer, videoDecoderRenderer, connectionListener)
            cancelAbandonedLaunch() // BOREALIS: host.launch
        }
            .start()
    }

    // BOREALIS: the start ended because the user left, after the host accepted its /launch and
    // before a stream came: the host would start the app and roll it back later, so it is asked to
    // cancel it, once, off the main thread (this runs on the thread of the start). A refused
    // request is only logged: the rollback of the host is what is left. Not when another connection
    // asked the host for a session since: every connection sends the same unique id and the host's
    // cancel ends the session of whoever asks, so it would end the stream that the user started
    // next. The generation is read right before the request. What is left is the time from that
    // read until the host handles the cancel: a /launch or /resume of another connection that the
    // host handles in it is ended too, and only a cancel that names its session could close that
    // (host.launch)
    private fun cancelAbandonedLaunch() {
        val h = acceptedLaunch ?: return
        if (
            !launchCancelled ||
                !launchCancelSent.compareAndSet(false, true) ||
                launchGeneration.get() != acceptedGeneration
        ) {
            return
        }
        try {
            h.cancelApp()
        } catch (e: IOException) {
            BorealisLog.w(LogTag.LAUNCH, "launch cancel failed", "error" to e.javaClass.simpleName)
        } catch (e: XmlPullParserException) {
            BorealisLog.w(LogTag.LAUNCH, "launch cancel failed", "error" to e.javaClass.simpleName)
        }
    }

    // BOREALIS: sets the flag that says this connection has the bridge, and takes it back when the
    // user already left: the start is then not to begin. The flag is written before the cancel is
    // read, and the read of the stop (which writes the cancel and then reads the flag) is the same
    // step against this one: the stop never sees the flag of a start that did not begin, and sees
    // it, and interrupts, for one that did (host.launch)
    private fun setBridgeUpUnlessCancelled(): Boolean =
        synchronized(bridgeFlagLock) {
            bridgeSetUp = true
            val cancelled = launchCancelled
            if (cancelled) {
                bridgeSetUp = false
            }
            !cancelled
        }

    // BOREALIS: the native start answered: a stream that started has nothing left to cancel on the
    // host (host.launch)
    private fun nativeStartEnded(ret: Int) {
        if (ret == 0) {
            acceptedLaunch = null
        }
    }

    /** What the thread of [start] does. */
    // Converted as is from the inherited Java; refactor in a later sprint (Sprint 12 backlog).
    @Suppress("LongMethod", "ReturnCount")
    private fun runStart(
        audioRenderer: AudioRenderer?,
        videoDecoderRenderer: VideoDecoderRenderer?,
        connectionListener: NvConnectionListener?,
    ) {
        context.connListener = connectionListener
        // A null renderer or listener fails here, in the thread, at its first use, as in the Java.
        context.videoCapabilities = checkNotNull(videoDecoderRenderer).getCapabilities()

        val appName = checkNotNull(checkNotNull(context.streamConfig).app).appName

        lastFailureCause = CauseKind.NONE // BOREALIS: host.failure
        lastHttpCode = 0 // BOREALIS
        launchRetried = false // BOREALIS
        acceptedLaunch = null // BOREALIS: host.launch
        startStep = StartSteps.SERVER_INFO // BOREALIS

        context.listener.stageStarting(appName)

        try {
            if (!startApp()) {
                // BOREALIS: the user left, nothing to report
                if (launchCancelled) {
                    return
                }
                // BOREALIS: the step, not the app name
                context.listener.stageFailed(startStep, 0, 0)
                return
            }
            context.listener.stageComplete(appName)
        } catch (e: HostHttpResponseException) {
            // BOREALIS: the user left during the request, nothing to report
            if (launchCancelled) {
                return
            }
            // BOREALIS: the log masks addresses; the screen never shows the exception text
            StartFailureLog.failed(startStep, e)
            recordStartFailure(e) // BOREALIS: host.failure
            // BOREALIS: the step, not the app name
            context.listener.stageFailed(startStep, 0, e.errorCode)
            return
        } catch (e: XmlPullParserException) {
            failedOnTheNetwork(e)
            return
        } catch (e: IOException) {
            failedOnTheNetwork(e)
            return
        }

        // BOREALIS: before the semaphore, whose place a stream gives back only by stop()
        if (launchCancelled) {
            return
        }

        val ib = ByteBuffer.allocate(RI_IV_SIZE)
        ib.putInt(context.riKeyId)

        // Acquire the connection semaphore to ensure we only have one
        // connection going at once.
        try {
            connectionAllowed.acquire()
        } catch (e: InterruptedException) {
            // BOREALIS: the exception text goes to the log only
            StartFailureLog.failed(startStep, e)
            // BOREALIS: the step, not the app name
            context.listener.stageFailed(startStep, 0, 0)
            return
        }
        // BOREALIS: before the cancel check, so that a stop() from here on gives the place back
        // too (host.launch)
        holdsPlace.set(true)
        // BOREALIS: left while waiting for the semaphore
        if (launchCancelled) {
            releasePlace()
            return
        }

        // Moonlight-core is not thread-safe with respect to connection start and stop, so
        // we must not invoke that functionality in parallel.
        synchronized(MoonBridge::class.java) {
            // BOREALIS: a whole stop() may have come since the check above, and the native start
            // would reset its interrupt: the start is either complete before the stop takes the
            // monitor, and the stop then stops it, or it never begins. The flag is written before
            // the cancel is read: stop() writes the cancel and then reads the flag outside the
            // monitor to decide on the interrupt, so either this sees the cancel and never begins,
            // or the stop sees the flag and interrupts. The opposite order would let the start
            // begin after a stop that skipped the interrupt (host.launch)
            if (!setBridgeUpUnlessCancelled()) {
                releasePlace()
                return
            }
            // BOREALIS: hold an RTSP failure that collides with another device
            MoonBridge.setupBridge(
                videoDecoderRenderer,
                audioRenderer,
                HeldStageFailures(checkNotNull(connectionListener), collisions),
            )
            val streamConfig = checkNotNull(context.streamConfig)
            val ret =
                MoonBridge.startConnection(
                    checkNotNull(context.serverAddress).address,
                    context.serverAppVersion,
                    context.serverGfeVersion,
                    context.rtspSessionUrl,
                    context.serverCodecModeSupport,
                    context.negotiatedWidth,
                    context.negotiatedHeight,
                    streamConfig.refreshRate,
                    streamConfig.bitrate,
                    context.negotiatedPacketSize,
                    context.negotiatedRemoteStreaming,
                    checkNotNull(streamConfig.audioConfiguration).toInt(),
                    streamConfig.supportedVideoFormats,
                    streamConfig.clientRefreshRateX100,
                    checkNotNull(context.riKey).encoded,
                    ib.array(),
                    context.videoCapabilities,
                    streamConfig.colorSpace,
                    streamConfig.colorRange,
                )
            connected = ret == 0 // BOREALIS: stream.keyframe
            nativeStartEnded(ret) // BOREALIS: host.launch
            if (ret != 0) {
                // LiStartConnection() failed, so the caller is not expected
                // to stop the connection themselves. We need to release their
                // semaphore count for them.
                // BOREALIS: and the bridge: LiStartConnection undid its own work, so what is left
                // is the renderers and the listener of this start, which must not outlive it. The
                // flag goes before the place, because another connection may start right after
                // this one gave it back, and a late stop() of this one must then make no native
                // call; a restart sets it again. A stop that read the flag has made its interrupt
                // by the time the lock lets the clear through, so it cannot land on the start of
                // the connection that takes the place next (host.launch)
                MoonBridge.cleanupBridge()
                synchronized(bridgeFlagLock) { bridgeSetUp = false }
                releasePlace()
                // BOREALIS: restart the whole start once
                if (collisions.restartAfterFailure()) {
                    start(audioRenderer, videoDecoderRenderer, connectionListener)
                }
                return
            }
        }
    }

    /** The XML of the host, or the connection to it, failed during the start. */
    private fun failedOnTheNetwork(e: Exception) {
        // BOREALIS: the user left during the /serverinfo retry, nothing to report
        if (launchCancelled) {
            return
        }
        // BOREALIS: the log masks addresses; the screen never shows the exception text
        StartFailureLog.failed(startStep, e)
        recordStartFailure(e) // BOREALIS: host.failure
        // BOREALIS: the step, not the app name
        context.listener.stageFailed(
            startStep,
            MoonBridge.ML_PORT_FLAG_TCP_47984 or MoonBridge.ML_PORT_FLAG_TCP_47989,
            0,
        )
    }

    open fun sendMouseMove(deltaX: Short, deltaY: Short) {
        if (!isMonkey) {
            MoonBridge.sendMouseMove(deltaX, deltaY)
        }
    }

    fun sendMousePosition(x: Short, y: Short, referenceWidth: Short, referenceHeight: Short) {
        if (!isMonkey) {
            MoonBridge.sendMousePosition(x, y, referenceWidth, referenceHeight)
        }
    }

    fun sendMouseMoveAsMousePosition(
        deltaX: Short,
        deltaY: Short,
        referenceWidth: Short,
        referenceHeight: Short,
    ) {
        if (!isMonkey) {
            MoonBridge.sendMouseMoveAsMousePosition(deltaX, deltaY, referenceWidth, referenceHeight)
        }
    }

    open fun sendMouseButtonDown(mouseButton: Byte) {
        if (!isMonkey) {
            MoonBridge.sendMouseButton(MouseButtonPacket.PRESS_EVENT, mouseButton)
        }
    }

    open fun sendMouseButtonUp(mouseButton: Byte) {
        if (!isMonkey) {
            MoonBridge.sendMouseButton(MouseButtonPacket.RELEASE_EVENT, mouseButton)
        }
    }

    open fun sendControllerInput(
        controllerNumber: Short,
        activeGamepadMask: Short,
        buttonFlags: Int,
        leftTrigger: Byte,
        rightTrigger: Byte,
        leftStickX: Short,
        leftStickY: Short,
        rightStickX: Short,
        rightStickY: Short,
    ) {
        if (!isMonkey) {
            MoonBridge.sendMultiControllerInput(
                controllerNumber,
                activeGamepadMask,
                buttonFlags,
                leftTrigger,
                rightTrigger,
                leftStickX,
                leftStickY,
                rightStickX,
                rightStickY,
            )
        }
    }

    fun sendKeyboardInput(keyMap: Short, keyDirection: Byte, modifier: Byte, flags: Byte) {
        if (!isMonkey) {
            MoonBridge.sendKeyboardInput(keyMap, keyDirection, modifier, flags)
        }
    }

    open fun sendMouseScroll(scrollClicks: Byte) {
        if (!isMonkey) {
            MoonBridge.sendMouseHighResScroll((scrollClicks * WHEEL_DELTA).toShort())
        }
    }

    open fun sendMouseHScroll(scrollClicks: Byte) {
        if (!isMonkey) {
            MoonBridge.sendMouseHighResHScroll((scrollClicks * WHEEL_DELTA).toShort())
        }
    }

    open fun sendMouseHighResScroll(scrollAmount: Short) {
        if (!isMonkey) {
            MoonBridge.sendMouseHighResScroll(scrollAmount)
        }
    }

    open fun sendMouseHighResHScroll(scrollAmount: Short) {
        if (!isMonkey) {
            MoonBridge.sendMouseHighResHScroll(scrollAmount)
        }
    }

    fun sendTouchEvent(
        eventType: Byte,
        pointerId: Int,
        x: Float,
        y: Float,
        pressureOrDistance: Float,
        contactAreaMajor: Float,
        contactAreaMinor: Float,
        rotation: Short,
    ): Int =
        if (!isMonkey) {
            MoonBridge.sendTouchEvent(
                eventType,
                pointerId,
                x,
                y,
                pressureOrDistance,
                contactAreaMajor,
                contactAreaMinor,
                rotation,
            )
        } else {
            MoonBridge.LI_ERR_UNSUPPORTED
        }

    fun sendPenEvent(
        eventType: Byte,
        toolType: Byte,
        penButtons: Byte,
        x: Float,
        y: Float,
        pressureOrDistance: Float,
        contactAreaMajor: Float,
        contactAreaMinor: Float,
        rotation: Short,
        tilt: Byte,
    ): Int =
        if (!isMonkey) {
            MoonBridge.sendPenEvent(
                eventType,
                toolType,
                penButtons,
                x,
                y,
                pressureOrDistance,
                contactAreaMajor,
                contactAreaMinor,
                rotation,
                tilt,
            )
        } else {
            MoonBridge.LI_ERR_UNSUPPORTED
        }

    open fun sendControllerArrivalEvent(
        controllerNumber: Byte,
        activeGamepadMask: Short,
        type: Byte,
        supportedButtonFlags: Int,
        capabilities: Short,
    ): Int =
        MoonBridge.sendControllerArrivalEvent(
            controllerNumber,
            activeGamepadMask,
            type,
            supportedButtonFlags,
            capabilities,
        )

    open fun sendControllerTouchEvent(
        controllerNumber: Byte,
        eventType: Byte,
        pointerId: Int,
        x: Float,
        y: Float,
        pressure: Float,
    ): Int =
        if (!isMonkey) {
            MoonBridge.sendControllerTouchEvent(
                controllerNumber,
                eventType,
                pointerId,
                x,
                y,
                pressure,
            )
        } else {
            MoonBridge.LI_ERR_UNSUPPORTED
        }

    open fun sendControllerMotionEvent(
        controllerNumber: Byte,
        motionType: Byte,
        x: Float,
        y: Float,
        z: Float,
    ): Int =
        if (!isMonkey) {
            MoonBridge.sendControllerMotionEvent(controllerNumber, motionType, x, y, z)
        } else {
            MoonBridge.LI_ERR_UNSUPPORTED
        }

    fun sendControllerBatteryEvent(
        controllerNumber: Byte,
        batteryState: Byte,
        batteryPercentage: Byte,
    ) {
        MoonBridge.sendControllerBatteryEvent(controllerNumber, batteryState, batteryPercentage)
    }

    fun sendUtf8Text(text: String?) {
        if (!isMonkey) {
            // BOREALIS: JNI needs standard UTF-8, not Java's modified UTF-8
            MoonBridge.sendUtf8Text(Utf8Text.bytes(requireNotNull(text)))
        }
    }

    companion object {
        private val connectionAllowed = Semaphore(1)

        // BOREALIS: counts the /launch and /resume requests of every connection of the process, one
        // more right before each goes out (host.launch)
        private val launchGeneration = AtomicLong()

        private const val AES_KEY_BITS = 128
        private const val CONNECT_TIMEOUT_MS = 1000
        private const val HDR_CODEC_MODES = 0x20200
        private const val CODEC_MODE_ABOVE_4K = 0x200L
        private const val MAX_H264_SIDE = 4096
        private const val HEIGHT_4K = 2160
        private const val WIDTH_1080P = 1920
        private const val HEIGHT_1080P = 1080
        private const val REMOTE_PACKET_SIZE = 1024
        private const val RI_IV_SIZE = 16
        private const val WHEEL_DELTA = 120
        private const val STATUS_NOT_YOUR_SESSION = 470
        private const val STATUS_MINIMIZED = 525
        private const val STATUS_NOT_YOUR_SESSION_TO_QUIT = 599

        @Suppress("PrintStackTrace", "TooGenericExceptionThrown")
        private fun generateRiAesKey(): SecretKey {
            try {
                val keyGen = KeyGenerator.getInstance("AES")

                // RI keys are 128 bits
                keyGen.init(AES_KEY_BITS)

                return keyGen.generateKey()
            } catch (e: NoSuchAlgorithmException) {
                e.printStackTrace()
                throw RuntimeException(e)
            }
        }

        private fun generateRiKeyId(): Int = SecureRandom().nextInt()

        @JvmStatic
        fun findExternalAddressForMdns(stunHostname: String?, stunPort: Int): String? =
            MoonBridge.findExternalAddressIP4(stunHostname, stunPort)
    }
}

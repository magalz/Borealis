package com.limelight.nvstream;

import android.app.ActivityManager;
import android.content.Context;
import android.net.ConnectivityManager;
import android.net.IpPrefix;
import android.net.LinkProperties;
import android.net.Network;
import android.net.NetworkCapabilities;
import android.net.RouteInfo;
import android.os.Build;

import java.io.IOException;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.Socket;
import java.nio.ByteBuffer;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.security.cert.X509Certificate;
import java.util.Timer;
import java.util.TimerTask;
import java.util.concurrent.Semaphore;

import javax.crypto.KeyGenerator;
import javax.crypto.SecretKey;

import org.xmlpull.v1.XmlPullParserException;

import com.limelight.LimeLog;
import com.limelight.nvstream.av.audio.AudioRenderer;
import com.limelight.nvstream.av.video.VideoDecoderRenderer;
import com.limelight.nvstream.http.ComputerDetails;
import com.limelight.nvstream.http.HostHttpResponseException;
import com.limelight.nvstream.http.LimelightCryptoProvider;
import com.limelight.nvstream.http.NvApp;
import com.limelight.nvstream.http.NvHTTP;
import com.limelight.nvstream.http.PairingManager;
import com.limelight.nvstream.input.MouseButtonPacket;
import com.limelight.nvstream.jni.MoonBridge;
// BOREALIS: standard UTF-8 for typed text (stream.keyframe)
import io.github.magalz.borealis.stream.StartFailureLog;
import io.github.magalz.borealis.stream.Utf8Text;
// BOREALIS: typed failure causes (host.failure)
import io.github.magalz.borealis.host.failure.CauseKind;
import io.github.magalz.borealis.host.failure.FailureCauses;
import io.github.magalz.borealis.host.failure.StartSteps;
import io.github.magalz.borealis.host.pairing.CertificateMismatchException;
// BOREALIS: LuminalShine launch retries (host.launch)
import io.github.magalz.borealis.host.launch.ConflictActions;
import io.github.magalz.borealis.host.launch.HostError;
import io.github.magalz.borealis.host.launch.LaunchConflicts;
import io.github.magalz.borealis.host.launch.LaunchHooks;
import io.github.magalz.borealis.host.launch.LaunchOutcome;
import io.github.magalz.borealis.host.launch.LaunchRequest;
import io.github.magalz.borealis.host.launch.LaunchRetries;
import io.github.magalz.borealis.host.launch.RequestedApp;
import io.github.magalz.borealis.host.launch.ResumeCollisions;
import io.github.magalz.borealis.host.launch.ServerInfoRetry;

public class NvConnection {
    // Context parameters
    private LimelightCryptoProvider cryptoProvider;
    private String uniqueId;
    private ConnectionContext context;
    private static Semaphore connectionAllowed = new Semaphore(1);
    private final boolean isMonkey;
    private final Context appContext;
    // BOREALIS: set by cancelLaunch() when the user leaves before the stream started (host.launch)
    private volatile boolean launchCancelled;
    // BOREALIS: one resolution of "an app is already running" per launch (host.launch)
    private final LaunchConflicts conflicts = new LaunchConflicts(() -> launchCancelled, this::cancelLaunch);
    // BOREALIS: wait, then restart once, when a /resume collides with another device's launch (host.launch)
    private final ResumeCollisions collisions = new ResumeCollisions(() -> launchCancelled, (c, a, m, d) -> context.connListener.launchRetrying(c, a, m, d), () -> context.serverAddress.address);

    public NvConnection(Context appContext, ComputerDetails.AddressTuple host, int httpsPort, String uniqueId, StreamConfiguration config, LimelightCryptoProvider cryptoProvider, X509Certificate serverCert)
    {
        this.appContext = appContext;
        this.cryptoProvider = cryptoProvider;
        this.uniqueId = uniqueId;

        this.context = new ConnectionContext();
        this.context.serverAddress = host;
        this.context.httpsPort = httpsPort;
        this.context.streamConfig = config;
        this.context.serverCert = serverCert;

        // This is unique per connection
        this.context.riKey = generateRiAesKey();
        this.context.riKeyId = generateRiKeyId();

        this.isMonkey = ActivityManager.isUserAMonkey();
    }
    
    private static SecretKey generateRiAesKey() {
        try {
            KeyGenerator keyGen = KeyGenerator.getInstance("AES");

            // RI keys are 128 bits
            keyGen.init(128);

            return keyGen.generateKey();
        } catch (NoSuchAlgorithmException e) {
            e.printStackTrace();
            throw new RuntimeException(e);
        }
    }
    
    private static int generateRiKeyId() {
        return new SecureRandom().nextInt();
    }

    public void stop() {
        connected = false; // BOREALIS: no new keyframe request from here on (stream.keyframe)
        // Interrupt any pending connection. This is thread-safe.
        MoonBridge.interruptConnection();

        // Moonlight-core is not thread-safe with respect to connection start and stop, so
        // we must not invoke that functionality in parallel.
        synchronized (MoonBridge.class) {
            MoonBridge.stopConnection();
            connected = false; // BOREALIS: the start thread may have set it again before it saw the interrupt (stream.keyframe)
            MoonBridge.cleanupBridge();
        }

        // Now a pending connection can be processed
        connectionAllowed.release();
    }

    // BOREALIS: ends the retry wait of start() without stop(): the semaphore is only released by stop() (host.launch)
    public void cancelLaunch() {
        launchCancelled = true;
    }

    public boolean endedByResumeCollision() { return collisions.collided(); } // BOREALIS: the start failed because another device was starting its stream (host.launch)

    // BOREALIS: why the last start failed, for the connection error screen (host.failure)
    private volatile CauseKind lastFailureCause = CauseKind.NONE;
    private volatile int lastHttpCode;
    private volatile boolean launchRetried;
    // BOREALIS: the HTTP step of the start that is under way, the stage word of a failure's raw code (host.failure)
    private volatile String startStep = StartSteps.SERVER_INFO;
    // BOREALIS: true only between a started connection and stop(); written under the MoonBridge lock (stream.keyframe)
    private volatile boolean connected;

    public CauseKind lastFailureCause() { return lastFailureCause; }

    // BOREALIS: the status of the host's answer when lastFailureCause() is HTTP, otherwise 0 (host.failure)
    public int lastHttpCode() { return lastHttpCode; }

    // BOREALIS: the host kept asking to retry /launch or /resume (409 or 503) until the client gave up (host.failure)
    public boolean launchRetriesExhausted() {
        return FailureCauses.retriesExhausted(launchRetried, lastFailureCause == CauseKind.HTTP ? Integer.valueOf(lastHttpCode) : null);
    }

    // BOREALIS: records why the start failed (host.failure)
    private void recordStartFailure(Throwable e) {
        Integer httpCode = e instanceof HostHttpResponseException ? Integer.valueOf(((HostHttpResponseException) e).getErrorCode()) : null;
        lastHttpCode = httpCode != null ? httpCode : 0;
        lastFailureCause = FailureCauses.of(e, httpCode);
    }

    // BOREALIS: asks the host for a new keyframe; does nothing before the stream started or after it stopped (stream.keyframe)
    public void requestKeyframe() {
        if (connected) {
            MoonBridge.requestIdrFrame();
        }
    }

    // BOREALIS: /launch and /resume through the host.launch retry runner (host.launch)
    private LaunchOutcome launchWithRetry(NvHTTP h, String verb, int appId) throws IOException, XmlPullParserException {
        startStep = verb; // BOREALIS: host.failure
        context.connListener.launchStarting("resume".equals(verb)); // BOREALIS: host.failure
        return LaunchRetries.run(new LaunchRequest(context.isNvidiaServerSoftware, verb, context.serverAddress.address),
                () -> h.launchApp(context, verb, appId, context.negotiatedHdr),
                new LaunchHooks(
                        e -> e instanceof HostHttpResponseException ? new HostError(((HostHttpResponseException) e).getErrorCode(), ((HostHttpResponseException) e).getErrorMessage()) : null,
                        () -> launchCancelled, (c, a, m, d) -> { launchRetried = true; context.connListener.launchRetrying(c, a, m, d); }, conflicts::isOpen));
    }

    // BOREALIS: Join: /resume of the running app, with upstream's message when the host answers 200 without resuming (host.launch)
    private LaunchOutcome joinRunning(NvHTTP h, ConnectionContext context, int appId) throws IOException, XmlPullParserException {
        LaunchOutcome joined = launchWithRetry(h, "resume", appId);
        if (joined == LaunchOutcome.FAILED) { context.connListener.displayMessage("Failed to resume existing session"); }
        return joined;
    }

    // BOREALIS: another app is running although /serverinfo said none: Join, Quit and relaunch or Cancel, asked once (host.launch)
    private boolean resolveConflict(NvHTTP h, ConnectionContext context) throws IOException, XmlPullParserException {
        NvApp app = context.streamConfig.getApp();
        return conflicts.resolve(() -> h.getCurrentGame(h.getServerInfo(true)),
                id -> { NvApp running = h.getAppById(id); return running != null ? running.getAppName() : null; },
                new RequestedApp(app.getAppId(), app.getAppName()),
                new ConflictActions(context.connListener::launchConflict,
                        () -> launchNotRunningApp(h, context), id -> joinRunning(h, context, id), () -> quitAndLaunch(h, context)),
                context.serverAddress.address);
    }

    private InetAddress resolveServerAddress() throws IOException {
        // Try to find an address that works for this host
        InetAddress[] addrs = InetAddress.getAllByName(context.serverAddress.address);
        for (InetAddress addr : addrs) {
            try (Socket s = new Socket()) {
                s.setSoLinger(true, 0);
                s.connect(new InetSocketAddress(addr, context.serverAddress.port), 1000);
                return addr;
            } catch (IOException e) {
                e.printStackTrace();
            }
        }

        // If we made it here, we didn't manage to find a working address. If DNS returned any
        // address, we'll use the first available address and hope for the best.
        if (addrs.length > 0) {
            return addrs[0];
        }
        else {
            throw new IOException("No addresses found for "+context.serverAddress);
        }
    }

    private int detectServerConnectionType() {
        ConnectivityManager connMgr = (ConnectivityManager) appContext.getSystemService(Context.CONNECTIVITY_SERVICE);
        Network activeNetwork = connMgr.getActiveNetwork();
        if (activeNetwork != null) {
            NetworkCapabilities netCaps = connMgr.getNetworkCapabilities(activeNetwork);
            if (netCaps != null) {
                if (netCaps.hasTransport(NetworkCapabilities.TRANSPORT_VPN) ||
                        !netCaps.hasCapability(NetworkCapabilities.NET_CAPABILITY_NOT_VPN)) {
                    // VPNs are treated as remote connections
                    return StreamConfiguration.STREAM_CFG_REMOTE;
                }
                else if (netCaps.hasTransport(NetworkCapabilities.TRANSPORT_CELLULAR)) {
                    // Cellular is always treated as remote to avoid any possible
                    // issues with 464XLAT or similar technologies.
                    return StreamConfiguration.STREAM_CFG_REMOTE;
                }
            }

            // Check if the server address is on-link
            LinkProperties linkProperties = connMgr.getLinkProperties(activeNetwork);
            if (linkProperties != null) {
                InetAddress serverAddress;
                try {
                    serverAddress = resolveServerAddress();
                } catch (IOException e) {
                    e.printStackTrace();

                    // We can't decide without being able to resolve the server address
                    return StreamConfiguration.STREAM_CFG_AUTO;
                }

                // If the address is in the NAT64 prefix, always treat it as remote
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                    IpPrefix nat64Prefix = linkProperties.getNat64Prefix();
                    if (nat64Prefix != null && nat64Prefix.contains(serverAddress)) {
                        return StreamConfiguration.STREAM_CFG_REMOTE;
                    }
                }

                for (RouteInfo route : linkProperties.getRoutes()) {
                    // Skip non-unicast routes (which are all we get prior to Android 13)
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU && route.getType() != RouteInfo.RTN_UNICAST) {
                        continue;
                    }

                    // Find the first route that matches this address
                    if (route.matches(serverAddress)) {
                        // If there's no gateway, this is an on-link destination
                        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                            // We want to use hasGateway() because getGateway() doesn't adhere
                            // to documented behavior of returning null for on-link addresses.
                            if (!route.hasGateway()) {
                                return StreamConfiguration.STREAM_CFG_LOCAL;
                            }
                        }
                        else {
                            // getGateway() is documented to return null for on-link destinations,
                            // but it actually returns the unspecified address (0.0.0.0 or ::).
                            InetAddress gateway = route.getGateway();
                            if (gateway == null || gateway.isAnyLocalAddress()) {
                                return StreamConfiguration.STREAM_CFG_LOCAL;
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
        return StreamConfiguration.STREAM_CFG_AUTO;
    }
    
    private boolean startApp() throws XmlPullParserException, IOException
    {
        NvHTTP h = new NvHTTP(context.serverAddress, context.httpsPort, uniqueId, context.serverCert, cryptoProvider);

        String serverInfo;
        try {
            serverInfo = collisions.readServerInfo(() -> h.getServerInfo(true)); // BOREALIS: retry a timed-out /serverinfo (host.launch)
        } catch (IOException | XmlPullParserException e) {
            // BOREALIS: the HTTP fallback after a mismatch can fail too; the mismatch is the cause (host.failure)
            if (h.sawCertificateMismatch()) { throw new CertificateMismatchException(e); }
            throw e;
        }
        if (h.sawCertificateMismatch()) { throw new CertificateMismatchException(null); } // BOREALIS: the fallback to HTTP would only say "not paired" (host.failure)
        
        context.serverAppVersion = h.getServerVersion(serverInfo);
        if (context.serverAppVersion == null) {
            context.connListener.displayMessage("Server version malformed");
            return false;
        }

        ComputerDetails details = h.getComputerDetails(serverInfo);
        context.isNvidiaServerSoftware = details.nvidiaServer;

        // May be missing for older servers
        context.serverGfeVersion = h.getGfeVersion(serverInfo);
                
        if (h.getPairState(serverInfo) != PairingManager.PairState.PAIRED) {
            context.connListener.displayMessage("Device not paired with computer");
            return false;
        }

        context.serverCodecModeSupport = (int)h.getServerCodecModeSupport(serverInfo);

        context.negotiatedHdr = (context.streamConfig.getSupportedVideoFormats() & MoonBridge.VIDEO_FORMAT_MASK_10BIT) != 0;
        if ((context.serverCodecModeSupport & 0x20200) == 0 && context.negotiatedHdr) {
            context.connListener.displayTransientMessage("Your PC GPU does not support streaming HDR. The stream will be SDR.");
            context.negotiatedHdr = false;
        }
        
        //
        // Decide on negotiated stream parameters now
        //
        
        // Check for a supported stream resolution
        if ((context.streamConfig.getWidth() > 4096 || context.streamConfig.getHeight() > 4096) &&
                (h.getServerCodecModeSupport(serverInfo) & 0x200) == 0 && context.isNvidiaServerSoftware) {
            context.connListener.displayMessage("Your host PC does not support streaming at resolutions above 4K.");
            return false;
        }
        else if ((context.streamConfig.getWidth() > 4096 || context.streamConfig.getHeight() > 4096) &&
                (context.streamConfig.getSupportedVideoFormats() & ~MoonBridge.VIDEO_FORMAT_MASK_H264) == 0) {
            context.connListener.displayMessage("Your streaming device must support HEVC or AV1 to stream at resolutions above 4K.");
            return false;
        }
        else if (context.streamConfig.getHeight() >= 2160 && !h.supports4K(serverInfo)) {
            // Client wants 4K but the server can't do it
            context.connListener.displayTransientMessage("You must update GeForce Experience to stream in 4K. The stream will be 1080p.");
            
            // Lower resolution to 1080p
            context.negotiatedWidth = 1920;
            context.negotiatedHeight = 1080;
        }
        else {
            // Take what the client wanted
            context.negotiatedWidth = context.streamConfig.getWidth();
            context.negotiatedHeight = context.streamConfig.getHeight();
        }

        // We will perform some connection type detection if the caller asked for it
        if (context.streamConfig.getRemote() == StreamConfiguration.STREAM_CFG_AUTO) {
            context.negotiatedRemoteStreaming = detectServerConnectionType();
            context.negotiatedPacketSize =
                    context.negotiatedRemoteStreaming == StreamConfiguration.STREAM_CFG_REMOTE ?
                            1024 : context.streamConfig.getMaxPacketSize();
        }
        else {
            context.negotiatedRemoteStreaming = context.streamConfig.getRemote();
            context.negotiatedPacketSize = context.streamConfig.getMaxPacketSize();
        }
        
        //
        // Video stream format will be decided during the RTSP handshake
        //
        
        NvApp app = context.streamConfig.getApp();
        
        // If the client did not provide an exact app ID, do a lookup with the applist
        if (!context.streamConfig.getApp().isInitialized()) {
            LimeLog.info("Using deprecated app lookup method - Please specify an app ID in your StreamConfiguration instead");
            app = h.getAppByName(context.streamConfig.getApp().getAppName());
            if (app == null) {
                context.connListener.displayMessage("The app " + context.streamConfig.getApp().getAppName() + " is not in GFE app list");
                return false;
            }
        }
        
        // If there's a game running, resume it
        if (h.getCurrentGame(serverInfo) != 0) {
            LaunchOutcome resumed = LaunchOutcome.SUCCEEDED; // BOREALIS: kept outside the try so the fallback launch is not handled as a resume error
            try {
                if (h.getCurrentGame(serverInfo) == app.getAppId()) {
                    resumed = collisions.settleBeforeResume() ? launchWithRetry(h, "resume", app.getAppId()) : LaunchOutcome.CANCELLED; // BOREALIS: wait while the host is busy; retries, cancellation and the no-app fallback
                    if (resumed == LaunchOutcome.CANCELLED) { return false; } // BOREALIS
                    if (resumed == LaunchOutcome.FAILED) { // BOREALIS: was !h.launchApp(context, "resume", ...)
                        context.connListener.displayMessage("Failed to resume existing session");
                        return false;
                    }
                } else {
                    return collisions.differentAppRunning(() -> resolveConflict(h, context)); // BOREALIS: ask before quitting another device's app; a restart never quits it
                }
            } catch (HostHttpResponseException e) {
                if (e.getErrorCode() == 470) {
                    // This is the error you get when you try to resume a session that's not yours.
                    // Because this is fairly common, we'll display a more detailed message.
                    context.connListener.displayMessage("This session wasn't started by this device," +
                            " so it cannot be resumed. End streaming on the original " +
                            "device or the PC itself and try again. (Error code: "+e.getErrorCode()+")");
                    return false;
                }
                else if (e.getErrorCode() == 525) {
                    context.connListener.displayMessage("The application is minimized. Resume it on the PC manually or " +
                            "quit the session and start streaming again.");
                    return false;
                } else {
                    throw e;
                }
            }
            if (resumed == LaunchOutcome.FALLBACK_TO_LAUNCH) { return launchNotRunningApp(h, context); } // BOREALIS: nothing to resume, launch instead

            collisions.resumeAccepted(context.isNvidiaServerSoftware); // BOREALIS
            LimeLog.info("Resumed existing game session");
            return true;
        }
        else {
            return launchNotRunningApp(h, context);
        }
    }

    protected boolean quitAndLaunch(NvHTTP h, ConnectionContext context) throws IOException,
            XmlPullParserException {
        startStep = StartSteps.QUIT; // BOREALIS: host.failure
        try {
            if (!h.quitApp()) {
                context.connListener.displayMessage("Failed to quit previous session! You must quit it manually");
                return false;
            } 
        } catch (HostHttpResponseException e) {
            if (e.getErrorCode() == 599) {
                context.connListener.displayMessage("This session wasn't started by this device," +
                        " so it cannot be quit. End streaming on the original " +
                        "device or the PC itself. (Error code: "+e.getErrorCode()+")");
                return false;
            }
            else {
                throw e;
            }
        }

        return launchNotRunningApp(h, context);
    }
    
    private boolean launchNotRunningApp(NvHTTP h, ConnectionContext context)
            throws IOException, XmlPullParserException {
        // Launch the app since it's not running
        LaunchOutcome launched = launchWithRetry(h, "launch", context.streamConfig.getApp().getAppId()); // BOREALIS: retries and cancellation
        if (launched == LaunchOutcome.CANCELLED) { return false; } // BOREALIS
        if (launched == LaunchOutcome.CONFLICT) { return resolveConflict(h, context); } // BOREALIS: ask the user once
        if (launched != LaunchOutcome.SUCCEEDED) { // BOREALIS: was !h.launchApp(context, "launch", ...)
            context.connListener.displayMessage("Failed to launch application");
            return false;
        }
        
        LimeLog.info("Launched new game session");
        
        return true;
    }

    public void start(final AudioRenderer audioRenderer, final VideoDecoderRenderer videoDecoderRenderer, final NvConnectionListener connectionListener)
    {
        new Thread(new Runnable() {
            public void run() {
                context.connListener = connectionListener;
                context.videoCapabilities = videoDecoderRenderer.getCapabilities();

                String appName = context.streamConfig.getApp().getAppName();

                lastFailureCause = CauseKind.NONE; // BOREALIS: host.failure
                lastHttpCode = 0; // BOREALIS
                launchRetried = false; // BOREALIS
                startStep = StartSteps.SERVER_INFO; // BOREALIS

                context.connListener.stageStarting(appName);

                try {
                    if (!startApp()) {
                        if (launchCancelled) { return; } // BOREALIS: the user left, nothing to report
                        context.connListener.stageFailed(startStep, 0, 0); // BOREALIS: the step, not the app name
                        return;
                    }
                    context.connListener.stageComplete(appName);
                } catch (HostHttpResponseException e) {
                    StartFailureLog.failed(startStep, e); // BOREALIS: the log masks addresses; the screen never shows the exception text
                    recordStartFailure(e); // BOREALIS: host.failure
                    context.connListener.stageFailed(startStep, 0, e.getErrorCode()); // BOREALIS: the step, not the app name
                    return;
                } catch (XmlPullParserException | IOException e) {
                    if (launchCancelled) { return; } // BOREALIS: the user left during the /serverinfo retry, nothing to report
                    StartFailureLog.failed(startStep, e); // BOREALIS: the log masks addresses; the screen never shows the exception text
                    recordStartFailure(e); // BOREALIS: host.failure
                    context.connListener.stageFailed(startStep, MoonBridge.ML_PORT_FLAG_TCP_47984 | MoonBridge.ML_PORT_FLAG_TCP_47989, 0); // BOREALIS: the step, not the app name
                    return;
                }

                if (launchCancelled) { return; } // BOREALIS: before the semaphore, which only stop() releases

                ByteBuffer ib = ByteBuffer.allocate(16);
                ib.putInt(context.riKeyId);

                // Acquire the connection semaphore to ensure we only have one
                // connection going at once.
                try {
                    connectionAllowed.acquire();
                } catch (InterruptedException e) {
                    StartFailureLog.failed(startStep, e); // BOREALIS: the exception text goes to the log only
                    context.connListener.stageFailed(startStep, 0, 0); // BOREALIS: the step, not the app name
                    return;
                }
                if (launchCancelled) { connectionAllowed.release(); return; } // BOREALIS: left while waiting for the semaphore

                // Moonlight-core is not thread-safe with respect to connection start and stop, so
                // we must not invoke that functionality in parallel.
                synchronized (MoonBridge.class) {
                    MoonBridge.setupBridge(videoDecoderRenderer, audioRenderer, new io.github.magalz.borealis.HeldStageFailures(connectionListener, collisions)); // BOREALIS: hold an RTSP failure that collides with another device
                    int ret = MoonBridge.startConnection(context.serverAddress.address,
                            context.serverAppVersion, context.serverGfeVersion, context.rtspSessionUrl,
                            context.serverCodecModeSupport,
                            context.negotiatedWidth, context.negotiatedHeight,
                            context.streamConfig.getRefreshRate(), context.streamConfig.getBitrate(),
                            context.negotiatedPacketSize, context.negotiatedRemoteStreaming,
                            context.streamConfig.getAudioConfiguration().toInt(),
                            context.streamConfig.getSupportedVideoFormats(),
                            context.streamConfig.getClientRefreshRateX100(),
                            context.riKey.getEncoded(), ib.array(),
                            context.videoCapabilities,
                            context.streamConfig.getColorSpace(),
                            context.streamConfig.getColorRange());
                    connected = ret == 0; // BOREALIS: stream.keyframe
                    if (ret != 0) {
                        // LiStartConnection() failed, so the caller is not expected
                        // to stop the connection themselves. We need to release their
                        // semaphore count for them.
                        connectionAllowed.release();
                        if (collisions.restartAfterFailure()) { start(audioRenderer, videoDecoderRenderer, connectionListener); } // BOREALIS: restart the whole start once
                        return;
                    }
                }
            }
        }).start();
    }
    
    public void sendMouseMove(final short deltaX, final short deltaY)
    {
        if (!isMonkey) {
            MoonBridge.sendMouseMove(deltaX, deltaY);
        }
    }

    public void sendMousePosition(short x, short y, short referenceWidth, short referenceHeight)
    {
        if (!isMonkey) {
            MoonBridge.sendMousePosition(x, y, referenceWidth, referenceHeight);
        }
    }

    public void sendMouseMoveAsMousePosition(short deltaX, short deltaY, short referenceWidth, short referenceHeight)
    {
        if (!isMonkey) {
            MoonBridge.sendMouseMoveAsMousePosition(deltaX, deltaY, referenceWidth, referenceHeight);
        }
    }

    public void sendMouseButtonDown(final byte mouseButton)
    {
        if (!isMonkey) {
            MoonBridge.sendMouseButton(MouseButtonPacket.PRESS_EVENT, mouseButton);
        }
    }
    
    public void sendMouseButtonUp(final byte mouseButton)
    {
        if (!isMonkey) {
            MoonBridge.sendMouseButton(MouseButtonPacket.RELEASE_EVENT, mouseButton);
        }
    }
    
    public void sendControllerInput(final short controllerNumber,
            final short activeGamepadMask, final int buttonFlags,
            final byte leftTrigger, final byte rightTrigger,
            final short leftStickX, final short leftStickY,
            final short rightStickX, final short rightStickY)
    {
        if (!isMonkey) {
            MoonBridge.sendMultiControllerInput(controllerNumber, activeGamepadMask, buttonFlags,
                    leftTrigger, rightTrigger, leftStickX, leftStickY, rightStickX, rightStickY);
        }
    }

    public void sendKeyboardInput(final short keyMap, final byte keyDirection, final byte modifier, final byte flags) {
        if (!isMonkey) {
            MoonBridge.sendKeyboardInput(keyMap, keyDirection, modifier, flags);
        }
    }
    
    public void sendMouseScroll(final byte scrollClicks) {
        if (!isMonkey) {
            MoonBridge.sendMouseHighResScroll((short)(scrollClicks * 120)); // WHEEL_DELTA
        }
    }

    public void sendMouseHScroll(final byte scrollClicks) {
        if (!isMonkey) {
            MoonBridge.sendMouseHighResHScroll((short)(scrollClicks * 120)); // WHEEL_DELTA
        }
    }

    public void sendMouseHighResScroll(final short scrollAmount) {
        if (!isMonkey) {
            MoonBridge.sendMouseHighResScroll(scrollAmount);
        }
    }

    public void sendMouseHighResHScroll(final short scrollAmount) {
        if (!isMonkey) {
            MoonBridge.sendMouseHighResHScroll(scrollAmount);
        }
    }

    public int sendTouchEvent(byte eventType, int pointerId, float x, float y, float pressureOrDistance,
                              float contactAreaMajor, float contactAreaMinor, short rotation) {
        if (!isMonkey) {
            return MoonBridge.sendTouchEvent(eventType, pointerId, x, y, pressureOrDistance,
                    contactAreaMajor, contactAreaMinor, rotation);
        }
        else {
            return MoonBridge.LI_ERR_UNSUPPORTED;
        }
    }

    public int sendPenEvent(byte eventType, byte toolType, byte penButtons, float x, float y,
                            float pressureOrDistance, float contactAreaMajor, float contactAreaMinor,
                            short rotation, byte tilt) {
        if (!isMonkey) {
            return MoonBridge.sendPenEvent(eventType, toolType, penButtons, x, y, pressureOrDistance,
                    contactAreaMajor, contactAreaMinor, rotation, tilt);
        }
        else {
            return MoonBridge.LI_ERR_UNSUPPORTED;
        }
    }

    public int sendControllerArrivalEvent(byte controllerNumber, short activeGamepadMask, byte type,
                                          int supportedButtonFlags, short capabilities) {
        return MoonBridge.sendControllerArrivalEvent(controllerNumber, activeGamepadMask, type, supportedButtonFlags, capabilities);
    }

    public int sendControllerTouchEvent(byte controllerNumber, byte eventType, int pointerId,
                                        float x, float y, float pressure) {
        if (!isMonkey) {
            return MoonBridge.sendControllerTouchEvent(controllerNumber, eventType, pointerId, x, y, pressure);
        }
        else {
            return MoonBridge.LI_ERR_UNSUPPORTED;
        }
    }

    public int sendControllerMotionEvent(byte controllerNumber, byte motionType,
                                         float x, float y, float z) {
        if (!isMonkey) {
            return MoonBridge.sendControllerMotionEvent(controllerNumber, motionType, x, y, z);
        }
        else {
            return MoonBridge.LI_ERR_UNSUPPORTED;
        }
    }

    public void sendControllerBatteryEvent(byte controllerNumber, byte batteryState, byte batteryPercentage) {
        MoonBridge.sendControllerBatteryEvent(controllerNumber, batteryState, batteryPercentage);
    }

    public void sendUtf8Text(final String text) {
        if (!isMonkey) {
            MoonBridge.sendUtf8Text(Utf8Text.bytes(text)); // BOREALIS: JNI needs standard UTF-8, not Java's modified UTF-8
        }
    }

    public static String findExternalAddressForMdns(String stunHostname, int stunPort) {
        return MoonBridge.findExternalAddressIP4(stunHostname, stunPort);
    }
}

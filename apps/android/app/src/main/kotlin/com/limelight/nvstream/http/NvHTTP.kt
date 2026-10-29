package com.limelight.nvstream.http

import com.limelight.BuildConfig
import com.limelight.LimeLog
import com.limelight.nvstream.ConnectionContext
import com.limelight.nvstream.http.ComputerDetails.AddressTuple
import com.limelight.nvstream.http.PairingManager.PairState
import com.limelight.nvstream.jni.MoonBridge
import io.github.magalz.borealis.host.caps.HostInfoReader
import io.github.magalz.borealis.host.launch.LaunchFrameRate
import io.github.magalz.borealis.host.pairing.BestEffortUnpair
import io.github.magalz.borealis.host.pairing.CertificateMismatchException
import io.github.magalz.borealis.host.pairing.TlsAudit
import io.github.magalz.borealis.logging.BorealisLog
import io.github.magalz.borealis.logging.LogTag
import java.io.FileNotFoundException
import java.io.IOException
import java.io.InputStream
import java.io.InputStreamReader
import java.io.Reader
import java.io.StringReader
import java.net.Inet4Address
import java.net.InetAddress
import java.net.Proxy
import java.net.Socket
import java.security.GeneralSecurityException
import java.security.KeyStore
import java.security.Principal
import java.security.PrivateKey
import java.security.SecureRandom
import java.security.cert.CertificateException
import java.security.cert.X509Certificate
import java.util.LinkedList
import java.util.Stack
import java.util.UUID
import java.util.concurrent.TimeUnit
import javax.net.ssl.HostnameVerifier
import javax.net.ssl.HttpsURLConnection
import javax.net.ssl.KeyManager
import javax.net.ssl.SSLContext
import javax.net.ssl.SSLHandshakeException
import javax.net.ssl.SSLPeerUnverifiedException
import javax.net.ssl.TrustManager
import javax.net.ssl.TrustManagerFactory
import javax.net.ssl.X509KeyManager
import javax.net.ssl.X509TrustManager
import okhttp3.ConnectionPool
import okhttp3.HttpUrl
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.ResponseBody
import org.xmlpull.v1.XmlPullParser
import org.xmlpull.v1.XmlPullParserException
import org.xmlpull.v1.XmlPullParserFactory

// Converted as is from the inherited Java; refactor in a later sprint (Sprint 12 backlog).
// The stack traces that the Java prints to stderr and the RuntimeException that wraps their cause
// stay as they were: PrintStackTrace and TooGenericExceptionThrown are suppressed for that.
@Suppress("TooManyFunctions", "PrintStackTrace", "TooGenericExceptionThrown")
class NvHTTP
@Throws(IOException::class)
constructor(
    address: AddressTuple,
    httpsPort: Int,
    // The id that the caller passes is not used: every client sends the same one.
    @Suppress("UNUSED_PARAMETER", "UnusedPrivateProperty") uniqueId: String?,
    serverCert: X509Certificate?,
    cryptoProvider: LimelightCryptoProvider,
) {
    // Use the same UID for all Moonlight clients so we can quit games
    // started by other Moonlight clients.
    private val uniqueId = "0123456789ABCDEF"

    val pairingManager: PairingManager

    private var baseUrlHttp: HttpUrl

    private var httpsPort: Int

    private lateinit var httpClientLongConnectTimeout: OkHttpClient
    private lateinit var httpClientLongConnectNoReadTimeout: OkHttpClient
    private lateinit var httpClientShortConnectTimeout: OkHttpClient

    private lateinit var defaultTrustManager: X509TrustManager
    private lateinit var trustManager: X509TrustManager
    private lateinit var keyManager: X509KeyManager
    private var serverCert: X509Certificate?

    // BOREALIS: set when the latest HTTPS request met another certificate than the pinned one; the
    // HTTP fallback after it keeps the flag (host.failure)
    @Volatile private var certificateMismatch = false

    // BOREALIS: whether this client saw the host present another certificate than the pinned one
    // (host.failure)
    fun sawCertificateMismatch(): Boolean = certificateMismatch

    internal fun setServerCert(serverCert: X509Certificate?) {
        this.serverCert = serverCert
    }

    private fun initializeHttpState(cryptoProvider: LimelightCryptoProvider) {
        keyManager =
            object : X509KeyManager {
                override fun chooseClientAlias(
                    keyTypes: Array<String>?,
                    issuers: Array<Principal>?,
                    socket: Socket?,
                ): String = "Limelight-RSA"

                override fun chooseServerAlias(
                    keyType: String?,
                    issuers: Array<Principal>?,
                    socket: Socket?,
                ): String? = null

                override fun getCertificateChain(alias: String?): Array<X509Certificate?> =
                    arrayOf(cryptoProvider.clientCertificate)

                override fun getClientAliases(
                    keyType: String?,
                    issuers: Array<Principal>?,
                ): Array<String>? = null

                override fun getPrivateKey(alias: String?): PrivateKey? =
                    cryptoProvider.clientPrivateKey

                override fun getServerAliases(
                    keyType: String?,
                    issuers: Array<Principal>?,
                ): Array<String>? = null
            }

        defaultTrustManager = getDefaultTrustManager()
        trustManager =
            object : X509TrustManager {
                override fun getAcceptedIssuers(): Array<X509Certificate> = arrayOf()

                override fun checkClientTrusted(certs: Array<X509Certificate>, authType: String) {
                    error("Should never be called")
                }

                @Throws(CertificateException::class)
                override fun checkServerTrusted(certs: Array<X509Certificate>, authType: String) {
                    try {
                        // Try the default trust manager first to allow pairing with certificates
                        // that chain up to a trusted root CA. This will raise CertificateException
                        // if the certificate is not trusted (expected for GFE's self-signed certs).
                        defaultTrustManager.checkServerTrusted(certs, authType)
                    } catch (e: CertificateException) {
                        // Check the server certificate if we've paired to this host
                        val pinned = this@NvHTTP.serverCert
                        if (certs.size == 1 && pinned != null) {
                            if (certs[0] != pinned) {
                                // BOREALIS: audit trail
                                TlsAudit.certificateChanged(
                                    this@NvHTTP.baseUrlHttp.host,
                                    certs[0],
                                    pinned,
                                )
                                this@NvHTTP.certificateMismatch = true // BOREALIS
                                throw CertificateException("Certificate mismatch")
                            }
                        } else {
                            // The cert chain doesn't look like a self-signed cert or we don't have
                            // a certificate pinned, so re-throw the original validation error.
                            // BOREALIS: audit trail
                            TlsAudit.failure(this@NvHTTP.baseUrlHttp.host, e)
                            throw e
                        }
                    }
                }
            }

        val hv = HostnameVerifier { hostname, session ->
            try {
                val certificates = session.peerCertificates
                if (certificates.size == 1 && certificates[0] == this@NvHTTP.serverCert) {
                    // Allow any hostname if it's our pinned cert
                    return@HostnameVerifier true
                }
            } catch (e: SSLPeerUnverifiedException) {
                e.printStackTrace()
            }

            // Fall back to default HostnameVerifier for validating CA-issued certs
            HttpsURLConnection.getDefaultHostnameVerifier().verify(hostname, session)
        }

        httpClientLongConnectTimeout =
            OkHttpClient.Builder()
                .connectionPool(ConnectionPool(0, 1, TimeUnit.MILLISECONDS))
                .hostnameVerifier(hv)
                .readTimeout(READ_TIMEOUT.toLong(), TimeUnit.MILLISECONDS)
                .connectTimeout(LONG_CONNECTION_TIMEOUT.toLong(), TimeUnit.MILLISECONDS)
                .proxy(Proxy.NO_PROXY)
                .fastFallback(false)
                .build()

        httpClientShortConnectTimeout =
            httpClientLongConnectTimeout
                .newBuilder()
                .connectTimeout(SHORT_CONNECTION_TIMEOUT.toLong(), TimeUnit.MILLISECONDS)
                .build()

        httpClientLongConnectNoReadTimeout =
            httpClientLongConnectTimeout.newBuilder().readTimeout(0, TimeUnit.MILLISECONDS).build()
    }

    @Throws(IOException::class)
    fun getHttpsUrl(likelyOnline: Boolean): HttpUrl {
        if (httpsPort == 0) {
            // Fetch the HTTPS port if we don't have it already
            httpsPort =
                getHttpsPort(
                    openHttpConnectionToString(
                        if (likelyOnline) httpClientLongConnectTimeout
                        else httpClientShortConnectTimeout,
                        baseUrlHttp,
                        "serverinfo",
                    )
                )
        }

        return HttpUrl.Builder().scheme("https").host(baseUrlHttp.host).port(httpsPort).build()
    }

    init {
        this.serverCert = serverCert

        initializeHttpState(cryptoProvider)

        this.httpsPort = httpsPort

        try {
            // If this is an IPv4-mapped IPv6 address, OkHTTP will choke on it if it's
            // in IPv6 form, because InetAddress.getByName() will return an Inet4Address
            // for what OkHTTP thinks is an IPv6 address. Normalize it into IPv4 form
            // to avoid triggering this bug.
            var addressString = address.address
            if (addressString.contains(":") && addressString.contains(".")) {
                val addr = InetAddress.getByName(addressString)
                if (addr is Inet4Address) {
                    addressString = checkNotNull(addr.hostAddress)
                }
            }

            this.baseUrlHttp =
                HttpUrl.Builder().scheme("http").host(addressString).port(address.port).build()
        } catch (e: IllegalArgumentException) {
            // Encapsulate IllegalArgumentException into IOException for callers to handle more
            // easily
            throw IOException(e)
        }

        this.pairingManager = PairingManager(this, cryptoProvider)
    }

    // Converted as is from the inherited Java; refactor in a later sprint (Sprint 12 backlog).
    @Suppress("ThrowsCount")
    @Throws(IOException::class, XmlPullParserException::class)
    fun getServerInfo(likelyOnline: Boolean): String {
        // If we believe the PC is online, give it a little extra time to respond
        val client =
            if (likelyOnline) httpClientLongConnectTimeout else httpClientShortConnectTimeout

        //
        // TODO Shield Hub uses HTTP for this and is able to get an accurate PairStatus with HTTP.
        // For some reason, we always see PairStatus is 0 over HTTP and only 1 over HTTPS. It looks
        // like there are extra request headers required to make this stuff work over HTTP.
        //

        // When we have a pinned cert, use HTTPS to fetch serverinfo and fall back on cert mismatch
        if (serverCert != null) {
            try {
                val resp =
                    try {
                        openHttpConnectionToString(client, getHttpsUrl(likelyOnline), "serverinfo")
                    } catch (e: SSLHandshakeException) {
                        // Detect if we failed due to a server cert mismatch
                        if (e.cause is CertificateException) {
                            // Jump to the GfeHttpResponseException exception handler to retry
                            // over HTTP which will allow us to pair again to update the cert
                            throw HostHttpResponseException(
                                HTTP_UNAUTHORIZED,
                                "Server certificate mismatch",
                            )
                        } else {
                            // BOREALIS: audit trail
                            TlsAudit.failure(baseUrlHttp.host, e)
                            throw e
                        }
                    } catch (ignored: CertificateMismatchException) {
                        // BOREALIS: the mismatch is typed now, the fallback is the same
                        throw HostHttpResponseException(
                            HTTP_UNAUTHORIZED,
                            "Server certificate mismatch",
                        )
                    }

                // This will throw an exception if the request came back with a failure status.
                // We want this because it will throw us into the HTTP case if the client is
                // unpaired.
                getServerVersion(resp)

                return resp
            } catch (e: HostHttpResponseException) {
                if (e.errorCode == HTTP_UNAUTHORIZED) {
                    // Cert validation error - fall back to HTTP
                    return openHttpConnectionToString(client, baseUrlHttp, "serverinfo")
                }

                // If it's not a cert validation error, throw it
                throw e
            }
        } else {
            // No pinned cert, so use HTTP
            return openHttpConnectionToString(client, baseUrlHttp, "serverinfo")
        }
    }

    @Throws(IOException::class, XmlPullParserException::class)
    fun getComputerDetails(serverInfo: String): ComputerDetails {
        val details = ComputerDetails()

        details.name = getXmlString(serverInfo, "hostname", false)
        if (details.name.isNullOrEmpty()) {
            details.name = "UNKNOWN"
        }

        // UUID is mandatory to determine which machine is responding
        details.uuid = getXmlString(serverInfo, "uniqueid", true)

        details.httpsPort = getHttpsPort(serverInfo)

        details.macAddress = getXmlString(serverInfo, "mac", false)

        // FIXME Do we want to use the current port?
        details.localAddress =
            makeTuple(getXmlString(serverInfo, "LocalIP", false), baseUrlHttp.port)

        // This is missing on on recent GFE versions, but it's present on Sunshine
        details.externalPort = getExternalPort(serverInfo)
        details.remoteAddress =
            makeTuple(getXmlString(serverInfo, "ExternalIP", false), details.externalPort)

        details.pairState = getPairState(serverInfo)
        details.runningGameId = getCurrentGame(serverInfo)

        // The MJOLNIR codename was used by GFE but never by any third-party server
        details.nvidiaServer = requireXmlString(serverInfo, "state").contains("MJOLNIR")

        // BOREALIS: host family, codecs and ports for the details dialog (host.caps)
        details.borealis =
            HostInfoReader.SHARED.read(
                details.uuid,
                getXmlString(serverInfo, "appversion", false),
                getXmlString(serverInfo, "ServerCodecModeSupport", false),
                details.httpsPort,
                baseUrlHttp.port,
            )

        // We could reach it so it's online
        details.state = ComputerDetails.State.ONLINE

        return details
    }

    @Throws(IOException::class, XmlPullParserException::class)
    fun getComputerDetails(likelyOnline: Boolean): ComputerDetails =
        getComputerDetails(getServerInfo(likelyOnline))

    // This hack is Android-specific but we do it on all platforms
    // because it doesn't really matter
    private fun performAndroidTlsHack(client: OkHttpClient): OkHttpClient {
        // Doing this each time we create a socket is required
        // to avoid the SSLv3 fallback that causes connection failures
        try {
            val sc = SSLContext.getInstance("TLS")
            sc.init(
                arrayOf<KeyManager>(keyManager),
                arrayOf<TrustManager>(trustManager),
                SecureRandom(),
            )
            return client.newBuilder().sslSocketFactory(sc.socketFactory, trustManager).build()
        } catch (e: GeneralSecurityException) {
            throw RuntimeException(e)
        }
    }

    private fun getCompleteUrl(baseUrl: HttpUrl, path: String, query: String?): HttpUrl =
        baseUrl
            .newBuilder()
            .addPathSegment(path)
            .query(query)
            .addQueryParameter("uniqueid", uniqueId)
            .addQueryParameter("uuid", UUID.randomUUID().toString())
            .build()

    // Read timeout should be enabled for any HTTP query that requires no outside action
    // on the GFE server. Examples of queries that DO require outside action are launch, resume, and
    // quit.
    // The initial pair query does require outside action (user entering a PIN) but subsequent
    // pairing
    // queries do not.
    // Converted as is from the inherited Java; refactor in a later sprint (Sprint 12 backlog).
    @Suppress("ThrowsCount")
    @Throws(IOException::class)
    private fun openHttpConnection(
        client: OkHttpClient,
        baseUrl: HttpUrl,
        path: String,
        query: String? = null,
    ): ResponseBody {
        val completeUrl = getCompleteUrl(baseUrl, path, query)
        val request = Request.Builder().url(completeUrl).get().build()
        // BOREALIS: the flag tells about the latest HTTPS request only (host.failure)
        if (baseUrl.isHttps) {
            certificateMismatch = false
        }
        val response =
            try {
                performAndroidTlsHack(client).newCall(request).execute()
            } catch (e: SSLHandshakeException) {
                // BOREALIS: surface a pinned-certificate mismatch as its own type (host.failure)
                if (certificateMismatch && e.cause is CertificateException) {
                    throw CertificateMismatchException(e)
                }
                throw e
            }

        val body = response.body

        if (response.isSuccessful) {
            return body
        }

        // Unsuccessful, so close the response body
        body.close()

        if (response.code == HTTP_NOT_FOUND) {
            throw FileNotFoundException(completeUrl.toString())
        } else {
            throw HostHttpResponseException(response.code, response.message)
        }
    }

    @Throws(IOException::class)
    private fun openHttpConnectionToString(
        client: OkHttpClient,
        baseUrl: HttpUrl,
        path: String,
        query: String? = null,
    ): String {
        try {
            val resp = openHttpConnection(client, baseUrl, path, query)
            val respString = resp.string()
            resp.close()

            if (verbose && path != "serverinfo") {
                LimeLog.info(getCompleteUrl(baseUrl, path, query).toString() + " -> " + respString)
            }

            return respString
        } catch (e: IOException) {
            if (verbose && path != "serverinfo") {
                LimeLog.warning(
                    getCompleteUrl(baseUrl, path, query).toString() + " -> " + e.message
                )
                e.printStackTrace()
            }

            throw e
        }
    }

    @Throws(XmlPullParserException::class, IOException::class)
    fun getServerVersion(serverInfo: String): String? {
        // appversion is present in all supported GFE versions
        return getXmlString(serverInfo, "appversion", true)
    }

    @Throws(IOException::class, XmlPullParserException::class)
    fun getPairState(): PairState = getPairState(getServerInfo(true))

    @Throws(IOException::class, XmlPullParserException::class)
    fun getPairState(serverInfo: String): PairState {
        // appversion is present in all supported GFE versions
        return if (requireXmlString(serverInfo, "PairStatus") == "1") {
            PairState.PAIRED
        } else {
            PairState.NOT_PAIRED
        }
    }

    @Throws(XmlPullParserException::class, IOException::class)
    fun getMaxLumaPixelsH264(serverInfo: String): Long {
        // MaxLumaPixelsH264 wasn't present on old GFE versions
        val str = getXmlString(serverInfo, "MaxLumaPixelsH264", false)
        return str?.toLong() ?: 0
    }

    @Throws(XmlPullParserException::class, IOException::class)
    fun getMaxLumaPixelsHEVC(serverInfo: String): Long {
        // MaxLumaPixelsHEVC wasn't present on old GFE versions
        val str = getXmlString(serverInfo, "MaxLumaPixelsHEVC", false)
        return str?.toLong() ?: 0
    }

    // Possible meaning of bits
    // Bit 0: H.264 Baseline
    // Bit 1: H.264 High
    // ----
    // Bit 8: HEVC Main
    // Bit 9: HEVC Main10
    // Bit 10: HEVC Main10 4:4:4
    // Bit 11: ???
    @Throws(XmlPullParserException::class, IOException::class)
    fun getServerCodecModeSupport(serverInfo: String): Long {
        // ServerCodecModeSupport wasn't present on old GFE versions
        val str = getXmlString(serverInfo, "ServerCodecModeSupport", false)
        return str?.toLong() ?: 0
    }

    @Throws(XmlPullParserException::class, IOException::class)
    fun getGpuType(serverInfo: String): String? {
        // ServerCodecModeSupport wasn't present on old GFE versions
        return getXmlString(serverInfo, "gputype", false)
    }

    @Throws(XmlPullParserException::class, IOException::class)
    fun getGfeVersion(serverInfo: String): String? {
        // ServerCodecModeSupport wasn't present on old GFE versions
        return getXmlString(serverInfo, "GfeVersion", false)
    }

    @Throws(XmlPullParserException::class, IOException::class)
    fun supports4K(serverInfo: String): Boolean {
        // Only allow 4K on GFE 3.x. GfeVersion wasn't present on very old versions of GFE.
        val gfeVersionStr = getXmlString(serverInfo, "GfeVersion", false)
        if (gfeVersionStr == null || gfeVersionStr.startsWith("2.")) {
            return false
        }

        return true
    }

    @Throws(IOException::class, XmlPullParserException::class)
    fun getCurrentGame(serverInfo: String): Int {
        // GFE 2.8 started keeping currentgame set to the last game played. As a result, it no
        // longer
        // has the semantics that its name would indicate. To contain the effects of this change as
        // much
        // as possible, we'll force the current game to zero if the server isn't in a streaming
        // session.
        return if (requireXmlString(serverInfo, "state").endsWith("_SERVER_BUSY")) {
            requireXmlString(serverInfo, "currentgame").toInt()
        } else {
            0
        }
    }

    fun getHttpsPort(serverInfo: String): Int =
        try {
            requireXmlString(serverInfo, "HttpsPort").toInt()
        } catch (e: XmlPullParserException) {
            e.printStackTrace()
            DEFAULT_HTTPS_PORT
        } catch (e: IOException) {
            e.printStackTrace()
            DEFAULT_HTTPS_PORT
        }

    fun getExternalPort(serverInfo: String): Int =
        // This is an extension which is not present in GFE. It is present for Sunshine to be able
        // to support dynamic HTTP WAN ports without requiring the user to manually enter the port.
        try {
            requireXmlString(serverInfo, "ExternalPort").toInt()
        } catch (expected: XmlPullParserException) {
            // Expected on non-Sunshine servers
            baseUrlHttp.port
        } catch (e: IOException) {
            e.printStackTrace()
            baseUrlHttp.port
        }

    /**
     * Get an app by ID
     *
     * @param appId The ID of the app
     * @return app details, or null if no app with that ID exists
     * @see getAppByName for alternative.
     */
    @Throws(IOException::class, XmlPullParserException::class)
    fun getAppById(appId: Int): NvApp? {
        val appList = getAppList()
        for (appFromList in appList) {
            if (appFromList.appId == appId) {
                return appFromList
            }
        }
        return null
    }

    /**
     * Get an app by name NOTE: It is perfectly valid for multiple apps to have the same name, this
     * function will only return the first one it finds. Consider using getAppById instead.
     *
     * @param appName The name of the app
     * @return app details, or null if no app with that name exists
     * @see getAppById for alternative.
     */
    @Throws(IOException::class, XmlPullParserException::class)
    fun getAppByName(appName: String?): NvApp? {
        val appList = getAppList()
        for (appFromList in appList) {
            if (checkNotNull(appFromList.appName).equals(appName, ignoreCase = true)) {
                return appFromList
            }
        }
        return null
    }

    @Throws(IOException::class)
    fun getAppListRaw(): String =
        openHttpConnectionToString(httpClientLongConnectTimeout, getHttpsUrl(true), "applist")

    @Throws(HostHttpResponseException::class, IOException::class, XmlPullParserException::class)
    fun getAppList(): LinkedList<NvApp> {
        if (verbose) {
            // Use the raw function so the app list is printed
            return getAppListByReader(StringReader(getAppListRaw()))
        } else {
            openHttpConnection(httpClientLongConnectTimeout, getHttpsUrl(true), "applist").use {
                resp ->
                return getAppListByReader(InputStreamReader(resp.byteStream()))
            }
        }
    }

    @Throws(HostHttpResponseException::class, IOException::class)
    internal fun executePairingCommand(
        additionalArguments: String,
        enableReadTimeout: Boolean,
    ): String =
        openHttpConnectionToString(
            if (enableReadTimeout) httpClientLongConnectTimeout
            else httpClientLongConnectNoReadTimeout,
            baseUrlHttp,
            "pair",
            "devicename=roth&updateState=1&$additionalArguments",
        )

    @Throws(HostHttpResponseException::class, IOException::class)
    internal fun executePairingChallenge(): String =
        openHttpConnectionToString(
            httpClientLongConnectTimeout,
            getHttpsUrl(true),
            "pair",
            "devicename=roth&updateState=1&phrase=pairchallenge",
        )

    @Throws(IOException::class)
    fun unpair(): Boolean {
        // BOREALIS: LuminalShine does not route /unpair; send it once, without connection reuse.
        return BestEffortUnpair.unpair(
            httpClientLongConnectTimeout,
            getCompleteUrl(baseUrlHttp, "unpair", null),
        )
    }

    // BOREALIS: aborts the requests in flight (the three clients share one dispatcher), so a
    // cancelled pairing does not wait for the PIN.
    fun cancelPendingRequests() {
        httpClientLongConnectTimeout.dispatcher.cancelAll()
    }

    @Throws(IOException::class)
    fun getBoxArt(app: NvApp): InputStream {
        val resp =
            openHttpConnection(
                httpClientLongConnectTimeout,
                getHttpsUrl(true),
                "appasset",
                "appid=" + app.appId + "&AssetType=2&AssetIdx=0",
            )
        return resp.byteStream()
    }

    @Throws(XmlPullParserException::class, IOException::class)
    fun getServerMajorVersion(serverInfo: String): Int = getServerAppVersionQuad(serverInfo)[0]

    @Throws(XmlPullParserException::class, IOException::class)
    fun getServerAppVersionQuad(serverInfo: String): IntArray {
        val serverVersion = getServerVersion(serverInfo)
        requireNotNull(serverVersion) { "Missing server version field" }
        // Java's String.split drops the empty strings at the end; Kotlin's keeps them.
        val serverVersionSplit = serverVersion.split(".").dropLastWhile { it.isEmpty() }
        require(serverVersionSplit.size == SERVER_VERSION_PARTS) {
            "Malformed server version field: $serverVersion"
        }
        val ret = IntArray(serverVersionSplit.size)
        for (i in ret.indices) {
            ret[i] = serverVersionSplit[i].toInt()
        }
        return ret
    }

    // Converted as is from the inherited Java; refactor in a later sprint (Sprint 12 backlog).
    @Suppress("LongMethod", "ComplexCondition")
    @Throws(IOException::class, XmlPullParserException::class)
    fun launchApp(
        context: ConnectionContext,
        verb: String,
        appId: Int,
        enableHdr: Boolean,
    ): Boolean {
        val streamConfig = checkNotNull(context.streamConfig)

        // Using an FPS value over 60 causes SOPS to default to 720p60,
        // so force it to 0 to ensure the correct resolution is set. We
        // used to use 60 here but that locked the frame rate to 60 FPS
        // on GFE 3.20.3.
        // BOREALIS: hosts other than GFE get the stream frame rate, so /launch and RTSP agree (was
        // the upstream expression)
        val fps =
            LaunchFrameRate.forLaunch(
                context.isNvidiaServerSoftware,
                streamConfig.launchRefreshRate,
                streamConfig.refreshRate,
            )

        var enableSops = streamConfig.sops
        if (context.isNvidiaServerSoftware) {
            // Using an unsupported resolution (not 720p, 1080p, or 4K) causes
            // GFE to force SOPS to 720p60. This is fine for < 720p resolutions like
            // 360p or 480p, but it is not ideal for 1440p and other resolutions.
            // When we detect an unsupported resolution, disable SOPS unless it's under 720p.
            // FIXME Detect support resolutions using the serverinfo response, not a hardcoded list
            if (
                context.negotiatedWidth * context.negotiatedHeight > RESOLUTION_720P &&
                    context.negotiatedWidth * context.negotiatedHeight != RESOLUTION_1080P &&
                    context.negotiatedWidth * context.negotiatedHeight != RESOLUTION_4K
            ) {
                LimeLog.info(
                    "Disabling SOPS due to non-standard resolution: " +
                        context.negotiatedWidth +
                        "x" +
                        context.negotiatedHeight
                )
                enableSops = false
            }
        }

        val xmlStr =
            openHttpConnectionToString(
                httpClientLongConnectNoReadTimeout,
                getHttpsUrl(true),
                verb,
                "appid=" +
                    appId +
                    "&mode=" +
                    context.negotiatedWidth +
                    "x" +
                    context.negotiatedHeight +
                    "x" +
                    fps +
                    "&additionalStates=1&sops=" +
                    (if (enableSops) 1 else 0) +
                    "&rikey=" +
                    bytesToHex(checkNotNull(context.riKey).encoded) +
                    "&rikeyid=" +
                    context.riKeyId +
                    (if (!enableHdr) "" else HDR_QUERY) +
                    "&localAudioPlayMode=" +
                    (if (streamConfig.playLocalAudio) 1 else 0) +
                    "&surroundAudioInfo=" +
                    checkNotNull(streamConfig.audioConfiguration).surroundAudioInfo +
                    "&remoteControllersBitmap=" +
                    streamConfig.attachedGamepadMask +
                    "&gcmap=" +
                    streamConfig.attachedGamepadMask +
                    "&gcpersist=" +
                    (if (streamConfig.persistGamepadsAfterDisconnect) 1 else 0) +
                    MoonBridge.getLaunchUrlQueryParameters(),
            )
        if (
            (verb == "launch" && requireXmlString(xmlStr, "gamesession") != "0" ||
                (verb == "resume" && requireXmlString(xmlStr, "resume") != "0"))
        ) {
            // sessionUrl0 will be missing for older GFE versions
            context.rtspSessionUrl = getXmlString(xmlStr, "sessionUrl0", false)
            return true
        } else {
            return false
        }
    }

    @Throws(IOException::class, XmlPullParserException::class)
    fun quitApp(): Boolean {
        // BOREALIS: where the time of a Quit goes (leave)
        BorealisLog.i(LogTag.HOST, "quit cancel sent")
        val cancelSent = System.nanoTime()
        val xmlStr =
            openHttpConnectionToString(
                httpClientLongConnectNoReadTimeout,
                getHttpsUrl(true),
                "cancel",
            )
        BorealisLog.i(LogTag.HOST, "quit cancel answered", "ms" to elapsedMs(cancelSent))
        if (requireXmlString(xmlStr, "cancel") == "0") {
            return false
        }

        // Newer GFE versions will just return success even if quitting fails
        // if we're not the original requestor.
        val checkSent = System.nanoTime()
        val runningGame = getCurrentGame(getServerInfo(true))
        BorealisLog.i(LogTag.HOST, "quit check answered", "ms" to elapsedMs(checkSent))
        if (runningGame != 0) {
            // Generate a synthetic GfeResponseException letting the caller know
            // that they can't kill someone else's stream.
            throw HostHttpResponseException(HTTP_SYNTHETIC_OTHER_CLIENT, "")
        }

        return true
    }

    private fun elapsedMs(sinceNanos: Long): Long =
        TimeUnit.NANOSECONDS.toMillis(System.nanoTime() - sinceNanos)

    companion object {
        private const val DEFAULT_HTTPS_PORT = 47984
        const val DEFAULT_HTTP_PORT = 47989
        const val SHORT_CONNECTION_TIMEOUT = 3000
        const val LONG_CONNECTION_TIMEOUT = 5000
        const val READ_TIMEOUT = 7000

        private const val HTTP_UNAUTHORIZED = 401
        private const val HTTP_NOT_FOUND = 404
        private const val HTTP_SYNTHETIC_OTHER_CLIENT = 599
        private const val STATUS_OK = 200
        private const val STATUS_AUDIO_CAPTURE_ERROR = 418
        private const val SERVER_VERSION_PARTS = 4
        private const val RESOLUTION_720P = 1280 * 720
        private const val RESOLUTION_1080P = 1920 * 1080
        private const val RESOLUTION_4K = 3840 * 2160
        private const val NIBBLE_BITS = 4
        private const val BYTE_MASK = 0xFF
        private const val NIBBLE_MASK = 0x0F

        private const val HDR_QUERY =
            "&hdrMode=1&clientHdrCapVersion=0&clientHdrCapSupportedFlagsInUint32=0" +
                "&clientHdrCapMetaDataId=NV_STATIC_METADATA_TYPE_1" +
                "&clientHdrCapDisplayData=0x0x0x0x0x0x0x0x0x0x0"

        // Print URL and content to logcat on debug builds. Not final, as in the Java: the tests
        // switch it to reach the release path.
        @Suppress("VarCouldBeVal") private var verbose = BuildConfig.DEBUG

        private val hexArray = "0123456789ABCDEF".toCharArray()

        private fun getDefaultTrustManager(): X509TrustManager {
            try {
                val tmf = TrustManagerFactory.getInstance(TrustManagerFactory.getDefaultAlgorithm())
                tmf.init(null as KeyStore?)

                for (tm in tmf.trustManagers) {
                    if (tm is X509TrustManager) {
                        return tm
                    }
                }
            } catch (e: GeneralSecurityException) {
                throw RuntimeException(e)
            }

            error("No X509 trust manager found")
        }

        @JvmStatic
        @Throws(XmlPullParserException::class, IOException::class)
        internal fun getXmlString(r: Reader, tagname: String, throwIfMissing: Boolean): String? {
            val factory = XmlPullParserFactory.newInstance()
            factory.isNamespaceAware = true
            val xpp = factory.newPullParser()

            xpp.setInput(r)
            var eventType = xpp.eventType
            val currentTag = Stack<String>()

            while (eventType != XmlPullParser.END_DOCUMENT) {
                when (eventType) {
                    XmlPullParser.START_TAG -> {
                        if (xpp.name == "root") {
                            verifyResponseStatus(xpp)
                        }
                        currentTag.push(xpp.name)
                    }
                    XmlPullParser.END_TAG -> {
                        currentTag.pop()
                    }
                    XmlPullParser.TEXT -> {
                        if (currentTag.peek() == tagname) {
                            return xpp.text
                        }
                    }
                }
                eventType = xpp.next()
            }

            if (throwIfMissing) {
                // We throw an XmlPullParserException here for ease of handling in all the various
                // callers.
                // We could also throw an IOException, but some callers expect those in cases where
                // the
                // host may not be reachable. We want to distinguish unreachable hosts vs. hosts
                // that
                // are returning garbage XML to us, so we use XmlPullParserException instead.
                throw XmlPullParserException("Missing mandatory field in host response: $tagname")
            }

            return null
        }

        @JvmStatic
        @Throws(XmlPullParserException::class, IOException::class)
        internal fun getXmlString(str: String, tagname: String, throwIfMissing: Boolean): String? =
            getXmlString(StringReader(str), tagname, throwIfMissing)

        /** A field that the host must send: [getXmlString] with `throwIfMissing` set. */
        @Throws(XmlPullParserException::class, IOException::class)
        private fun requireXmlString(str: String, tagname: String): String =
            checkNotNull(getXmlString(str, tagname, true))

        @Throws(HostHttpResponseException::class)
        private fun verifyResponseStatus(xpp: XmlPullParser) {
            // We use Long.parseLong() because in rare cases GFE can send back a status code of
            // 0xFFFFFFFF, which will cause Integer.parseInt() to throw a NumberFormatException due
            // to exceeding Integer.MAX_VALUE. We'll get the desired error code of -1 by just
            // casting
            // the resulting long into an int.
            // The attribute can be missing: that is a NumberFormatException, as it is in the Java.
            val statusAttribute: String? =
                xpp.getAttributeValue(XmlPullParser.NO_NAMESPACE, "status_code")
            var statusCode =
                (statusAttribute ?: throw NumberFormatException("Cannot parse null string"))
                    .toLong()
                    .toInt()
            if (statusCode != STATUS_OK) {
                var statusMsg: String? =
                    xpp.getAttributeValue(XmlPullParser.NO_NAMESPACE, "status_message")
                if (statusCode == -1 && "Invalid" == statusMsg) {
                    // Special case handling an audio capture error which GFE doesn't
                    // provide any useful status message for.
                    statusCode = STATUS_AUDIO_CAPTURE_ERROR
                    statusMsg = "Missing audio capture device. Reinstall GeForce Experience."
                }
                throw HostHttpResponseException(statusCode, statusMsg)
            }
        }

        private fun makeTuple(address: String?, port: Int): AddressTuple? {
            if (address == null) {
                return null
            }

            return AddressTuple(address, port)
        }

        @JvmStatic
        @Throws(XmlPullParserException::class, IOException::class)
        fun getAppListByReader(r: Reader): LinkedList<NvApp> {
            val factory = XmlPullParserFactory.newInstance()
            factory.isNamespaceAware = true
            val xpp = factory.newPullParser()

            xpp.setInput(r)
            var eventType = xpp.eventType
            val appList = LinkedList<NvApp>()
            val currentTag = Stack<String>()
            var rootTerminated = false

            while (eventType != XmlPullParser.END_DOCUMENT) {
                when (eventType) {
                    XmlPullParser.START_TAG -> {
                        if (xpp.name == "root") {
                            verifyResponseStatus(xpp)
                        }
                        currentTag.push(xpp.name)
                        if (xpp.name == "App") {
                            appList.addLast(NvApp())
                        }
                    }
                    XmlPullParser.END_TAG -> {
                        currentTag.pop()
                        if (xpp.name == "root") {
                            rootTerminated = true
                        }
                    }
                    XmlPullParser.TEXT -> {
                        // The last of an empty list is a NoSuchElementException, as in the Java.
                        val app = appList.last()
                        if (currentTag.peek() == "AppTitle") {
                            app.appName = xpp.text
                        } else if (currentTag.peek() == "ID") {
                            app.setAppId(xpp.text)
                        } else if (currentTag.peek() == "IsHdrSupported") {
                            app.isHdrSupported = xpp.text == "1"
                        }
                    }
                }
                eventType = xpp.next()
            }

            // Throw a malformed XML exception if we've not seen the root tag ended
            if (!rootTerminated) {
                throw XmlPullParserException("Malformed XML: Root tag was not terminated")
            }

            // Ensure that all apps in the list are initialized
            val i = appList.listIterator()
            while (i.hasNext()) {
                val app = i.next()

                // Remove uninitialized apps
                if (!app.isInitialized) {
                    LimeLog.warning("GFE returned incomplete app: " + app.appId + " " + app.appName)
                    i.remove()
                }
            }

            return appList
        }

        private fun bytesToHex(bytes: ByteArray): String {
            val hexChars = CharArray(bytes.size * 2)
            for (j in bytes.indices) {
                val v = bytes[j].toInt() and BYTE_MASK
                hexChars[j * 2] = hexArray[v ushr NIBBLE_BITS]
                hexChars[j * 2 + 1] = hexArray[v and NIBBLE_MASK]
            }
            return String(hexChars)
        }
    }
}

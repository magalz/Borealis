package io.github.magalz.borealis.diagnostics.bundle

import android.app.Activity
import android.app.AlertDialog
import android.content.Intent
import android.content.pm.ActivityInfo
import android.content.pm.ResolveInfo
import android.content.res.Resources
import android.graphics.Rect
import android.graphics.drawable.BitmapDrawable
import android.net.Uri
import android.os.Looper
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.ScrollView
import android.widget.TextView
import com.google.common.truth.Truth.assertThat
import com.google.zxing.BinaryBitmap
import com.google.zxing.DecodeHintType
import com.google.zxing.RGBLuminanceSource
import com.google.zxing.common.HybridBinarizer
import com.google.zxing.qrcode.QRCodeReader
import io.github.magalz.borealis.diagnostics.DiagnosticSection
import java.io.File
import java.net.ConnectException
import java.net.InetAddress
import java.net.Socket
import java.time.Duration
import java.util.concurrent.atomic.AtomicLong
import kotlinx.coroutines.Dispatchers
import org.junit.After
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.Shadows.shadowOf
import org.robolectric.shadows.ShadowAlertDialog
import org.robolectric.shadows.ShadowToast

/** An activity whose resources fail once on demand, as a window that cannot be built would. */
class FailingResourcesActivity : Activity() {
    var failuresLeft = 0

    override fun getResources(): Resources {
        if (failuresLeft > 0) {
            failuresLeft--
            throw IllegalStateException("no resources")
        }
        return super.getResources()
    }
}

/** An activity whose `startActivity` fails the way a revoked grant or a bad intent would. */
class RefusingActivity : Activity() {
    override fun startActivity(intent: Intent?) {
        throw SecurityException("not allowed")
    }
}

@RunWith(RobolectricTestRunner::class)
class ExportDiagnosticsPreferenceTest {
    private val activity: Activity = Robolectric.buildActivity(Activity::class.java).setup().get()
    private lateinit var provider: FakeDownloadsProvider
    private val clock = AtomicLong(1_000L)

    @Before
    fun setUp() {
        File(activity.cacheDir, "diagnostics").deleteRecursively()
        provider = FakeDownloadsProvider.install()
    }

    @After
    fun tearDown() {
        ShadowToast.reset()
        opened?.let { if (it.isShowing) dismissAndIdle(it) }
        opened = null
        // Every transfer dialog a test opens must have stopped its server.
        assertThat(transferThreads()).isEqualTo(0)
    }

    private fun preference(
        sections: Set<DiagnosticSection> = emptySet(),
        context: Activity = activity,
        address: InetAddress? = InetAddress.getByName("127.0.0.1"),
    ) =
        ExportDiagnosticsPreference(context).apply {
            sectionsProvider = { sections }
            transferAddress = { address }
            transferClock = clock::get
            // androidx FileProvider cannot map Windows paths under Robolectric; the real one is
            // covered by FileProviderConfigTest (not on Windows) and ManifestXmlTest.
            uriFor = { file ->
                Uri.Builder()
                    .scheme("content")
                    .authority(fileProviderAuthority(activity.packageName))
                    .appendPath("diagnostics")
                    .appendPath(file.name)
                    .build()
            }
            // Run the "background" work on the test thread so the result is there when onClick
            // returns.
            ioDispatcher = Dispatchers.Unconfined
        }

    @Suppress("DEPRECATION")
    private fun allowSharing() {
        val send = Intent(Intent.ACTION_SEND).setType("application/zip")
        val resolved =
            ResolveInfo().apply {
                activityInfo =
                    ActivityInfo().apply {
                        packageName = "com.example.mail"
                        name = "com.example.mail.Send"
                    }
            }
        shadowOf(activity.packageManager).addResolveInfoForIntent(send, resolved)
    }

    private fun texts(view: View): List<String> =
        when (view) {
            is TextView -> listOf(view.text.toString())
            is ViewGroup -> (0 until view.childCount).flatMap { texts(view.getChildAt(it)) }
            else -> emptyList()
        }

    private fun containsScrollView(view: View): Boolean =
        view is ScrollView ||
            (view is ViewGroup &&
                (0 until view.childCount).any { containsScrollView(view.getChildAt(it)) })

    @Test
    fun `a click flushes the pending log lines into the bundle`() {
        allowSharing()
        val logDir = File(activity.noBackupFilesDir, "logs").apply { mkdirs() }

        preference()
            .apply { flushLogs = { File(logDir, "borealis.log").writeText("queued\n") } }
            .onClick()

        val cache = File(activity.cacheDir, "diagnostics").listFiles()!!.single()
        assertThat(unzip(cache.readBytes())["logs/borealis.log"]).isEqualTo("queued\n")
    }

    @Test
    fun `a share that the system refuses becomes a message and does not crash`() {
        allowSharing()
        val refusing = Robolectric.buildActivity(RefusingActivity::class.java).setup().get()

        preference(context = refusing).onClick()

        assertThat(ShadowToast.getTextOfLatestToast())
            .isEqualTo("Could not create the diagnostic bundle. See the log for details.")
    }

    @Test
    fun `a click shares the bundle through a chooser`() {
        allowSharing()

        preference(setOf(TextSection("audit.jsonl", "line\n"))).onClick()

        val started = shadowOf(activity).nextStartedActivity
        assertThat(started.action).isEqualTo(Intent.ACTION_CHOOSER)
        @Suppress("DEPRECATION")
        val send = started.getParcelableExtra<Intent>(Intent.EXTRA_INTENT)!!
        assertThat(send.type).isEqualTo("application/zip")
        @Suppress("DEPRECATION") val uri = send.getParcelableExtra<Uri>(Intent.EXTRA_STREAM)!!
        assertThat(uri.scheme).isEqualTo("content")
        assertThat(uri.authority).isEqualTo("${activity.packageName}.diagnostics.fileprovider")
        assertThat(uri.lastPathSegment).startsWith("borealis-diagnostics-")
        assertThat(ShadowAlertDialog.getLatestAlertDialog()).isNull()
    }

    private fun savedName() =
        provider.rows
            .single()
            .current
            .getAsString(android.provider.MediaStore.MediaColumns.DISPLAY_NAME)

    private fun savedDialog(): AlertDialog = ShadowAlertDialog.getLatestAlertDialog()

    @Test
    fun `with no share target the Downloads path and the adb command are shown in a dialog`() {
        preference().onClick()

        assertThat(shadowOf(activity).nextStartedActivity).isNull()
        val dialog = savedDialog()
        assertThat(dialog.isShowing).isTrue()
        val text = texts(dialog.window!!.decorView).joinToString("\n")
        assertThat(text).contains("Download/${savedName()}")
        assertThat(text).contains("adb pull /sdcard/Download/${savedName()}")
        // A D-pad reaches the text (it scrolls) and both buttons.
        assertThat(containsScrollView(dialog.window!!.decorView)).isTrue()
        assertThat(dialog.getButton(AlertDialog.BUTTON_POSITIVE).isFocusable).isTrue()
        assertThat(dialog.getButton(AlertDialog.BUTTON_NEUTRAL).isFocusable).isTrue()
    }

    @Test
    fun `a TV saves the bundle and shows the dialog even when a receiver exists`() {
        RuntimeEnvironment.setQualifiers("+television")
        val tv = Robolectric.buildActivity(Activity::class.java).setup().get()
        allowSharing()

        preference(context = tv).onClick()

        assertThat(shadowOf(tv).nextStartedActivity).isNull()
        val dialog = savedDialog()
        assertThat(dialog.isShowing).isTrue()
        assertThat(texts(dialog.window!!.decorView).joinToString("\n"))
            .contains("adb pull /sdcard/Download/${savedName()}")
    }

    @Test
    fun `a TV dialog offers Send to phone instead of Share and Close keeps the focus`() {
        RuntimeEnvironment.setQualifiers("+television")
        val tv = Robolectric.buildActivity(Activity::class.java).setup().get()
        allowSharing()

        preference(context = tv).onClick()
        shadowOf(Looper.getMainLooper()).idle()

        val dialog = savedDialog()
        val send = dialog.getButton(AlertDialog.BUTTON_NEUTRAL)
        assertThat(send.visibility).isEqualTo(View.VISIBLE)
        assertThat(send.text.toString()).isEqualTo("Send to phone")
        assertThat(send.isFocusable).isTrue()
        assertThat(texts(dialog.window!!.decorView)).doesNotContain("Share…")
        assertThat(dialog.getButton(AlertDialog.BUTTON_POSITIVE).hasFocus()).isTrue()
    }

    @Test
    fun `a phone with no receiver still has the Share button`() {
        preference().onClick()

        val share = savedDialog().getButton(AlertDialog.BUTTON_NEUTRAL)
        assertThat(share.visibility).isEqualTo(View.VISIBLE)
        assertThat(share.text.toString()).isEqualTo("Share…")
    }

    @Test
    fun `the Close button has the focus when the saved dialog opens`() {
        preference().onClick()
        shadowOf(Looper.getMainLooper()).idle()

        assertThat(savedDialog().getButton(AlertDialog.BUTTON_POSITIVE).hasFocus()).isTrue()
    }

    @Test
    fun `the Share button opens the chooser with a read grant for the bundle`() {
        preference().onClick()

        savedDialog().getButton(AlertDialog.BUTTON_NEUTRAL).performClick()
        shadowOf(Looper.getMainLooper()).idle()

        val started = shadowOf(activity).nextStartedActivity
        assertThat(started.action).isEqualTo(Intent.ACTION_CHOOSER)
        assertThat(started.flags and Intent.FLAG_GRANT_READ_URI_PERMISSION).isNotEqualTo(0)
        @Suppress("DEPRECATION")
        val send = started.getParcelableExtra<Intent>(Intent.EXTRA_INTENT)!!
        assertThat(send.type).isEqualTo("application/zip")
        assertThat(send.flags and Intent.FLAG_GRANT_READ_URI_PERMISSION).isNotEqualTo(0)
        @Suppress("DEPRECATION") val uri = send.getParcelableExtra<Uri>(Intent.EXTRA_STREAM)!!
        assertThat(uri.authority).isEqualTo("${activity.packageName}.diagnostics.fileprovider")
    }

    @Test
    fun `a Share button that the system refuses becomes a message and does not crash`() {
        val refusing = Robolectric.buildActivity(RefusingActivity::class.java).setup().get()
        preference(context = refusing).onClick()

        savedDialog().getButton(AlertDialog.BUTTON_NEUTRAL).performClick()
        shadowOf(Looper.getMainLooper()).idle()

        assertThat(ShadowToast.getTextOfLatestToast())
            .isEqualTo("Could not create the diagnostic bundle. See the log for details.")
    }

    @Test
    fun `a failure becomes a short message and never throws`() {
        // The bundle directory cannot be created: a file is in its place.
        File(activity.cacheDir, "diagnostics").writeText("a file, not a directory")

        preference().onClick()

        assertThat(ShadowToast.getTextOfLatestToast())
            .isEqualTo("Could not create the diagnostic bundle. See the log for details.")
        assertThat(shadowOf(activity).nextStartedActivity).isNull()
    }

    private var tvActivity: Activity? = null

    /** The last transfer dialog a test opened; closed in tearDown if the test failed before it. */
    private var opened: AlertDialog? = null

    private fun tvSendToPhone(
        address: InetAddress? = InetAddress.getByName("127.0.0.1"),
        qualifiers: String = "+television",
    ): Pair<AlertDialog, AlertDialog> {
        RuntimeEnvironment.setQualifiers(qualifiers)
        val tv = Robolectric.buildActivity(Activity::class.java).setup().get()
        tvActivity = tv
        preference(context = tv, address = address).onClick()
        val saved = savedDialog()
        saved.getButton(AlertDialog.BUTTON_NEUTRAL).performClick()
        shadowOf(Looper.getMainLooper()).idle()
        return saved to ShadowAlertDialog.getLatestAlertDialog().also { opened = it }
    }

    private fun imageOf(view: View): ImageView? =
        when (view) {
            is ImageView -> view.takeIf { it.contentDescription?.contains("QR code") == true }
            is ViewGroup ->
                (0 until view.childCount).firstNotNullOfOrNull { imageOf(view.getChildAt(it)) }
            else -> null
        }

    private fun urlIn(dialog: AlertDialog): String =
        texts(dialog.window!!.decorView).first { it.startsWith("http://") }

    private fun portOf(url: String) = url.substringAfterLast(":").substringBefore("/").toInt()

    private fun decode(image: ImageView): String {
        val bitmap = (image.drawable as BitmapDrawable).bitmap
        val pixels = IntArray(bitmap.width * bitmap.height)
        bitmap.getPixels(pixels, 0, bitmap.width, 0, 0, bitmap.width, bitmap.height)
        val source = RGBLuminanceSource(bitmap.width, bitmap.height, pixels)
        return QRCodeReader()
            .decode(
                BinaryBitmap(HybridBinarizer(source)),
                mapOf(DecodeHintType.PURE_BARCODE to true),
            )
            .text
    }

    private fun assertRefused(url: String) {
        var refused = false
        try {
            Socket("127.0.0.1", portOf(url)).close()
        } catch (_: ConnectException) {
            refused = true
        }
        assertThat(refused).isTrue()
    }

    @Test
    fun `Send to phone keeps the saved dialog and shows a QR code of the link`() {
        val (saved, transfer) = tvSendToPhone()

        assertThat(saved.isShowing).isTrue()
        assertThat(transfer).isNotSameInstanceAs(saved)
        assertThat(transfer.isShowing).isTrue()
        val url = urlIn(transfer)
        assertThat(url).matches("http://127\\.0\\.0\\.1:\\d+/[0-9a-f]{32}")
        assertThat(decode(imageOf(transfer.window!!.decorView)!!)).isEqualTo(url)
        dismissAndIdle(transfer)
    }

    @Test
    fun `the QR code is about 60 percent of the shorter screen side`() {
        val (_, transfer) = tvSendToPhone()

        val metrics = tvActivity!!.resources.displayMetrics
        val target = (minOf(metrics.widthPixels, metrics.heightPixels) * 0.6).toInt()
        val width = imageOf(transfer.window!!.decorView)!!.drawable.intrinsicWidth
        assertThat(width).isAtMost(target)
        assertThat(width).isGreaterThan(target - 45)
        dismissAndIdle(transfer)
    }

    @Test
    fun `the transfer dialog explains the link, counts down and starts as waiting`() {
        val (_, transfer) = tvSendToPhone()

        val text = texts(transfer.window!!.decorView).joinToString("\n")
        assertThat(text).contains("Scan this code with your phone's camera")
        assertThat(text).contains("same network")
        assertThat(text).contains("one download")
        assertThat(text).contains("5 minutes")
        assertThat(text).contains("Expires in 5:00")
        assertThat(text).contains("Waiting")
        assertThat(transfer.getButton(AlertDialog.BUTTON_POSITIVE).text.toString())
            .isEqualTo("Close")
        assertThat(transfer.getButton(AlertDialog.BUTTON_POSITIVE).hasFocus()).isTrue()
        dismissAndIdle(transfer)
    }

    @Test
    fun `the countdown follows the clock`() {
        val (_, transfer) = tvSendToPhone()

        clock.addAndGet(61_000L)
        shadowOf(Looper.getMainLooper()).idleFor(Duration.ofSeconds(1))

        assertThat(texts(transfer.window!!.decorView)).contains("Expires in 3:59")
        dismissAndIdle(transfer)
    }

    @Test
    fun `a finished download shows Downloaded`() {
        val (_, transfer) = tvSendToPhone()
        val url = urlIn(transfer)
        val name = File(activity.cacheDir, "diagnostics").listFiles()!!.single().name

        Socket("127.0.0.1", portOf(url)).use {
            val path = "/${url.substringAfterLast("/")}/$name"
            it.getOutputStream().write("GET $path HTTP/1.1\r\n\r\n".toByteArray())
            it.getInputStream().readBytes()
        }
        shadowOf(Looper.getMainLooper()).idleFor(Duration.ofSeconds(1))

        assertThat(texts(transfer.window!!.decorView)).contains("Downloaded")
        dismissAndIdle(transfer)
    }

    @Test
    fun `an expired link shows Expired`() {
        val (_, transfer) = tvSendToPhone()

        clock.addAndGet(300_001L)
        val deadline = System.nanoTime() + 5_000_000_000L
        while ("Expired" !in texts(transfer.window!!.decorView) && System.nanoTime() < deadline) {
            Thread.sleep(50)
            shadowOf(Looper.getMainLooper()).idleFor(Duration.ofSeconds(1))
        }

        assertThat(texts(transfer.window!!.decorView)).contains("Expired")
        dismissAndIdle(transfer)
    }

    @Test
    fun `closing the transfer dialog stops the server`() {
        val (saved, transfer) = tvSendToPhone()
        val url = urlIn(transfer)

        transfer.getButton(AlertDialog.BUTTON_POSITIVE).performClick()
        shadowOf(Looper.getMainLooper()).idle()

        assertThat(transfer.isShowing).isFalse()
        assertThat(saved.isShowing).isTrue()
        assertRefused(url)
    }

    @Test
    fun `a settings screen that is finishing stops the server`() {
        val (_, transfer) = tvSendToPhone()
        val url = urlIn(transfer)

        tvActivity!!.finish()
        shadowOf(Looper.getMainLooper()).idleFor(Duration.ofSeconds(1))

        assertRefused(url)
    }

    @Test
    fun `with no local network a message replaces the QR code and the saved dialog stays`() {
        val (saved, transfer) = tvSendToPhone(address = null)

        val text = texts(transfer.window!!.decorView).joinToString("\n")
        assertThat(text).contains("No local network")
        assertThat(imageOf(transfer.window!!.decorView)).isNull()
        assertThat(saved.isShowing).isTrue()
        assertThat(texts(saved.window!!.decorView).joinToString("\n")).contains("adb pull")
    }

    @Test
    fun `an address that cannot be bound becomes a message and does not crash`() {
        val (_, transfer) = tvSendToPhone(address = InetAddress.getByName("203.0.113.1"))

        assertThat(texts(transfer.window!!.decorView).joinToString("\n"))
            .contains("Could not start the transfer")
        assertThat(imageOf(transfer.window!!.decorView)).isNull()
    }

    @Test
    fun `a transfer that fails to open becomes a message and does not crash`() {
        RuntimeEnvironment.setQualifiers("+television")
        val tv = Robolectric.buildActivity(Activity::class.java).setup().get()
        preference(context = tv).apply { transferAddress = { error("no network stack") } }.onClick()

        savedDialog().getButton(AlertDialog.BUTTON_NEUTRAL).performClick()
        shadowOf(Looper.getMainLooper()).idle()

        assertThat(ShadowToast.getTextOfLatestToast())
            .isEqualTo("Could not start the transfer. See the log for details.")
    }

    @Test
    fun `a dialog that cannot be built stops the server it started`() {
        RuntimeEnvironment.setQualifiers("+television")
        val tv = Robolectric.buildActivity(FailingResourcesActivity::class.java).setup().get()
        preference(context = tv).onClick()
        val saved = savedDialog()
        val before = transferThreads()

        tv.failuresLeft = 1
        saved.getButton(AlertDialog.BUTTON_NEUTRAL).performClick()
        shadowOf(Looper.getMainLooper()).idle()

        assertThat(ShadowToast.getTextOfLatestToast())
            .isEqualTo("Could not start the transfer. See the log for details.")
        assertThat(transferThreads()).isEqualTo(before)
    }

    private fun viewWithText(view: View, prefix: String): TextView? =
        when (view) {
            is TextView -> view.takeIf { it.text.startsWith(prefix) }
            is ViewGroup ->
                (0 until view.childCount).firstNotNullOfOrNull {
                    viewWithText(view.getChildAt(it), prefix)
                }
            else -> null
        }

    private fun scrollViewOf(view: View): ScrollView? =
        when (view) {
            is ScrollView -> view
            is ViewGroup ->
                (0 until view.childCount).firstNotNullOfOrNull { scrollViewOf(view.getChildAt(it)) }
            else -> null
        }

    private fun positionOf(view: View): Rect {
        val at = IntArray(2)
        view.getLocationInWindow(at)
        return Rect(at[0], at[1], at[0] + view.width, at[1] + view.height)
    }

    /** The views of an open transfer dialog, laid out. */
    private class TransferViews(dialog: AlertDialog, test: ExportDiagnosticsPreferenceTest) {
        val root: View = dialog.window!!.decorView
        val qr = test.imageOf(root)!!
        val instructions = test.viewWithText(root, "Scan this code")!!
        val url = test.viewWithText(root, "http://")!!
        val status = test.viewWithText(root, "Waiting")!!
        val scroll = test.scrollViewOf(root)!!
    }

    private fun assertNothingClipped(views: TransferViews) {
        val content = views.scroll.getChildAt(0)
        assertThat(content.height).isGreaterThan(0)
        // The scroll view shows all of its content: no row, the status line included, is cut off.
        assertThat(views.scroll.height).isAtLeast(content.height)
        val status = positionOf(views.status)
        assertThat(status.height()).isEqualTo(views.status.measuredHeight)
        assertThat(status.height()).isGreaterThan(0)
        val shown = Rect()
        assertThat(views.status.getGlobalVisibleRect(shown)).isTrue()
        assertThat(shown.height()).isEqualTo(status.height())
    }

    private fun landscape(qualifiers: String, shortSide: Int) {
        val (_, transfer) = tvSendToPhone(qualifiers = qualifiers)
        val views = TransferViews(transfer, this)

        val metrics = tvActivity!!.resources.displayMetrics
        assertThat(minOf(metrics.widthPixels, metrics.heightPixels)).isEqualTo(shortSide)
        val qr = positionOf(views.qr)
        // The code is on the left, the instructions, link and status on the right.
        assertThat(qr.right).isAtMost(positionOf(views.instructions).left)
        assertThat(qr.right).isAtMost(positionOf(views.url).left)
        assertThat(qr.right).isAtMost(positionOf(views.status).left)
        assertThat(positionOf(views.status).top).isAtLeast(positionOf(views.instructions).bottom)
        assertThat(views.qr.drawable.intrinsicWidth).isAtMost(shortSide / 2)
        assertNothingClipped(views)
        dismissAndIdle(transfer)
    }

    @Test
    fun `on a 1080p TV the code is left of the text, large enough to scan, and nothing is clipped`() {
        landscape("+w960dp-h540dp-land-television-xhdpi", 1080)
    }

    @Test
    fun `the 1080p TV code is at least 400 pixels wide`() {
        val (_, transfer) = tvSendToPhone(qualifiers = "+w960dp-h540dp-land-television-xhdpi")

        assertThat(imageOf(transfer.window!!.decorView)!!.drawable.intrinsicWidth).isAtLeast(400)
        dismissAndIdle(transfer)
    }

    @Test
    fun `on a 720p TV the code is left of the text and nothing is clipped`() {
        landscape("+w640dp-h360dp-land-television-xhdpi", 720)
    }

    @Test
    fun `in portrait the code stays above the text`() {
        val (_, transfer) = tvSendToPhone(qualifiers = "+w360dp-h640dp-port-television-xhdpi")
        val views = TransferViews(transfer, this)

        val qr = positionOf(views.qr)
        assertThat(qr.bottom).isAtMost(positionOf(views.instructions).top)
        assertThat(positionOf(views.instructions).bottom).isAtMost(positionOf(views.url).top)
        assertThat(positionOf(views.url).bottom).isAtMost(positionOf(views.status).top)
        val metrics = tvActivity!!.resources.displayMetrics
        val target = (minOf(metrics.widthPixels, metrics.heightPixels) * 0.6).toInt()
        assertThat(views.qr.drawable.intrinsicWidth).isAtMost(target)
        assertThat(views.qr.drawable.intrinsicWidth).isGreaterThan(target - 45)
        dismissAndIdle(transfer)
    }

    /** The dismiss listener, which stops the server, is posted to the main looper. */
    private fun dismissAndIdle(dialog: AlertDialog) {
        dialog.dismiss()
        shadowOf(Looper.getMainLooper()).idle()
    }

    private fun transferThreads(): Int =
        Thread.getAllStackTraces().keys.count {
            it.name.startsWith("borealis-diagnostic-transfer") && it.isAlive
        }
}

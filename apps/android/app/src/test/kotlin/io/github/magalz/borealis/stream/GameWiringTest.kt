package io.github.magalz.borealis.stream

import com.google.common.truth.Truth.assertThat
import java.io.File
import org.junit.Test

/**
 * The stream activity hands the stream chrome the controller, the connection and the renderer. Reads
 * the source, so it needs no Android runtime and never initializes `Game`.
 */
class GameWiringTest {
    private val source: String = File("src/main/java/com/limelight/Game.java").readText()
    private val renderer: String =
        File("src/main/java/com/limelight/binding/video/MediaCodecDecoderRenderer.java").readText()

    private fun body(signature: String): String {
        val start = source.indexOf(signature)
        // A method that is not there has an empty body, so the test fails on its assertion.
        if (start < 0) return ""
        return source.substring(start, source.indexOf("\n    }\n", start))
    }

    // The methods of the service connection are one level deeper than the others.
    private val nestedEnd = "\n        }\n"
    private fun connectionBody(signature: String): String {
        val start = source.indexOf(signature)
        if (start < 0) return ""
        return source.substring(start, source.indexOf(nestedEnd, start))
    }

    @Test
    fun `the first frame tells the USB driver service that the picture is up`() {
        val start = source.indexOf("decoderRenderer.firstFrameSignal.setListener(")
        val listener = if (start < 0) "" else source.substring(start, source.indexOf("});", start))

        assertThat(listener).contains("usbDriverBinder")
        assertThat(listener).contains("onStreamReady()")
    }

    @Test
    fun `a service that connects after the first frame is told at once`() {
        val connected = connectionBody("public void onServiceConnected(")

        assertThat(connected).contains("usbDriverBinder = binder")
        assertThat(connected.indexOf("usbDriverBinder = binder"))
            .isLessThan(connected.indexOf("if (firstFrameShown)"))
        assertThat(connected.substringAfter("if (firstFrameShown)")).contains("binder.onStreamReady()")
    }

    @Test
    fun `a service that goes away is forgotten`() {
        assertThat(connectionBody("public void onServiceDisconnected("))
            .contains("usbDriverBinder = null")
    }

    @Test
    fun `the chrome gets the look that the settings store when the stream starts`() {
        val host = source.substringAfter("new StreamChromeHost(", "").substringBefore(");")

        assertThat(host).contains("StreamSessionFactory.look(this)")
    }

    @Test
    fun `the chrome takes and gives back the gamepad`() {
        assertThat(body("private void chromeCaptured()")).contains("controllerHandler.setCaptured(true)")
        assertThat(body("private void chromeReleased()")).contains("controllerHandler.setCaptured(false)")
    }

    @Test
    fun `the gamepad gives the gamepad back before the pointer is grabbed again`() {
        val released = body("private void chromeReleased()")

        assertThat(released).contains("setCaptured(false)")
        assertThat(released.indexOf("setCaptured(false)"))
            .isLessThan(released.indexOf("setInputGrabState(true)"))
    }

    @Test
    fun `the menu chord opens the menu and the USB pad keys reach the chrome`() {
        assertThat(source).contains("controllerHandler.setMenuChordListener(streamSession::openMenu)")
        assertThat(source).contains("controllerHandler.setChromeKeySink(chromeHost::dispatchUsbPadKey)")
    }

    @Test
    fun `the keyboard asks the gamepad handler whether the USB driver holds a pad`() {
        assertThat(source).contains("StreamSessionFactory.create(")
        assertThat(source).contains("controllerHandler::hasUsbPad")
    }

    @Test
    fun `the menu reads the gamepad mouse and the decoder numbers follow the overlay and the menu`() {
        assertThat(source).contains("GamepadMouseSync.bind(")
        assertThat(source).contains("controllerHandler::isMouseEmulationOnAnyPad")
        assertThat(source).contains("PerfGate.bind(")
        assertThat(source).contains("decoderRenderer::setPerfEnabled")
        assertThat(source).contains("decoderRenderer.setPerfSink(streamSession::onPerf)")
    }

    @Test
    fun `starting the app is a stage of its own, the stage names tell it from the display`() {
        assertThat(body("public void launchStarting(")).contains("StartStage.STARTING_APP")
        assertThat(body("public void stageStarting(")).contains("StreamSessionFactory.startStage(")
    }

    @Test
    fun `a message of the start is no toast, it waits for the error screen`() {
        val message = body("public void displayMessage(")

        assertThat(message).isNotEmpty()
        assertThat(message).doesNotContain("Toast")
    }

    @Test
    fun `warnings during the stream stay toasts`() {
        assertThat(body("public void displayTransientMessage(")).contains("Toast.makeText")
    }

    @Test
    fun `a failure carries whether the first frame was shown and the note of the start`() {
        for (name in listOf("public void stageFailed(", "public void connectionTerminated(")) {
            val body = body(name)
            assertThat(body).contains("firstFrameShown")
            assertThat(body).contains("startNote")
        }
        assertThat(source).contains("firstFrameShown = true")
    }

    @Test
    fun `the connection never hands an exception text to the listener`() {
        val connection =
            File("src/main/kotlin/com/limelight/nvstream/NvConnection.kt").readText()

        assertThat(connection).doesNotContain("displayMessage(e.message")
        assertThat(connection).doesNotContain("stageFailed(appName")
    }

    @Test
    fun `sending the launch or the resume is the step that fails after the serverinfo`() {
        val connection =
            File("src/main/kotlin/com/limelight/nvstream/NvConnection.kt").readText()
        val start = connection.indexOf("private fun launchWithRetry(")
        val launch = connection.substring(start, connection.indexOf("\n    }\n", start))

        // The launch needs the native library, so a unit test cannot run it: read the wiring.
        assertThat(launch).contains("startStep = verb")
        assertThat(connection).contains("startStep = StartSteps.SERVER_INFO")
        assertThat(connection).contains("stageFailed(startStep")
    }

    @Test
    fun `a failed quit is the quit step, set before the quit request is sent`() {
        val connection =
            File("src/main/kotlin/com/limelight/nvstream/NvConnection.kt").readText()
        val start = connection.indexOf("protected open fun quitAndLaunch(")
        val quit = connection.substring(start, connection.indexOf("\n    }\n", start))

        assertThat(quit).contains("startStep = StartSteps.QUIT")
        assertThat(quit.indexOf("startStep = StartSteps.QUIT"))
            .isLessThan(quit.indexOf("h.quitApp()"))
    }

    @Test
    fun `the session info names the decoder`() {
        assertThat(source).contains("decoderRenderer.getDecoderName()")
    }

    @Test
    fun `failures are classified from the facts of the connection`() {
        for (name in listOf("public void stageFailed(", "public void connectionTerminated(")) {
            val body = body(name)
            assertThat(body).contains("StreamFailures.facts(")
            assertThat(body).contains("StreamFailures.failure(")
            assertThat(body).contains("streamSession.onFailed(")
            assertThat(body).doesNotContain("StreamSessionFactory.failure(")
        }
    }

    @Test
    fun `the error text is built by the classifier, not by the activity`() {
        assertThat(source).doesNotContain("conn_error_msg")
        assertThat(source).doesNotContain("conn_terminated_msg")
        assertThat(source).doesNotContain("StreamErrorHints")
        assertThat(source).doesNotContain("ResumeCollisions.withHint")
    }

    @Test
    fun `the 12 point 2 text overlay is gone`() {
        assertThat(source).doesNotContain("performanceOverlay")
        assertThat(source).doesNotContain("onPerfUpdate")
        assertThat(source).doesNotContain("PerfOverlayListener")
        assertThat(renderer).doesNotContain("perfListener")
        assertThat(renderer).doesNotContain("perf_overlay_")
        assertThat(File("src/main/res/layout/activity_game.xml").readText())
            .doesNotContain("performanceOverlay")
        assertThat(File("src/main/java/com/limelight/binding/video/PerfOverlayListener.java").exists())
            .isFalse()
    }

    @Test
    fun `no string of the text overlay stays in any language`() {
        val leftovers =
            File("src/main/res")
                .walkTopDown()
                .filter { it.name == "strings.xml" && it.readText().contains("perf_overlay_") }
                .map { it.path }
                .toList()

        assertThat(leftovers).isEmpty()
    }

    @Test
    fun `the strings of the old error dialogs are gone in every language`() {
        val names = listOf("check_ports_msg", "conn_error_msg", "conn_terminated_msg", "error_code_prefix")
        val leftovers =
            File("src/main/res")
                .walkTopDown()
                .filter { file ->
                    file.name == "strings.xml" && names.any { file.readText().contains("\"$it\"") }
                }
                .map { it.path }
                .toList()

        assertThat(leftovers).isEmpty()
    }

    @Test
    fun `the touch mode comes from the session, not from the preference`() {
        assertThat(source).doesNotContain("prefConfig.touchscreenTrackpad")
        assertThat(source).contains("TouchContexts.mode(streamSession, prefConfig)")
    }

    @Test
    fun `a touch screen that is switched off is ignored`() {
        assertThat(source).contains("TouchMode.OFF")
    }

    @Test
    fun `the three-finger tap toggles the Borealis keyboard, not the Android one`() {
        val toggle = body("public void toggleKeyboard()")

        assertThat(toggle).contains("StreamKeyboards.toggle(streamSession,")
        assertThat(toggle).contains("ImeKeyGate.hideIme(")
        assertThat(toggle).doesNotContain("toggleSoftInput")
    }

    @Test
    fun `the Android keyboard keeps the D-pad, Enter and Back of a gamepad`() {
        for (name in listOf("public boolean handleKeyDown(", "public boolean handleKeyUp(")) {
            assertThat(body(name)).contains("imeKeys.toIme(event)")
        }
        assertThat(source).contains("ImeKeyGate.isImeVisible(")
    }
}

package com.limelight.nvstream

import io.github.magalz.borealis.host.launch.ConflictAnswer
import io.github.magalz.borealis.host.launch.ConflictChoice

// Converted as is from the inherited Java; refactor in a later sprint (Sprint 12 backlog).
@Suppress("TooManyFunctions")
interface NvConnectionListener {
    fun stageStarting(stage: String?)

    fun stageComplete(stage: String?)

    fun stageFailed(stage: String, portFlags: Int, errorCode: Int)

    fun connectionStarted()

    fun connectionTerminated(errorCode: Int)

    fun connectionStatusUpdate(connectionStatus: Int)

    fun displayMessage(message: String)

    fun displayTransientMessage(message: String)

    fun rumble(controllerNumber: Short, lowFreqMotor: Short, highFreqMotor: Short)

    fun rumbleTriggers(controllerNumber: Short, leftTrigger: Short, rightTrigger: Short)

    fun setHdrMode(enabled: Boolean, hdrMetadata: ByteArray?)

    fun setMotionEventState(controllerNumber: Short, motionType: Byte, reportRateHz: Short)

    fun setControllerLED(controllerNumber: Short, r: Byte, g: Byte, b: Byte)

    // BOREALIS: the host asked to try /launch or /resume again (host.launch); category is a
    // LaunchCategory ordinal
    fun launchRetrying(category: Int, attempt: Int, maxAttempts: Int, delayMs: Int) {}

    // BOREALIS: /launch (or /resume when resume is true) is about to be requested (host.failure);
    // Game shows the "starting app" stage
    fun launchStarting(resume: Boolean) {}

    // BOREALIS: another app is running on the host: the listener asks the user and sets the
    // answer; without a UI the launch is cancelled (host.launch)
    fun launchConflict(
        runningAppId: Int,
        runningAppName: String?,
        requestedAppId: Int,
        requestedAppName: String?,
        answer: ConflictAnswer,
    ) {
        answer.choose(ConflictChoice.CANCEL)
    }
}

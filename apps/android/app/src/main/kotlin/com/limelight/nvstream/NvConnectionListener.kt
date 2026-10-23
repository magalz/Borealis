package com.limelight.nvstream;

public interface NvConnectionListener {
    void stageStarting(String stage);
    void stageComplete(String stage);
    void stageFailed(String stage, int portFlags, int errorCode);
    
    void connectionStarted();
    void connectionTerminated(int errorCode);
    void connectionStatusUpdate(int connectionStatus);
    
    void displayMessage(String message);
    void displayTransientMessage(String message);

    void rumble(short controllerNumber, short lowFreqMotor, short highFreqMotor);
    void rumbleTriggers(short controllerNumber, short leftTrigger, short rightTrigger);

    void setHdrMode(boolean enabled, byte[] hdrMetadata);

    void setMotionEventState(short controllerNumber, byte motionType, short reportRateHz);

    void setControllerLED(short controllerNumber, byte r, byte g, byte b);

    // BOREALIS: the host asked to try /launch or /resume again (host.launch); category is a LaunchCategory ordinal
    default void launchRetrying(int category, int attempt, int maxAttempts, int delayMs) {
    }

    // BOREALIS: /launch (or /resume when resume is true) is about to be requested (host.failure); Game shows the "starting app" stage
    default void launchStarting(boolean resume) {
    }

    // BOREALIS: another app is running on the host: the listener asks the user and sets the answer; without a UI the launch is cancelled (host.launch)
    default void launchConflict(int runningAppId, String runningAppName, int requestedAppId, String requestedAppName, io.github.magalz.borealis.host.launch.ConflictAnswer answer) {
        answer.choose(io.github.magalz.borealis.host.launch.ConflictChoice.CANCEL);
    }
}

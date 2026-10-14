package io.github.magalz.borealis

import android.app.Application
import android.os.Build
import com.limelight.BuildConfig
import com.limelight.R
import dagger.hilt.android.HiltAndroidApp
import io.github.magalz.borealis.logging.BorealisLog
import io.github.magalz.borealis.logging.CrashHandler
import io.github.magalz.borealis.logging.JulBridge
import io.github.magalz.borealis.logging.LogLevel
import io.github.magalz.borealis.logging.LogTag
import io.github.magalz.borealis.logging.LogcatSink
import io.github.magalz.borealis.logging.Redactor
import io.github.magalz.borealis.logging.RotatingFileSink
import io.github.magalz.borealis.nativecore.BorealisCore
import io.github.magalz.borealis.settings.LogSettings
import java.io.File

@HiltAndroidApp
class BorealisApplication : Application() {
    // Kept so that the preference listener of LogSettings stays registered.
    private lateinit var logSettings: LogSettings

    override fun onCreate() {
        super.onCreate()

        // noBackupFilesDir: logs and crash reports must never end up in Auto Backup.
        val logDir = File(noBackupFilesDir, "logs")
        // Release builds log INFO and above; "Verbose logging" in the settings lowers it to DEBUG.
        val baseLevel = if (BuildConfig.DEBUG) LogLevel.DEBUG else LogLevel.INFO
        // One redactor for log lines and crash reports, so both follow "Mask addresses".
        val redactor = Redactor()
        BorealisLog.initialize(
            listOf(LogcatSink(baseLevel), RotatingFileSink(logDir)),
            minLevel = baseLevel,
            redactor = redactor,
        )
        logSettings = LogSettings.start(this, baseLevel)
        JulBridge.install()
        CrashHandler.install(
            logDir,
            header = listOf("app=Borealis ${BuildConfig.VERSION_NAME}", "base=$UPSTREAM_BASE"),
            redactor = redactor,
        )

        BorealisLog.i(
            LogTag.APP,
            "startup",
            "version" to BuildConfig.VERSION_NAME,
            "commit" to getString(R.string.borealis_git_sha),
            "base" to UPSTREAM_BASE,
            "applicationId" to BuildConfig.APPLICATION_ID,
            "sdk" to Build.VERSION.SDK_INT,
        )
        BorealisCore.logStartup(bridgeVersion = NativeBridge.coreVersion())
    }

    private companion object {
        const val UPSTREAM_BASE = "Moonlight 12.2"
    }
}

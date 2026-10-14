package io.github.magalz.borealis

import android.app.Application
import android.os.Build
import com.limelight.BuildConfig
import dagger.hilt.android.HiltAndroidApp
import io.github.magalz.borealis.logging.BorealisLog
import io.github.magalz.borealis.logging.CrashHandler
import io.github.magalz.borealis.logging.JulBridge
import io.github.magalz.borealis.logging.LogLevel
import io.github.magalz.borealis.logging.LogTag
import io.github.magalz.borealis.logging.LogcatSink
import io.github.magalz.borealis.logging.RotatingFileSink
import io.github.magalz.borealis.nativecore.BorealisCore
import java.io.File

@HiltAndroidApp
class BorealisApplication : Application() {
    override fun onCreate() {
        super.onCreate()

        // noBackupFilesDir: logs and crash reports must never end up in Auto Backup.
        val logDir = File(noBackupFilesDir, "logs")
        val logcatLevel = if (BuildConfig.DEBUG) LogLevel.DEBUG else LogLevel.INFO
        BorealisLog.initialize(listOf(LogcatSink(logcatLevel), RotatingFileSink(logDir)))
        JulBridge.install()
        CrashHandler.install(
            logDir,
            header = listOf("app=Borealis ${BuildConfig.VERSION_NAME}", "base=$UPSTREAM_BASE"),
        )

        BorealisLog.i(
            LogTag.APP,
            "startup",
            "version" to BuildConfig.VERSION_NAME,
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

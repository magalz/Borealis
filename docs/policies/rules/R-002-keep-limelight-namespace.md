# R-002 Never rename the com.limelight namespace or MoonBridge

The Android `namespace` stays `com.limelight` and the JNI class stays `com.limelight.nvstream.jni.MoonBridge`. Only the `applicationId` changes (see [ADR 0028](../../adr/0028-application-id.md)).

Why: the native library exports `Java_com_limelight_nvstream_jni_MoonBridge_*` symbols and `callbacks.c` looks the class up by name; renaming either breaks JNI at runtime and diverges from upstream.

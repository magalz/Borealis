// BOREALIS: forwards the moonlight-common-c log lines to BorealisLog (ADR 0050, item 4).
#include "borealis_log.h"

#include <stdio.h>

// Lines are cut here; common-c lines are short and the Kotlin side rate limits and redacts them.
#define LOG_BUFFER_SIZE 512

static jclass BridgeClass;
static jmethodID BridgeClLogMessageMethod;

void BorealisLogInit(JNIEnv* env, jclass bridgeClass) {
    jmethodID method = (*env)->GetStaticMethodID(env, bridgeClass, "bridgeClLogMessage", "([B)Z");
    if (method == NULL) {
        // Leave forwarding off: the callers fall back to logcat.
        (*env)->ExceptionClear(env);
        return;
    }
    BridgeClass = (*env)->NewGlobalRef(env, bridgeClass);
    BridgeClLogMessageMethod = method;
}

bool BorealisLogForward(const char* format, va_list args) {
    if (BridgeClass == NULL || BridgeClLogMessageMethod == NULL) {
        return false;
    }

    // The caller still needs args for its own fallback, so work on a copy.
    char buffer[LOG_BUFFER_SIZE];
    va_list copy;
    va_copy(copy, args);
    int length = vsnprintf(buffer, sizeof(buffer), format, copy);
    va_end(copy);
    if (length < 0) {
        return false;
    }
    if (length >= (int)sizeof(buffer)) {
        length = (int)sizeof(buffer) - 1;
    }

    JNIEnv* env = GetThreadEnv();
    if (env == NULL || (*env)->ExceptionCheck(env)) {
        return false;
    }

    // A byte array, not NewStringUTF: the text is not guaranteed to be valid modified UTF-8.
    jbyteArray bytes = (*env)->NewByteArray(env, length);
    if (bytes == NULL) {
        (*env)->ExceptionClear(env);
        return false;
    }
    (*env)->SetByteArrayRegion(env, bytes, 0, length, (const jbyte*)buffer);

    jboolean handled = (*env)->CallStaticBooleanMethod(env, BridgeClass, BridgeClLogMessageMethod, bytes);
    bool failed = (*env)->ExceptionCheck(env);
    if (failed) {
        (*env)->ExceptionClear(env);
    }
    (*env)->DeleteLocalRef(env, bytes);

    return !failed && handled == JNI_TRUE;
}

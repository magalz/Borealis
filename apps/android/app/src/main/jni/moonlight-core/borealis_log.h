// BOREALIS: forwards the moonlight-common-c log lines to BorealisLog (ADR 0050, item 4).
#ifndef BOREALIS_LOG_H
#define BOREALIS_LOG_H

#include <jni.h>

#include <stdarg.h>
#include <stdbool.h>

// Defined in callbacks.c.
JNIEnv* GetThreadEnv(void);

// Looks up MoonBridge.bridgeClLogMessage. Call once from MoonBridge_init, before any callback is
// registered; until then BorealisLogForward returns false.
void BorealisLogInit(JNIEnv* env, jclass bridgeClass);

// Formats the line (truncated to a fixed buffer) and hands it to Kotlin as UTF-8 bytes. Leaves
// args usable by the caller. Returns false when the line was not handled, so the caller falls
// back to logcat; never blocks on a lock of its own and never throws.
bool BorealisLogForward(const char* format, va_list args) __attribute__((format(printf, 1, 0)));

#endif

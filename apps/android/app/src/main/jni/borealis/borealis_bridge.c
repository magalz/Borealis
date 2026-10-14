#include "borealis/core.h"

#include <jni.h>

/* JNI binding for io.github.magalz.borealis.NativeBridge. */
JNIEXPORT jstring JNICALL Java_io_github_magalz_borealis_NativeBridge_nativeCoreVersion(
  JNIEnv *env,
  jclass clazz
) {
  (void) clazz;
  return (*env)->NewStringUTF(env, borealis_core_version());
}

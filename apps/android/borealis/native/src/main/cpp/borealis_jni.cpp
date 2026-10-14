#include "borealis/core.h"

#include <jni.h>

// JNI binding for io.github.magalz.borealis.nativecore.CoreJni. Only plain C types from
// borealis/core.h are used; the version string is static and is never freed.
extern "C" JNIEXPORT jstring JNICALL
  Java_io_github_magalz_borealis_nativecore_CoreJni_version(JNIEnv *env, jclass /*clazz*/) {
  return env->NewStringUTF(borealis_core_version());
}

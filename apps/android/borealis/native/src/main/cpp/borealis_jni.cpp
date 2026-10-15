#include "borealis/core.h"
#include "borealis/host.h"

#include <array>
#include <cstddef>
#include <cstdint>
#include <jni.h>

// JNI binding for io.github.magalz.borealis.nativecore.CoreJni. Only primitives and byte[] cross the
// boundary; the host rules answer with an int[] whose first element is the BOREALIS_* status. The
// layouts are documented in HostMarshalling.kt and must stay in sync with it.
//
// Nothing here throws: inputs are copied into bounded stack buffers after their length has been
// checked, and a pending Java exception is cleared before null is returned.

namespace {

  constexpr jsize kCapsResultSize = 8;  // status, family, version[4], codec_flags, pyrowave_profiles
  constexpr jsize kDecisionResultSize = 5;  // status, action, delay_ms, category, max_attempts
  constexpr jsize kPairResultSize = 3;  // status, action, category

  enum class ReadResult {
    Ok,
    TooLarge,
    Failed
  };

  // A nullable Java byte[] copied into a stack buffer of at most Capacity bytes.
  template<std::size_t Capacity>
  class ByteInput {
  public:
    ReadResult read(JNIEnv *env, jbyteArray array) {
      present_ = array != nullptr;
      length_ = 0;
      if (!present_) {
        return ReadResult::Ok;
      }
      const jsize size = env->GetArrayLength(array);
      if (env->ExceptionCheck() == JNI_TRUE || size < 0) {
        return ReadResult::Failed;
      }
      if (static_cast<std::size_t>(size) > Capacity) {
        return ReadResult::TooLarge;
      }
      if (size > 0) {
        env->GetByteArrayRegion(array, 0, size, reinterpret_cast<jbyte *>(buffer_.data()));
        if (env->ExceptionCheck() == JNI_TRUE) {
          return ReadResult::Failed;
        }
      }
      length_ = static_cast<std::size_t>(size);
      return ReadResult::Ok;
    }

    // NULL for an absent array: the C ABI accepts NULL only together with length 0.
    [[nodiscard]] const char *data() const {
      return present_ ? buffer_.data() : nullptr;
    }

    [[nodiscard]] std::size_t length() const {
      return length_;
    }

  private:
    std::array<char, Capacity> buffer_ {};
    std::size_t length_ = 0;
    bool present_ = false;
  };

  // Clears whatever exception a JNI call left pending: nothing may reach Java from here.
  jintArray clearPending(JNIEnv *env) {
    if (env->ExceptionCheck() == JNI_TRUE) {
      env->ExceptionClear();
    }
    return nullptr;
  }

  // Copies `values` into a new int[]; null (exception cleared) when it cannot be allocated.
  jintArray makeResult(JNIEnv *env, const jint *values, jsize size) {
    jintArray result = env->NewIntArray(size);
    if (result == nullptr) {
      return clearPending(env);
    }
    env->SetIntArrayRegion(result, 0, size, values);
    if (env->ExceptionCheck() == JNI_TRUE) {
      return clearPending(env);
    }
    return result;
  }

  // The result for an input that could not be read: an error status for an over-long array, null
  // when the JNI itself failed.
  jintArray rejectedInput(JNIEnv *env, ReadResult why, jsize size) {
    if (why != ReadResult::TooLarge) {
      return clearPending(env);
    }
    const std::array<jint, kCapsResultSize> values {BOREALIS_ERR_INPUT_TOO_LARGE};
    return makeResult(env, values.data(), size);
  }

}  // namespace

extern "C" JNIEXPORT jstring JNICALL
  Java_io_github_magalz_borealis_nativecore_CoreJni_version(JNIEnv *env, jclass /*clazz*/) {
  return env->NewStringUTF(borealis_core_version());
}

extern "C" JNIEXPORT jintArray JNICALL Java_io_github_magalz_borealis_nativecore_CoreJni_parseCaps(
  JNIEnv *env,
  jclass /*clazz*/,
  jbyteArray appVersion,
  jint codecMask
) {
  ByteInput<BOREALIS_HOST_APPVERSION_MAX_LEN> input;
  const ReadResult read = input.read(env, appVersion);
  if (read != ReadResult::Ok) {
    return rejectedInput(env, read, kCapsResultSize);
  }

  borealis_host_caps caps {};
  const int status =
    borealis_host_parse_caps(input.data(), input.length(), static_cast<uint32_t>(codecMask), &caps);
  const std::array<jint, kCapsResultSize> values {
    status,
    caps.family,
    caps.version[0],
    caps.version[1],
    caps.version[2],
    caps.version[3],
    static_cast<jint>(caps.codec_flags),
    static_cast<jint>(caps.pyrowave_profiles),
  };
  return makeResult(env, values.data(), kCapsResultSize);
}

extern "C" JNIEXPORT jintArray JNICALL
  Java_io_github_magalz_borealis_nativecore_CoreJni_decideLaunch(
    JNIEnv *env,
    jclass /*clazz*/,
    jint verb,
    jint status,
    jbyteArray message,
    jint attempts
  ) {
  ByteInput<BOREALIS_HOST_MESSAGE_MAX_LEN> input;
  const ReadResult read = input.read(env, message);
  if (read != ReadResult::Ok) {
    return rejectedInput(env, read, kDecisionResultSize);
  }

  borealis_launch_decision decision {};
  const int result = borealis_launch_decide(
    verb,
    status,
    input.data(),
    input.length(),
    static_cast<uint32_t>(attempts),
    &decision
  );
  const std::array<jint, kDecisionResultSize> values {
    result,
    decision.action,
    decision.delay_ms,
    decision.category,
    decision.max_attempts,
  };
  return makeResult(env, values.data(), kDecisionResultSize);
}

extern "C" JNIEXPORT jintArray JNICALL
  Java_io_github_magalz_borealis_nativecore_CoreJni_classifyPairing(
    JNIEnv *env,
    jclass /*clazz*/,
    jint status,
    jbyteArray message
  ) {
  ByteInput<BOREALIS_HOST_MESSAGE_MAX_LEN> input;
  const ReadResult read = input.read(env, message);
  if (read != ReadResult::Ok) {
    return rejectedInput(env, read, kPairResultSize);
  }

  borealis_pair_decision decision {};
  const int result = borealis_pair_classify(status, input.data(), input.length(), &decision);
  const std::array<jint, kPairResultSize> values {result, decision.action, decision.category};
  return makeResult(env, values.data(), kPairResultSize);
}

extern "C" JNIEXPORT jint JNICALL Java_io_github_magalz_borealis_nativecore_CoreJni_launchFps(
  JNIEnv * /*env*/,
  jclass /*clazz*/,
  jboolean isGfe,
  jint launchFps,
  jint streamFps
) {
  return borealis_launch_fps(isGfe != JNI_FALSE ? 1 : 0, launchFps, streamFps);
}

/*
 * borealis-core public C ABI.
 *
 * Rules for everything declared here:
 *   - plain C types only (no exceptions, no STL types across the boundary);
 *   - every function is exported through BOREALIS_CORE_API;
 *   - memory ownership is documented per function.
 */
#ifndef BOREALIS_CORE_H
#define BOREALIS_CORE_H

#ifdef __cplusplus
extern "C" {
#endif

/*
 * BOREALIS_CORE_API marks the exported C ABI.
 *   - Windows shared build: dllexport while building the library, dllimport
 *     for consumers (BOREALIS_CORE_SHARED / BOREALIS_CORE_BUILDING are set by
 *     CMake).
 *   - Windows static build: empty.
 *   - clang/gcc elsewhere: default visibility (the library itself is compiled
 *     with -fvisibility=hidden).
 */
#if defined(_WIN32) || defined(__CYGWIN__)
  #ifdef BOREALIS_CORE_SHARED
    #ifdef BOREALIS_CORE_BUILDING
      #define BOREALIS_CORE_API __declspec(dllexport)
    #else
      #define BOREALIS_CORE_API __declspec(dllimport)
    #endif
  #else
    #define BOREALIS_CORE_API
  #endif
#elif defined(__GNUC__) || defined(__clang__)
  #define BOREALIS_CORE_API __attribute__((visibility("default")))
#else
  #define BOREALIS_CORE_API
#endif

  /*
   * Returns the borealis-core version as a SemVer core string "MAJOR.MINOR.PATCH".
   *
   * The returned pointer is never NULL, points to static storage, is
   * null-terminated, stays valid for the lifetime of the process and must not be
   * freed or modified by the caller. Thread-safe.
   */
  BOREALIS_CORE_API const char *borealis_core_version(void);

#ifdef __cplusplus
} /* extern "C" */
#endif

#endif /* BOREALIS_CORE_H */

---
status: accepted
date: 2026-10-13
decision-makers: João Pedro Magalhães
---

# 0010 Build the Android native core via Prefab

## Context and Problem Statement

AGP allows only one `externalNativeBuild` (CMake or ndk-build) per Gradle module. Upstream uses ndk-build, and `borealis-core` is built with CMake. How do the two native builds coexist without changing upstream files? Upstream 12.2 changed its `Android.mk` (nanors, libssl removal, opus directory rename), so every mirrored change costs effort.

## Considered Options

* Separate via Prefab: core in its own Gradle module built with CMake and published as a Prefab package; the app keeps the upstream ndk-build untouched and imports the package from a new `jni/borealis/Android.mk`
* All CMake (port moonlight-core's `Android.mk`)
* All ndk-build
* Separate without native linking (core `.so` talks to the app only through Java/JNI)
* Earlier round: Bazel

## Decision Outcome

Chosen option: "Separate via Prefab", because the upstream `jni/Android.mk` already includes all subdirectory makefiles, so the upstream build stays untouched and the core is consumed as a package.

### Consequences

* Good, because the upstream native build stays untouched.
* Bad, because there are two native build systems, and the Prefab setup is a one-time effort.

## Pros and Cons of the Options

### Separate via Prefab

* Good, because the upstream native build stays untouched.
* Good, because the core is consumed as a Prefab package.
* Bad, because there are two native build systems.
* Bad, because the Prefab setup is a one-time effort.

### All CMake (port moonlight-core's `Android.mk`)

* Good, because there is a single build and better IDE support.
* Good, because PyroWave links directly into the video callbacks.
* Bad, because every upstream `Android.mk` change must be mirrored by hand (12.2 changed it: nanors, libssl removal, opus directory rename).

### All ndk-build

* Good, because it is the same as upstream.
* Bad, because there are two builds for the same core (`Android.mk` plus CMake for desktop).
* Bad, because it is legacy and makes the SPIR-V shader build harder.

### Separate without native linking

* Good, because no link setup is needed.
* Bad, because it adds an extra JNI hop and copies per frame for PyroWave, which adds latency.

### Bazel (earlier round)

* Good, because it is hermetic.
* Bad, because it is disproportionate and has poor Android Studio integration.

## More Information

See [0008](0008-shared-core-language-and-scope.md), [0009](0009-organized-monorepo-layout.md) and [0043](0043-thin-c-api-with-hand-written-jni.md).

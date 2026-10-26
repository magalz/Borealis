---
status: accepted
date: 2026-10-26
decision-makers: João Pedro Magalhães
---

# 0060 Convert MoonBridge to a Kotlin object

## Context and Problem Statement

[ADR 0059](0059-convert-the-inherited-java-to-kotlin.md) rule 1.3 says that `com.limelight.nvstream.jni.MoonBridge`
stays Java. The roadmap's Sprint 14 plan converts it last. `MoonBridge.java` is 431 lines. It declares 33 native
methods, each exported by a C function named `Java_com_limelight_nvstream_jni_MoonBridge_*`, and 22 static callbacks
that the C code looks up: 21 in `callbacks.c` and 1 in `borealis_log.c`. Its static initializer loads `moonlight-core`
and calls the native `init()`. After Sprint 13 it is one of the seven Java files left, and it is the JNI bridge that
`Game` and the video renderer call. How should `MoonBridge` be converted, and when, so that the C code still finds
every native method and callback?

## Decision Drivers

* The C code finds each native method and each callback by name and JNI descriptor. These must not change.
* Behaviour does not change. All other rules of [ADR 0059](0059-convert-the-inherited-java-to-kotlin.md) still apply.
* The Robolectric test shadows that replace the static initializer of `MoonBridge` must keep working.
* One language for the code that ships, with the static analysis and coverage of Kotlin.
* `Game` and the video renderer are converted later in Sprint 14, and should be converted against the final shape of the bridge.

## Considered Options

* Convert `MoonBridge` first in Sprint 14, before `Game` and the video and audio code, as a Kotlin `object` (chosen).
* Convert `MoonBridge` last, as the roadmap first planned, after `Game`.
* Convert `MoonBridge` as a `class` with a `companion object`.
* Keep `MoonBridge` in Java, as rule 1.3 is written.
* Defer the conversion until after the release.

## Decision Outcome

Chosen option: "Convert `MoonBridge` first in Sprint 14, before `Game` and the video and audio code, as a Kotlin
`object`", because it gives the bridge the same language and analysis as the rest of the code, and `Game` and the
renderer are then converted against its final shape. This is the owner's decision of 2026-10-26.

The class keeps its name, its package `com.limelight.nvstream.jni` and every native method with its JNI signature.
Every static method that the C code calls back keeps its name and descriptor too. The C code finds the class with
`FindClass` and the callbacks with `GetStaticMethodID`, and the native methods are exported as
`Java_com_limelight_nvstream_jni_MoonBridge_*` functions. Members that are verified as unused are removed first, under
[ADR 0059](0059-convert-the-inherited-java-to-kotlin.md) rule 8: the native `getPendingVideoFrames` with its C wrapper,
and the constants that only the C code reads. Every other native method and every callback stays.

The shape is a Kotlin `object MoonBridge`. Every member is `@JvmStatic`, `@JvmField` or `const val`, and every native
method is a `@JvmStatic external fun`. This keeps each native and each callback a static method of the class itself,
with the same descriptor. Each annotation has a reason:

* the C code: the natives take `jclass`, and `init` passes the class to `GetStaticMethodID`;
* the Java callers that remain until they are converted (`Game.java`, `MediaCodecDecoderRenderer.java` and
  `AndroidAudioRenderer.java`): they call the static members directly, and the `COLORSPACE_*` constants are `case`
  labels, so they need `const val`;
* the Robolectric test shadows, which replace the static initializer of `MoonBridge`.

A `companion object` is not used. A call through `Companion`, or through `INSTANCE` for a member without these
annotations, would hit a null reference when a shadow replaces the static initializer.

Every reference parameter that the C code passes to a callback is nullable, because the C code can pass `NULL`. For
example, `callbacks.c` (lines 344 to 353) sets the HDR metadata of `bridgeClSetHdrMode` only when HDR is on and the
metadata is available. Without a nullable type, Kotlin would check the parameter and throw inside a native callback.

The lock that `NvConnection` takes stays the class monitor, `MoonBridge::class.java`. In an `object`,
`synchronized(MoonBridge)` would lock `INSTANCE`, which is a different monitor.

The conversion also removes the coverage exclusion of `com.limelight.nvstream.jni.*` (ADR 0059 rule 7), so the bridge
counts in Kover coverage.

The checks are:

* a JNI contract test that reads the C sources and checks by reflection that every exported native is a static native
  method, and that every looked-up callback is a static method with the right descriptor. It is the only check for
  `bridgeClLogMessage`, which `borealis_log.c` looks up leniently: if the method is missing, log forwarding turns off
  silently;
* `javap -p -s` before and after the conversion, because the `javap -public` of ADR 0059 rule 2.10 does not show
  descriptors;
* the Sprint 14 device round, with CheckJNI where the device allows it, and the rarely called natives exercised, because
  JNI links a native at its first call.

### Consequences

* Good, because the bridge is in Kotlin and gets the static analysis and coverage of Kotlin code.
* Good, because `Game` and the video renderer are converted against the final shape of the bridge.
* Good, because a wrong JNI name or descriptor is caught by a JNI contract test before a device run.
* Bad, because a JNI linkage fault still shows only at run time, on a device.
* Bad, because `javap` shows expected differences: `final` on the static methods, an `INSTANCE` field and a private constructor.
* Bad, because every member keeps its Java-facing annotation while `Game` and the renderer are still Java, which adds noise.

## Pros and Cons of the Options

### Convert `MoonBridge` first in Sprint 14, as a Kotlin `object`

* Good, because `Game` and the renderer are converted against the final bridge.
* Good, because the bridge gets the Kotlin analysis early in the sprint.
* Bad, because every member keeps its Java-facing annotation while `Game` and the renderer are still Java.

### Convert `MoonBridge` last, after `Game`

* Good, because `Game` is converted first, with the Java bridge.
* Bad, because `Game` and the renderer may need to be reopened if the nullability of the bridge changes.

### Convert `MoonBridge` as a `class` with a `companion object`

* Good, because it is a common Kotlin shape for static members.
* Bad, because calls go through `Companion`, which is null when a Robolectric shadow replaces the static initializer, so the tests that use these shadows fail.

### Keep `MoonBridge` in Java

* Good, because there is no JNI risk.
* Bad, because one Java file stays, with its coverage exclusion, and the goal of one language is not reached.

### Defer the conversion until after the release

* Good, because the release is not affected by the conversion.
* Bad, because the Java bridge stays in the shipped code, and the Sprint 14 plan is not met.

## More Information

* This ADR amends rule 1.3 of [ADR 0059](0059-convert-the-inherited-java-to-kotlin.md). Rule 1.3 no longer keeps
  `MoonBridge` in Java. The package, the class name and the JNI signatures stay the same.
* Related: [0054](0054-declare-independence-from-moonlight.md) (inherited code is Borealis code).
* Related: [roadmap, Sprint 14](../roadmap.md#sprint-14-kotlin-migration-video-audio-and-the-stream-activity).

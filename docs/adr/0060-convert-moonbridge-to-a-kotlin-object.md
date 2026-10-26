---
status: accepted
date: 2026-10-26
decision-makers: João Pedro Magalhães
---

# 0060 Convert MoonBridge to a Kotlin object

## Context and Problem Statement

[ADR 0059](0059-convert-the-inherited-java-to-kotlin.md) rule 1.3 says that `com.limelight.nvstream.jni.MoonBridge`
stays Java. The roadmap's Sprint 14 plan converts it last. After Sprint 13 it is one of the few Java files left, and
it is the JNI bridge that `Game` and the video renderer call. How should `MoonBridge` be converted, and when, so that
the C code still finds every native method and callback?

## Decision Drivers

* The C code finds each native method and each callback by name and JNI descriptor. These must not change.
* Behaviour does not change. [ADR 0059](0059-convert-the-inherited-java-to-kotlin.md) rules 4 to 10 still apply.
* The Robolectric test shadows that replace the static initializer of `MoonBridge` must keep working.
* One language for the code that ships, with the static analysis and coverage of Kotlin.
* `Game` and the video renderer are converted later in Sprint 14, and should be converted against the final shape of the bridge.

## Considered Options

* Convert `MoonBridge` in the first wave of Sprint 14, as a Kotlin `object` (chosen).
* Convert `MoonBridge` last, as the roadmap first planned, after `Game`.
* Convert `MoonBridge` as a `class` with a `companion object`.
* Keep `MoonBridge` in Java, as rule 1.3 is written.
* Defer the conversion until after the release.

## Decision Outcome

Chosen option: "Convert `MoonBridge` in the first wave of Sprint 14, as a Kotlin `object`", because it gives the bridge
the same language and analysis as the rest of the code, and `Game` and the renderer are then converted against its
final shape. This is the owner's decision of 2026-10-26.

The class keeps its name, its package `com.limelight.nvstream.jni` and every native method with its JNI signature.
Every static method that the C code calls back keeps its name and descriptor too. The C code finds the class with
`FindClass` and the callbacks with `GetStaticMethodID`, and the native methods are exported as
`Java_com_limelight_nvstream_jni_MoonBridge_*` functions.

The shape is a Kotlin `object MoonBridge`. Every member is `@JvmStatic`, `@JvmField` or `const val`, and every native
method is a `@JvmStatic external fun`. This keeps each native and each callback a static method of the class itself,
with the same descriptor. A `companion object` is not used: Robolectric test shadows replace the static initializer of
`MoonBridge`, and a call through `Companion`, or through `INSTANCE` for a member without these annotations, would then
hit a null reference.

### Consequences

* Good, because the bridge is in Kotlin and gets the static analysis and coverage of Kotlin code.
* Good, because `Game` and the video renderer are converted against the final shape of the bridge.
* Good, because a wrong JNI name or descriptor is caught by a JNI contract test before a device run.
* Bad, because a JNI linkage fault still shows only at run time, on a device.
* Bad, because `javap` shows expected differences: `final` on the static methods, an `INSTANCE` field and a private constructor.
* Bad, because every member carries an annotation. This is needed for the test shadows, but it adds noise.

## Pros and Cons of the Options

### Convert `MoonBridge` in the first wave of Sprint 14, as a Kotlin `object`

* Good, because `Game` and the renderer are converted against the final bridge.
* Good, because the bridge gets the Kotlin analysis early in the sprint.
* Bad, because the first wave carries the JNI risk before the device round.

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
* Related: [0043](0043-thin-c-api-with-hand-written-jni.md) (thin C API with hand-written JNI).
* Related: [0054](0054-declare-independence-from-moonlight.md) (inherited code is Borealis code).
* Related: [roadmap, Sprint 14](../roadmap.md#sprint-14-kotlin-migration-video-audio-and-the-stream-activity).

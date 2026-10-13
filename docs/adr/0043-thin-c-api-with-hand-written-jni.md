---
status: accepted
date: 2026-10-13
decision-makers: João Pedro Magalhães
---

# 0043 Use a thin C API with hand-written JNI

## Context and Problem Statement

`borealis-core` (see [0008](0008-shared-core-language-and-scope.md)) must be called from Kotlin. How should the bindings between the native core and Java or Kotlin be produced?

## Considered Options

* Thin C API + hand-written JNI
* djinni
* SWIG
* JavaCPP

## Decision Outcome

Chosen option: "Thin C API + hand-written JNI", because the interface is small, no generator is needed, and the boilerplate is controlled.

### Consequences

* Good, because the interface is small and no generator is needed.
* Good, because the boilerplate is controlled.
* Bad, because JNI code is written and maintained by hand.

## Pros and Cons of the Options

### Thin C API + hand-written JNI

* Good, because the interface is small.
* Good, because no generators are needed.
* Good, because the boilerplate is controlled.
* Bad, because JNI code is written by hand.

### djinni

* Good, because it generates Java and ObjC.
* Bad, because it is poorly maintained.

### SWIG

* Good, because it generates everything.
* Bad, because the generated code is ugly.

### JavaCPP

* Good, because it is powerful.
* Bad, because it is heavy.

## More Information

See [0008](0008-shared-core-language-and-scope.md) and [0010](0010-prefab-for-android-native-build.md).

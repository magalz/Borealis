---
status: accepted
date: 2026-10-13
decision-makers: João Pedro Magalhães
---

# 0008 Share a C++20 core (borealis-core) with protocol, decoder and host rules

## Context and Problem Statement

Two questions need an answer for the shared core: which scope should it cover, and which language should `borealis-core` use? The core is shared across platforms (see [0003](0003-native-apps-per-platform-with-shared-core.md)).

## Considered Options

Scope:

* Protocol + decoder + host rules
* Minimal: protocol + decoder only, with host rules as a spec and shared conformance fixtures
* Broad app core in C++ (REST client with HTTP + TLS, host store, settings)
* Earlier note: no shared core at all

Language for `borealis-core`:

* C++20
* Rust
* C11
* No `borealis-core` (platforms implement the logic themselves)

## Decision Outcome

Chosen option (scope): "Protocol + decoder + host rules", which includes a fork of moonlight-common-c, the PyroWave decoder, and `borealis-core` with pure deterministic LuminalShine logic (capability bits and serverinfo parsing, launch retry policy, display-mode rules, host contract constants). HTTP transport and UI stay per platform.

Chosen option (language): "C++20", because it is the same language as PyroWave and LuminalShine (C++23), so host headers such as `luminalshine/src/pyrowave/contract.h` can be reused. It also means one fewer toolchain, smaller binaries, and host contributions in the same language.

Note: common-c (C) and PyroWave (C++20) stay C/C++ regardless, to keep merging with their upstreams.

### Consequences

* Good, because host headers can be reused, and host contributions use the same language.
* Good, because there is one fewer toolchain and smaller binaries.
* Bad, because C++ has no memory safety; this is mitigated with ASan/UBSan, libFuzzer on parsers and clang-tidy.
* Bad, because the JNI surface adds some friction (see [0043](0043-thin-c-api-with-hand-written-jni.md)).

## Pros and Cons of the Options

### Scope: Protocol + decoder + host rules

* Good, because pure deterministic host rules are implemented once.
* Bad, because the small JNI surface adds some friction.

### Scope: Minimal (protocol + decoder only, host rules as spec)

* Good, because each platform stays idiomatic.
* Bad, because host rules are duplicated and subtle divergence can occur.

### Scope: Broad app core in C++ (REST client, host store, settings)

* Good, because it maximizes sharing.
* Bad, because HTTP and TLS move to C++ instead of the native stacks.
* Bad, because heavy bindings are needed for UI data.

### Scope: No shared core

* Good, because each platform is idiomatic.
* Bad, because host rules are duplicated across platforms.

### Language: C++20

* Good, because it matches PyroWave and LuminalShine (C++23), so host headers can be reused.
* Good, because it needs one fewer toolchain and produces smaller binaries.
* Good, because host contributions are in the same language.
* Bad, because it has no memory safety; mitigated with ASan/UBSan, libFuzzer on parsers and clang-tidy.

### Language: Rust

* Good, because it gives memory safety for network parsing.
* Good, because UniFFI generates Kotlin and Swift bindings, and cargo tooling is available.
* Bad, because it adds a third native language.
* Bad, because it needs cargo-ndk, corrosion and iOS targets.
* Bad, because host C++ headers cannot be reused.
* Bad, because the binary is slightly larger.

### Language: C11

* Good, because it has the simplest ABI and matches the style of common-c.
* Bad, because it is verbose and has no RAII.
* Bad, because PyroWave needs C++20 anyway.

### No `borealis-core`

* Good, because each platform is idiomatic.
* Bad, because host rules are duplicated.

## More Information

See [0003](0003-native-apps-per-platform-with-shared-core.md), [0010](0010-prefab-for-android-native-build.md), [0016](0016-testing-strategy-and-tdd.md) and [0043](0043-thin-c-api-with-hand-written-jni.md).

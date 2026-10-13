---
status: accepted
date: 2026-10-13
decision-makers: João Pedro Magalhães
---

# 0003 Native apps per platform with a shared C/C++ core

## Context and Problem Statement

Borealis targets phones and tablets, Android TV, Google TV, Fire TV and handhelds (Retroid, AYN). Desktop and iOS may come later. How should the code be split across platforms: native per platform, shared UI through a multiplatform framework, or a single language everywhere?

## Considered Options

* Native per platform with a shared C/C++ core
* Kotlin "KMP-ready": Android only now, with pure-Kotlin domain/data modules convertible to KMP later
* KMP with Compose Multiplatform from day one
* Java only

## Decision Outcome

Chosen option: "Native per platform with a shared C/C++ core", because each platform starts from the most mature Moonlight base (desktop: a fork of Aurora/moonlight-qt, which already decodes PyroWave; iOS: TBD), and the hardest parts (protocol and decoder) are shared. UI and app logic are written per platform.

### Consequences

* Good, because each platform builds on the most mature Moonlight base.
* Good, because protocol and decoder are shared across platforms.
* Bad, because UX consistency across platforms is harder to keep.
* Bad, because shared host logic must live in C++.

## Pros and Cons of the Options

### Native per platform with a shared C/C++ core

* Good, because each platform starts from the most mature Moonlight base (desktop: fork of Aurora/moonlight-qt, which already decodes PyroWave).
* Good, because the hardest parts (protocol and decoder) are shared.
* Bad, because UX consistency is harder to keep across platforms.
* Bad, because shared host logic must be written in C++.

### Kotlin "KMP-ready" (Android only now)

* Good, because pure-Kotlin domain and data modules can be converted to KMP later.
* Bad, because it requires discipline to keep modules pure and some rework at migration time.

### KMP with Compose Multiplatform from day one

* Good, because it guarantees code sharing.
* Good, because Compose Multiplatform has been stable on iOS since 1.8 (May 2025).
* Bad, because the build complexity is much higher and the multiplatform cost is paid before iOS or desktop are confirmed.
* Bad, because desktop via KMP would still need to rewrite video decoding.

### Java only

* Good, because it is one language, identical to upstream.
* Bad, because it has no Compose and no sharing path.

## More Information

See [0004](0004-kotlin-for-new-android-code.md), [0008](0008-shared-core-language-and-scope.md) and [0009](0009-organized-monorepo-layout.md).

---
status: accepted
date: 2026-10-13
decision-makers: João Pedro Magalhães
---

# 0009 Use an organized monorepo

## Context and Problem Statement

Borealis has several parts: an Android app (a fork of moonlight-android), a future desktop app, a shared core, design tokens and documentation. How should the repository be laid out?

## Considered Options

* Organized monorepo: `apps/android` (git subtree of moonlight-android, history preserved), `apps/desktop` (future), `core/`, `external/` (third-party fork submodules when needed), `design/`, `docs/`, `tools/`
* Android fork at the repository root with own folders beside it
* Umbrella repository with submodules per app fork
* Pure multi-repo

## Decision Outcome

Chosen option: "Organized monorepo", because third-party code is isolated from own code, cross-cutting changes need one PR, and CI can be path-filtered. Upstream sync is done via `git subtree pull` (less common).

Detail: the moonlight-common-c submodule stays at its upstream path (`apps/android/app/src/main/jni/moonlight-core/moonlight-common-c`), registered in the root `.gitmodules`, so upstream files do not change. Its URL moves to a Borealis fork at the first native change.

### Consequences

* Good, because third-party code is isolated from own code.
* Good, because cross-cutting changes need one PR, and CI can be path-filtered.
* Bad, because upstream sync uses `git subtree pull`, which is less common.
* Bad, because the repository is larger.

## Pros and Cons of the Options

### Organized monorepo

* Good, because third-party code is isolated from own code.
* Good, because one PR covers cross-cutting changes.
* Good, because path-filtered CI is possible.
* Bad, because upstream sync via `git subtree pull` is less common.
* Bad, because the repository is larger.

### Android fork at the repository root with own folders beside it

* Good, because upstream merges are plain `git merge`.
* Bad, because the root looks like an Android project mixed with core and docs.
* Bad, because the core must be extracted when desktop arrives.

### Umbrella repository with submodules per app fork

* Good, because fork histories stay clean.
* Bad, because the app depends on a relative path to the core.
* Bad, because cross-repo PRs and more sync work are needed.

### Pure multi-repo

* Good, because it is Moonlight's own model.
* Bad, because nothing is centralized, which is the opposite of the goal.

## More Information

See [0003](0003-native-apps-per-platform-with-shared-core.md), [0008](0008-shared-core-language-and-scope.md) and [0010](0010-prefab-for-android-native-build.md).

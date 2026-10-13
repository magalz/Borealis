---
status: accepted
date: 2026-10-13
decision-makers: João Pedro Magalhães
---

# 0014 Version each app with SemVer and prefixed tags

## Context and Problem Statement

Borealis will have several apps (Android now, desktop later). How should versions be numbered, and what should the About screen and logs show?

## Considered Options

* SemVer per app with prefixed tags (`android-v0.1.0`, later `desktop-v...`)
* Single SemVer for the monorepo
* CalVer
* Coupled to upstream (for example `12.2-borealis.3`)

## Decision Outcome

Chosen option: "SemVer per app with prefixed tags", because the apps evolve independently and the version communicates the impact of a change. `versionCode` is derived and monotonic. The About screen and logs show the Moonlight base and the common-c and core SHAs (see [0020](0020-logging.md)).

### Consequences

* Good, because apps evolve independently and version numbers communicate impact.
* Good, because the About screen and logs show the upstream base and component SHAs.
* Bad, because it requires discipline about breaking changes.

## Pros and Cons of the Options

### SemVer per app with prefixed tags

* Good, because apps evolve independently.
* Good, because the version communicates impact.
* Bad, because it requires discipline about breaking changes.

### Single SemVer for the monorepo

* Good, because it is simple.
* Bad, because platforms without changes skip versions.

### CalVer

* Good, because it shows the age of a release.
* Bad, because it does not communicate impact.

### Coupled to upstream (for example `12.2-borealis.3`)

* Good, because it makes the base explicit.
* Bad, because it ties the identity to Moonlight, which is confusing as Borealis diverges.

## More Information

See [0002](0002-base-the-app-on-vanilla-moonlight-12-2.md), [0013](0013-github-flow-with-merge-commits.md) and [0039](0039-changelog-with-git-cliff.md).

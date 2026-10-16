---
status: "superseded by ADR-0054"
date: 2026-10-14
decision-makers: João Pedro Magalhães
---

# 0048 Fork moonlight-common-c with a first-frame deadline patch

> Status note (2026-10-15): superseded by [0054](0054-declare-independence-from-moonlight.md). moonlight-common-c is vendored at `core/third_party/moonlight-common-c/`, and the fork tag rule ends. The fork repository stays as an archive.

## Context and Problem Statement

LuminalShine can take about 7 to 18 seconds to send the first video frame on NVENC. It sends a 1-byte "runt" UDP datagram about 8 seconds after the client attaches. Upstream moonlight-common-c at commit `874ac95` uses `FIRST_FRAME_TIMEOUT_SEC 10` (`src/VideoStream.c:4`) for two timers: the "no video traffic" timer (`ML_ERROR_NO_VIDEO_TRAFFIC`, -100) and the "no complete frame" timer (`ML_ERROR_NO_VIDEO_FRAME`, -101). The runt starts the full-frame clock, so the effective deadline is about 18 seconds after attach. The full-frame deadline is only evaluated when a datagram arrives, so a runt followed by silence leaves the client on a black screen forever. Java cannot change these values. How should Borealis change the first-frame behaviour in moonlight-common-c?

## Decision Drivers

* A black screen with no error must not last forever
* Blocked UDP ports should still be reported quickly
* Hosts that need up to about 18 seconds must not fail with -101
* The divergence from upstream must stay small, so rebases remain easy

## Considered Options

* Fork with patch B and a Java watchdog
* Fork with patch A: change the single constant to 30 seconds
* No fork: watchdog and message only
* Make the deadline configurable through `STREAM_CONFIGURATION`

## Decision Outcome

Chosen option: "Fork with patch B and a Java watchdog", because it keeps the 10-second no-traffic timer, so runts still count as traffic and blocked ports are reported quickly. It adds a separate `FIRST_FULL_FRAME_TIMEOUT_SEC 25` deadline, measured from the first datagram of any size, and it evaluates that deadline also when no datagram arrives. A 45-second Java watchdog covers the case where frames arrive but the decoder never produces output. This closes the black-screen case and covers slow NVENC starts without hiding blocked ports. The worst case with patch B is about 33 seconds, which is below the 45-second watchdog.

Decision details:

* The public fork is `magalz/moonlight-common-c`. Its branch `borealis` is based on the upstream commit used by Borealis.
* The new check is evaluated in the `err == 0` branch, guarded by `#ifndef LC_FUZZING`.
* Every Borealis change is marked with a `BOREALIS:` comment.
* Every commit referenced by the Borealis submodule is tagged `borealis/<upstream-sha7>-<n>`. Tagged commits are never rewritten or force-pushed.
* The fork commit is pushed before any Borealis commit references it.
* The root `.gitmodules` points the existing submodule path at the fork.
* When moonlight-android moves its submodule pointer, the Borealis patches are rebased onto the new upstream commit and a new tag is created.
* The patch is offered upstream.
* No C test harness exists. The patch is therefore exempt from test-first and is verified by build, review and device logs (`Received first video packet after N ms`).

### Consequences

* Good, because a black screen with no error no longer lasts forever
* Good, because blocked ports are still reported after the 10-second no-traffic timer
* Good, because the fork is needed anyway for PyroWave in Phase 4
* Bad, because Borealis maintains a fork and must rebase the patches and create a new tag on every upstream bump
* Bad, because the patch has no automated test, so it relies on build, review and device logs
* Neutral, because the SBOM native entry and NOTICE will reference the fork, and the upstream-sync procedure gains a fork section

This ADR complements [0047](0047-harvest-upstream-then-emancipate.md).

## Pros and Cons of the Options

### Fork with patch B and a Java watchdog

* Good, because the first-frame deadline is evaluated also when no datagram arrives
* Good, because the 10-second no-traffic timer still reports blocked ports quickly
* Good, because the worst case of about 33 seconds stays below the 45-second watchdog
* Bad, because Borealis owns the fork and its rebases
* Bad, because the patch has no C test harness

### Fork with patch A

* Good, because it is a one-line patch
* Bad, because a blocked UDP port is only reported after 30 seconds
* Bad, because the change affects both timers

### No fork: watchdog and message only

* Good, because there is no fork to maintain now
* Bad, because hosts slower than about 18 seconds keep failing with -101
* Bad, because an automatic retry costs another NVENC start

### Configurable through STREAM_CONFIGURATION

* Good, because the deadline can be set per host
* Bad, because it changes the struct, the JNI signature and the callers
* Bad, because it creates a larger divergence from upstream and makes rebases harder

## More Information

* Related: [0043](0043-thin-c-api-with-hand-written-jni.md) for the thin C API and the hand-written JNI layer that this change touches.
* Related: [0042](0042-sbom-with-cyclonedx.md) for the SBOM that references the native components.
* Related: [0047](0047-harvest-upstream-then-emancipate.md) for the upstream strategy, and [0049](0049-coverage-gate.md) for the coverage gate.
* Policy: [upstream sync](../policies/upstream-sync.md).

---
status: accepted
date: 2026-10-15
decision-makers: João Pedro Magalhães
---

# 0054 Declare independence from Moonlight

## Context and Problem Statement

[ADR 0047](0047-harvest-upstream-then-emancipate.md) keeps the upstream sync and the minimal `// BOREALIS:` hooks until an independence milestone: the end of Phase 2, or an earlier owner decision. Sprint 5 puts Compose UI inside the stream activity (`com.limelight.Game`): the starting-stream, conflict and error screens, the in-game menu, the Borealis keyboard and the overlay. That needs many edits to inherited Java in `Game`, `ControllerHandler`, `NvConnection`, `NvHTTP`, `MediaCodecDecoderRenderer`, `MoonBridge` and `simplejni.c`. Under the hook rule these edits would need a large exception. Should Borealis declare independence now, and how should it take the moonlight-common-c code that it still shares with other projects?

## Decision Drivers

* Sprint 5 must change the stream activity, the input path and the video path directly
* Upstream moonlight-android has not moved since 12.2, so little is left to harvest
* One rule set is easier to follow than two
* Protocol fixes in moonlight-common-c still have to reach Borealis
* GPL-3.0 attribution must stay in NOTICE

## Considered Options

For the Android app:

* Declare independence now
* An approved hook exception of about 30 lines, independence stays in Sprint 6
* Strict hooks of 5 lines or fewer per file, with the menu incomplete
* Rewrite the stream activity in Kotlin

For moonlight-common-c:

* Vendor it into `core/third_party/`
* Keep the fork and its sync
* Keep the fork, cherry-pick only
* Decide in Sprint 6

## Decision Outcome

Chosen option for the app: "Declare independence now", because it lets Sprint 5 change `Game` and the input and video paths directly, and it ends hook accounting for the next sprints. The cost is that easy subtree syncs stop earlier than planned. That cost is small, because upstream has not moved since 12.2.

Chosen option for moonlight-common-c: "Vendor it into `core/third_party/`", because the owner wants one repository and Borealis code, with no submodule pointer. The cost is that the fork history and the easy sync are lost, and the vendoring changes the native build in this sprint.

This ADR refines [0047](0047-harvest-upstream-then-emancipate.md) and supersedes [0048](0048-fork-moonlight-common-c.md). The milestone of 0047 is reached early, by owner decision.

### Inherited code

* Code under `apps/android/` is a subtree of moonlight-android 12.2. It is Borealis code now. It may be changed, refactored and migrated to Kotlin when a task needs it.
* Rules that stay in force:
  * new code is Kotlin, and no new Java files are added ([0004](0004-kotlin-for-new-android-code.md));
  * edits to existing Java are kept to wiring, and new logic goes to Kotlin;
  * inherited files are not reformatted wholesale, so that diffs stay reviewable;
  * the `com.limelight` namespace and `com.limelight.nvstream.jni.MoonBridge` stay, because the JNI symbols depend on them (R-002);
  * existing `// BOREALIS:` markers stay as history. New edits do not need them.
* Rule R-001 and the upstream-sync policy are historical.

### moonlight-common-c

* moonlight-common-c, with enet and nanors, is vendored at `core/third_party/moonlight-common-c/`. Its README in `core/third_party/` records the source commits, the licences, the Borealis patch `591bddf` (first-frame wait in `src/VideoStream.c`), and the upstream commits that were and were not harvested.
* The submodule ends, and so does the fork tag rule of [0048](0048-fork-moonlight-common-c.md). The fork repository stays as an archive.

### Upstream fixes

Upstream fixes arrive only by cherry-pick:

* the `moonlight-android` remote stays configured;
* for moonlight-common-c, use `git format-patch` and `git am --directory=core/third_party/moonlight-common-c`.

The procedure is in [upstream cherry-picks](../development/upstream-sync.md). Attribution stays in [NOTICE](../../NOTICE): GPL-3.0 for the derived code, and the app-store exception note for moonlight-common-c.

### Consequences

* Good, because Sprint 5 can change `Game` and the input and video paths directly
* Good, because there is no submodule in clones or CI
* Good, because there is one rule set
* Bad, because a future Moonlight release must be cherry-picked by hand
* Bad, because protocol fixes in moonlight-common-c also need manual cherry-picks
* Bad, because the vendoring changes the native build in this sprint
* Neutral, because the 0.1.0 release stays at the end of Phase 2

## Pros and Cons of the Options

### Declare independence now

* Good, because Sprint 5 can change `Game` and the input and video paths directly
* Good, because hook accounting ends now
* Good, because upstream has not moved since 12.2, so the cost of losing the subtree sync is small
* Bad, because a future Moonlight release becomes manual cherry-pick work
* Bad, because the subtree sync is lost earlier than planned

### An approved hook exception of about 30 lines, independence stays in Sprint 6

* Good, because the subtree sync keeps working
* Good, because the stream activity changes can go ahead under the exception
* Bad, because the exception adds conflict points at every sync
* Bad, because two rule sets apply for another sprint

### Strict hooks of 5 lines or fewer per file, with the menu incomplete

* Good, because the hook rule stays unchanged
* Good, because the subtree sync keeps working
* Bad, because the in-game menu and overlay stay incomplete
* Bad, because the Java files still need to be wired around the hooks

### Rewrite the stream activity in Kotlin

* Good, because the stream activity ends up as Kotlin code
* Bad, because it is too large and risky for one sprint

### Vendor moonlight-common-c into `core/third_party/`

* Good, because there is one repository and no submodule pointer
* Good, because the code is Borealis code, with one rule set
* Bad, because the fork history is lost
* Bad, because the easy sync is lost, and the vendoring changes the native build in this sprint

### Keep the fork and its sync

* Good, because the fork history and the easy sync are kept
* Good, because the protocol is shared with Sunshine and LuminalShine, so the upstream history stays useful
* Bad, because the submodule and the fork tag rule stay in every clone and CI run

### Keep the fork, cherry-pick only

* Good, because the fork history is kept
* Bad, because the submodule and the fork tag rule stay in place
* Bad, because the fork is still a second repository to keep in step

### Decide in Sprint 6

* Good, because the decision waits for more information
* Bad, because the native build question stays open through Sprint 5

## More Information

* Refines [0047](0047-harvest-upstream-then-emancipate.md), which defined the milestone.
* Supersedes [0048](0048-fork-moonlight-common-c.md), the moonlight-common-c fork.
* Relates to [0002](0002-base-the-app-on-vanilla-moonlight-12-2.md), [0004](0004-kotlin-for-new-android-code.md) and [0009](0009-organized-monorepo-layout.md).
* Rule R-001 is in [rules](../policies/rules/R-001-no-upstream-reformat.md); R-002 stays in force.
* Procedure: [upstream cherry-picks](../development/upstream-sync.md). Policy: [upstream sync](../policies/upstream-sync.md).
* Vendored code and its README: [core/third_party/README.md](../../core/third_party/README.md) (added by the parallel vendoring task, S5-T1; TODO(verify) that it exists when this ADR is merged).

# Upstream sync policy

Borealis is independent from Moonlight since 2026-10-15
([ADR 0054](../adr/0054-declare-independence-from-moonlight.md)). This policy
replaces the subtree sync policy of
[ADR 0047](../adr/0047-harvest-upstream-then-emancipate.md). The procedure is in
[../development/upstream-sync.md](../development/upstream-sync.md).

## Principles

1. **Cherry-pick only.** Upstream fixes arrive by `git cherry-pick
   -Xsubtree=apps/android` or a manual port for moonlight-android, and by
   `git format-patch` and `git am` for moonlight-common-c. There are no subtree
   pulls and no submodule pointers.
2. **Inherited code is Borealis code.** It may be changed, refactored and
   migrated to Kotlin when a task needs it.
3. **The owner decides.** The owner chooses which upstream changes to take and
   merges every harvest PR. AI agents never merge.
4. **One PR per harvest.** Do not mix a harvest with feature work.
5. **Offer fixes upstream** where they belong.
6. **Keep the stated rules.** New code is Kotlin and no new Java files are
   added ([ADR 0004](../adr/0004-kotlin-for-new-android-code.md)). The
   `com.limelight` namespace and the `MoonBridge` JNI class stay, because the
   JNI symbols depend on them (R-002).

R-001 and the subtree sync rules are historical since ADR 0054.

## Attribution

Upstream is GPL-3.0. [NOTICE](../../NOTICE) keeps the attribution for
Moonlight, moonlight-common-c and the other reused components. It also carries
the app-store exception note for moonlight-common-c. A harvest that changes a
component's source or licence updates NOTICE in the same PR.

## Checks for a harvest PR

* Full CI is green.
* Each harvested change is traceable to an upstream commit in the PR
  description.
* Tests for touched behaviour pass.
* Dependency and licence changes pass the
  [dependency policy](dependencies.md) and `licensee`.

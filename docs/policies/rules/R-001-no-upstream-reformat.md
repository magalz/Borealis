# R-001 Never reformat upstream files

> Status: historical since [ADR 0054](../../adr/0054-declare-independence-from-moonlight.md) (2026-10-15). Inherited code is Borealis code and may be changed and refactored when a task needs it, but it is not reformatted wholesale, so that diffs stay reviewable. Existing `// BOREALIS:` markers stay as history; new edits do not need them. Upstream fixes arrive by cherry-pick. The text below is kept as the record of the rule in force until then.

`apps/android/` is a git subtree of moonlight-android. Outside the Borealis-owned paths (`apps/android/borealis/`, `apps/android/build-logic/`, `apps/android/app/src/main/kotlin/`, `apps/android/app/src/main/jni/borealis/`, `apps/android/gradle/libs.versions.toml`, `apps/android/config/`), files are upstream code.

- Never reformat, reorder or restyle upstream files.
- Keep every edit to an upstream file minimal and mark it with a `// BOREALIS:` comment (`<!-- BOREALIS: -->` in XML, `# BOREALIS:` in properties files).
- Prefer adding behaviour from Borealis-owned code (convention plugins, Kotlin modules, new JNI subdirectories) over editing upstream files.

Why: upstream syncs (`docs/development/upstream-sync.md`) must stay mechanical; reformatting turns every sync into a conflict. See [ADR 0002](../../adr/0002-base-the-app-on-vanilla-moonlight-12-2.md) and [ADR 0009](../../adr/0009-organized-monorepo-layout.md).

# Development guide

Start with [CONTRIBUTING.md](../../CONTRIBUTING.md) for the workflow and the
Definition of Done. These pages hold the details.

| Page | Contents |
| --- | --- |
| [building.md](building.md) | Toolchain setup and build, check and test commands (Windows first, Linux notes for CI). |
| [tdd.md](tdd.md) | Red-green-refactor, commit order, characterization tests, what counts as a behaviour test. |
| [upstream-sync.md](upstream-sync.md) | Upstream cherry-picks: procedure for moonlight-android and moonlight-common-c fixes (ADR 0054). |
| [release.md](release.md) | Step-by-step procedure for cutting a release. |
| [device-validation.md](device-validation.md) | Phase 1 and Sprints 2 to 15 runbook for the checks on a Galaxy S22 and a Google TV Streamer against LuminalShine. Sections 6 to 20 record the device rounds. |
| [orchestration.md](orchestration.md) | How AI agents are orchestrated: roles, worktrees, hand-offs. |
| [task-brief.md](task-brief.md) | Template for briefing an implementer on one task. |
| [decoder-errata.md](decoder-errata.md) | Known MediaCodec hardware decoder quirks, inherited from Moonlight. |

Policies behind these procedures are in [../policies/](../policies/):
[dependencies](../policies/dependencies.md),
[release](../policies/release.md),
[upstream sync](../policies/upstream-sync.md),
[AI-assisted development](../policies/ai-assisted-development.md),
[host contributions](../policies/host-contributions.md) and
[privacy and logging](../policies/privacy-and-logging.md).

Architecture decisions: [../adr/README.md](../adr/README.md). Use cases:
[../use-cases/README.md](../use-cases/README.md).

## Repository layout

| Path | Contents |
| --- | --- |
| `apps/android/` | Android client. It started as a subtree of moonlight-android 12.2 and is Borealis code since [ADR 0054](../adr/0054-declare-independence-from-moonlight.md). |
| `apps/android/borealis/` | Borealis Android code. |
| `apps/android/build-logic/` | Gradle convention plugins for Borealis modules. |
| `core/` | `borealis-core`, C++20 library with CMake and GoogleTest. |
| `design/` | Design tokens, icons, fonts, the animated backgrounds (clips and the scripts that made them) and the contrast tool. |
| `docs/` | ADRs, use cases, host contract, development guides, policies. |

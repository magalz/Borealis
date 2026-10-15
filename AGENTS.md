# Working on Borealis

Borealis is a game-streaming client built on Moonlight and tailored to the LuminalShine host. Use English for source, documentation, commits and product text. Versioned [ADRs](docs/adr/README.md), the [host contract](docs/host-contract/README.md) and the [development guides](docs/development/README.md) are canonical.

## How work is organized

- The main Claude session (Opus) orchestrates. Exploration goes to the `br-scout` subagent (Haiku), light text work (PR descriptions, release notes, changelog entries, issue text, ADRs from orchestrator notes) to `br-scribe` (Haiku), implementation tasks to `br-implementer` (Sonnet, high effort), and independent checks to `br-reviewer` (Sonnet, high effort), as described in [orchestration](docs/development/orchestration.md). Briefs follow the [task brief template](docs/development/task-brief.md).
- Use Memtrace first for code discovery, impact and history when its tools are connected (`repo_id: Borealis`); fall back to targeted Grep and ranged reads. If Memtrace is unavailable or inconsistent, say so and continue. Versioned documents stay canonical; Memtrace results are not verification evidence.
- This holds for every subagent too: each loads the `memtrace-skills:memtrace-first` skill before exploring. Memtrace indexes the orchestrator's checkout, which stays on the phase branch during a phase. A task worktree's own changes are an overlay: the orchestrator indexes the worktree once (`index_directory` with the worktree `path`, `repo_id: Borealis`, the task `branch`, `incremental: true`) and the subagent passes `worktree: "Borealis:<worktree folder name>"` (exact id from `list_worktrees`) to `find_code`. Every path Memtrace returns is mapped to the subagent's own worktree before it reads or edits the file.
- One worktree and one branch per task, under `D:\Repos\Borealis-worktrees\<phase>-<task>`. Work only in the worktree and on the files the brief names.
- Follow the [TDD rule](docs/development/tdd.md): commit the observed failing test before the implementation.
- One phase is one integration PR, merged with **Create a merge commit** so the test-first commits stay in history.

## Upstream code

- `apps/android/` is a git subtree of [moonlight-android](https://github.com/moonlight-stream/moonlight-android). Never reformat upstream files and keep edits to them minimal; mark every Borealis hook inside an upstream file with a `// BOREALIS:` comment.
- Borealis Android code lives in `apps/android/borealis/` (Kotlin modules) and `apps/android/build-logic/`. New code is Kotlin; no new Java.
- Never rename the `com.limelight` namespace or `com.limelight.nvstream.jni.MoonBridge`: the JNI symbols depend on them.
- `moonlight-common-c` stays a submodule at its upstream path; sync procedure in [upstream sync](docs/development/upstream-sync.md).
- Reference sources (read-only): `D:\Repos\CandelaMoon-src\` holds `moonlight-android`, `Artemis` and `luminalshine` checkouts used during the analysis.

## Writing code

- Write the least code that does the job: no speculative abstractions or options without a consumer, but never drop validation, error handling or security.
- `core/` is C++20 behind a thin C ABI (no exceptions or STL types across it). HTTP, UI and decoding stay per platform.
- Logs go through `BorealisLog`; never log PINs, tokens, keys or certificates.

## Verification

Run the checks for what you touched before finishing:

- Android: `./gradlew spotlessCheck detekt lint testDebugUnitTest :build-logic:test assembleNonRootDebug licensee koverXmlReportBorealis` from `apps/android`.
- Core: `cmake --preset dev && cmake --build --preset dev && ctest --preset dev` from `core`, plus the format check CI runs, also from `core`: `find include src tests fuzz -type f \( -name '*.h' -o -name '*.hpp' -o -name '*.cpp' \) -print0 | xargs -0 clang-format --dry-run --Werror`. Use clang-format 22; CI pins the exact version in `.github/workflows/ci.yml`.

## GitHub and merges

- Pull requests are opened with the owner's own GitHub account (`magalz`); there is no bot. Never merge: only the owner merges, after review. Passing CI or a prepared PR is not merge authorization.
- Ask before any push to `main`, visibility or settings change, store action, or message to other people.
- Commits made by agents end with a `Co-Authored-By: Claude <model> <noreply@anthropic.com>` trailer.
- Never print or commit secrets, signing keys or tokens.

Report honestly: behaviour changed, checks run and the exact revision tested, remaining limitations, and anything blocked.

# Working on Borealis

Borealis is a game-streaming client built on Moonlight and tailored to the LuminalShine host. Use English for source, documentation, commits and product text. Versioned [ADRs](docs/adr/README.md), the [host contract](docs/host-contract/README.md) and the [development guides](docs/development/README.md) are canonical.

## How work is organized

- The main Claude session (Opus) orchestrates. Exploration goes to the `br-scout` subagent (Haiku), light text work (PR descriptions, release notes, changelog entries, issue text, ADRs from orchestrator notes) to `br-scribe` (Haiku), implementation tasks to `br-implementer` (Sonnet, extra high effort), and independent checks to `br-reviewer` (Sonnet, extra high effort), as described in [orchestration](docs/development/orchestration.md). Briefs follow the [task brief template](docs/development/task-brief.md).
- Use Memtrace first for code discovery, impact and history when its tools are connected (`repo_id: Borealis`); fall back to targeted Grep and ranged reads. If Memtrace is unavailable or inconsistent, say so and continue. Its main use is impact analysis: `get_impact`, `preflight_check` and `detect_changes` before an edit. Versioned documents stay canonical; Memtrace results are not verification evidence. Its dead-code and duplicate-code results are leads: a deletion needs a manual check for callable references (`::f`), Compose, JNI, the manifest, reflection and Hilt.
- This holds for every subagent that explores code too (`br-scribe` has no Memtrace tools): each loads the `memtrace-skills:memtrace-first` skill before exploring. The orchestrator's checkout is indexed with `branch: "main"`, incrementally, after each merge wave. We do not call `watch_directory`, because it labels changes with the checked-out branch. `memtrace start` arms its own watcher under the checked-out branch, so answers for `branch: "main"` reflect the last incremental index of the checkout, not live edits. Every Memtrace call passes `branch: "main"` when the tool takes a branch (`get_symbol_context`, `analyze_relationships`, `get_impact`, `detect_changes` and the quality tools), because the store keeps older copies of the repository under other labels (phase, task and docs branches): a call without the branch mixes them and comes back ambiguous, empty or slow, and the quality tools match the exact label. `find_symbol`, `find_code` and `preflight_check` take no branch; scope them with `repo_id` and, when needed, `file_path`. When two symbols share a name, pass `file_path` or the `node_id` from an earlier result. A task worktree is an overlay with its task branch: the orchestrator indexes the worktree once (`index_directory` with the worktree `path`, `repo_id: Borealis`, the task `branch`, `incremental: true`) and the subagent passes `worktree: "Borealis:<worktree folder name>"` (exact id from `list_worktrees`) to `find_code`. Every path Memtrace returns is mapped to the subagent's own worktree before it reads or edits the file.
- At the end of every sprint, after the owner merges the phase, the orchestrator removes the task worktrees, resets the store (`memtrace reset Borealis`) and indexes the checkout again from scratch as `branch: "main"`. The reset drops the history replays, which we do not use. See [orchestration](docs/development/orchestration.md#task-lifecycle).
- What is in the graph: the tracked `.memtraceignore` keeps Gradle Kotlin DSL scripts (`**/*.gradle.kts`), the design tokens and the prebuilt OpenSSL and Opus headers out of it, and Memtrace's default exclusions drop `**/third_party/**`, so `core/third_party/` is not in it. Use Grep and ranged reads there.
- One worktree and one branch per task, under `D:\Repos\Borealis-worktrees\<phase>-<task>`. Work only in the worktree and on the files the brief names.
- Follow the [TDD rule](docs/development/tdd.md): commit the observed failing test before the implementation.
- One phase is one integration PR, merged with **Create a merge commit** so the test-first commits stay in history.

## Inherited code

- `apps/android/` started as a subtree of [moonlight-android](https://github.com/moonlight-stream/moonlight-android) 12.2, and `core/third_party/moonlight-common-c/` is a vendored copy of moonlight-common-c. Since [ADR 0054](docs/adr/0054-declare-independence-from-moonlight.md) both are Borealis code: change them when a task needs it.
- New code is Kotlin (C++20 in `core/`); never add Java files. Keep edits to inherited Java to wiring and put new logic in Kotlin. Do not reformat inherited or vendored files, so diffs stay reviewable.
- Borealis Kotlin modules live in `apps/android/borealis/` and `apps/android/build-logic/`. `apps/android/app/src/main/kotlin/` and `apps/android/app/src/main/jni/borealis/` are Borealis code too, inside the inherited `:app` module.
- Never rename the `com.limelight` namespace or `com.limelight.nvstream.jni.MoonBridge`: the JNI symbols depend on them.
- Upstream fixes arrive only by cherry-pick ([upstream cherry-picks](docs/development/upstream-sync.md)). Record every change to the vendored C sources in `core/third_party/README.md`.
- Reference sources (read-only): `D:\Repos\CandelaMoon-src\` holds `moonlight-android`, `Artemis` and `luminalshine` checkouts used during the analysis.

## Writing code

- Write the least code that does the job: no speculative abstractions or options without a consumer, but never drop validation, error handling or security.
- `core/` is C++20 behind a thin C ABI (no exceptions or STL types across it). HTTP, UI and decoding stay per platform.
- Logs go through `BorealisLog`; never log PINs, tokens, keys or certificates.

## Verification

Run the checks for what you touched before finishing:

- Android: `./gradlew spotlessCheck detekt lint testDebugUnitTest :build-logic:test assembleDebug licensee koverXmlReportBorealis` from `apps/android`.
- Compose screens: also `./gradlew :borealis:designsystem:testDebugUnitTest :borealis:library:testDebugUnitTest :borealis:stream:testDebugUnitTest :borealis:settings:testDebugUnitTest -Proborazzi.test.verify=true` from `apps/android`; record references on Windows (see [building](docs/development/building.md)).
- Design tools: `python -m unittest discover -s design/accessibility` from the repository root (Python 3.11 or later, `numpy` and `Pillow`; see [building](docs/development/building.md#contrast-tool-and-the-glass-table)), for a change under `design/`, or to the glass table, the icons or the clips. CI runs it in the "Design tools" job.
- Core: `cmake --preset dev && cmake --build --preset dev && ctest --preset dev` from `core`, plus the format check CI runs, also from `core`: `find include src tests fuzz -type f \( -name '*.h' -o -name '*.hpp' -o -name '*.cpp' \) -print0 | xargs -0 clang-format --dry-run --Werror`. Use clang-format 22; CI pins the exact version in `.github/workflows/ci.yml`.

## GitHub and merges

- Pull requests are opened with the owner's own GitHub account (`magalz`); there is no bot. Subagents never push or open PRs: the orchestrator pushes the task and phase branches and opens the phase PR. Never merge: only the owner merges, after review. Passing CI or a prepared PR is not merge authorization.
- Ask before any push to `main`, visibility or settings change, store action, or message to other people.
- Commits made by agents end with a `Co-Authored-By: Claude <model> <noreply@anthropic.com>` trailer.
- Never print or commit secrets, signing keys or tokens.

Report honestly: behaviour changed, checks run and the exact revision tested, remaining limitations, and anything blocked.

# Contributing to Borealis

Thank you for helping. This page is the short version. The details live in
[docs/development/](docs/development/README.md) and
[docs/policies/](docs/policies/).

By participating you agree to the [Code of Conduct](CODE_OF_CONDUCT.md).
Security problems go through [SECURITY.md](SECURITY.md), never through public
issues.

## Workflow

Borealis uses GitHub Flow with merge commits.

1. Open or pick an issue. Discuss the approach there before large work.
2. Branch from `main`: `feat/<topic>`, `fix/<topic>`, `docs/<topic>` or
   `chore/<topic>`. A sprint runs on a `phase/s<n>-<name>` branch cut from `main`
   (for example `phase/s8-tidy-up`), and each task in it is a `task/<topic>`
   branch cut from the phase branch.
3. Write the failing test first and commit it, then implement (see below).
4. Open a pull request using the template. Link the issue or ADR.
5. CI must be green. Every change goes through a PR; nothing is pushed to
   `main` directly.
6. Only the owner ([@magalz](https://github.com/magalz)) merges. GitHub
   rulesets require a PR and passing status checks.

AI-assisted contributions follow the same rules and the extra ones in
[docs/policies/ai-assisted-development.md](docs/policies/ai-assisted-development.md).

## Test-driven development

TDD is the development paradigm here. For every behaviour change:

1. Commit a failing test: `test: <what must hold>`.
2. Commit the implementation that makes it pass: `feat: ...` or `fix: ...`.
3. Refactor with the tests green.

Exempt: pure documentation, deletions, and mechanical build fixes. Before
changing untested upstream code, add characterization tests first. Details:
[docs/development/tdd.md](docs/development/tdd.md).

## Commit messages

[Conventional Commits](https://www.conventionalcommits.org/) are required:

```text
<type>(<optional scope>): <summary in imperative mood>

<optional body: why, not what>

<optional footers: Closes #123, Co-Authored-By: ...>
```

Types: `feat`, `fix`, `security`, `docs`, `test`, `refactor`, `perf`,
`build`, `ci`, `chore`, `style` and `revert`. `test`, `build`, `ci`, `chore` and
`style` commits are left out of the changelog. Mark breaking
changes with `!` or a `BREAKING CHANGE:` footer. The changelog is generated
from these messages by git-cliff (see [cliff.toml](cliff.toml)).

Commits harvested from upstream keep the upstream subject, so they carry no
type and no `sync:` prefix. The record of harvests is
[core/third_party/README.md](core/third_party/README.md).

Commits written with AI assistance carry a `Co-Authored-By:` trailer.

## Definition of Done

A pull request is ready when all of these hold:

- [ ] The test was written first and committed before the implementation
      (unless exempt).
- [ ] CI is green: format (Spotless, clang-format), lint (Android Lint,
      clang-tidy), detekt, tests, and build.
- [ ] Docs are updated. If the change records a decision, an ADR is added or
      updated.
- [ ] Log and audit-trail entries are added where applicable (pairing,
      certificate changes, TLS failures, launches, sessions, REST actions),
      with redaction per [docs/policies/privacy-and-logging.md](docs/policies/privacy-and-logging.md).
- [ ] The inherited files the change touches are listed in the PR. Edits to
      inherited code are wiring only, new logic is in Kotlin, and no inherited
      file is reformatted wholesale.
- [ ] New dependencies are justified in the PR and pass the licence policy
      ([docs/policies/dependencies.md](docs/policies/dependencies.md)).
- [ ] No secrets, tokens, keys or real PINs in code, tests, fixtures or logs.

## Proposing an ADR

Architecture decisions are recorded as ADRs in
[docs/adr/](docs/adr/README.md). To propose one:

1. Open an issue describing the problem and the options.
2. Write the ADR following the template described in
   [docs/adr/README.md](docs/adr/README.md): context, decision, alternatives
   and consequences.
3. Open a PR with status `Proposed`. The owner moves it to `Accepted`.
4. Never rewrite an accepted ADR. Supersede it with a new one.

## Inherited code

`apps/android/` started as a subtree of
[moonlight-android](https://github.com/moonlight-stream/moonlight-android) 12.2,
and `core/third_party/moonlight-common-c/` is a vendored copy of
moonlight-common-c. Since [ADR 0054](docs/adr/0054-declare-independence-from-moonlight.md)
both are Borealis code.

- Do not reformat, reorganise or "clean up" inherited or vendored files.
  Spotless, detekt and the editor config exclude them on purpose, so diffs
  stay reviewable.
- Put new Borealis code in `apps/android/borealis/` (or
  `apps/android/build-logic/`). Never add Java files: keep edits to inherited
  Java to wiring and write new logic in Kotlin.
- Record every change to the vendored C sources in
  [core/third_party/README.md](core/third_party/README.md).
- Upstream fixes arrive by cherry-pick. Fixes that belong upstream should also
  be offered upstream.

See [docs/policies/upstream-sync.md](docs/policies/upstream-sync.md) and
[docs/development/upstream-sync.md](docs/development/upstream-sync.md).

## Code style summary

- **Kotlin:** ktfmt (kotlinlang style) via Spotless, detekt, Android Lint.
- **C++ (`core/`):** C++20, clang-format using LuminalShine's LLVM-based style
  (2-space indent), clang-tidy.
- **Everything:** UTF-8, LF line endings, final newline, no trailing
  whitespace (see [.editorconfig](.editorconfig)). Markdown is checked with
  [.markdownlint.jsonc](.markdownlint.jsonc).
- Everything in the repository is written in English.

Run the formatters before committing; see
[docs/development/building.md](docs/development/building.md).

## Git hooks

Activate the repository hooks once per clone:

```sh
git config core.hooksPath .githooks
```

`.githooks/pre-commit` runs the fast format and lint checks. Do not bypass it
with `--no-verify`.

## Licensing of contributions

Borealis is licensed under the
[GNU General Public License v3.0](LICENSE), like Moonlight. Contributions are
accepted under the same licence (inbound = outbound). There is no CLA. By
opening a pull request you confirm that you have the right to submit the work
under GPL-3.0. Do not copy code from sources with incompatible licences. See
[NOTICE](NOTICE) for third-party components.

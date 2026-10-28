# Test-driven development

TDD is the development paradigm in Borealis. This page explains how to apply
it. The rule itself is in [CONTRIBUTING.md](../../CONTRIBUTING.md).

## The cycle

1. **Red.** Write a test that describes the behaviour you want. Run it and see
   it fail for the right reason (not a compile error or a typo).
2. **Commit the failing test** as `test: <behaviour>`. CI may be red on this
   commit; the PR must be green at its head.
3. **Green.** Write the smallest implementation that passes. Commit it as
   `feat:` or `fix:`.
4. **Refactor.** Clean up with tests green. Commit as `refactor:`.

The history of a PR therefore reads: `test:` then `feat:` or `fix:` (then
optional `refactor:`). Reviewers use this order to check that the test really
failed first.

## Commit order

```text
test: reject pairing when the host certificate changed
feat: reject pairing when the host certificate changed
```

- Never squash the test commit into the implementation commit.
- For a bug: first a `test:` that reproduces it, then `fix:`.
- If a fix needs several steps, each step still starts with its test.

## Exemptions

Test-first is not required for:

- Pure documentation changes.
- Deletions (removing code or files).
- Mechanical build fixes (dependency bumps, CI or Gradle configuration).

Say in the PR which exemption applies. Everything else is not exempt, including
"small" changes.

## Characterization tests for inherited code

Inherited Moonlight code mostly has no tests. The Java that Sprints 12 to 14
converted has characterization tests now. Before changing inherited code:

1. Write a **characterization test** that pins the current behaviour, and
   commit it as `test: characterize <behaviour>`.
2. Make your change with a new failing test for the new behaviour first. No
   inherited Java is left (since Sprint 14), so new logic goes in Kotlin. An edit
   to the vendored C under `core/third_party/` needs the task brief to say so.
3. Keep the characterization tests: they protect against regressions when an
   upstream fix is cherry-picked.

Prefer adding Borealis code in `apps/android/borealis/` behind a small seam, so
that the new behaviour is tested without touching inherited internals.

## What counts as a behaviour test

A test must fail if the behaviour breaks, and pass if the implementation is
refactored. Good:

- Given an input and state, the observable result (return value, emitted
  state, written audit entry, request sent) is as expected.
- A parser rejects malformed or hostile input.
- A UI state is rendered and responds to input (Robolectric and Compose UI).
- The host contract is honoured, checked against golden fixtures.

Not enough:

- A test that only asserts a method was called.
- A test that restates the implementation.
- A test that cannot fail (no assertion, or asserts a constant).
- Coverage numbers without assertions.

## Tools (introduced progressively)

| Area | Tools |
| --- | --- |
| Kotlin unit | JUnit 4, Turbine (flows), Truth. MockK is planned and not used yet; the tests use fakes. |
| Android UI | Robolectric, Compose UI tests, and the Accessibility Test Framework of Google through `roborazzi-accessibility-check` (`assertAccessible` in the test fixtures of `:borealis:designsystem`; each accepted finding carries a reason, see [accessibility](../ux/accessibility.md#screen-readers-and-touch-targets)). |
| Emulator | Gradle Managed Devices, planned and not set up. |
| Screenshots | Roborazzi. |
| Design tools | Python `unittest` for the contrast tool in `design/accessibility/` (see [building](building.md#contrast-tool-and-the-glass-table)). |
| core (C++) | GoogleTest, ASan/UBSan, libFuzzer for parsers. |
| Host contract | Golden fixtures with contract tests. |

Mock at boundaries (network, clock, storage), not inside the unit under test.
Use fakes for the host where a fake is simple. Tests use throwaway values for
PINs, keys and certificates, never real ones.

### Screenshot tests

A screenshot test follows the same order. The test that renders a new screen
or state is committed before the implementation. The reference image is
recorded on Windows and committed with the implementation. The screenshot
tests cover `:borealis:designsystem`, `:borealis:library`, `:borealis:stream`
and `:borealis:settings`. Commands, the CI job and the Linux plan B are in
[building](building.md#screenshot-tests).

## Practicalities

- Keep tests fast and deterministic: no real network, no sleeps, injected
  clocks and dispatchers.
- One behaviour per test; name tests after the behaviour.
- Run the relevant tests before committing; the pre-commit hook covers the
  fast checks only.
- A bug found later gets a regression test before the fix.

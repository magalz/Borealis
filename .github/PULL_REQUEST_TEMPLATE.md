<!-- markdownlint-disable MD041 -->

## What

<!-- What does this change do? -->

## Why

<!-- Why is it needed? Link the issue, ADR or use case: Closes #, ADR, UC-NNN. -->

## Tests

- [ ] The failing test was committed first (`test:` before `feat:`/`fix:`)
- [ ] Exempt from test-first (pure docs, deletion, mechanical build fix): <!-- say which -->

<!-- Describe what the tests cover. -->

## Inherited files

- [ ] This PR does not touch inherited files (files under `apps/android/` that come from Moonlight, or vendored files under `core/third_party/`)
- [ ] It touches inherited files; no Java file is added (the app has no Java since Sprint 14), new logic is in Kotlin, and nothing was reformatted wholesale
- [ ] It touches vendored files under `core/third_party/`; each change is recorded in `core/third_party/README.md` as that README describes

<!-- List the inherited files touched and why. -->

## Definition of Done

- [ ] CI is green: format, lint, detekt, tests, build
- [ ] Docs updated; ADR added or updated if a decision was made
- [ ] Log and audit-trail entries added where applicable, with redaction
- [ ] New dependencies are justified here and pass the licence policy
- [ ] No secrets, tokens, keys or real PINs anywhere in the change
- [ ] Commits follow Conventional Commits; AI-assisted commits have `Co-Authored-By`

## Notes for the reviewer

<!-- Risks, follow-ups, screenshots, host or LuminalShine version tested. -->

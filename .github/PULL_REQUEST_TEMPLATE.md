<!-- markdownlint-disable MD041 -->

## What

<!-- What does this change do? -->

## Why

<!-- Why is it needed? Link the issue, ADR or use case: Closes #, ADR, UC-NNN. -->

## Tests

- [ ] The failing test was committed first (`test:` before `feat:`/`fix:`)
- [ ] Exempt from test-first (pure docs, deletion, mechanical build fix): <!-- say which -->

<!-- Describe what the tests cover. -->

## Upstream files

- [ ] This PR does not touch files under `apps/android/` that come from Moonlight
- [ ] It touches upstream files; every change there is a minimal hook marked `// BOREALIS:` and no upstream file was reformatted

<!-- List the upstream files touched and why. -->

## Definition of Done

- [ ] CI is green: format, lint, detekt, tests, build
- [ ] Docs updated; ADR added or updated if a decision was made
- [ ] Log and audit-trail entries added where applicable, with redaction
- [ ] New dependencies are justified here and pass the licence policy
- [ ] No secrets, tokens, keys or real PINs anywhere in the change
- [ ] Commits follow Conventional Commits; AI-assisted commits have `Co-Authored-By`

## Notes for the reviewer

<!-- Risks, follow-ups, screenshots, host or LuminalShine version tested. -->

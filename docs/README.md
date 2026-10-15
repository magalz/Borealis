# Documentation

Everything is written in English. Start with the repository
[README](../README.md) and [CONTRIBUTING.md](../CONTRIBUTING.md).

| Folder | Contents |
| --- | --- |
| [adr/](adr/README.md) | Architecture decision records: why Borealis is built the way it is. Never rewritten once accepted; superseded by a new ADR instead. |
| [use-cases/](use-cases/README.md) | Use cases (UC-NNN) with actors, flows and acceptance tests. They drive the tests. |
| [ux/](ux/phase-2-proposal.md) | Phase 2 UX proposal, clickable prototype and screen captures. |
| `host-contract/` | What the client expects from the LuminalShine host (endpoints, behaviour, golden fixtures), and which parts are proposals. |
| [development/](development/README.md) | How to build, test (TDD), sync upstream, release and orchestrate AI agents. |
| [policies/](policies/) | Rules: [dependencies](policies/dependencies.md), [release](policies/release.md), [upstream sync](policies/upstream-sync.md), [AI-assisted development](policies/ai-assisted-development.md), [host contributions](policies/host-contributions.md), [privacy and logging](policies/privacy-and-logging.md). |

Top-level project files:

- [SECURITY.md](../SECURITY.md): how to report a vulnerability.
- [PRIVACY.md](../PRIVACY.md): what the app does with data.
- [CODE_OF_CONDUCT.md](../CODE_OF_CONDUCT.md): community standards.
- [NOTICE](../NOTICE): derivation from Moonlight and third-party components.

## Writing documentation

- One H1 per file; blank lines around headings, lists and code blocks.
- Describe what exists. Mark anything planned as planned.
- A change that records a decision updates or adds an ADR in the same PR.
- Markdown is checked with [.markdownlint.jsonc](../.markdownlint.jsonc).

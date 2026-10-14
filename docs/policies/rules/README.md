# Standing rules

Standing rules are short, permanent constraints that every contributor and AI agent must follow. Each rule lives in its own file so that Memtrace Cortex can register it as a governance contract (`memtrace govern add <file> --type standing_rule --guidance "<rule>" --scope <scope> --ban`).

| Rule | Summary |
| --- | --- |
| [R-001](R-001-no-upstream-reformat.md) | Never reformat upstream files; keep hooks minimal and marked `// BOREALIS:` |
| [R-002](R-002-keep-limelight-namespace.md) | Never rename the `com.limelight` namespace or the `MoonBridge` JNI class |
| [R-003](R-003-ai-never-merges.md) | Every change goes through a pull request; AI agents never merge |
| [R-004](R-004-tdd-red-first.md) | Behaviour changes start with a failing test committed before the implementation |

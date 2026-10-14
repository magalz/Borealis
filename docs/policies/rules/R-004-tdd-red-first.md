# R-004 Behaviour changes start with a failing test

New or changed behaviour starts with a test that fails for the right reason, committed (`test: ...`) before the implementation (`feat:` / `fix:`). Pure documentation, deletions and mechanical build fixes are exempt. Before changing untested upstream behaviour, add a characterization test first.

See [ADR 0016](../../adr/0016-testing-strategy-and-tdd.md) and the [TDD guide](../../development/tdd.md).

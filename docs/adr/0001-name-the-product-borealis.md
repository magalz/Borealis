---
status: accepted
date: 2026-10-13
decision-makers: João Pedro Magalhães
---

# 0001 Name the product Borealis

## Context and Problem Statement

The project was developed under the working name CandelaMoon during analysis. A final product name is needed before identifiers, modules and repositories are created.

## Considered Options

* Borealis
* CandelaMoon (working name, replaced)

## Decision Outcome

Chosen option: "Borealis", because it is the final product name chosen by the owner.

The name is used in the GitHub repository `magalz/Borealis` and the local folder `D:\Repos\Borealis`.

### Consequences

* Good, because identifiers follow one name: `borealis-core`, `BorealisTheme`, `BorealisLog`, `:borealis:*` Gradle modules, `// BOREALIS:` hook markers and `br-*` agent names.
* Bad, because "borealis" is also the name of a Nintendo Switch UI library (natinusala/borealis) used by Moonlight-Switch, which may cause search confusion. Not a blocker.

## Pros and Cons of the Options

### Borealis

* Good, because it is the final, chosen name.
* Good, because it is used consistently across repository, folder and identifiers.
* Bad, because a Nintendo Switch UI library with the same name exists, which may cause search confusion.

### CandelaMoon

* Good, because it was the working name during analysis.
* Bad, because it was replaced and is obsolete; it appears only as the legacy name (e.g. `com.candelamoon` in ADR 0028).

## More Information

See [0028](0028-application-id.md) for the application identifier.

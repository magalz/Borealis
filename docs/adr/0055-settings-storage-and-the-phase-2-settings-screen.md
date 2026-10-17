---
status: accepted
date: 2026-10-16
decision-makers: João Pedro Magalhães
---

# 0055 Store settings through one repository and build the Phase 2 settings screen

## Context and Problem Statement

The upstream stream settings screen holds 41 preferences. The interim Borealis screen from [0050](0050-diagnostics-audit-and-interim-settings.md) holds two logging switches. Inherited Java reads the stream keys through `PreferenceConfiguration.readPreferences`: `Game` reads them synchronously in `onCreate`, and `ControllerHandler`, `UsbDriverService`, the on-screen controls loader and `UiHelper` read them too. [0050](0050-diagnostics-audit-and-interim-settings.md) promised that the two `borealis_` switches move to DataStore in Phase 2. How should settings be stored and read in Phase 2, so that the Phase 2 settings screen can be built without changing the Java readers?

## Decision Drivers

* The stream keys are read synchronously by Java code that must not change in Sprint 6
* Masking of addresses must apply from the first log line of the app
* The settings of later phases must be able to move to DataStore one key at a time, without a rewrite
* The device-specific options (display modes, decoder limits, HDR, features) must come from the device, not from fixed lists

## Considered Options

* Move all settings to DataStore now, and bridge the Java readers to DataStore
* Keep the stream keys in the default SharedPreferences file behind a Kotlin repository, and move only the two logging switches to DataStore
* Keep the interim screen of [0050](0050-diagnostics-audit-and-interim-settings.md) and add no new settings screen until a later phase

The alternatives are reconstructed from the Context and the consequences of the decision. TODO(verify): the owner's record of the alternatives is not in the sprint notes.

## Decision Outcome

Chosen option: "Keep the stream keys in SharedPreferences behind a repository, and move the two logging switches to DataStore", because it leaves the Java readers unchanged, it lets the two logging switches be read at start for masking, and it keeps the move of each key to DataStore a separate, small step.

Decision details:

1. **One Kotlin settings catalog.** Every key, type and default lives in one place, in the `:borealis:settings` module. A parity test ties the catalog to the Java defaults.
2. **A `SettingsRepository` is the only writer from Kotlin.** It exposes a typed snapshot as a state flow, so a read is synchronous. It also exposes a flow per setting, writes that apply at once, and `clear`, which is used to return the bitrate to automatic.
3. **The stream settings stay in the default SharedPreferences file**, under their current keys. The Java readers do not change.
4. **The two logging switches move to a DataStore file named `borealis_settings`**, with a `SharedPreferencesMigration`:
   * Verbose logging (`borealis_verbose_logging`);
   * Mask addresses in logs and diagnostics (`borealis_mask_addresses`).

   The app reads the DataStore file once, blocking, at start, so that masking applies from the first log line. The DataStore directory is excluded from backups.
5. **Migration path for later.** The owner asked that later moves stay easy.
   * Step 1: `PreferenceConfiguration.readPreferences` becomes a one-line delegate that builds the same object from the repository's snapshot. The legacy migrations move into the repository.
   * Step 2: after step 1, any key can move to DataStore on its own, with a `SharedPreferencesMigration` for that key and no Java change. `Game.onCreate` keeps reading the snapshot, which is primed at app start and never blocks.
6. **The settings screen.**
   * It ports 39 of the 41 upstream settings into the approved groups, plus three extra groups: Gamepad, On-screen controls and Advanced. The groups and rows are listed in [the Phase 2 proposal](../ux/phase-2-proposal.md#settings-groups).
   * It drops the language setting and the small icon mode.
   * It deletes `StreamSettings` and the interim screen of [0050](0050-diagnostics-audit-and-interim-settings.md).
   * The performance overlay setting becomes Off, Compact or Detailed, as in the in-game menu. It uses a new key, `borealis_perf_overlay`. The old `true` value becomes Detailed.
7. **The app ships in English only until a translation sprint.** The inherited translations and the Borealis pt-BR strings are removed. The locale config lists English only, and the language picker is removed.
8. **Settings are global** ([0052](0052-structure-the-phase-2-ui.md), D7). Per-host overrides come later.
9. **Device-specific options are computed in Kotlin** from the device's facts: the display modes, the decoder limits, HDR and the features. An app-side adapter provides these facts.

### Consequences

* Good, because the Java readers of the stream keys do not change in Sprint 6.
* Good, because the two logging switches are read before the first log line, so masking applies from the start.
* Good, because each later key can move to DataStore with a migration for that key only.
* Bad, because two storage backends exist for a while.
* Bad, because one parity test must be kept in step with the Java defaults.

This ADR amends [0050](0050-diagnostics-audit-and-interim-settings.md) section 3 (the interim settings screen and its storage) and [0011](0011-datastore-room-and-keystore-for-new-persistence.md), because the stream settings stay in SharedPreferences behind a repository.

## Pros and Cons of the Options

### Move all settings to DataStore now

* Good, because there is one storage mechanism for settings.
* Bad, because the Java readers in `Game`, `ControllerHandler`, `UsbDriverService`, the on-screen controls loader and `UiHelper` would need a bridge that blocks on DataStore.

### Keep the stream keys in SharedPreferences behind a repository, and move the two switches (chosen)

* Good, because the Java readers stay unchanged.
* Good, because the two logging switches are read once at start, so masking applies from the first line.
* Bad, because two storage backends exist for a while.

### Keep the interim screen and add no new settings screen

* Good, because there is no new code in Sprint 6.
* Bad, because the Phase 2 settings screen and its groups would not exist, and the device-specific options would not be offered.

## More Information

* Related: [0011](0011-datastore-room-and-keystore-for-new-persistence.md), [0050](0050-diagnostics-audit-and-interim-settings.md), [0052](0052-structure-the-phase-2-ui.md).
* Use case: [UC-009, change settings](../use-cases/0009-change-settings.md).
* Policy: [privacy and logging](../policies/privacy-and-logging.md) (where settings are stored).

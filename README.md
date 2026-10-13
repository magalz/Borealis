# Borealis

Borealis is a game-streaming client built on [Moonlight](https://moonlight-stream.org/) and tailored to the
[LuminalShine](https://github.com/NortheBridge/luminalshine) host. The first target is Android (phones, tablets,
Android TV / Google TV / Fire TV and handhelds).

> Status: early foundation work. Not ready for use yet.

## Repository layout

| Path | Contents |
| --- | --- |
| `apps/android/` | Android client. A git subtree of [moonlight-android](https://github.com/moonlight-stream/moonlight-android); Borealis code lives in `apps/android/borealis/` and small hooks marked `// BOREALIS:` |
| `core/` | `borealis-core`, the C++20 library shared across platforms (LuminalShine host rules) |
| `design/` | Design tokens, icons and fonts |
| `docs/` | Architecture decision records, use cases, host contract, development guides and policies |
| `tools/` | Developer tooling |

## License

Borealis is licensed under the [GNU General Public License v3.0](LICENSE), like Moonlight.

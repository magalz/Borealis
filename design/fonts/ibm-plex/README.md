# IBM Plex

The Borealis UI uses IBM Plex Sans for text and IBM Plex Mono for data, codes,
addresses and PIN digits ([ADR 0005](../../../docs/adr/0005-ui-toolkit-and-visual-style.md),
[design/tokens.json](../../tokens.json)). The app bundles the fonts, so it never
fetches them at run time.

- Licence: SIL Open Font License 1.1, in [OFL.txt](OFL.txt). Copyright 2017 IBM
  Corp., with the Reserved Font Name "Plex". The files are unmodified, so the
  name stays.
- Source: the official releases at <https://github.com/IBM/plex/releases>.
- Use: `res/font/` is added as an Android resource directory of
  `:borealis:designsystem`. Android resource names must be lowercase, so the
  files are renamed. Their content is not changed.

| File | Release | Original file | SHA-256 |
| --- | --- | --- | --- |
| `ibm_plex_sans_regular.ttf` | `@ibm/plex-sans@1.1.0` | `fonts/complete/ttf/IBMPlexSans-Regular.ttf` | `975dcda37d80f038dcd143c22e33ca2d97a0cc5a929aace1c749153b0fe1afa5` |
| `ibm_plex_sans_medium.ttf` | `@ibm/plex-sans@1.1.0` | `fonts/complete/ttf/IBMPlexSans-Medium.ttf` | `331c8639d7598b2cde62a911a71db195e30cb655cd6bdf2e324a7e984955f907` |
| `ibm_plex_sans_semibold.ttf` | `@ibm/plex-sans@1.1.0` | `fonts/complete/ttf/IBMPlexSans-SemiBold.ttf` | `a20caf8286023a6a7a85e40b1d2a4ae9fc3e3b1f9eda8f4c542dd4986af67bb1` |
| `ibm_plex_mono_regular.ttf` | `@ibm/plex-mono@2.5.0` | `fonts/complete/ttf/IBMPlexMono-Regular.ttf` | `7c6fbddca4b700be918f5f6183d9bd4464fa427fe435f0b480d77fe2bb8c5a43` |
| `ibm_plex_mono_medium.ttf` | `@ibm/plex-mono@2.5.0` | `fonts/complete/ttf/IBMPlexMono-Medium.ttf` | `98fbd727aae340b236955879dabed4d991aac9e8e90b3b2a67ce4a59221cc97c` |
| `ibm_plex_mono_semibold.ttf` | `@ibm/plex-mono@2.5.0` | `fonts/complete/ttf/IBMPlexMono-SemiBold.ttf` | `f04d7c488ddf7d1fa99f2574efc3406ea4cbe17bb1af3a1ab960f84d0c96a172` |

The release archives were `ibm-plex-sans.zip` (SHA-256
`fb365d910566e6d199cc2c15579a7dd9a267128e18431a394ed81f1970c69200`) and
`ibm-plex-mono.zip` (SHA-256
`6d23f01257663d8cc49a0d64c22ced630b79e0e2a0ac08a0da86e9a38bbc481c`). Their
`LICENSE.txt` files are identical; `OFL.txt` is that file.

Only the weights the tokens use are bundled: 400, 500 and 600 of each family.

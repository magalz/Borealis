---
status: accepted
date: 2026-10-13
decision-makers: João Pedro Magalhães
---

# 0005 Use Jetpack Compose with Material 3 and Compose for TV

## Context and Problem Statement

Borealis needs one UI toolkit for phones, tablets and Android TV, and a visual style that matches the LuminalShine host UI. Which toolkit should be used, and how should the visual style be defined?

## Considered Options

* Jetpack Compose (Material 3 base) + Compose for TV (`androidx.tv.material3`), themed with LuminalShine "Mission Control" tokens
* AndroidX Views + Material 3
* WebView reusing LuminalShine's Vue/Tailwind UI
* Compose Multiplatform now
* Earlier round: framework Views as upstream
* Earlier round: hybrid Views for mobile + Leanback for TV

## Decision Outcome

Chosen option: "Jetpack Compose (Material 3 base) + Compose for TV, themed with LuminalShine Mission Control tokens, with the gold accent replaced by purple", because it is the best fit for a token-based design system, custom components are cheap, the same theme works on TV, and it stays portable to Compose Multiplatform later. The stream surface and the on-screen controller stay as Views/SurfaceView.

### Consequences

* Good, because it fits a token-based design system and makes custom components cheap.
* Good, because the same theme works on TV and the UI stays portable to Compose Multiplatform later.
* Bad, because it requires Kotlin and AndroidX, and minSdk must be 23 or higher (see [0006](0006-min-sdk-28.md)).
* Bad, because the APK is a few MB larger.

Style details: the Mission Control palette comes from `luminalshine/src_assets/common/assets/web/tailwind.config.js`. It is dark-only, with surfaces `#0E1013` (page), `#0B0D10` (chrome) and `#14171B` (surface), text `#E8EAED`, a single gold accent `#FFB020`, status colors reserved for state, IBM Plex Sans/Mono, and hairline borders `rgba(255,255,255,0.07)`. Borealis replaces the gold accent family with purple; the exact shades are defined in the UX proposal and validated for AA contrast. The UI is dark-first; the light theme is decided in the UX proposal.

## Pros and Cons of the Options

### Jetpack Compose (Material 3) + Compose for TV

* Good, because it fits a token-based design system and makes custom components cheap.
* Good, because the same theme works on TV.
* Good, because it stays portable to Compose Multiplatform later.
* Bad, because it requires Kotlin and AndroidX (minSdk 23 or higher).
* Bad, because the APK is a few MB larger.

### AndroidX Views + Material 3

* Good, because it is mature and works with Java.
* Bad, because every visual detail becomes a style, attr or drawable.
* Bad, because TV D-pad navigation needs manual work.
* Bad, because Leanback is deprecated.

### WebView reusing LuminalShine's Vue/Tailwind UI

* Good, because it gives a pixel-identical UI.
* Bad, because D-pad and gamepad navigation on TV is poor.
* Bad, because it results in two stacks with a web feel.
* Bad, because the integration with the native stream and input is complex.

### Compose Multiplatform now

* Good, because the UI is portable.
* Bad, because it only makes sense together with KMP from day one, and the TV UI is Android-only anyway.

### Framework Views as upstream (earlier round)

* Good, because it has zero extra dependencies.
* Bad, because it uses deprecated APIs and contradicts the UX redesign.

### Hybrid Views for mobile + Leanback for TV (earlier round)

* Bad, because it means two UIs, and Leanback is deprecated.

## More Information

See [0003](0003-native-apps-per-platform-with-shared-core.md), [0004](0004-kotlin-for-new-android-code.md), [0006](0006-min-sdk-28.md), [0034](0034-screenshot-testing-with-roborazzi.md) and [0044](0044-design-tokens-pipeline.md).

Amended by [0052](0052-structure-the-phase-2-ui.md): violet accent shades and dark only for 1.0.

Amended by [0053](0053-material-3-with-borealis-focus-on-tv.md): Material 3 with a Borealis focus indication instead of `androidx.tv.material3`.

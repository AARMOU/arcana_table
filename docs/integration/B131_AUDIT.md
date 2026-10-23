# Build 131 compatibility audit

## Revisions

- Starting task branch SHA: `fc1e69ffa525596505887157c8ec17b06acab7cc`.
- Development base / audited PR #47 source: `98626c7709023346d5f45756bceb864bc3041766`.
- Verified Argentum upstream revision: `61e71e586b100faa661f86cac827ac3c3bbb5a78`.
- Verified runtime bundle: [Actions run 39844575500](https://github.com/AARMOU/arcana_table/actions/runs/39844575500).

## Findings

Build 131's stock card index contains Rocco, Cabaretti Caterer; Kodama of the West Tree; and Raffine, Scheming Seer. The combined six-precon integration removes their three custom registry entries before importing definitions, leaving 211 distinct custom registrations with no stock-name collisions. The regression test also asserts the three names remain in the stock index, so a missing upstream card cannot silently hide a duplicate registration.

The combined source includes 217 per-card scenario tests. No card implementation, rules behavior, or AI behavior was changed.

## Validation evidence

- [Initial integration run 39874523503](https://github.com/AARMOU/arcana_table/actions/runs/39874523503): failed compiling `DrownerOfHope.kt` and `SifterOfSkulls.kt` because `createEldraziScions` was unresolved.
- [Integration run 39874812474](https://github.com/AARMOU/arcana_table/actions/runs/39874812474): passed after adding the Eldrazi Scion helper; verified the 211 registrations and collision-free export, ran all custom-card tests, then Android unit tests, lint, and unsigned debug compilation.
- [Exact committed-source run 39875693567](https://github.com/AARMOU/arcana_table/actions/runs/39875693567), at `98626c7709023346d5f45756bceb864bc3041766`: passed committed data/runtime checks, `:custom-cards:test`, `:app:testDebugUnitTest`, `:app:lintDebug`, and `:app:assembleDebug`.
- The local post-change Gradle tests could not run: configuration stopped because `com.android.application:8.13.2` could not be resolved from the configured plugin repositories.

## Scope

The website, branding, privacy, closed-testing, README, and Pages-workflow changes have been restored to the development-base state. The only audit paths differing from the base are:

- `android/custom-cards/src/test/kotlin/com/arcanatable/customcards/CustomCardDataTest.kt`
- `docs/integration/B131_AUDIT.md`

PR #61 remains draft and has not been merged. No APK was published and no production release was performed.

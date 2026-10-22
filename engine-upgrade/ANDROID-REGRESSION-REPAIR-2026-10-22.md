# Android regression repair evidence

Candidate parent: `be527c95345bf1a7b275a0a142a5fab6796f95d1`.
Shipped Arcana baseline: `43b27a89f01e850ec651f85f9be775d7804cb0bd`.
Upstream target: `61e71e586b100faa661f86cac827ac3c3bbb5a78`.

## Causes established from source and failed CI

- `UiTestSupport.playWithArgentum` still follows Build 97's online menu. Both baseline and candidate `PlaySheet` park that option (since Build 113); it cannot select the server transport. Clicking Start therefore launches an unrelated embedded game, or fails to reach the off-screen Start control. All 16 server UI scenarios use this helper. Repair the helper to invoke the real retained `startArgentumPractice` path through an internal screen observer; keep the production menu parked and keep all protocol, lifecycle, interaction and layout assertions.
- `Build97Test.treasuresShowAsSacrificeForMana` expects `Sacrifice for mana`; baseline and candidate use `Sacrifice this source for mana`. `ManaReliability128Test` explicitly requires this distinction from Goose/Food. Update the stale exact expectation, retain classification and visibility checks.
- `CustomDecksOfflineTest` expects exactly 15 custom implementations. The upgrade deliberately prefers upstream definitions and removes redundant custom entries. Assert the registry contains all 15 previously custom cards, only Goblin Welder is unsupported, and the notice matches the actual custom-only set. This catches loss of implementations while allowing upstream ownership.

## Verification

The focused CI workflow runs identical tests with two runtimes: shipped JARs/assets restored from the pinned baseline, and matching rebuilt replacement JARs/exports. Its replacement job then runs the entire app test suite, lint and debug compilation. Evidence artifacts include XML and HTML reports. Runtime binaries and APKs are not committed or published.

Results are pending until the exact committed workflow finishes; this document is not a pass claim. No production merge or release is authorized by these changes.

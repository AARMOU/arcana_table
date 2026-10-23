# Build 122 — based on Build 120

Package: `com.arcanatable`. Compiled version code: **122**. Version name: `0.33.1-build122`.

## Source identity

Base game source: `3e17b24e620590e4170bd4c2de2abaa4d18d9e87`, checked out through release branch commit `10fc08dfb0fb237a291d3521af613afff2893ded`.

The saved `ArcanaTable-Play-120-FIXED.aab` was compared with the unsigned AAB from GitHub Actions run `39489709601`. All 55 code, asset and resource entries are byte-for-byte identical. Build 120's upload manifest was renumbered, while its compiled version text still said 113. Build 122 sets the version in source before compilation; never renumber a compiled manifest to represent a new build.

Build 121 is not used as this build's source. The seven WOTC PRECONS are preserved: four Fallout decks, Family Matters, Peace Offering and Animated Army.

## Included changes

- Token-defined triggers are copiable values. Smaug copies retain their death trigger without duplicate Treasure payouts.
- Negative subtype death filters use last-known battlefield types. A creature returned by Valkyrie's Call is an Angel with flying and a counter; its next death does not trigger another return.
- Offline AI uses a bounded mobile profile and smaller searches on crowded boards. Action enumeration time counts against the subsequent search deadline. Timing diagnostics separate AI choice, action resolution, legal actions and snapshots. Engine work is cooperative; the time budget is not a hard interruption of an individual rules operation.
- Quick UI Tips popup: grouped tips, Dismiss for this session, persisted Don't remind me again.
- Temporarily suppress the custom-card/opponent startup notice; preserve its diagnostics.
- Installer/in-app/build files use Build 122; build details retain the Build 120 baseline.

## Validation and signing

Engine base: `wingedsheep/argentum-engine@7a9663d8e4df4ce7db223023ae7714f0028e08a9`; apply patches 0001, 0002, 0004, 0005 and 0006. Patch 0003 is a card export tool and is not required to rebuild the four runtime jars.

CI first reproduces the stock Smaug and Cid failures, then compiles matching Java 17 libraries. It runs the full shipped-card suite, crowded-board AI scenarios, offline host tests, popup tests, APK/AAB assembly, and upstream engine/card/AI tests. Check the actual workflow result; this document alone is not evidence of a passing build.

The earlier broad app UI suite hit a Robolectric/Compose out-of-memory error and six UI assertions. Those are disclosed separately from focused tests for this build; no claim that the broad app suite passed is implied.

Signing happens outside GitHub with the existing upload key, after comparing its certificate with the Build 120 AAB. Keys and passwords must not be committed. Artifact checksums and the source commit accompany the signed deliverables. No Play publication is performed by the build workflow.

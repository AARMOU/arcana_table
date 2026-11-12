# Approved card → playable Android APK

Verified checkpoint: October 28, 2026 (Mountain). Coordination: [issue #81](https://github.com/AARMOU/arcana_table/issues/81).

Approval is sign-off for integration review. It does not merge, build, sign or release.

## Current sources and ownership

| Role | Exact source at checkpoint | Meaning |
| --- | --- | --- |
| Control/certification infrastructure | `main@8b90f1053425b39ad1fca956d12309c80bc16a5e` | Trusted approval and certification; main Android is not the latest numbered app. |
| Card worker development target | `gpt/arcana-integration-next@9dd5fc9c8da129d8aeb58ef6ecbfecc9ff575c9f` | PR #410 targets this branch; its AGENTS engine pin is `7fb073337e4389cfabe2ebf4c6416633a7fc63d2`. |
| Latest verified numbered Android source | `gpt/b140-card-request-test-apk-20261028@310f7dd6a7f1829aa90955af02c2cd48fb8d1a61` | Current B140 source. Runtime manifest pins engine `61e71e586b100faa661f86cac827ac3c3bbb5a78`, with B140 patches. |
| Last successful Card Request B140 APK | `49d0b8c02a4137ef23ee9bb04b2c2b5f0f9bb4eb` | Earlier source, NOT current B140 HEAD. |

The development and B140 sources have diverged (B140 206 ahead / 13 behind at this checkpoint). Do not merge the old development branch wholesale or assume its engine certification transfers to B140. There is no verified single current production release source or reusable current manual B140 APK lane. B140 is the latest numbered development source, not release approval.

Goose owns integration preparation under AGENTS.md. The owner authorizes merges, builds, signing and releases separately. No card integration worker watching approvals was found in the inspected main workflows. Weekly engine upgrade has its own specifically authorized automatic merge into the older development branch; that is not card approval automation and does not promote source to B140.

## Actual stages

| Stage | Existing automation / evidence | Remaining action |
| --- | --- | --- |
| Implement card | Worker adds Kotlin DSL, registry, positive/negative real-Argentum tests and generated data. [#410 files](https://github.com/AARMOU/arcana_table/pull/410/files) | Rules review remains mandatory. |
| Validate development | `Arcana Development CI` regenerates custom exports, executes custom-card and Android tests. [#410 run](https://github.com/AARMOU/arcana_table/actions/runs/40795665740) | These results apply to that source/engine only. |
| Independent certification | `Arcana Independent Card Certification` injects trusted-main tests into exact PR head. [Orb certification](https://github.com/AARMOU/arcana_table/actions/runs/40803465324) | Other cards need registered trusted suites. |
| Owner approval | Arcana Control records exact-commit GitHub review. [Orb review](https://github.com/AARMOU/arcana_table/pull/410#pullrequestreview-5692318797) | Already complete for unchanged Orb HEAD; do not ask again. |
| Integrate into Android source | No automatic approved-card promotion to B140 was found. | Goose prepares a narrow card-only forward-port and tests against the destination's engine; owner authorizes merging. Never port old JARs/UI/assets wholesale. |
| Export runtime data | `:custom-cards:exportCards` uses destination SDK, round-trips each definition, writes custom JSONL and TSV. | This draft adds a dependency from Android `preBuild`, so asset merge cannot omit export. |
| Package and inspect | This draft adds `verify_packaged_card_assets.py`, wired to existing B140 APK job. | Byte-compare all seven Argentum runtime assets against checkout; retain SHA-256 receipt. Only runs when a separately authorized APK/AAB exists. |
| Prove playable | Card scenarios + trusted independent coverage on destination engine; complete integration regression gate; actual app loading and on-device play. | Packaged presence alone cannot establish Oracle behavior, collision/override selection, or working phone UI. |

## Resurrection Orb checkpoint

- [PR #410](https://github.com/AARMOU/arcana_table/pull/410) OPEN, ready for review, exact `56f184291575eb9b5c5ceb46ffa5f90d406f756d`.
- Independent four-scenario certification passed; observed exact-head checks success/skipped.
- Owner `AARMOU` approved at **20:56:21 Mountain**, October 28. Body contains `arcana-owner-decision-v1`, explicitly excludes merge/build/release authorization.
- Not merged into development or B140. Not exported in B140 or proven in any APK.

Next integration preparation is a narrow forward-port to B140, preserving its runtime and UI, followed by the existing card and independent scenarios on B140. That will produce new destination evidence; it does not invalidate or silently reuse approval as authorization to merge.

## Existing APK workflows are not interchangeable

[B140 Card Request TEST APK](https://github.com/AARMOU/arcana_table/actions/runs/40691168051) succeeded on historical `49d0b8c`. [Artifact 12817746727](https://github.com/AARMOU/arcana_table/actions/runs/40691168051/artifacts/12817746727), `arcana-b140-card-request-debug-test-apk`, was unexpired when checked. It is a separately installable debug app `com.arcanatable.cardrequests`, versionCode 140, NOT a production release. Its workflow only runs for a specifically named PR head/base and workflow-file changes. Opening this plumbing draft does not satisfy that build guard. Reuse requires an explicitly authorized build plan and updated source/version guard; no next build number is allocated here.

[Arcana Fast Test APK — manual only (seed or build)](https://github.com/AARMOU/arcana_table/actions/workflows/fast-test-apk-manual.yml) has `seed-engine` (no APK) and `fast-apk`. It remains pinned to B136 source joins/upstream `c38f145359c342a04f951b7ba48f741bcb1627ac`, artifact `arcana-fast-engine-b136-pinned`. It is not a current B140 card pipeline. `play-com-arcanatable.yml` also has historical version/source guards, not authority for a new release.

Full B140 no-APK regression [40773993264](https://github.com/AARMOU/arcana_table/actions/runs/40773993264) is not green. Existing owners handle #426 registry restoration, #404 ShockFetch, #357 Nullifier, and Claude engine lanes. No baseline tests are waived by this change. The original Eldrazi 90-playable milestone gate remains separate; 93 indexed is not 90 playable.

## Use the packaging check after an authorized build

```sh
python3 tools/verify_packaged_card_assets.py path/to/app.apk \
  --require-custom-card 'Resurrection Orb' > card-assets.json
```

The optional requirement proves that the exact name is in packaged custom JSONL/index. It does not prove whether the runtime selects a custom or upstream override. The checker also accepts `.aab` (`base/assets/argentum/`). Output records archive SHA-256, all seven asset hashes and custom-card count. Run real-engine export/tests first. This draft's `Card packaging plumbing checks (NO APK)` runs corruption/missing/stale fixtures, the existing five real-engine `CustomCardDataTest` methods, and Gradle's asset-merge task graph with `--dry-run`; it never invokes APK/AAB assembly.

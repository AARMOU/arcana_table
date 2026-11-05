# B142 runtime staging evidence — Red Bull, 2026-11-05

Base: accf5f79f47ee21982bcae512aa4e508324c36c3 (Elmer source checkpoint).
Upstream: 173a09a2ed15763dce854e385b2df0d085e03975 (2026-11-05 06:32 UTC).
Patched engine commit: 4599b86d667ede28aa77d8c901298a76e96acd1f.
Engine tree: 81223c2ab4d24595504d17996adea5ec4042eb8d.
Strict cumulative 34-patch application produced exactly that tree. Clean existing engine tree reused; no engine source modification.

## Validation

- Engine Gradle build/export/SDK+rules+AI tests: exit0; 11,469 tests, 0 failures/errors, 18 skips. Most tasks cached/up-to-date on identical source. Export: 20,165 cards, 0 round-trip failures.
- First attempt stopped at SDK index equality because Windows newline conversion differed. Canonical SDK-index generator passed; git source-content diff empty. Retest passed. No assertion weakened.
- weekly.install: exit0; four Java17 jars and card exports installed into this isolated candidate.
- Android engine compatibility: PASS, no new missing APIs or bytecode violations; existing known missing Java APIs remain listed in android.json. This is static compatibility evidence, not phone smoke.
- Canonical Android dependency alignment: Kotlin2.4.0 to2.4.21; serialization1.11.0 to1.12.0.
- Canonical docs refresh, actual-jar SDK index, docs-check: PASS. Corrected inherited AGENTS pin from c1c04c to installed173a. Vendored upstream docs content unchanged.
- Automatic collision reconciliation: 52 collisions; 48 use Argentum, 4 retain Arcana (Captain James T. Kirk, Sifter of Skulls, Transmogrifying Wand, Ugin, the Ineffable). No hand-edited override selection. Full final custom suite: 345 suites / 1,082 tests / 0 failures/errors/skips.
- Full Android app suite: 92 suites / 487 tests / 0 failures/errors / 5 skips, including BuildCompatibilityTest and delivered UI regression classes.
- Imported-card report: PASS. Cardkit check/docs-check PASS; Cardkit59 tests PASS. Android regex852 sources0issues;12 tests PASS. Whitespace check PASS.

## Jar SHA256

- argentum-ai.jar: 0e5278c155374725ce1f7be5ca055981df62fe37c7d25ddf53b7db51d45a34e1
- argentum-mtg-sdk-tooling.jar: 176cc2d65276d099086c1bcc8a0749bde2175ffadc71677f439c48a4e04103c4
- argentum-mtg-sdk.jar: 6ce0415f7b6f3386e5cf8dedb1191f25decda07f2fbff8ac02348d0b3bca0e31
- argentum-rules-engine.jar: 0a17ad838d237fd97b0efdb080a94d080b8bc266370c59a53059bb24061ba938

## Remaining Elmer gates

VersionCode/What's New still141 intentionally; Elmer owns final142 metadata, review, fresh exact-candidate GitHub validate, debug packaging and official Drive delivery. No APK, phone smoke, production release or merged candidate claimed. Recheck upstream age at delivery; target becomes24h old at2026-11-06 06:32 UTC (Nov5 23:32 Mountain). Existing B141 preserved. UI Designer and #947 excluded.

Full PC logs remain under redbull/logs/b142-engine-stage and b142-android-stage. Selected machine-readable reports accompany this commit in engine-upgrade/weekly. Engine/tests initial setup ~4min, SDK diagnosis+regeneration ~2min; passing engine retry43.4s; Android app task5m14s, SDK/reconcile wall times retained in local logs (not separately inferred).

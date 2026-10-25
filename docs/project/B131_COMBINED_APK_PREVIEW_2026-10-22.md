# Arcana Table B131 — combined installable preview APK

**Status:** Compiled, debug-signed, uploaded and checksum verified. Android phone install and gameplay have **not** been tested yet.

## Delivery

- Build: **ArcanaTable-B131-Combined-Preview-debug.apk**, 38,405,210 bytes.
- [Successful GitHub Actions build](https://github.com/AARMOU/arcana_table/actions/runs/39879225111), artifact **ArcanaTable-B131-Combined-Preview-APK**, artifact ID `12469498777` (GitHub Actions archive; time-limited retention).
- Built from **`gpt/b131-combined-apk-preview-20261022@af6bad65f67c5c2fc5acffd9b14de7d5d1b0527b`**, itself based on fully verified combined candidate **`98626c7709023346d5f45756bceb864bc3041766`**.
- SHA-256: **`b77fd69eceada562fb045f4ad9a4f8dfd38d34cf27108108e7030f8937886940`** (checked after GitHub artifact download).
- **Application ID:** `com.arcanatable.b131combined`; intentionally different from existing Arcana test and production packages, installs side-by-side with independent local data.
- Android versionCode `131`, versionName `0.33.9-build131-combined-preview`; min SDK 26, target SDK 36.
- **Debug signing only**, Android Debug, v2 APK signature scheme; certificate SHA-256 `bda4520a8d15b9e203c226a878e06f566d90249ccb802b1c15be3dbf0b97df78`. This is **not** the user's original app signing key; cannot be used to update an existing differently signed app. Never claim production-signature parity.
- Verified engine upstream `61e71e586b100faa661f86cac827ac3c3bbb5a78`; **211 custom cards** with no upstream name collisions, merge of game-over cause logging and card worker integration.

## Exact build validations

[Actions run #39879225111](https://github.com/AARMOU/arcana_table/actions/runs/39879225111) completed **SUCCESS**:
- Checked source ancestry to `98626c77` and pinned runtime/card asset checksums.
- Ran `:custom-cards:test :app:testDebugUnitTest :app:lintDebug :app:assembleDebug`.
- Checked `aapt` package ID / version and `apksigner verify --print-certs` (v2 true).
- Checked `zipalign` and published checksums plus `B131_CARDS.json` / `B131_RUNTIME.json`.

**Limitations:** No device launch, permission UX check, full actual games, performance stress under thousands of triggers, long-running background restore, or Play Store signing has been demonstrated for this APK. Do not merge `main`, publish a release, or claim production readiness based only on this CI evidence.

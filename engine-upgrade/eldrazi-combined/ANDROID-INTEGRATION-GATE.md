# Eldrazi Temple + Corrupted Crossroads — Android integration gate

## Evidence already obtained

- Source-only draft PR #202; exact-head Actions run 40215966244 succeeded.
- 11 real Argentum engine tests and 14 Android custom-card scenarios passed, no failures/errors/skips.
- Runner applied both patches to pinned Argentum `61e71e586b100faa661f86cac827ac3c3bbb5a78`, compiled temporary JARs, injected both card definitions and registrations only inside CI, and verified deterministic exports.

## Not yet proven

- A persistent, independently reviewable Android candidate that contains both card definitions and the source-built runtime, without replacing the shipped production JARs.
- Full app debug compilation and `:app:testDebugUnitTest` against the combined runtime; gameplay from original Incursion deck slots; physical Fold testing.
- An approved production runtime upgrade or release. **Do not count either card as shipped or certified playable.**

## Acceptance criteria for a subsequent isolated development-only implementation

1. Pin exact upstream and patch SHAs; audit scope and verify patch application with `git apply --check`.
2. Keep candidate JARs outside tracked `android/app/libs` and select them only for a developer-only test task; no committed binaries or default runtime substitution.
3. Register Temple and Crossroads in a candidate-only build; assert their exact names resolve from the exported custom-card index and original 100-card manifest.
4. Run the existing 11 engine and 14 gameplay tests plus `:app:testDebugUnitTest` and debug app compilation with the candidate runtime; capture exact-head XML counts and fingerprints.
5. Negative tests must reject Temple mana for non-Eldrazi payments and Crossroads colored mana for non-Devoid spells, including generic-cost laundering attempts; verify insufficient-mana state/pool preservation.
6. Validate a real offline AI-vs-AI game setup using original Eldrazi Incursion slots, and record remaining unsupported cards and engine blockers without substitution or inflated counts.
7. Open a separate draft PR for code and CI evidence. Require explicit owner approval before any shipped runtime JAR replacement, production merge, APK signing or release.

## Ownership / safety

This branch documents the gate only; it does not claim the integration is implemented. Claude's deck-builder/weekly-upgrade lanes and other GPT/Copilot card owners remain untouched. No new paid workers or dispatch; unknown usage liabilities fail closed.

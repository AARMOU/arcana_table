# B142 repaired-engine integration checkpoint — 2026-11-05

Owner selection: issue #81 comment 6668785945; source review: PR #970 comment 6668118963.

This branch starts at delivered preview 326b92f and retains its cards and hand/combat UI.
It carries the old B142 compatibility checks (1145678 + 05cdd6d), not the old B142 jars,
and the reviewed repair's patch series, rerere records and upgrade tooling from
61a4cbf0208c07ee121efb1e14fef404c2e6feab. UI Designer is excluded.

## Pending serialized Red Bull validation

Build the exact audit target 173a09a2ed15763dce854e385b2df0d085e03975 with all
MANDATORY patches from this branch. Its upstream committer time is 2026-11-05T06:32:00Z;
verified at approximately 20:45Z it is less than 24 hours old. Recheck age at delivery.
Historical dry-run target 6c9a5c is distinct and must not be relabeled as this target.

Use an isolated Red Bull tree and the existing single-heavy-job protocol. Strict patch audit,
SDK/rules tests and four runtime jars/library export must pass. Install using weekly.py;
align Android Kotlin/serialization dependencies upward using check_android_engine.py;
verify Android bytecode/API compatibility; regenerate SDK index and vendored docs from the
actual engine; reconcile custom cards without hand-editing overrides. Run full custom-card
and app tests, including BuildCompatibilityTest and the delivered UI regressions.

Return exact base/result commits, upstream SHA, patch audit, test totals/skips, jar hashes,
generated-data changes and Android compatibility evidence to Elmer. Do not push into this
integration branch, sign, publish, delete B141 or start a second heavy job.

Elmer then reviews the staged result, sets versionCode142 and an accurate dated What's New,
runs fresh GitHub validation on the complete candidate, and coordinates one final debug
APK/Drive/hash/readback delivery. This source checkpoint still ships B141 jars and metadata;
it is NOT a completed B142 app and must not be distributed as one.

Their Number Is Legion #947 remains excluded pending its separate owner merge permission.
B15/B16 dispatch changes are independent and are not silently enabled by this engine order.

## Local checkpoint checks

Python UTF-8 mode: 24 targeted upgrade/Android-check tests passed, with 7 existing
Java-tool-dependent skips. Android regex scan: 852 sources, zero issues; 12 regex tests pass.
Full Kotlin/engine/app tests remain pending on the staged runtime.
test_weekly_imports' Git rebase exec test fails on Windows because its unquoted native paths
lose backslashes in Git's shell; do not call the complete tooling suite green. Initial
CP1252 failures in reconciliation disappear with Python -X utf8; retain UTF-8 mode on PC.

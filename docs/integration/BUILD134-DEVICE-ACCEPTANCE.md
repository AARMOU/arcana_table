# Build 134 — manual device acceptance and evidence protocol

**Status: protocol only; no device run or release authorization.** Control point: [issue #81](https://github.com/AARMOU/arcana_table/issues/81). This protocol complements the isolated release-source CI in [draft #260](https://github.com/AARMOU/arcana_table/pull/260) and the manual-only staged debug preview in [draft #254](https://github.com/AARMOU/arcana_table/pull/254). It is not a request to run either workflow, merge code, sign, upload, or publish anything.

## Admission gates (stop on any unknown)

1. Record the **exact** candidate source commit, upstream Argentum source commit, installed four-JAR checksums, Android versionCode/versionName, workflow run/job IDs, and completed CI conclusion. Do not substitute a previous head's green run.
2. Obtain and inspect the actual #260 CI test XML and package evidence: upgraded source/patch tests, custom cards, original-deck AI terminal-state scenarios, full debug Android tests, release-only capability tests, four tutorial setup tests **and two real-host priority tests** in **each** debug/release variant, lintRelease, unsigned APK/AAB packaging, production application ID, original launcher assets, and byte-identical 20-chapter tutorial catalog/glossary. Any failed, missing, or skipped required case blocks acceptance.
3. Keep 94/100 **indexed** original Eldrazi Incursion slots distinct from Oracle-complete gameplay coverage. Never describe the remaining six as implemented or infer playability from index presence.
4. The #254 workflow is **manual-only**. An explicit owner decision is required **before initiating** the debug preview build or distributing any artifact. A green #260 source-validation run is not itself permission to build or release.
5. Preserve first-release production scope: **two-player**, premade decks, offline AI; deck import and four-player are not exposed. The separate #268 internet-multiplayer work is excluded from this candidate.
6. Record the actual installed APK SHA-256 and package name. The preview is separately debug-signed (`com.arcanatable.argentum`); the Play release is `com.arcanatable`. Never conflate preview signing with the original Play upload key.

## Source-join drift and candidate identity gate (2026-10-26)

The original Build 134 preview and a later **all-latest** candidate are different acceptance targets. Never carry the preview's test results or packaging identity forward to a newly assembled source tree.

| Lane | Exact evidence | What it proves / does not prove |
| --- | --- | --- |
| Frozen Build 134 source gate | Draft [#260](https://github.com/AARMOU/arcana_table/pull/260) at `b32e499a65f0a4a9b70367a67ea00f91eef8dbaa`; [run 40269834547](https://github.com/AARMOU/arcana_table/actions/runs/40269834547) | Passed the original `c38f1453`-based Argentum/Eldrazi 94-indexed-slot source and unsigned packaging gates. It is not a validation of a subsequent weekly engine. |
| Existing installable preview | [run 40273659781](https://github.com/AARMOU/arcana_table/actions/runs/40273659781), source `2ec96ac9246ddce1412f89bf4f39f5676d309b8d` | Debug APK was uploaded and hash-checked. Physical Fold gameplay remains untested; this artifact must not be relabeled as a new all-latest build. |
| New development engine | `gpt/arcana-integration-next@f9bfdfdea4608839ad7e98ffda89de807bf5d077`, upstream `ffebf419be4d4bc0ead60548df474d0847619c4f`; [weekly run 40345172596](https://github.com/AARMOU/arcana_table/actions/runs/40345172596) | Weekly development gates passed. This does **not** certify downstream Build 134 Eldrazi patches, 94 indexed original slots, app/AI/tutorial compatibility, or an APK. |
| Proposed next-APK source-only join | Draft [#278](https://github.com/AARMOU/arcana_table/pull/278) at `6c455fe70f76cd6e39c716ee5e94ceb077544986`; [run 40348094390](https://github.com/AARMOU/arcana_table/actions/runs/40348094390), artifact `12661708873` | Source conflict reconciliation and a patch artifact succeeded. **No engine binary replacement, Android compilation, APK packaging, installation or online session** was proven by this source-only run. |

**Fail-closed promotion rule:** For a new candidate, record the exact combined source SHA, merged source pins, upstream engine SHA, installed four-JAR hashes, compiled debug/release test XML, Eldrazi coverage evidence, tutorial and release-feature checks, and final package/certificate metadata **from that same combined run**. If the compiler still rebuilds `c38f1453` while the source join claims `ffebf419be`, mark **HOLD — engine/runtime mismatch**; never call the candidate current-engine validated. A source-only patch cannot substitute for compilation. Do not manually dispatch an APK build or alter the active #278 ownership lane under this protocol.

**Scope separation:** This document certifies only the frozen **two-player / premade / no-import** Build 134 release baseline. A later owner-directed 2–4-player online preview is a **different candidate**: it requires its own explicit release-scope decision, release-capability-policy reconciliation, matching combined build, per-seat hidden-information and cross-network/reconnect acceptance, and separate Worker deployment authorization. Neither its source-only success nor an isolated #268 CI pass waives these gates. Preserve both plans without silently widening the already validated Build 134 baseline.

## Later owner-directed release-look preview — separate experimental acceptance lane (2026-10-26)

This section records **newly observed owner-authorized activity**, not an authorization from this protocol and **not** a change to the frozen first Build 134 release scope. The first production release remains two-player, premade-deck-only, offline-AI, with deck import disabled and four-player excluded.

- The separately authorized **all-latest integrated debug candidate** [run 40353390125](https://github.com/AARMOU/arcana_table/actions/runs/40353390125), job `121679230807`, completed **SUCCESS** and uploaded `arcana-next-apk-build134-ai-ui-online-debug` artifact **12665934267**. Workflow source was [#278](https://github.com/AARMOU/arcana_table/pull/278) at `6c455fe70f76cd6e39c716ee5e94ceb077544986`. This is **not** the historical #254 preview and does not establish device/cross-network acceptance. Record the actual compiled upstream pin and runtime JAR hashes from this run before making any engine-version claim.
- The owner separately requested a **mascot-free, release-looking follow-up**. Isolated workflow `.github/workflows/release-look-followup-build134.yml` at `b82aa9fef429f395d6a6c5b0a835731cceaed44a` ran [40359839369](https://github.com/AARMOU/arcana_table/actions/runs/40359839369), job `121701983988`, and **COMPLETED FAILURE**. Raw log: `:app:compileDebugUnitTestKotlin FAILED`, `HomeScreenSizesTest.kt:5:33 Unresolved reference 'assertDoesNotExist'` (16:49:16 UTC). The runner's mascot-removal transform switched the home assertion from `.assertIsDisplayed()` to `.assertDoesNotExist()`; the combined test source also contains an unresolved top-level Compose test import. Earlier engine build, engine regression and custom-card tasks succeeded, but the full Android tests and later package/signature gates did **not** finish. The only uploaded artifact is diagnostic `argentum-eldrazi-source-compatibility` **12668228575**, ZIP SHA-256 `36eb47af3a0322c66ac818f5f1fb52a9e5da63e7d77d31ef5f489e8ae4deac26`; **no debug or release-layout APK was produced by this run**. Existing release-look owner should remove the invalid import in the runner-combined test (preserve the `onNodeWithTag(...).assertDoesNotExist()` assertion and all four phone/fold cases), then compile/test on a *new exact head* before requesting any owner-authorized build. Do not edit that owner's workflow from this acceptance lane. The intended future outputs remain (a) full-feature debug `com.arcanatable.argentum` and (b) **test/debug-certificate-signed release-variant** `com.arcanatable`, neither a Play upload-key-signed release.
- A **newer weekly Argentum upgrade** to upstream `7fb073337e4389cfabe2ebf4c6416633a7fc63d2` passed its *development* [run 40356479587](https://github.com/AARMOU/arcana_table/actions/runs/40356479587), validation job `121690199525`: app XML reported **764 tests, zero failures/errors, four skips**; publish and verdict jobs also succeeded and #81 records the automatic development merge. This occurred **after** the earlier `ffebf419` source-join freeze. Neither the successful earlier combined APK run nor the active follow-up may be called `7fb073337e`-validated without a matching exact-head compiled source/runtime/JAR evidence chain. Do not auto-promote the weekly upgrade into the frozen release.
- The mascot-free follow-up requires **no goose/duck home-screen banner** while retaining the original Arcana launcher logo and existing tutorial/QA entry points. Test the actual installed artifacts, not only source assertions. A release-variant **test-signed** `com.arcanatable` package may collide with an existing production install because the signing certificate differs: do not overwrite or uninstall a production app to test it. Use a separately controlled device/profile with backed-up app data and record the installed certificate fingerprint.
- The experimental online/four-seat functionality must be assessed separately from release policy. Compile-time feature flags and debug/release capability guards can intentionally differ even when the source contains multiplayer. Record what the **release-variant APK actually exposes**. Do not describe cross-network four-player as working until two real devices on separate networks, a separately owner-authorized relay deployment, seat-private state checks, reconnect, and lifecycle behavior have been verified. No Worker deployment is authorized by this protocol.

**Separate new evidence (not release acceptance):** [draft Ugin current-engine test PR #281](https://github.com/AARMOU/arcana_table/pull/281) at `eaad4f7a3ba287a3cc34883218f6c88d52554ab8` passed [run 40367826423](https://github.com/AARMOU/arcana_table/actions/runs/40367826423), job `121730322359`. The raw log's XML validator reports **22 Ugin real-engine tests plus 304 Android debug tests, zero failures** and deterministic export; evidence artifact `12670479722`, ZIP digest `9306aa5deb5868b40dbd9e43fd0a8e01375ba3df41ad9782680bd534e4f4c7fa`. This is runner-only candidate validation against upgraded development Argentum `7fb073337e`, **not** shipped registration, a combined release build or device play. Keep Ugin integration with its existing #150/#153 owner.

**Owner-approved relay status (separate from frozen B134):** [Cloudflare deployment run 40365400541 attempt 2](https://github.com/AARMOU/arcana_table/actions/runs/40365400541), job `121731774115`, reports the directed relay deployed with public health and directed WebSocket privacy tests passing at the pinned #268 Worker source `6641f1883f54ca3a13c8faa7ef0b01b05976ef74`. This **does not** complete L04: real Android phones on distinct networks, seat privacy, reconnect and lifecycle remain NOT RUN here. Do not deploy again or modify #268 from this lane.

**New tutorial admission check:** [draft #282](https://github.com/AARMOU/arcana_table/pull/282) at `8e4702065dff96ba7ce0a64cd5d420fb54def43d` identifies a step-2 **HAND** callback missing from Table overview. Its source-only proposed fix is not a CI/device pass. During D03, explicitly run **Your First Turn**, tap HAND from both Table overview and Player view, verify exactly one advance to **Play a land**, and verify normal HAND behavior outside tutorials on cover and unfolded displays. Mark HOLD if either path stalls; do not infer that #282 is integrated into the installed candidate.

**Additional preview-only physical checks** (never substitute these for D01–D10 on the frozen candidate):

| ID | Scenario | Required observation |
| --- | --- | --- |
| L01 | Install each new preview on a controlled test device; compare SHA-256, application ID, versionCode, signing certificate and launcher | Correct artifact identity, original logo, **no home-screen goose/duck banner**, no unintended production-app replacement. |
| L02 | Exercise tutorial, QA picker, two-player AI and saved-game resume in each variant | Existing training/priority behavior survives source integration; note debug-versus-release capability differences and actual evidence. |
| L03 | Inspect multiplayer and deck-import entry points in the release-variant test-signed APK | Record actual flags and visibility. Any four-player exposure is a **separate experimental scope**, not first Build 134 release approval; deck import remains outside first-release scope. |
| L04 | When relay deployment is separately authorized, test two devices across distinct networks, directed private state, seat ownership, disconnect/reconnect | Only device/network evidence can pass this gate; otherwise mark **BLOCKED / NOT RUN**, never inferred from CI. |

## Physical acceptance matrix — perform on the actual installed candidate

Record **PASS / FAIL / BLOCKED / NOT RUN** for every row. A test not run is not a pass. Run both on the Fold's cover display and unfolded display where relevant; additionally exercise portrait/landscape. Preserve reproducible steps and sanitized diagnostics for failures.

| ID | Scenario and actions | Required observation |
| --- | --- | --- |
| D01 | Fresh install, cold launch, then relaunch; inspect initial tips/tutorial overlay | Visible controls and dismiss action match the current UI; no stale `Dismiss` assumption, inaccessible Play button, or modal trapped behind another overlay. |
| D02 | Cover portrait, cover landscape, unfolded portrait, unfolded landscape; open Home and Options | Launcher/branding, safe areas, button hit targets, layout, scroll, and deck selection remain usable without clipping or offscreen controls. |
| D03 | Open each of four offline practice drills, leave, reset, and return | Real Argentum-backed state is shown, no simulated resolution, and exit/reset restore normal gameplay. |
| D04 | Practice stack drill: target Grizzly Bears with Giant Growth, inspect stack, explicitly pass priority | Learner retains the actual priority window; spell resolves only through real engine priority and reaches the correct destination. |
| D05 | Start an ordinary non-tutorial match with normal own-stack preference | Tutorial-specific hold does not leak into normal auto-pass behavior or silently rewrite the saved preference. |
| D06 | Start 1v1 with offline AI using bundled premade decks; play through combat, mana, stack, graveyard and turn transition | Game progresses with correct seat/target visibility and no invalid action, frozen priority, or unexplained auto-pass. Record seed and terminal state where available. |
| D07 | Find release UI entry points for deck import and four-player/networked multiplayer | **Absent/disabled** in the production candidate. Debug-only scenario picker must not leak into release. |
| D08 | In a developer preview only, open scenario picker from its intended entry point | Picker is reachable without hunting below deck controls, can launch and exit, and does not break Home layout. Do not demand this picker in production. |
| D09 | Open diagnostic/notification controls; rotate/fold, background/foreground, resume | Preferences persist, notifications and overlays are bounded, no modal is stranded or duplicated. |
| D10 | Review the original launcher icon, round icon, and the 20-chapter tutorial/glossary on device | Installed visuals and tutorial content match the reviewed source, not merely the CI-side ZIP inspection. |

## Exact preview evidence and unresolved test exclusions (2026-10-26)

This is a **historical, SHA-pinned evidence record**, not a fresh build authorization or a substitute for device acceptance.

- Owner-authorized debug-preview source: `2ec96ac9246ddce1412f89bf4f39f5676d309b8d`, [run 40273659781](https://github.com/AARMOU/arcana_table/actions/runs/40273659781), job `121411708015` **success**. Uploaded debug APK artifact `12630376500` and source/test evidence artifact `12630381547`; the APK is the **debug-signed** `com.arcanatable.argentum` versionCode 134 preview, not a signed production `com.arcanatable` release. Recorded APK SHA-256: `573d41ceeff5e568f555f0000850b170f3eb1a354352fea877d78f8172b7f274`. Verify the downloaded byte stream and installed package again before testing.
- Independently audited JUnit evidence in [issue #81](https://github.com/AARMOU/arcana_table/issues/81#issuecomment-6412670594): **1,018 passed, four skipped, zero failed/error** across 351 available XML suites. This count is not 1,022 executed/passed and does not prove any physical UI or network session.
- **Skipped `LiveArgentumGameTest.playRealArgentumGameThroughArcanaUi`:** do not claim full live UI/engine gameplay from unit CI; capture a device-backed match with actual stack/priority, zone changes and terminal-state evidence (D06), or mark this acceptance item **BLOCKED** with the exact instrumentation prerequisite.
- **Skipped `LiveArgentumGameTest.unsupportedCardOpensReviewAndSandboxFallback`:** do not infer a passing fallback flow. Determine whether this is even an allowed production path under **premade decks only / import disabled**. If in scope, exercise a safely isolated debug-only negative scenario and capture the visible result; if excluded, record a reasoned release-scope exclusion rather than treating a skip as a pass.
- **Skipped `OnlineRelayTest.hostAndGuestTalkThroughTheRelay` and `OnlineRelayTest.droppedPhonesReconnectAndCatchUp`:** explicitly **out of scope** for the first **offline/two-player** Build 134 candidate; these are not passed tests and do not block the offline release *if* production UI/network capabilities remain absent (D07). Route multiplayer acceptance to its existing #268 owner; do not spawn a second worker, deploy a relay, or silently fold multiplayer into the release candidate.

For every skip, preserve its name, classification (**REQUIRED DEVICE EVIDENCE / EXPLICIT RELEASE-SCOPE EXCLUSION / BLOCKED**), owner, and evidence reference in the final acceptance report. A skipped required case without replacement evidence is a **HOLD**, even when the parent workflow is green. The Fold smoke and production-scope checks are still **NOT RUN** in this record.

## Evidence record for each physical run

Use a private evidence store for any screenshots, diagnostic logs, device identifiers, or sensitive card/game state. Post **links and sanitized summaries only** to #81.

```text
Date/time (UTC):
Tester:
Candidate lane (frozen B134 / newer all-latest) and exact combined source SHA:
Pinned constituent branch SHAs / engine version actually compiled:
Upstream Argentum SHA / installed four-JAR SHA-256:
Source-only vs compiled-and-packaged evidence classification:
#260 workflow run + job + conclusion + artifact evidence:
#254 invocation authorization + run ID (if approved):
Installed APK SHA-256 / application ID / versionCode:
Device form factor / Android version:
Test IDs and PASS|FAIL|BLOCKED|NOT RUN:
Steps to reproduce failures + expected/actual:
Sanitized diagnostics/trace reference:
Disposition: ACCEPT FOR OWNER REVIEW | HOLD (reason):
```

## Separate signing and Play gates — not delegated by this protocol

Unsigned release-source packaging is **not** an installable Play release. The owner must separately approve final source, retrieve the **original** upload key through a private channel, sign the final `com.arcanatable` AAB, and independently compare its upload certificate fingerprints with the previously verified original Play identity recorded in #81. Never substitute a newly generated key, expose keystore/passwords in GitHub, or publish from a preview workflow. Explicit owner approval is required again before any Play Store submission.

**Completion definition:** exact-head source CI green **and** all required physical checks evidenced **and** signing/identity gate independently verified **and** separate owner release approval. Until then, label the candidate *validation only*, not released, certified, or production-ready.

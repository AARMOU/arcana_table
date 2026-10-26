# Build 134 — manual device acceptance and evidence protocol

**Status: protocol only; no device run or release authorization.** Control point: [issue #81](https://github.com/AARMOU/arcana_table/issues/81). This protocol complements the isolated release-source CI in [draft #260](https://github.com/AARMOU/arcana_table/pull/260) and the manual-only staged debug preview in [draft #254](https://github.com/AARMOU/arcana_table/pull/254). It is not a request to run either workflow, merge code, sign, upload, or publish anything.

## Admission gates (stop on any unknown)

1. Record the **exact** candidate source commit, upstream Argentum source commit, installed four-JAR checksums, Android versionCode/versionName, workflow run/job IDs, and completed CI conclusion. Do not substitute a previous head's green run.
2. Obtain and inspect the actual #260 CI test XML and package evidence: upgraded source/patch tests, custom cards, original-deck AI terminal-state scenarios, full debug Android tests, release-only capability tests, four tutorial setup tests **and two real-host priority tests** in **each** debug/release variant, lintRelease, unsigned APK/AAB packaging, production application ID, original launcher assets, and byte-identical 20-chapter tutorial catalog/glossary. Any failed, missing, or skipped required case blocks acceptance.
3. Keep 94/100 **indexed** original Eldrazi Incursion slots distinct from Oracle-complete gameplay coverage. Never describe the remaining six as implemented or infer playability from index presence.
4. The #254 workflow is **manual-only**. An explicit owner decision is required **before initiating** the debug preview build or distributing any artifact. A green #260 source-validation run is not itself permission to build or release.
5. Preserve first-release production scope: **two-player**, premade decks, offline AI; deck import and four-player are not exposed. The separate #268 internet-multiplayer work is excluded from this candidate.
6. Record the actual installed APK SHA-256 and package name. The preview is separately debug-signed (`com.arcanatable.argentum`); the Play release is `com.arcanatable`. Never conflate preview signing with the original Play upload key.

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

## Evidence record for each physical run

Use a private evidence store for any screenshots, diagnostic logs, device identifiers, or sensitive card/game state. Post **links and sanitized summaries only** to #81.

```text
Date/time (UTC):
Tester:
Candidate source SHA:
Upstream Argentum SHA / installed JAR SHA-256:
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

# Automatic approved-card integration — NO APK

Owner-authorized October 28, 2026 (Mountain), in the conversation about making the existing Verify/Approve click sufficient for card-only integration. Coordination and results: issue #81. This policy authorizes the tested setup and card-only fast-forward merges into the designated Android development branch. It does **not** authorize APK/AAB builds, signing, distribution, releases, runtime JAR changes, paid AI workers or production Worker deployments.

## What runs automatically

`Arcana Approved Card Integration (NO APK)` runs after the existing review-inbox publisher, on a 15-minute reconciliation schedule, and when its trusted main implementation changes. GitHub schedules can be delayed; no exact pickup-time guarantee. There is also an optional manual retry of an unchanged blocker. PR premerge probes run the same planner and real-engine checks but cannot publish or merge.

One approved card is processed at a time under workflow-wide concurrency. The planner checks current PR/head, owner AARMOU's immutable GitHub identity, latest meaningful owner review, trusted queue provenance, required successful source CI and exact trusted independent-certification identity. It selects only registered independent suites. The first registry contains Resurrection Orb; registering reviewed suites remains necessary for other cards. Existing approvals are reconciled, including #410; no repeat click is required for an unchanged approved source.

The source PR currently targets `gpt/arcana-integration-next`; destination is explicitly `gpt/b140-card-request-test-apk-20261028`, initially `310f7dd6a7f1829aa90955af02c2cd48fb8d1a61`. The destination must remain descended from that seed and its four actual engine JAR hashes must match trusted `policy.json`. This bridges the currently different sources by retesting on the destination engine; it does not switch active worker settings, copy the old engine, merge old UI, or assume source certification transfers.

## Card-only scope and destination proof

The forward-port permits exactly one new card definition, its scenario-test file, and exactly one added registry entry. Source PR edits to JARs, workflows, app UI, other cards, build scripts or tests are ineligible. Old exported assets and pattern indexes are never copied wholesale: the destination exporter and Cardkit generator regenerate them. Existing exported card definitions must be semantically unchanged; only the approved new card may be added.

Destination gates are mandatory:

1. Fixed actual engine JAR hashes; clean exact Android base; unchanged approved source-file list; no symlinks or unsafe paths.
2. Actual `:custom-cards:exportCards` and regenerated pattern indexes.
3. Cardkit unit tests/check/docs-check plus Android regex checks.
4. New card's real-engine positive/negative scenario class, with executed JUnit cases and no failures/errors/skips.
5. Trusted-main independent suite injected into the destination checkout, with exact registered case count and no failures/errors/skips.
6. Complete `:custom-cards:test` and `:app:testDebugUnitTest` with `--continue`, preserving existing test behavior and failures. The existing two release-safety/branding classes also must pass.
7. Seven-file output allowlist, clean diff, stable existing exported card definitions and matching JSONL/index names.

Known B140 baseline failures remain blockers; they are not accepted as green and no assertions are weakened. A blocked attempt records the precise gates and JUnit failures on the original card PR and #81. The dashboard retains Owner Approved while showing **Integration blocked**, its reason and the destination-run link. It never calls a blocked card integrated or APK-playable.

## Write isolation and merge lease

The candidate code and Gradle run in a read-only job with no stored Git credentials, write token or external secrets. A separate trusted publisher independently reconstructs the three approved source files without executing candidate code. It checks the artifact allowlist/hashes, every gate, exact plan hash, live approval/source/certification, trusted policy/code hash, no main change to integration control paths since planning (unrelated main merges are tolerated) and unchanged destination base. It creates one commit whose sole parent is the tested Android base, then updates only the designated branch with `force:false`. A concurrent target writer therefore causes non-fast-forward rejection, never an untested merge. No `main` or release-branch target is permitted by policy.

An authenticated GitHub Actions receipt records original PR/source SHA, approved review, destination base/commit, test run and time. Dashboard Integrated status also verifies that destination commit remains an ancestor of the live Android branch. The source PR remains open on its older base to preserve ownership/duplicate-work claims; its integration receipt is authoritative for Android promotion, not a claim GitHub merged the legacy PR. Verified receipts feed the existing daily-card activity count.

An unchanged blocked source/base/trusted-policy combination is not retried every schedule, except for the single infrastructure retry described below. The planner continues looking for another eligible card. A changed Android base, changed trusted verification inputs or explicit manual retry permits fresh validation. A source change always requires a fresh exact-head owner approval. Set `enabled:false` in trusted main's `integration/policy.json` to stop new automatic integration; in-flight publishers recheck policy before writing. An engine upgrade or a different Android integration branch requires a reviewed policy change.

## Completion means source-ready

Success means the card definition, scenario tests, regenerated runtime JSONL/index and pattern indexes are committed in the designated Android branch. It does not mean the installed app has updated, nor that the card has been verified on a phone. APK packaging uses the separately prepared #430 checks and a separately authorized build.

## Tests and rollout

```sh
node --test scripts/card-integration.test.cjs scripts/review-inbox.test.cjs
python3 -m unittest discover -s integration -p 'test_*.py' -v
```

The tests cover forged/stale/revoked approval and certification, forbidden scope/targets, dirty/conflicting forward-ports, unrelated exported-card edits, missing/zero/skipped JUnit results, incomplete/tampered output, changed source/main/base/policy, non-fast-forward races, no target writes on blocked tests, receipt identity/ancestry, retry deduplication and dashboard integration evidence. The PR workflow also exercises the actual approved-card forward-port and destination runtime without APK assembly or writes. Infrastructure CI may pass while the real card is correctly reported blocked; only `report.status=passed` with every mandatory gate allows promotion.

## Bounded infrastructure retry

A blocked destination gets at most one automatic retry only when complete structured JUnit identifies exactly the original `FourPlayerLanTransportTest.threeGuestSocketsHavePrivateSendChannelsAndRejectFifthPhone` assertion (`java.net.BindException: Address already in use`), and **every other destination gate passed**. Missing or malformed evidence, additional failures, other exceptions, earlier blockers, and old receipts without this structured classification remain blocked. This deliberately narrow allowlist is based on run 41125946189/artifact 13011257764. The separate LAN port-isolation fix #537 has since removed that reproduced collision; this change supplies the missing bounded recovery contract rather than retrying Shield Sphere.

The planner verifies that the bot receipt came from this repository's completed successful integration workflow on `main`, with matching control SHA; PR/unrelated workflow receipts cannot authorize a retry. Immediately before validation a trusted job rechecks source, approval, certification, base, policy and receipt, then atomically creates the lightweight tag `arcana-integration-retry/<sha256>`. Its key binds PR, card SHA, destination/base SHA, policy hash and failure classification. The tag points at trusted control code, not a new app version. **Never delete/update these reservation tags to reset a retry.** Concurrent reference creation permits only one attempt. Existing workflow concurrency also remains in place.

A reservation is consumed before candidate code runs. Cancellation, failed comment updates, runner loss, or an interrupted publish never refund it. A GitHub rerun of an automatic attempt cannot execute destination tests again (`run_attempt == 1` guard). The source PR and existing #81 blocker note are updated in place to describe the reservation and, on a second failed report, the exhausted retry. If GitHub cannot update those notes, the failed reservation job is the escalation; recovery still requires owner review, and no repeated automatic attempt occurs. This favors bounded cost and safety over silent repeated recovery. Unknown failures remain hard blocks, and an explicit owner workflow dispatch can still request manual revalidation.

The retry executes the entire existing destination-validation job with read-only permissions. Successful publication still requires every gate, exact source reconstruction and the existing live approval/base leases. No paid agent is dispatched, no APK/AAB is assembled, and no deployment occurs. New source/base/policy identities receive ordinary full revalidation; a changed source still requires its own owner approval. Existing legacy receipts are not retroactively granted retries.

## Relevant-input retries (B9)

Blocked integration receipts now record a per-card `retryInputHash` and an explicit `primaryCause`. The hash covers the integration policy, trusted integration/certification runners, certification policy, shared manifest settings, and this card's suite definition and test. Adding or editing another card's suite does not reset it. The full policy hash, exact-head checks, owner approval, destination lease and every publication gate still apply independently.

For the same approved source and trusted retry inputs, advancing the destination only permits another validation when its complete Git comparison changes the registry, this card's source/scenario, engine JARs, upstream card index or Android build configuration. Renames check both paths. Unrelated destination commits stay quiet. Missing/truncated comparisons never justify suppression; API failures stop planning. Legacy receipts use their full policy hash until a fresh receipt supplies the per-card hash. Already-registered cards retain their visible blocked disposition; no divergent card is treated as integrated.

A blocked report without a concrete reason or failed-gate detail is rejected before publication. Generic "not run" placeholders are not causes. The existing single, authenticated infrastructure retry and explicit owner retry remain available under their existing restrictions; this does not grant additional attempts or paid work.

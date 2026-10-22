# Arcana Table — Team Notes and Evidence Ledger

**Owner:** User. **Tracking branch:** `gpt/work-multiworker-handoff`. **Last human-authored entry:** 2026-10-21 (America/Denver).

This is a durable, append-only notebook for development team handoffs and any verified subsequent work. **Issues are assignments, not active agents.** Do not mark an agent as launched without a runner task ID, agent assignment, or observed session/PR. Do not mark tests passed without command logs and outputs.

## Team board

14 independent feature teams, with 3 roles each (A core/source, B UI/integration, C tests/review): **42 prepared roles**. The assistant's current GitHub connection did not provide an agent runner with explicit target base-branch control; work cannot truthfully be reported as started.

| Team | Goal | GitHub issue | Integration branch | Wave | Observed status |
|---|---|---|---|---|---|
| 01 | Action failure explanations | [#12](https://github.com/AARMOU/arcana_table/issues/12) | `gpt/work-log-events` | B | Queued; agent unverified |
| 02 | Engine processing progress | [#13](https://github.com/AARMOU/arcana_table/issues/13) | `gpt/work-engine-progress` | C | Queued; agent unverified |
| 03 | Token grouping | [#14](https://github.com/AARMOU/arcana_table/issues/14) | `gpt/work-token-groups` | C | Queued; agent unverified |
| 04 | Offline save/resume | [#15](https://github.com/AARMOU/arcana_table/issues/15) | `gpt/work-local-resume` | C | Queued; agent unverified |
| 05 | Turn recap | [#16](https://github.com/AARMOU/arcana_table/issues/16) | `gpt/work-turn-recap` | B | Queued; agent unverified |
| 06 | Priority stops | [#17](https://github.com/AARMOU/arcana_table/issues/17) | `gpt/work-priority-stops` | C | Queued; agent unverified |
| 07 | Reproducible bug reports | [#18](https://github.com/AARMOU/arcana_table/issues/18) | `gpt/work-repro-reports` | A | Queued; agent unverified |
| 08 | Deck support report | [#19](https://github.com/AARMOU/arcana_table/issues/19) | `gpt/work-deck-support` | A | Queued; agent unverified |
| 09 | Table UI verification | [#20](https://github.com/AARMOU/arcana_table/issues/20) | `gpt/table-manual-expand-pinch-zoom` | A | Queued; agent unverified |
| 10 | Menu layout cleanup | [#21](https://github.com/AARMOU/arcana_table/issues/21) | `gpt/work-menu-layout` | A | Queued; agent unverified |
| 11 | Game-log event counters | [#22](https://github.com/AARMOU/arcana_table/issues/22) | `gpt/work-log-grouping` | B | Queued; agent unverified |
| 12 | Popup readability | [#23](https://github.com/AARMOU/arcana_table/issues/23) | `gpt/work-popup-notifications` | A | Queued; agent unverified |
| 13 | Targeting warning | [#24](https://github.com/AARMOU/arcana_table/issues/24) | `gpt/work-targeting-alerts` | B | Queued; agent unverified |
| 14 | Graveyard/exile casts | [#25](https://github.com/AARMOU/arcana_table/issues/25) | `gpt/zone-casting-graveyard-exile` | A | Queued; agent unverified |

### Sequencing
- **Wave A:** 07, 08, 09, 10, 12, 14 — relatively separate work, including verification of existing Table UI and graveyard/exile implementation.
- **Wave B:** 01, 05, 11, 13 — log/event schema collaboration and separate targeting alerts.
- **Wave C:** 02, 03, 04, 06 — engine/persistence-sensitive workloads with higher equivalence requirements.
- The log producer (01) and log aggregator (11) share an event schema contract; the ordinary notification popup (12) and targeting alert (13) must remain separate components.
- The board (09) and token stacks (03) must coordinate layout file ownership. Diagnostics (07) and local saves (04) use separate public/private formats.
- `gpt/argentum-six-precons` and engine-performance branches remain independent and must not be conflated or merged without explicit permission.

## Chronological notes

### 2026-10-21 (America/Denver) — User authorized kickoff + continuing notes

**What was actually done:**
- Confirmed that the currently exposed tools can create branches/issues, inspect files, and edit repository documentation, but do **not** expose a direct autonomous worker launcher with safe feature-branch targeting.
- Created GitHub issues **#12–#25**, one per feature team, with A/B/C role specifications, branch ownership, evidence requirements and regression gates. All are **queued, not assigned to running agents**.
- Preserved existing feature branches; did not merge or publish.
- Added team **14** for graveyard/exile casting, which has source modifications and tests committed on `gpt/zone-casting-graveyard-exile` but **no confirmed compilation, automated test execution, or device validation**.
- The preexisting Table view changes on `gpt/table-manual-expand-pinch-zoom` are similarly **not confirmed compiled/device-tested**.
- Existing engine performance optimization and six-precon-card work are not assumed to be running or finished.

**Blocker to actual agent launch:** A coding-agent runner that can select `gpt/work-*` branches/worktrees and return a verifiable task or PR id. The current connector's issue-assignment action cannot set that per-task base branch. Do not silently assign agents to `main`.

**Open integration and correctness rules:** Preserve legal actions, game states, hidden information, trigger counts, legitimate tokens and AI capabilities; no approximations, skipped events or release builds. Every completed task requires an independent code review and **executed** tests, not a written checklist.

### 2026-10-21 22:56 (America/Denver) — Notes monitoring enabled

- Created an hourly **condition-based** Arcana team notes check covering issues #12–#25, branch/PR changes, test evidence, meaningful blockers and independent review.
- It is configured to **append only verifiable developments** to this ledger and notify only about meaningful changes. It has not run yet; no future success or GitHub edits are assumed.
- This scheduled monitor is **not** a coding agent, and none of the 42 coding roles has a verified launch/task ID.
- No source changes, merges, signing operations or releases were performed as part of the monitoring setup.


### 2026-10-21 ~23:00 (America/Denver) — Attempted actual GitHub coding-agent launch

**Direct request from user:** Start workers immediately; no further handoffs or only preparing assignments.

**Verified attempts / outcomes:**
- Opened draft parent PR [#26](https://github.com/AARMOU/arcana_table/pull/26) for Team 09 (Table view) from `gpt/table-manual-expand-pinch-zoom`, with base `gpt/build128-mana-reliability`. Posted explicit `@copilot` task comment, GitHub comment ID `6333111061`. At check time, no Copilot 👀 reaction, child PR or confirmed agent session.
- Opened draft parent PR [#27](https://github.com/AARMOU/arcana_table/pull/27) for Team 14 (graveyard/exile) from `gpt/zone-casting-graveyard-exile`, with base `gpt/table-manual-expand-pinch-zoom`. Posted explicit `@copilot` task comment, GitHub comment ID `6333120119`. At check time, no Copilot 👀 reaction, child PR or confirmed session.
- Attempted `mcp__GitHub__add_issue_assignees` for Team 07 issue [#18](https://github.com/AARMOU/arcana_table/issues/18) with assignee `copilot-swe-agent[bot]`. GitHub returned **HTTP 403 Forbidden**. Assignment was not confirmed; no agent launched.
- GitHub's Copilot issue-assignment API supports specifying the base branch via `agent_assignment.base_branch`, but the presently available typed GitHub connector action has no such parameter. Creating assignment without it would risk starting the wrong branch. The separate GitHub Agents Tasks API requires its own user-level agent authorization not exposed here.
- **Observed running agents: ZERO.** GitHub issues are ready; draft PRs are not an execution result. Keep monitoring for concrete sessions/child PRs before changing the status.
- There were **no production merges/releases**, no APK/AAB creation, no signing changes. Feature code was unchanged during launch attempts.

**Actual launch blocker:** Insufficient permission to assign Copilot (403), and no accessible agent-tasks POST action with a base-ref option. If GitHub web browser or a properly authorized coding-agent account becomes available, launch based on each existing development branch, following the A/B/C role plan.

**Next:** Confirm authorizations, start an actual worker session using GitHub's Agents UI or an authorized agent tasks API, and record task ID + real base branch + first commits. Do not substitute GitHub issue comments for proof of agent execution.

### 2026-10-21 — Copilot activation constrained; direct implementation began on two isolated branches

- Verified GitHub's official agent assignment API accepts `agent_assignment.base_branch` only when invoking the complete REST or GraphQL mutation. The available connector's typed issue-assignment call does not expose this field; prior attempted Copilot assignment returned **403 Forbidden**. The command-line GitHub client and a separate authenticated Copilot agent runner are not available in this workspace. No attempts to bypass authorization or switch the default branch.
- PR [#26](https://github.com/AARMOU/arcana_table/pull/26) and [#27](https://github.com/AARMOU/arcana_table/pull/27) have `@copilot` comments but no observed agent response, no 👀 reactions and no Copilot-authored child PR as of this check. **0 actual Copilot workers confirmed launched.**
- Started **direct source implementation** (not running workers) on the independently owned feature branches:
  - [Team 10 issue #21](https://github.com/AARMOU/arcana_table/issues/21), `gpt/work-menu-layout`: fixed missing/JSON-null/blank decision labels, stacked cramped BatchYesNo buttons vertically and added `DecisionDisplayTextTest.kt`. Commits: `ceb51dc0`, `c3cb6e9b`, `75717412`. Regression tests created, **not executed**; UI/layout audit still open.
  - [Team 11 issue #22](https://github.com/AARMOU/arcana_table/issues/22), `gpt/work-log-grouping`: added conservative semantic identities to existing Argentum log records, immutable consecutive display grouping, counted/expandable game log UI (paginated for heavy trigger floods), and `GameLogGroupingTest.kt`. Commits `6c926d57`, `43e473ea`, `3d9dc5c6`, `0b5b64ac`, `123927b9`. Original events retained; no sandbox grouping without event identity. Tests **not executed**.
- Issues #21/#22 now have evidence comments distinguishing source commits from actual agent execution. No merge, APK/AAB, signing change or production deployment.
- **Next:** Obtain an authorized coding-agent session tool that can supply per-issue `base_branch`, or continue directly implementing feature work in current turns with test verification. Do not claim agent concurrency without verified task IDs.

### 2026-10-21 — Notification readability workstream committed directly

- [Team 12 issue #23](https://github.com/AARMOU/arcana_table/issues/23), branch `gpt/work-popup-notifications`, new source commits `849cb820`, `89c9ab96` and test commit `ed92f6c0`.
- Long text in card popup, top-center ability banner and turn recap no longer uses two/four-line truncation; it can scroll within a bounded-height notice.
- Transient notice duration now has a readable minimum, scales with content length and caps at 30 seconds. Critical decisions remain separate from transient notices. Dedicated targeting alerts are **not** bundled into the popup changes.
- Added `ReadableNotificationDurationTest.kt`; **not executed**; notification queues and multi-event deduplication remain outstanding.
- No coding-agent session was launched or confirmed. These are edits by ChatGPT via the connected GitHub repository.
- No feature branch merge, APK/AAB, release, or production change.

### 2026-10-21 ~23:26 (America/Denver) — Android test execution, actual GitHub Actions evidence

Real CI workflows were created and automatically executed on each isolated development branch; no release builds or production merge.

- **Team 10 menu cleanup:** [GitHub Actions run 39727464475](https://github.com/AARMOU/arcana_table/actions/runs/39727464475); commit `5b2669b3ab06dee75b11250ab7ef1d0773a19c30`; executed `./gradlew --no-daemon --stacktrace --max-workers=2 :app:testDebugUnitTest --tests '*DecisionDisplayTextTest*'`; **BUILD SUCCESSFUL** (2m57s). App Kotlin and test Kotlin compiled; artifacts uploaded.
- **Team 11 log grouping:** [GitHub Actions run 39727467578](https://github.com/AARMOU/arcana_table/actions/runs/39727467578); commit `74fe1e2e139bce8375bfe0e258dd5b9a658c43da`; executed `./gradlew --no-daemon --stacktrace --max-workers=2 :app:testDebugUnitTest --tests '*GameLogGroupingTest*'`; **BUILD SUCCESSFUL** (2m36s). App and test Kotlin compiled; test reports uploaded.
- **Team 12 notification timing:** [GitHub Actions run 39727471127](https://github.com/AARMOU/arcana_table/actions/runs/39727471127); commit `88b8894eb45b189ce18f8623cb3044baad80f677`; executed `./gradlew --no-daemon --stacktrace --max-workers=2 :app:testDebugUnitTest --tests '*ReadableNotificationDurationTest*'`; **success**. Test artifacts uploaded.
- **Team 09 Table view:** [Run 39727721906](https://github.com/AARMOU/arcana_table/actions/runs/39727721906); checks `TableViewNavigationTest` and `TableOverviewInteractionTest`; **running at this note entry**, no result yet.
- **Team 14 graveyard/exile:** [Run 39727726145](https://github.com/AARMOU/arcana_table/actions/runs/39727726145); checks `ZonePlaySourceTest`, `GraveyardExileLegalActionsTest`, `ZoneCardActionsUiTest`; **running at this note entry**, no result yet.
- These are **focused CI unit/UI JVM tests**, not an all-project regression suite or on-device test. No autonomous Copilot agent was launched.

### 2026-10-21 ~23:32 (America/Denver) — Table/zone test failure investigation and reruns

Both initial tests compiled far enough to reveal test-harness defects. This is not evidence of actual gameplay failures.
- Table [run 39727721906](https://github.com/AARMOU/arcana_table/actions/runs/39727721906) failed **compiling the unit tests** due to non-existent top-level Compose assertion imports `assertExists`/`assertDoesNotExist`. Removed imports, next [run 39728090839](https://github.com/AARMOU/arcana_table/actions/runs/39728090839) compiled and executed, then failed at `TableOverviewInteractionTest initializationError` (`RobolectricTestRunner DefaultSdkPicker`); fixed by adding `@Config(sdk = [34])`. Latest branch commit `9d676256` and [run 39728537192](https://github.com/AARMOU/arcana_table/actions/runs/39728537192) **pending**.
- Graveyard/exile [run 39727726145](https://github.com/AARMOU/arcana_table/actions/runs/39727726145) and subsequent [run 39728096272](https://github.com/AARMOU/arcana_table/actions/runs/39728096272) failed unit-test compilation because the derived branch still had outdated TableOverview test assertion imports. Fixed inherited imports and class Robolectric config. Latest commit `8c62a3f0`, [run 39728542228](https://github.com/AARMOU/arcana_table/actions/runs/39728542228) **pending**.
- Both corrections were test-harness-only; feature source was not merged or changed. **Do not call either branch passing until green.**

### 2026-10-21 ~23:38 (America/Denver) — Four passing branch suites, Table investigation

- Team 14 graveyard/exile [Actions run 39728542228](https://github.com/AARMOU/arcana_table/actions/runs/39728542228), head `8c62a3f0`: **completed SUCCESS**. Real JVM/Android compilation and focused `ZonePlaySourceTest`, `GraveyardExileLegalActionsTest`, `ZoneCardActionsUiTest` executed.
- Teams 10, 11 and 12 previously completed **SUCCESS** in Actions runs `39727464475`, `39727467578` and `39727471127`.
- Team 09 Table overview [Actions run 39728537192](https://github.com/AARMOU/arcana_table/actions/runs/39728537192): **5 tests executed, 1 failed**; `TableOverviewInteractionTest.playerPanelsOnlyExpandWhenTappedAndStayOpenAcrossTurnChanges` failed on line 66 checking the expanded card grid's `testTag`. The element is nested inside an ancestor `combinedClickable` that merges semantics; altered test query to use `useUnmergedTree = true` (commit `929dedf5`), without changing app code. [Rerun 39729020743](https://github.com/AARMOU/arcana_table/actions/runs/39729020743) **pending**. Do not claim Table view behavior confirmed until passing.
- Focused JVM/Robolectric tests are distinct from release build, all-project regressions or real-device tests. No APK, no signing and no production merge.

### 2026-10-21 ~23:40 (America/Denver) — Five tested branches all GREEN

**Verification complete for focused tests:** All five GitHub Actions runs returned `status=completed`, `conclusion=success` with actual Android Gradle compilation and JVM unit/Robolectric execution.

| Team | Branch and tested head | Executed filter(s) | CI result |
|---|---|---|---|
| 10 Menu formatting | `gpt/work-menu-layout` — `5b2669b3` | `*DecisionDisplayTextTest*` | [39727464475](https://github.com/AARMOU/arcana_table/actions/runs/39727464475) — SUCCESS |
| 11 Log grouping | `gpt/work-log-grouping` — `74fe1e2e` | `*GameLogGroupingTest*` | [39727467578](https://github.com/AARMOU/arcana_table/actions/runs/39727467578) — SUCCESS |
| 12 Popup readability | `gpt/work-popup-notifications` — `88b8894e` | `*ReadableNotificationDurationTest*` | [39727471127](https://github.com/AARMOU/arcana_table/actions/runs/39727471127) — SUCCESS |
| 09 Table view | `gpt/table-manual-expand-pinch-zoom` — `929dedf5` | `*TableViewNavigationTest*`, `*TableOverviewInteractionTest*` | [39729020743](https://github.com/AARMOU/arcana_table/actions/runs/39729020743) — SUCCESS |
| 14 Graveyard/exile | `gpt/zone-casting-graveyard-exile` — `8c62a3f0` | `*ZonePlaySourceTest*`, `*GraveyardExileLegalActionsTest*`, `*ZoneCardActionsUiTest*` | [39728542228](https://github.com/AARMOU/arcana_table/actions/runs/39728542228) — SUCCESS |

**Failure-and-fix trace:** Earlier Table/zone CI attempts failed on invalid test imports, an unsupported Robolectric automatic SDK selection and a merged accessibility semantics query. These were diagnosed from CI logs, corrected by commits on the isolated branches and re-executed. The final five runs above are all green.

**Explicit scope limits:** These are **targeted** app test suites, not full application/engine/custom-card regressions, not real-device testing, and not a release APK/AAB build. Passing these does not prove all game rules mechanics, complex exile permissions or all screen layouts. No changes were merged into production, and no signing keys were touched.

### 2026-10-21 ~23:48 (America/Denver) — Five-feature APK integration build requested

- User authorized creation of **one combined APK**. Created isolated integration branch `gpt/arcana-five-feature-preview` at source commit `dc0cac32e07e40905794919f8b539d319a7a6472`, based on tested `gpt/zone-casting-graveyard-exile` plus tested Table UI changes.
- Integrated source/test changes from `gpt/work-menu-layout`, `gpt/work-log-grouping` and `gpt/work-popup-notifications`. Resolves overlapping files `ArgentumInteraction.kt`, `TableOverlays.kt`, `TableDialogs.kt`, `TableOverlaysB.kt`, `TableEffects.kt` without overwriting zone/Table changes; preserved the latest Table UI regression test.
- CI: [GitHub Actions run 39730056278](https://github.com/AARMOU/arcana_table/actions/runs/39730056278), executing all eight five-feature test filters plus existing `ArgentumInteractionTest` and `GameRulesTest` before compiling `:app:assembleDebug`.
- Build targets **debug-signed, separately installable `com.arcanatable.argentum`**, not the production app id/signing key. APK artifact should be `ArcanaTable-Five-Feature-Debug-APK` with SHA256 and signing provenance. No main merge, Play Store release or original key use.
- This entry documents *started* build, not a successful artifact until CI concludes. Remaining 9 of 14 feature teams have not been silently included; only five implemented/tested workstreams are part of this preview.

### Update format to use for future work

```text
YYYY-MM-DD HH:MM (America/Denver) — Team ## — [event]
GitHub: issue #; branch; commit SHA or PR #; agent/task ID if launched
Changes: filenames and what actually changed
Verification: exact test commands + pass/fail output, or "not run"
Open questions / blockers: specific reproducible facts
Next: concrete action
Production: untouched unless specifically authorized
```

**Do not erase prior entries; append verified developments and update board statuses only with evidence.**

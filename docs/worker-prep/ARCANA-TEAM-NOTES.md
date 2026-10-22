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

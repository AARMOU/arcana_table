# Arcana Table — Copilot Admission Control Design

**Status:** Approved by the owner 2026-10-23; implemented on `claude/admission-control-impl` (draft PR). Nothing is enabled: all new variables default to off.
**Author:** Claude · **Date:** 2026-10-23 · **Baseline:** `main` at `8b4ec05`
**Supersedes when approved:** `docs/usage-controller-design.md` (describes an earlier 5-minute dispatcher and a snapshot variable that no longer match the code).
**Coordination:** issue #81.

## 1. Goal

Prove that automation can perform **exactly one authorized paid operation and nothing else**, then scale up only within explicit readiness, capacity and budget limits.

A *paid operation* is anything that can start a Copilot coding session:

| Paid operation | Today's source |
|---|---|
| Assigning an issue to Copilot | `arcana-card-dispatcher.yml` |
| Posting a repair request that mentions Copilot on a PR | `arcana-ci-failure-router.yml` |
| A person mentioning Copilot on an issue or PR | Manual (outside automation; see §9) |

## 2. Current state (verified 2026-10-23, see #81)

| Area | Implemented | Live-verified | Gap |
|---|---|---|---|
| Readiness flag `ARCANA_AUTOMATION_READY` | Yes, both workflows | No (gated dispatcher has never run; value unreadable to Claude) | One flag gates both dispatch and repairs |
| Budget gate (50/75/90, fail closed, 10% reserve, 60-min staleness) | Yes | One live `normal` reading (run 40012565437) | Reserve is flat; no in-flight accounting; schedule not observed firing |
| Dispatcher capacity (max 2, counts Copilot-assigned issues) | Yes | No | Ignores PRs, pending pickups, repairs |
| Exact-base CI gate | Yes | No | Base `959b8d9` has no qualifying run; planned new base is B131 |
| Repair router (3 per PR, exact-head dedupe) | Yes | Observation path only | No global cap; drops when concurrency queue is full |
| Serialization | Shared group `arcana-copilot-control` | — | Default queue keeps one pending run; extras replaced |
| Branch protection on `main` | No | GitHub: "Branch not protected", no rulesets | — |

## 3. Three controls

Every paid operation passes **all three**, in this order, inside the single serialized admission step (§6). Any unknown answer means **no**.

### 3.1 Readiness — *is this kind of work authorized right now?*

Owner-set repository variables (workflows never write them):

| Variable | Meaning | Default |
|---|---|---|
| `ARCANA_AUTOMATION_READY` | Master switch for all paid automation | unset = off |
| `ARCANA_DISPATCH_MODE` | `off` · `authorize-once` · `normal` | unset = `off` |
| `ARCANA_REPAIR_MODE` | `off` · `observe` · `request` | unset = `observe` |

An operation is allowed only if the master switch is `true` **and** its own mode permits it. This separates the dispatcher from the repair router: a controlled test can run dispatch with repairs fully off.

`authorize-once` means only issues carrying the one-shot label may dispatch (§7), including scheduled runs.

### 3.2 Capacity — *how much work is already in flight?*

Capacity counts **work units**, not GitHub objects (§4). A unit occupies one slot while it is `pending-pickup`, `working`, or `repairing`. Its issue, assignment, PR and repair comments all count as that **one** slot.

- `ARCANA_MAX_ACTIVE_UNITS` (owner variable, default `1`, hard ceiling `3` in code). It is the owner's **ceiling**; effective capacity scales with budget level: `normal` → ceiling, `warn` → at most 2, `slow` → 1, `paused`/`unknown` → 0. Slots fill only when an eligible build or repair exists, so 1–3 workers run depending on load. With 2+ slots, builds and repairs each take at most capacity−1, so neither lane starves the other (owner request 2026-10-30).
- A unit stays `pending-pickup` from assignment until a `copilot_work_started` event appears, or until a pickup timeout (default 30 min). A unit that never starts is marked `halted` and reported in #81; it is **not** silently freed or reassigned.

### 3.3 Budget — *can this work finish within the allowance?*

```
effective_used = reported_used
               + fixed_reserve                      # existing: 10% of budget by default
               + in_flight_units × est_credits_per_session
               + pending_repairs × est_credits_per_repair
```

- `est_credits_per_session` and `est_credits_per_repair` start conservative (owner variables) and are **calibrated from the controlled test** (billing reading before vs. after one session).
- Thresholds unchanged: 50% warn, 75% one new operation at a time, 90% none, unknown/stale none.
- Copilot AI credits, GitHub Actions minutes and ChatGPT/OpenAI usage stay separate. ChatGPT usage is **reported as unavailable** — no verified source exists.

## 4. Work units

A **work unit** is one coherent task: typically one `[Arcana Queue]` issue and everything that results from it.

| State | Entered when | Holds a slot |
|---|---|---|
| `queued` | Issue eligible, not authorized | No |
| `authorized` | One-shot label consumed or normal-mode admission granted | Yes (briefly) |
| `pending-pickup` | Assignment API confirmed | Yes |
| `working` | `copilot_work_started` seen | Yes |
| `review` | `copilot_work_finished` seen, draft PR exists | No |
| `repairing` | Repair request posted | Yes |
| `done` | PR merged or closed by owner | No |
| `halted` | Pickup timeout, per-unit ceiling hit, or error | No (needs owner) |

**Storage — no new permissions needed.** State lives on the unit's issue as a label (`wu:<state>`) plus an append-only structured comment per paid operation (marker `<!-- arcana-wu-ledger-v1 -->`, fields: operation, run ID, head SHA, timestamp, budget level). Workflows already have `issues: write`. A PR links to its unit via `Unit: #N` in its body; the planner refuses repairs on PRs with no unit.

**Per-unit ceilings** (stops one stubborn PR eating the budget):
- At most **1 assignment** and **2 repair requests** per unit (today: 3 repairs per PR).
- Hitting a ceiling moves the unit to `halted` with a #81 note.

## 5. Planner (pure function, both workflows)

One module, `scripts/admission.cjs`, replaces the separate capacity logic in `worker-dispatch.cjs` and `failure-routing.cjs`:

```
admit({operation, unit, readiness, units, budget, now}) ->
  {allowed: bool, reason, reservation}
```

- Inputs are fetched fresh inside the admission step; the planner itself does no I/O and is fully unit-tested.
- `reason` is always logged and written to the job summary, so every "no" is explainable.
- Existing guards stay: exact-head dedupe, trusted default-branch code only, no relaying of `@` handles, bot-authored comment checks.

## 6. Serialization and durable records

- **Keep one concurrency group** (`arcana-copilot-control`) for every workflow that can perform a paid operation. Separate groups would let two workflows see the same free slot.
- Add `queue: max` to that group **after verifying the exact syntax against current GitHub documentation** in a non-paid workflow first.
- **Durable failure records:** a dropped router run must not lose information. Each scheduled dispatcher run also performs a cheap **reconcile pass**: list recent failed runs of the watched CI workflows on open unit PRs, and add an *observation* ledger comment for any failure with none. Reconciliation never requests a repair by itself; it only records.
- The billing watch keeps its own group because it never performs paid operations.

## 7. One-shot controlled test

**Authorization:** owner adds label `arcana:authorize-once` to exactly one issue (#68) and sets `ARCANA_DISPATCH_MODE=authorize-once`, `ARCANA_REPAIR_MODE=off`, `ARCANA_MAX_ACTIVE_UNITS=1`, `ARCANA_AUTOMATION_READY=true`.

**Dispatcher behavior in `authorize-once`:**
1. Only issues carrying the label are eligible. Scheduled and manual runs behave identically.
2. If more than one issue carries the label → refuse everything, report in #81.
3. **Interrupted-attempt check first** (see below). Any unresolved attempt anywhere → refuse everything.
4. Write an `authorizing` ledger comment (run ID, issue, timestamp). If the write fails → stop; the label is still present and nothing was spent.
5. **Remove the label.** If removal fails → stop and mark the unit `halted:interrupted`.
6. Attempt the assignment **once**. Success or failure, no retry.
7. Write the `attempt-result` ledger comment (confirmed / not confirmed / error).
8. On any later run, no label means nothing to do.

**Interrupted attempts (crash between steps 4 and 7).** An `authorizing` record with no matching `attempt-result` is an *interrupted attempt*. Every dispatcher run checks for one before doing anything else:
- Read the issue's current assignees and record what is observed (Copilot assigned or not) as an observation, never as a new attempt.
- Mark the unit `halted:interrupted`, post the evidence to #81, and **refuse all dispatch** until the owner reviews it and clears the state.
- Never retry the assignment automatically, even if the observation shows it did not happen.

**Observation:** record the billing reading before, confirm `copilot_work_started` and `copilot_work_finished`, record the billing reading after the next scheduled billing run, and confirm no other issue was assigned and no repair comment was posted.

**Pass criteria:** exactly one assignment; zero repair requests; zero other assignments; ledger and #81 entries complete; credit delta recorded and used to set `est_credits_per_session`.

**Afterwards:** owner sets `ARCANA_AUTOMATION_READY` back to unset/`false` until the result is reviewed.

## 8. Prerequisites before the test (dependency order)

1. Owner confirms `ARCANA_AUTOMATION_READY` is unset or `false`.
2. Owner clears the stale Copilot assignments on #63–67 (work is accounted for in PRs #74, #76, #77, #84, #85).
3. Confirm scheduled runs actually fire (dispatcher and billing watch have not been observed on schedule since 18:04 / 19:35 UTC on 2026-10-23). Investigate before relying on them.
4. Implement §3–§7 on a `claude/` branch with unit tests, plus a **dry-run mode** (`ARCANA_DISPATCH_MODE=dry-run`: plan and log, never call the assignment API).
5. Run dry-run live at least once on the schedule; review its logged decision.
6. Point the CI gate at the new integration base (proposed: B131 `98626c7` lineage), once GPT creates it and an exact-head `Arcana Development CI` run passes.
7. Owner applies branch protection (§10).
8. Owner approves the controlled test.

## 9. Out of scope / known limits

- **Manual Copilot mentions** by people are not controlled by automation. Policy: during the test window, nobody mentions Copilot on issues or PRs.
- **Already-running sessions** cannot be stopped by these gates; the gates only prevent new operations. Today none are running.
- **Billing lag** is mitigated, not eliminated, by the reserve and in-flight estimates.
- **Notification delivery** to the owner's phone/email depends on GitHub notification settings; a #78 comment alone is not proof of delivery. The owner should confirm one alert reaches the phone.
- **ChatGPT/OpenAI usage** is reported as unavailable.

## 10. Proposed `main` ruleset (owner applies)

- Require a pull request before merging; no direct pushes.
- **Every required check must run on every PR.** Today `controls` (workflow "Stabilization control regressions") is path-filtered to `scripts/**` and the control workflows, so requiring it as-is would block docs and website PRs forever. Before enabling the ruleset, add a lightweight job that runs on every PR: it executes the control tests when control files changed and passes immediately otherwise. Require that job.
- Block force pushes and branch deletion.
- Owner bypass: the owner's choice. Recommend allowing bypass only for the owner, used rarely and noted in #81.

## 11. Implementation plan (after approval)

| Step | Owner | Size |
|---|---|---|
| `scripts/admission.cjs` + tests (readiness, capacity, budget, ceilings) | Claude | Medium |
| Wire dispatcher: modes, one-shot label flow, dry-run, ledger comments | Claude | Medium |
| Wire router: `ARCANA_REPAIR_MODE`, unit lookup, shared admission | Claude | Small |
| Reconcile pass for dropped failures | Claude | Small |
| `queue: max` syntax verification in a non-paid workflow | Claude | Small |
| Review against this document; Oracle and integration unaffected | GPT | Small |
| Variables, labels, ruleset, test approval | Owner | Small |

All of it ships as one draft PR to `main`. No merge without owner approval.

## 12. Decisions (agreed with GPT review on PR #92; owner may override)

1. `ARCANA_MAX_ACTIVE_UNITS=1`. Consider 2 only after a verified controlled test.
2. Per-unit ceiling: 1 assignment + 2 repair requests.
3. Provisional reservations: 60 AI credits per session, 30 per repair; replaced by calibrated values from actual readings.
4. Per-unit observations are logged immediately. A weekly #81 summary only if it proves useful and low-noise.

## 13. Implementation notes (deviations and decisions made while building)

- **Files:** `scripts/admission.cjs` (pure policy), `scripts/units.cjs` (GitHub reads), `scripts/dispatch-runner.cjs` (dispatcher), `scripts/failure-routing.cjs` (router + scheduled reconcile). `scripts/worker-dispatch.cjs` is removed; its manual `test_issue` input is replaced by `authorize-once`.
- **Defaults after merge:** dispatcher unset → `off`, makes no API calls. Router unset → `observe`: records failure observations (as today) and, every 2 hours, backfills observations for failures from the last 6 hours. `ARCANA_REPAIR_MODE=off` silences the router entirely.
- **More conservative than §4:** a pickup timeout keeps its slot (`halted`, `holdsSlot`) until the owner reviews it, because a late session could still start. A legacy Copilot assignment with no observed session is treated the same way.
- **Resolving an interrupted attempt (append-only):** the owner adds label `wu:resolve-interrupted` to the issue. The next dispatcher run in `authorize-once` or `normal` mode appends a `resolution` record per open authorization (observed assignment state, who applied the label), removes the label, reports to #81, and dispatches nothing that run. No ledger record is ever deleted or edited.
- **Assignment accounting:** every `authorizing` record consumes the unit's one assignment, whatever happened next. `confirmed`, `error` and `not-confirmed` results, and resolutions observing an assignment, are treated as possibly paid: the unit holds a slot as a pending pickup (and keeps it after the timeout). `not-attempted` and resolved-not-assigned units are `halted` (`assignment-consumed`) with no slot. Resolution can never reopen a unit; retrying a card means a new queue issue.
- **Tokens:** ledger, label and report writes use the workflow's `GITHUB_TOKEN` (`github-actions[bot]`), the only author the ledger trusts. `COPILOT_DISPATCH_TOKEN` is used solely for the assignment call.
- **PR linkage:** the assignment instructions ask Copilot to put `Unit: #N` in its PR body; without it, the lowest referenced unit issue is used.
- **Fixed during testing:** admission is evaluated after the billing read. Evaluating it with a timestamp taken earlier made every fresh reading look future-dated, so live runs would have denied everything as `unknown`.
- **Deferred:** `queue: max` is not added yet (syntax still to be verified in a non-paid workflow). The reconcile pass covers dropped router runs in the meantime.
- **Control tests** now run on every PR (path filter removed) so the check can be required.

## 14. Unit readiness (added after the first one-shot)

The first controlled one-shot (#68, run 40030804598) worked exactly as designed: one assignment, a complete ledger, the label consumed, nothing else started. But Copilot opened a zero-diff PR (#95). The gates checked *whether* to spend, not *whether the work item was usable*. Changes:

- **Unit-readiness gate** (`unitActionable` in `scripts/admission.cjs`). `authorize-once`, `normal` and `dry-run` deny with `unit-not-actionable: <missing>` unless the issue body has: a `## Cards` section with bullets, or a `## Discovery` section naming a script path; a `## Acceptance` section mentioning Oracle behavior and executable tests; and a `Base:` line equal to `ARCANA_INTEGRATION_BASE`. Template: `.github/ISSUE_TEMPLATE/arcana-queue-unit.md`.
- **Per-unit worker instructions.** The assignment no longer says "Read issue #81". It points the worker at the unit issue's procedure, requires claiming specific cards, and says to comment on the issue instead of opening an empty PR.
- **Base must be configured.** The stale default branch is removed; an unset or malformed `ARCANA_INTEGRATION_BASE` returns `base-not-configured` before any GitHub call that could spend.
- **PR linking:** each PR belongs to exactly one unit. An explicit `Unit: #N` line wins, then GitHub's `connected` development link (the #54 → #59 case, where the PR body never named the unit), then the lowest referenced unit issue. Plain cross-references are ignored because worker PRs mention many queue issues.
- **Hardened after GPT review on #99:** HTML comments are stripped before validation. Placeholder or template-default bullets (`Card name`, `TBD`, `<...>`, the template's generic acceptance lines) do not count. Acceptance needs at least one unit-specific bullet. Discovery needs a runnable command naming a script, not just a filename. A PR connected to more than one unit is **ambiguous**: it counts against every such unit, and admission is denied for them until a `Unit: #N` line resolves it. A test feeds the real, unedited template and asserts it is refused.

## 15. Trusted origin and approval integrity (added after the 2026-10-24 audit)

The repository is public, so anyone can open an issue. A Copilot worker follows the unit issue's body
and reads its comments, so every paid path now requires that all of it comes from trusted people.

- **Trusted** means GitHub's own `author_association` is `OWNER`, `MEMBER` or `COLLABORATOR`. That value
  is computed by GitHub from live permissions and cannot be set by issue text. The dispatcher's
  `github-actions[bot]` and Copilot's bot account are also trusted. A user account named like a bot is not.
- **Unit issue:** the author must be trusted, and every comment must be from a trusted person or bot.
  One untrusted comment blocks the unit until it is deleted.
- **One-shot approval:** the latest application of `arcana:authorize-once` must
  - be made by an **approver**: a login in `ARCANA_SPEND_APPROVERS` (comma-separated), or the repository
    owner alone when unset. A missing or unlisted actor is refused. The approver is written to the
    `authorizing` ledger record (`reason:approved-by <login>`);
  - be newer than the issue body's last edit (GraphQL `lastEditedAt`, else `createdAt`), any title change,
    and **every comment's creation or last edit**. Only the dispatcher's own ledger records
    (`github-actions[bot]` + ledger marker) are exempt; other bot content is not.
  Editing anything after approval needs a fresh label. Missing edit, rename or comment history fails closed.
- **Normal mode:** untrusted issues are never candidates, so they can neither spend credits nor hold up
  the queue.
- **Right before the paid call** the dispatcher re-reads the issue, comments and approval history, and
  re-checks the fresh body against the readiness rules and `Base:` line. A change since admission records
  `attempt-result: not-attempted` with the reason; there is no retry.
- **Dry-run preflight:** `dry-run` reads the candidate's edit history and reports
  `editHistoryPreflight: ok|unavailable` in the log and job summary, so the GraphQL path is proven from
  Actions before any one-shot depends on it. Nothing is written or assigned.
- **Repairs:** the unit, the PR author (Copilot, or a trusted person) and every PR comment must be trusted.

Known limits: direct `@copilot` mentions by people bypass the dispatcher entirely (process rule: GPT and
Claude never post them); a comment *deleted* after approval can't be detected through the REST API. The repair path checks the
whole PR conversation (issue comments, review bodies and inline review comments) and re-runs that check
immediately before its ledger write and agent-triggering comment (`untrustedConversation` in
`scripts/failure-routing.cjs`, 2026-10-30), so `ARCANA_REPAIR_MODE=request` is an owner decision rather
than blocked;
branch protection on `main` is an owner setting (section 10).

## 16. Certification-suite units (added 2026-10-30)

A card PR cannot be certified until `certification/suites/<key>.json` and its test are on `main`.
Suite authoring is now an ordinary admitted unit:

- **Request.** `certification/backfill.cjs` (hourly and on certification pushes) calls
  `certification/suite-requests.cjs` for every open Copilot card PR with card code but no suite whose
  queue issue is not `arcana:engine-gap`. It opens one issue titled `[Arcana Suite] <card> — …` as
  `github-actions[bot]`, body starting `<!-- arcana-suite-request-v1 -->` plus `arcana-suite-for-unit:<N>`,
  with `Card unit:`, `Card PR:`, `Suite key:`, `Base: main` and the feeder's Oracle record. An existing
  request (open or closed) is never duplicated; units naming several cards or lacking an Oracle record are
  only reported on #81. Opening a request spends nothing.
- **Trust.** `provenance()` trusts such an issue exactly like a feeder unit: bot author, marker first,
  body never edited (`contentWrittenAt`). Comments follow the normal rules. `suiteActionable()` replaces
  `unitActionable()`; no Cardkit verdict is needed (the card passed Cardkit in its own PR).
- **Dispatch.** `loadUnits` includes `[Arcana Suite]` issues. In `normal` mode an eligible suite request is
  picked before card units. `admit()` is unchanged: same ceiling, capacity, lanes (build) and budget.
  `assignCopilot` uses `base_branch: main` and suite-only instructions (write only the two suite files,
  strictly from the Oracle record, never read the card PR).
- **Merge.** `.github/workflows/arcana-suite-automerge.yml` runs on each completed premerge. It merges only
  when `mergeBlockers()` is empty: green run on the PR's exact head, same repository, Copilot PR on `main`,
  linked to an open bot suite request, exactly the two requested files (both added), a registration whose
  key, unit and source match the request, and the scenario job actually ran green. Then it runs the backfill
  on the new `main` (a `GITHUB_TOKEN` merge starts no push workflow), which re-runs the card PR's CI and so
  certification. A red premerge goes back to Copilot through normal repair, or stays for a person.


## 17. No step waits on an unassigned person (added 2026-10-30, owner: "fix it")

- **Stuck slots release themselves.** A slot held only by `pickup-timeout` or `session-stale` is released
  `AUTO_RELEASE_MS` (6 h) after the stuck event (`reason: auto-released`). Billing keeps reserving for it via
  `recentlyFinished` for the lag window. `wu:halted` still releases at once.
- **Interrupted attempts resolve themselves.** One hour after the `authorizing` record, the dispatcher appends
  the same append-only `resolution` the owner label would (`by dispatcher-timeout`), with the observed
  assignment, and dispatches nothing that run. Never a retry; `wu:resolve-interrupted` still works sooner.
- **`arcana:engine-gap` is applied automatically.** A queue unit whose Copilot session finished, on which
  Copilot commented, and which has no open PR (Copilot's instructions say to comment instead of opening a PR
  when the card can't be implemented exactly) is labeled by the dispatcher, with a ledger observation.
  Known limit: a unit whose card PR was *closed* without merging also has no open PR and could be labeled;
  removing the label returns it.
- **The GPT engine lane has an owner and a deadline.** Every 15 min the #78 stall report lists `[Card Work]`
  issues and `arcana:engine-gap` queue units with no activity for 48 h: owner Work GPT, with the next action.
- **B140 → numbered build has a trigger.** The same report lists B140 when its head has changes newer than
  the latest GitHub release for 72 h: owner AARMOU, next action the B141 checklist. Publishing the build
  as a release clears it. (Building, signing and releasing stay owner-only.)

**§16 update (2026-10-30):** GitHub refuses `markPullRequestReadyForReview` from the workflow token, so the
suite auto-merge marks ready and merges with `ARCANA_SUITE_MERGE_TOKEN` if set, else `COPILOT_DISPATCH_TOKEN`.
If GitHub still refuses, it posts one notice on the suite PR with the owner's two clicks (Ready for review,
Squash and merge) instead of stalling silently. A red premerge is now routed back to Copilot like card CI
(`Arcana Card Certification Premerge` is in failure routing's workflow set; repair mode still applies).


## 18. Merged suite completion and billing retention (#526 / #529)

`loadUnits` recovers a suite request's explicitly connected PR even after the PR leaves the
open-PR list. The durable completion record is read from GitHub on each observation: same-repository
Copilot PR merged into `main`, exact two added suite files, matching registration at the immutable
merge commit, and a successful premerge on the exact head whose matching scenario job actually
executed the shared verification step before the merge. PR text, issue closure and completion
comments are not proof. No new completion comments or issue-closing writes are necessary.

After the real session started and finished, a validated suite becomes `completed`, independent
of whether GitHub auto-closed its request. It cannot be assigned or repaired again. A new session
start, pending repair or interrupted authorization still wins and retains/reclaims capacity.
A merge never fabricates a session finish. Unavailable GitHub evidence fails the read closed.

Closed bot-authored suite requests remain in the accounting input, so `recentlyFinished` continues
reserving credits for `ARCANA_BILLING_LAG_HOURS` (24 h default). Closing a request cannot erase its
liability. The normal billing-lag expiry is unchanged. No capacity setting, admission ceiling,
paid assignment, owner approval, merge token or auto-merge policy is changed by this reconciliation.

The closed-request read deliberately has no time cutoff: a late session event on its PR need not
update the issue timestamp. This adds historical suite reads to each existing bounded observer tick;
API failures stop admission rather than substituting zero liabilities. If suite history grows large,
compact it only with a durable settled-liability/session reconciliation design, not a timestamp-only
cutoff that can hide late work. Existing event-refresh throttling and router load reuse still apply.

Validation: regressions cover open/closed requests, duplicate observations, billing lag, late sessions
and repairs, missing timelines, wrong merge/source/suite/job evidence and forged comments. A read-only
replay of real #526/#529 at 2026-10-30 19:47 UTC changes baseline `pending-pickup` / slot held / no recent
liability to patched `completed` / no slot / recent liability, including simulated issue closure.
The replay uses captured GitHub responses, not live production dispatch. #531's merge-token path
still requires the next naturally admitted real suite PR; a no-op run does not verify that path.

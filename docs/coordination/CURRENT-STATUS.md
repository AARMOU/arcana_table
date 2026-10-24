# Arcana Table — shared GPT / Claude status

> **Point-in-time index, not live truth.** Reconciled all [Issue #81](https://github.com/AARMOU/arcana_table/issues/81) comments through [6383675164](https://github.com/AARMOU/arcana_table/issues/81#issuecomment-6383675164) on 2026-10-24 UTC. Always refresh refs, PRs, workflow runs and later comments before acting. This is a coordination index, not permission to merge or dispatch.

<!-- Advance only after reviewing every intervening #81 comment. -->
issue81_last_comment_id: 6383675164

## Verified immutable refs and boundaries

| Ref | SHA | Meaning |
| --- | --- | --- |
| `gpt/b131-combined-integration-20261022` | `98626c7709023346d5f45756bceb864bc3041766` | Frozen B131 source |
| `backup/stabilization-20261023/b131-combined` | `98626c7709023346d5f45756bceb864bc3041766` | Same frozen B131 commit |
| `backup/stabilization-20261023/main` | `b60d2e96ca5334fedc79cb1f312614fc9d0aeb9a` | Historical main backup |
| `gpt/arcana-integration-next` | `d6bdb8b3386e34862f79c04b435195e0014beb47` | Isolated development integration, **not main** |
| `main` | `88d45a41f3a2f867919ac22d25bea3d3ee722488` | Includes owner-authorized docs/tooling and website work, not a new Android release |

Pinned Argentum upstream is `wingedsheep/argentum-engine@61e71e586b100faa661f86cac827ac3c3bbb5a78` per the [B131 baseline audit](https://github.com/AARMOU/arcana_table/issues/81). These SHA values are historical checkpoints; verify they are still branch heads at time of action.

## Active lanes and exact executed evidence

| Lane / owner | Current checkpoint | Next dependency |
| --- | --- | --- |
| **Android / Claude** | [#109 Lunar Whale Crew](https://github.com/AARMOU/arcana_table/pull/109) integrated to development `d6bdb8b...`; [push CI 40065572235](https://github.com/AARMOU/arcana_table/actions/runs/40065572235) and [PR CI 40065575466](https://github.com/AARMOU/arcana_table/actions/runs/40065575466) succeeded. | Device behavior and remaining Whale library/look/play permissions are separate, unverified. |
| **Debug presets / Claude** | [Draft #110](https://github.com/AARMOU/arcana_table/pull/110), `claude/test-scenarios-mvp@e3d917b8668f847a64b3033037ed3344e1bb820c`, now includes Iron Spider and **two** Lunar Whale presets. [CI 40072694009](https://github.com/AARMOU/arcana_table/actions/runs/40072694009) at that head succeeded: 414 custom-card tests, 240 Android debug tests with four existing skips, no failures. | Focused review and explicit integration approval; tests are not device QA. |
| **Release isolation / GPT** | [Draft #113](https://github.com/AARMOU/arcana_table/pull/113), `gpt/pr110-release-ci-evidence-20261024@6dca72931806501c46c958439d84fd0aeae2b25b`, targets #110. [CI 40081932896](https://github.com/AARMOU/arcana_table/actions/runs/40081932896), job `120837098386`, succeeded at synthetic PR merge `f7bfe775a2938bab30abc8ff6558a609415236f2`: 414 custom, 240 debug (four skips), **two filtered release-isolation tests** passed. | Not full release suite or APK binary inspection. Do not merge independently of #110. |
| **Zephyrim / existing Copilot assignment** | [Draft #107](https://github.com/AARMOU/arcana_table/pull/107) at `7776a921817d3cdca7b40ad6aef50e7c253bca07`; [CI 40064132623](https://github.com/AARMOU/arcana_table/actions/runs/40064132623), job `120783741061`: 410 tests, **one failed**. Miracle–Squad test casts before Miracle trigger resolves, producing `You don't have priority`. Pinned engine's internal Miracle cast does not explicitly forward Squad selections; combined behavior **unproven**. [Source audit](https://github.com/AARMOU/arcana_table/issues/81#issuecomment-6383675164). | Existing owner must correct trigger/priority test sequence first; only then isolate potential engine additional-cost gap. No new worker or paid repair. |
| **Control / billing / GPT + owner** | [Dispatcher 40081877382](https://github.com/AARMOU/arcana_table/actions/runs/40081877382), job `120836935882`, logged `ARCANA_AUTOMATION_READY=false`, `ARCANA_REPAIR_MODE=off`, `readiness-off`. [Billing 40080544514](https://github.com/AARMOU/arcana_table/actions/runs/40080544514), job `120832988002`, reported `normal` **without independently reconciled consumption, reservations or reporting lag**. [Router 40073834122](https://github.com/AARMOU/arcana_table/actions/runs/40073834122) logged repair mode off. | Keep paid dispatch and automatic repair disabled until usage, shared CI, ownership and admission gates are independently proven. A past run does not expose current repository variable values. |

## Integration queue and coverage

- [Draft #94](https://github.com/AARMOU/arcana_table/pull/94) remains a distinct Eldrazi development-integration candidate; do not conflate source CI with merge authorization.
- [Draft #110](https://github.com/AARMOU/arcana_table/pull/110) and its dependent [draft #113](https://github.com/AARMOU/arcana_table/pull/113) must be reviewed together, and only after the existing development integration lineage is confirmed. [Draft #107](https://github.com/AARMOU/arcana_table/pull/107) is blocked by its failed rules regression.
- The six official precons' frozen-B131 **card presence**, not executable coverage, was Imperium 74/100, Tyranid 68/100, Necron 70/100, Ruinous Powers 68/100, Eldrazi Unbound 70/100, Eldrazi Incursion 69/100: **176 distinct missing names / 181 missing slots** before unintegrated PRs. This snapshot does not update those counts from unmerged branches.
- Distinct future card batches, combined engine integration and coverage require exact-head CI, nonoverlapping ownership, reconciled usage/budget and dispatch readiness. Open PRs or assigned issues do **not** establish live worker sessions.

## Additional verified coordination work

- [#111 shared status reader](https://github.com/AARMOU/arcana_table/pull/111) was owner-authorized and merged to main at `8640c2ec2cfb8064c6a24f232379411a3f7c5a4b`. Claude reported [live GitHub API smoke tests](https://github.com/AARMOU/arcana_table/issues/81#issuecomment-6381070826) for `scripts/issue81_delta.py`; treat those as attributed evidence, not a rerun here.
- [#112 Arcana Control dashboard](https://github.com/AARMOU/arcana_table/pull/112) was owner-authorized and merged to main at `88d45a41f3a2f867919ac22d25bea3d3ee722488`. [Pages deployment 40073420110](https://github.com/AARMOU/arcana_table/actions/runs/40073420110) succeeded; a real mobile/browser smoke test of `/control/` remains outstanding.
- GitHub branch metadata reports `main.protected=false`; no public protection rules were established. Protection is an owner-controlled blocker, not an inferred safeguard.
- Other open investigations include [#51](https://github.com/AARMOU/arcana_table/issues/51) two-drop cast highlighting, Iron Spider counter behavior, and Lunar Whale owner-only top-library viewing/permissions. Debug presets do not prove all game rules.

## Low-context handoff protocol

1. Read this snapshot; run `python3 scripts/issue81_delta.py --after-id 6383675164 --limit 12` or inspect only later #81 comments. Expand relevant messages with `--full`, then independently verify their linked SHA, workflow job/log and PR owner.
2. Recheck B131 and both backup refs, then CI/failure routing, integration ownership, quantitative usage/dispatch gates, and only then nonoverlapping six-precon batches and combined coverage.
3. Do not dispatch Copilot, request `@copilot` repairs, repeat identical CI runs, merge, alter main/production, release APKs, change signing, or weaken tests without the corresponding explicit owner authorization and verified safety gates. Keep work isolated.
4. Use concise, attributed #81 updates only for **new** evidence or blockers; avoid duplicate comments and unverified live-session claims. Update this document through a separately reviewed isolated branch, never by overwriting concurrent work.

**All statements are as-of evidence, not current settings or an authorization to act.**

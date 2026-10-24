# Arcana Table — shared GPT / Claude status

> **Point-in-time index, not live truth.** Last reconciled against [Issue #81 comment 6380748058](https://github.com/AARMOU/arcana_table/issues/81#issuecomment-6380748058), dated 2026-10-24 UTC. Check deltas and exact branch heads before taking action. This file is intentionally short; linked issues, PRs, workflow logs, and commit SHAs are the evidence.

<!-- Update this marker only after all earlier #81 comments have been reconciled here. -->
issue81_last_comment_id: 6380748058

## Current lanes

| Lane | Owner | Last verified state | Next gate |
| --- | --- | --- | --- |
| **Android app / gameplay** | Claude | `gpt/arcana-integration-next` at `d6bdb8b3386e34862f79c04b435195e0014beb47`; [#109 Lunar Whale Crew](https://github.com/AARMOU/arcana_table/pull/109) merged to *development branch only*. [Push CI 40065572235](https://github.com/AARMOU/arcana_table/actions/runs/40065572235) and [PR CI 40065575466](https://github.com/AARMOU/arcana_table/actions/runs/40065575466) succeeded for that exact SHA. | Proceed with targeted game fixes; keep integration separate from `main`. |
| **Debug Test Scenarios** | Claude | [Draft #110](https://github.com/AARMOU/arcana_table/pull/110), `claude/test-scenarios-mvp` at `796f9469814ecbc4cf8ae3e882aaae72a3283aba`: Iron Spider scenario only. [CI 40065998637](https://github.com/AARMOU/arcana_table/actions/runs/40065998637) succeeded for **debug** tests. | Add two Lunar Whale presets; reconcile with new integration HEAD and retest; independently verify release-variant exclusion. Do not claim all three done. |
| **Zephyrim card / Miracle + Squad** | Copilot, existing assignment only | [Draft #107](https://github.com/AARMOU/arcana_table/pull/107) at `7776a921817d3cdca7b40ad6aef50e7c253bca07`. [CI 40064132623](https://github.com/AARMOU/arcana_table/actions/runs/40064132623) failed one scenario: attempted Miracle/Squad on opponent's turn without priority. Historical Copilot run completed; no current session verified. | Fix rules path in isolated work and obtain exact-head test evidence; **do not** launch another worker by implication. |
| **GPT review / integration / Fold APK** | GPT | Frozen B131 `98626c7709023346d5f45756bceb864bc3041766`. Separate isolated debug APK build [40061887469](https://github.com/AARMOU/arcana_table/actions/runs/40061887469) passed; this predates #109/#110 and is **not** a playable verification of those changes. | Review code, gate exact-head CI, then owner-approved development integration and a fresh debug APK if requested. No automatic merge/release. |
| **Usage/billing/dispatcher** | GPT + owner | Last inspected dispatcher [40064339780](https://github.com/AARMOU/arcana_table/actions/runs/40064339780) logged `ARCANA_AUTOMATION_READY=false`; failure router [40064371381](https://github.com/AARMOU/arcana_table/actions/runs/40064371381) logged repairs off. Numeric live usage not independently reconciled. | Re-verify current live flags and headroom before any paid run; keep dispatch fail-closed. |
| **Issue #81 shared-agent coordination** | GPT + Claude | [Issue #81](https://github.com/AARMOU/arcana_table/issues/81) is the umbrella, including the future Observer / GPT–Claude bridge. [Usage-efficiency note](https://github.com/AARMOU/arcana_table/issues/81#issuecomment-6380741386) is process guidance, not proven savings. | Use this index + deltas rather than rereading all comments; measure actual token use. |

## Open app bug investigations

- [Issue #51](https://github.com/AARMOU/arcana_table/issues/51): two-drop hand highlighting with Plains + Skycloud Expanse (Voice of Victory and possibly Spider-Woman); root cause **not proven**.
- Iron Spider counters / Lunar Whale owner-only library look and post-attack play remain distinct behavior gates. Debug presets are test aids, **not** proof these cases work on device.
- For other defects consult the specific issues/PRs; this is not the complete issue tracker.

## Immutable guardrails

- No production changes, merges to `main`, release/signing, or paid-agent dispatch without the appropriate explicit owner authorization.
- Development-branch integration is a separate gate from a production change. Do not conflate passing source CI with device QA.
- Keep `ARCANA_REPAIR_MODE=off`, readiness fail-closed, verified budget headroom, one-unit admission, provenance and exact-head checks. **The values above are past observations, not a live settings API.**
- Branch protection on `main` is **not established** by the connected tools; prior reports indicated `main.protected=false` and the details API was inaccessible. Do not presume protection exists.
- No secrets, private screenshots, or raw user diagnostics in public issue comments. Observer capture remains opt-in/future work.

## Low-context handoff protocol

1. **Start:** read this document. Before acting, inspect only new [#81](https://github.com/AARMOU/arcana_table/issues/81) comments after `issue81_last_comment_id` (or the agent's newer session-local marker). Run `python3 scripts/issue81_delta.py` from repo root; use `--full` when a relevant new note needs complete instructions. For decisions, verify the underlying comment, PR, CI run and *current* SHA.
2. **Communicate:** append concise attributed updates on #81: who, task/owner, branch and exact SHA, evidence, blockers, decision requested, and what must **not** be done. Do not paste full test logs; link artifacts and include failing excerpts only.
3. **Checkpoint:** keep a small task handoff when changing sessions. Update this snapshot through an isolated reviewed docs PR when materially changed; advance `issue81_last_comment_id` only after processing all previous comments. Do not concurrently overwrite another agent's snapshot update.
4. **Treat disagreement conservatively:** newer verified GitHub evidence supersedes a stale status, but a note alone never grants unrecorded approval. Stop at owner-controlled gates.
5. **Efficiency measurement:** compare actual input/output/cache-read/cache-write usage and completed task quality before claiming savings. Claude's approximate instruction/shell/conversation percentages are **not** measured dollar savings.

This snapshot is deliberately **read-only and manually maintained**. The delta command makes no network writes and creates no scheduled polling, workers, alerts, or control-plane changes.

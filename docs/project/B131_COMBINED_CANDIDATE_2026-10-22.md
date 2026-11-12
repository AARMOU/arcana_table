# Combined B131 integration candidate — 2026-10-22

## Authorized goal
Produce one **isolated, reviewed and tested** candidate combining previously validated game-over outcome logging and the six-precon card implementation, against the exact verified B131 Argentum replacement engine. Do not merge to `main`, modify the original worker branches, publish/sign an APK, or release.

## Pinned inputs and independent preservation

| Role | Exact input | Evidence |
| --- | --- | --- |
| Build 131 preview | `83aa342e3fcd200710fb9188192189fc7cea7abf` | [Validated engine artifact workflow](https://github.com/AARMOU/arcana_table/actions/runs/39844575500) |
| Game-over logging | `dca22b4bfc3e453ebbb849c846859bcdffda5d54` | [Post-fix original-branch tests](https://github.com/AARMOU/arcana_table/actions/runs/39872381961) |
| Six-precon worker | `959b8d980bdf7e43537671a5651db8619c5c9cd6` | [Post-fix card tests and verified card export](https://github.com/AARMOU/arcana_table/actions/runs/39872389748) |
| B131 Argentum runtime | upstream engine `61e71e586b100faa661f86cac827ac3c3bbb5a78` | binary SHA-256 list pinned in candidate `docs/integration/B131_RUNTIME.json` |

## Integration route

- Created isolated `gpt/b131-combined-integration-20261022` from logging commit; opened [draft PR #47](https://github.com/AARMOU/arcana_table/pull/47) targeting B131 upgrade preview.
- **Did not merge the divergent six-precon history wholesale.** Its source diverges from B131 by hundreds of commits; the candidate preserves all B131-owned custom definitions and selectively imports new definitions/scenario tests.
- Original B131 custom registry: 111 definitions. Six-precon registry: 214. Remove precisely Rocco, Kodama of the West Tree, Raffine already owned by upstream B131, resulting in **211 registered unique custom cards**. Keep independent scenario tests, original B131 Android UI code and logging fixes.
- An initial test run [#39874523503](https://github.com/AARMOU/arcana_table/actions/runs/39874523503) identified a missing helper for Scion mana tokens (`createEldraziScions`). This was a legitimate integration omission, not a CI infrastructure fault. The isolated workflow was corrected to copy exactly the `EldraziScionFactory.kt` helper.
- [Combined gate #39874812474](https://github.com/AARMOU/arcana_table/actions/runs/39874812474): **SUCCESS**. Full custom-card scenario tests, 211 distinct cards/no B131 stock collisions, Android full unit tests, lint, and debug assembly passed in the combined runner worktree, after validating all four B131 runtime JARs and full card corpus against published SHA-256.
- As a deliberate gate, code and verified replacement binaries were only committed after the combined job passed. Integrated source committed at `8b36c1810b987b78a695f1247574f9f6eb89c0fa`.
- **Exact committed-tree CI passed:** [run #39875693567](https://github.com/AARMOU/arcana_table/actions/runs/39875693567) at candidate HEAD `98626c7709023346d5f45756bceb864bc3041766` (**SUCCESS**). Rechecked pinned engine JAR and asset SHA256s, exact source provenance, 211-card registry and stock-collision checks, full `:custom-cards:test`, and complete `:app:testDebugUnitTest :app:lintDebug :app:assembleDebug` directly from committed source. Evidence artifact: `b131-combined-exact-commit-evidence`, ID `12467838872`. Candidate source integration commit was `8b36c1810b987b78a695f1247574f9f6eb89c0fa`; HEAD adds only the exact-source verification workflow.

## Status boundaries

- PR #47 remains a draft. Its target is B131 upgrade preview, **not** website/default `main`. Parent PRs #42 and #43 remain open, separate and unmerged, as historical owner workstreams.
- Signed/buildable APK delivery, full six-precon deck playability, real-device stress equivalence, background session recovery and large-token performance are **not** established by this integration run.
- The original verified 74-branch pre-cleanup snapshot is preserved. New integration branches added after that snapshot have their own exact refs and documents; do not reset any live source to the snapshot.

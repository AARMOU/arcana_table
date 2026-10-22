# Post-merge CI and Build 131 compatibility — 2026-10-22

## Final verification result — both B131 checks PASSED

- **B131 six-precon/cards:** [workflow #39872589479](https://github.com/AARMOU/arcana_table/actions/runs/39872589479) **SUCCESS**, cards pinned at `93eaeebf85e0f60cecad5a8f134a6163d7fab5cd`. Confirmed exact engine bundle SHA checks, upstream engine `61e71e586b100faa661f86cac827ac3c3bbb5a78`, Java 17 classfiles, **211 custom-card definitions**, **zero upstream name collisions**, full card scenarios, B131 Android app unit tests and lint. Artifact: `b131-card-compat-six-precon-twelve-card-batch` (ID 12466872607).
- **B131 game-over logging:** [workflow #39872674003](https://github.com/AARMOU/arcana_table/actions/runs/39872674003) **SUCCESS**, logging pinned at `dca22b4bfc3e453ebbb849c846859bcdffda5d54`. Confirmed exact replacement engine bundle hashes and upstream identity, focused `GameOutcomeDetailsTest` and `LocalArgentumHostTest`, full `:app:testDebugUnitTest`, `:app:lintDebug`, `:app:assembleDebug`. Both Gradle invocations finished `BUILD SUCCESSFUL`. Artifact: `b131-post-merge-game-over-compatibility` (ID 12466834315).
- **Destination runs:** [card integration #39872389748](https://github.com/AARMOU/arcana_table/actions/runs/39872389748) **SUCCESS**; [logging #39872381961](https://github.com/AARMOU/arcana_table/actions/runs/39872381961) **SUCCESS**.
- **Strict scope:** This verifies each merged fix against B131's replacement engine in isolated CI. It does **not** constitute a single combined production merge of PR #42 and #43, signed device-install verification, or release authorization. `main` remains separate.

---

## Authorized operation
Merge only the two isolated fixes into their own development destinations, rerun those destination validations, and independently verify compatibility with Build 131's **verified replacement Argentum engine**. No merge to main, no PR #42/#43 promotion, no APK/AAB publishing.

## Actual integration (verified)
| Fix PR | Destination branch | Merge commit | Notes |
|---|---|---|---|
| [#45](https://github.com/AARMOU/arcana_table/pull/45) | `gpt/game-over-cause-logging-20261022` | `dca22b4bfc3e453ebbb849c846859bcdffda5d54` | Made outcome banner viewer ID nullable and tested null behavior |
| [#46](https://github.com/AARMOU/arcana_table/pull/46) | `gpt/six-precon-worker-integration-20261022` | `93eaeebf85e0f60cecad5a8f134a6163d7fab5cd` | Forge of Heroes test fixture supplies printed Jace starting loyalty |
| Automatic post-test generated export | `gpt/six-precon-worker-integration-20261022` | `959b8d980bdf7e43537671a5651db8619c5c9cd6` | Additional commit changed only `custom-cards.index.tsv` and `custom-cards.jsonl`, three additions each; no Kotlin/source changes |

## Exact post-merge destination CI
- [Game-over validation at `dca22b4`](https://github.com/AARMOU/arcana_table/actions/runs/39872381961): **success**.
- [Full custom-card regression at `93eaeeb`](https://github.com/AARMOU/arcana_table/actions/runs/39872389748): **success**. Workflow regenerates card export, executes all custom-card tests, audits both indexes, and committed updated verified export. Its additional post-run data-only commit is documented above.

## Cross-engine Build 131 compatibility gates

Both workflows live on an **isolated, nonproduction** branch `gpt/b131-post-merge-compatibility-20261022`. They perform read-only source checkout / ephemeral worktree modifications and never move any product development branch:

1. [Six-precon / Build 131 interoperability](https://github.com/AARMOU/arcana_table/actions/runs/39872589479) checks out card source at `93eaeebf85e0f60cecad5a8f134a6163d7fab5cd`, exact B131 source `83aa342e3fcd200710fb9188192189fc7cea7abf`, and the GitHub Actions verified runtime bundle from [#39844575500](https://github.com/AARMOU/arcana_table/actions/runs/39844575500). Verifies the engine version, complete SHA-256 manifest, Java 17 bytecode, card corpus, full custom-card scenarios, export audit, then B131 Android app test and lint on exported card assets.
2. [Game-over logging / Build 131 interoperability](https://github.com/AARMOU/arcana_table/actions/runs/39872674003) checks out merged `dca22b4`, verifies the *same* runtime bundle and replaces only ephemeral worktree bundled Argentum JARs/stock card corpus, then runs dedicated game-over/LocalArgentumHost tests, full app unit tests, lint, and debug assemble.

**Final status:** Both workflows completed successfully on the pinned references detailed above. Recheck GitHub before making any later release or integration claim.

## Scope limitations
- B131 compatibility jobs are a CI-level integration check against the exact **replacement engine** and B131 Android consumer, not a device install, signed APK equivalence, human playtest, or blanket six-precon rules guarantee.
- This does not integrate feature PR #42 into the upgrade preview or card PR #43 into the six-precon base; those remain separate draft integration decisions.
- No changes to `main` (expected `c533d0520feeab5a9c98d1d61ddb034b34672bf3`); pre-cleanup backup and manifest remain untouched.

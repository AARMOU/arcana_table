# Pre-cleanup snapshot — verified backup and test evidence

Snapshot: 2026-10-22 (74 original branch heads); see [PRE_CLEANUP_SNAPSHOT.md](PRE_CLEANUP_SNAPSHOT.md).

## Git-history backup: PASS

- Backup refs: `backup/precleanup-20261022/<original branch name>`
- Exact source commit identities checked: **74 / 74 PASS**.
- Direct source-vs-backup file checks: **6 / 6 PASS** (main, B131 engine preview, six-precon source, six-precon integration, game-over logging, B131 card compatibility).
- [GitHub Actions full Git-bundle and clean mirror-restoration test](https://github.com/AARMOU/arcana_table/actions/runs/39865508161): **SUCCESS**.
- All 74 saved refs survived the bundle creation, `git bundle verify`, clean `git clone --mirror`, and post-restore SHA verification.
- [Downloadable GitHub Actions artifact](https://github.com/AARMOU/arcana_table/actions/runs/39865508161/artifacts/12464116057): `arcana-precleanup-20261022-bundle`, artifact ID `12464116057`, size 34,964,932 bytes (ZIP), expires 2027-01-20 UTC.
- Bundle contains committed Git history reachable from those refs. It does **not** independently back up unpushed changes, GitHub issues/discussions, settings, CI artifacts, external dependencies or secrets.
- The in-repository backup refs alone are not offsite or branch-protected. Download and retain an independent copy of the bundle; its GitHub Actions retention period is 90 days.

## Snapshot-specific pre-cleanup software CI: MIXED, NOT FULLY GREEN

| Saved branch and commit | Observed run at that exact SHA | Result |
| --- | --- | --- |
| `main@c533d052` | [GitHub Pages deploy](https://github.com/AARMOU/arcana_table/actions/runs/39495796100) | success; website only |
| `gpt/argentum-upgrade-preview-20261022@83aa342e` | [Argentum upgrade preview package](https://github.com/AARMOU/arcana_table/actions/runs/39844575500) | success; scope is workflow-specific |
| `gpt/b131-card-compatibility-20261022@89536604` | [Myriad port-viability gate](https://github.com/AARMOU/arcana_table/actions/runs/39857770885) | success; scoped gate |
| `gpt/six-precon-worker-integration-20261022@d3687c84` | [Full custom-card regression](https://github.com/AARMOU/arcana_table/actions/runs/39864582988) | **failure**; active workstream, must not change during inventory |
| `gpt/game-over-cause-logging-20261022@273df437` | [Game-over cause logging validation](https://github.com/AARMOU/arcana_table/actions/runs/39863475469) | **failure** |
| `gpt/argentum-six-precons@ff5e2b71` | No matching run found in first 50 listed branch runs | **unverified at this SHA** |

**Interpretation:** The *backup and restore* have passed. This does **not** certify that all application/Argentum regressions pass or that the full project can be safely consolidated yet. Do not infer app health from successful Git-bundle verification.

## Change-control gate

- **No original branch has been deleted, rebased, merged, force-pushed, renamed or changed during this backup.**
- No production `main` update, APK publication, signing, or release is part of the backup.
- Original active branches may advance after this snapshot; treat the saved SHA as an *as-of* state rather than the latest development head.
- Before consolidation, investigate red test gates, log exact test evidence, and reconcile website commits on `main`.
- Do not archive or delete source branches without a further verified preservation check and the owner's explicit approval.

# Arcana coordination backup — Nov 5, 2026, ~10 AM America/Denver

**Backup branch:** `backup/arcana-coordination-20261105-1000mt`

**Original main snapshot:** `4021dc86ee73e84aab33834aea69fc47e4b697a1`.

The branch was created at that exact main commit. All files under this backup directory were added only to the backup branch; no merge, release, engine adoption, local PC changes or production deployment was requested.

## Included

- Complete Git source tree from `main` at the source SHA above (because backup branch forks at it)
- `BOARD-883.md`: copy of owner orders board issue body, Revision 48
- `INBOX-949.md`: Boneknuckles' issue body and 15 most recent comments
- `INBOX-950.md`: Goose/Elmer issue body and 15 most recent comments
- This manifest with exact outstanding PR refs at capture time

**Not included:** local PC Codex worktrees, Windows Task Scheduler entries, PC environment variables and credentials, private ChatGPT automation prompt bodies/account state, external GitHub secrets, live database state, Android APK bytes, or complete issue #81 history. Those require separate scoped exports. Do not treat this repository backup as a full machine image.

## GitHub PR / engine references captured

| PR | State at capture | Head SHA | Link |
|---|---|---|---|
| #953 B15: cap Copilot attempts and hand off hard cards | open | `003fb251f6a8aad` | https://github.com/AARMOU/arcana_table/pull/953 |
| #933 B16: add verified Cardkit context to card requests | open | `2dfe197ab796fad` | https://github.com/AARMOU/arcana_table/pull/933 |
| #972 Fix Control Work live-status reads blocked by deployed proxy | open (draft) | `ca59f8c196e592e` | https://github.com/AARMOU/arcana_table/pull/972 |
| #970 Repair daily Argentum conflicts and stack-copy compatibility | open (draft) | `61a4cbf0208c07e` | https://github.com/AARMOU/arcana_table/pull/970 |
| #951 fix(Control): keep unresolved card progress visible after activity goes stale | open (draft) | `198d6d1069bbf41` | https://github.com/AARMOU/arcana_table/pull/951 |
| #971 Fix stale owner board with independent live-status heartbeat | merged | `44a2c126bfd0244` | https://github.com/AARMOU/arcana_table/pull/971 |
| #947 Necron: Their Number Is Legion + intrinsic graveyard-cast X selection | open (draft) | `4eae253d5746129` | https://github.com/AARMOU/arcana_table/pull/947 |
| #948 Carry intrinsic zone-cast X selection to development (0040) | open (draft) | `6812963302de69f` | https://github.com/AARMOU/arcana_table/pull/948 |

## Coordination at capture

- Owner orders: #883, GitHub evidence #81, Boneknuckles inbox #949, Goose/Elmer inbox #950
- Worker times per owner board: Elmer :05, Boneknuckles :20, Red Bull :35, Goose :50; Claude off until Saturday afternoon
- **Important observed difference:** Goose's ChatGPT :50 automation was actually DISABLED in the account at capture, despite board describing it as scheduled. Do not infer successful hourly runs from the board.
- Three active ChatGPT automations were independently visible: Arcana Daily Development Report, Arcana GitHub and AI Credit Watch, Arcana Boneknuckles Exact Hourly; other 14 records disabled.
- Keep all spending/merge/Cloudflare/engine safeguards from original #883. Recovery is read-only until owner specifically approves any write.

## Restore steps (do not execute without owner approval)

1. To inspect the original source tree, use `git show 4021dc86ee73e84aab33834aea69fc47e4b697a1` or check out the backup branch in a **separate** worktree.
2. Compare desired files against current main, and cherry-pick or manually reapply only reviewed changes. Do not force-reset or merge backup into main without explicit owner approval and required CI.
3. Restore owner-order text and inboxes manually using these files only after reconciling newer owner decisions; do not erase subsequent decisions.
4. Restore ChatGPT automations through their own settings/configuration interface, and Windows timers on the authorized PC from their own exports; this repo does not contain those private prompt bodies.
5. Re-run integration and security checks. Separate approval remains required for production/Pages/Cloudflare/engine/release actions.

Generated as a point-in-time record. It does not assert current worker liveness.

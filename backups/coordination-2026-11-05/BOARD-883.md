# Archived owner orders board #883

Captured: 2026-11-05 around 10:00 America/Denver.
Original: https://github.com/AARMOU/arcana_table/issues/883
GitHub issue updated_at at capture: 2026-11-05T17:01:04Z
Original title: 📌 Current orders & owner decisions

---

**Updated:** 2026-11-05 16:03 UTC · **Revision 48** · Reconciled verified operational state (Nov 5). Owner decisions unchanged. Live GitHub reports: [automatic status](https://github.com/AARMOU/arcana_table/issues/883#issuecomment-6660916658) · [Goose/Elmer inbox](https://github.com/AARMOU/arcana_table/issues/950) · [Boneknuckles inbox](https://github.com/AARMOU/arcana_table/issues/949).

**Every agent: read this first, every run.** If a newer owner instruction conflicts, pause that work and ask. Report each order as **picked up / blocked / done**, with a link.

## 0. Live verified status (bot-owned; updates separately)
- [Latest automated status and worker inbox evidence](https://github.com/AARMOU/arcana_table/issues/883#issuecomment-6660916658). Its `checkedAt` is the refresh heartbeat; the issue body is a reconciled snapshot, **not a live agent dashboard**.
- The automatic engine-watch feed still reflects the **Nov 4 failed live upgrade**; [Red Bull's later green repair dry run](https://github.com/AARMOU/arcana_table/actions/runs/42003271575) is separate. A passed dry run is **not** an installed engine update.
- [Control board repair #971](https://github.com/AARMOU/arcana_table/pull/971) was merged and [GitHub Pages deployed successfully](https://github.com/AARMOU/arcana_table/actions/runs/42046931980). Android installed-PWA smoke **FAILED / PENDING RETEST**: live-status comments transport blocked by deployed proxy; scoped repair [draft #972](https://github.com/AARMOU/arcana_table/pull/972) has green exact-head controls/site checks, awaiting Goose review and owner merge/Pages approval.



## 1. Owner decisions
- **D1** Priority: the six original WOTC precons. No decks on hold.
- **D3/D17** Copilot: **70 AI credits/day** (running total); monthly budget **3,500** is intentional. Don't lower it.
- **D4** Claude is PM/lead engineer and merges development branches. Releases, signing, Play, Cloudflare, budgets, safeguards need the owner.
- **D5/D19** Never stop running work unless the owner says so (exception: a validate run silent 60+ min, with reason posted in #81).
- **D6/D16** Every test app has an updated What's New. **D7** Rules questions get an issue `❓ Rules question: <Card>`. **D8** New engine mechanics get a `CARD-PATTERNS.md` recipe. **D9** Every order has Estimate/Actual ([#867](https://github.com/AARMOU/arcana_table/issues/867)).
- **D10** No branch deletion or bulk-closing.
- **D11/D14** The owner doesn't use GitHub; this page shows on the **Work** tab of **Arcana Control**. No new websites or apps. Plain language.
- **D12** Owner instructions go in #81 as `OWNER DECISION (via <agent>)`. **D13** GSD takes orders from Claude PM. **D15** No spending test or extra workers without the owner's OK on the cost. **D18** Verify against the source. **D20** Keep your line current.
- **D21** Engine problems while Claude is off: **Red Bull → Boneknuckles → owner** (D28; Goose removed).
- **D22** Replaced (owner, Nov 4): the owner decides on the build's engine after Red Bull's real job ENGINE-1 ([order](https://github.com/AARMOU/arcana_table/issues/81#issuecomment-6637330860)).
- **D26** (owner, Nov 4) Owner requests and approvals are done **right away**, not at the next check-in, unless the owner says to schedule them. Worker parts are posted as `URGENT`, and the owner starts that worker directly.
- **D23** Goose hourly at :50; Boneknuckles hourly at :20, exact times.
- **D25** (owner, Nov 4) Elmer covers GSD with GSD's permissions until Sat afternoon; Red Bull runs heavy computing jobs on the owner's PC to keep AI usage low. Engine patches always carried to the development line; engine-upgrade failures reported (D21 unchanged).
- **D24** (owner, Nov 3) Scheduled agents: read this page first, then **ChatGPT workers (Goose, Boneknuckles): only your inbox, never #81 (D27)**; Codex workers and Claude: only #81's last page; retry GitHub errors; **never disable your own scheduled task**; report two failed runs in a row and keep running; check you can push before engineering work ([rules](https://github.com/AARMOU/arcana_table/issues/81#issuecomment-6620458544)).
- **D27** (owner, Nov 4) **ChatGPT workers read their inbox, not #81** (#81 is too big for their connection): 🔧 Boneknuckles [#949](https://github.com/AARMOU/arcana_table/issues/949), 🪿 Goose [#950](https://github.com/AARMOU/arcana_table/issues/950). They read this page plus their inbox, and report in their inbox. Codex workers and Claude still use #81.
- **D28** (owner, Nov 4) **Claude PM is off until Saturday afternoon.** PM is **split**: 🪿 Goose watches, reviews and reports (inbox #950: "For Elmer" / "For the owner"); 🚀 Elmer does the hands-on work (merges, ENGINE-1 judging, daily engine check, test app). Engine problems: Red Bull → Boneknuckles → owner ([order](https://github.com/AARMOU/arcana_table/issues/81#issuecomment-6637878346)).
- Contract: [standing](https://github.com/AARMOU/arcana_table/issues/81#issuecomment-6536029052), [addendum 1](https://github.com/AARMOU/arcana_table/issues/81#issuecomment-6563983134), [addendum 2](https://github.com/AARMOU/arcana_table/issues/81#issuecomment-6565664065).

## 2. Current orders ([today's orders](https://github.com/AARMOU/arcana_table/issues/81#issuecomment-6620188201))
| ID | Agent | Order | Status |
|---|---|---|---|
| DRILL-7Q4K | ~~Goose~~ | Practice engine repair | **ENDED by owner** Nov 4, 10:21 am MT ([decision](https://github.com/AARMOU/arcana_table/issues/81#issuecomment-6637271738)). New drill for Codex: to be ordered |
| B15 → B13 → B14 | Boneknuckles | Copilot 2-try cap; app-task lane; recipe back-fill | **B15 IMPLEMENTED / GOOSE RE-REVIEW PENDING:** [PR #953](https://github.com/AARMOU/arcana_table/pull/953) at `003fb25`, [controls green](https://github.com/AARMOU/arcana_table/actions/runs/42003325617); board handoff no longer rewrites #883 and cap-lift requires audited owner label. **Unmerged. B13/B14 blocked** awaiting full PM orders. [Boneknuckles report](https://github.com/AARMOU/arcana_table/issues/949#issuecomment-6662236955) |
| B16 | Boneknuckles | Better card requests ([#933](https://github.com/AARMOU/arcana_table/pull/933)) | **IMPLEMENTED / GOOSE RE-REVIEW PENDING:** packet admission/negative tests corrected in [PR #933](https://github.com/AARMOU/arcana_table/pull/933) at `2dfe197`; [controls green](https://github.com/AARMOU/arcana_table/actions/runs/42004572437). **Unmerged**; prior changes-needed review belongs to older head. |
| G-R | Goose | Angelic Aberration certification check | **CERTIFICATION NOT ACCEPTED:** prior Angelic Aberration run failed 1 of 6 checks; no verified current-build root cause, new green certification, or merge. |
| GSD | Elmer (covering GSD until Sat Nov 7 pm), **hourly at :05** | 1) Their Number Is Legion ([#947](https://github.com/AARMOU/arcana_table/pull/947)); 2) Necron routine: Out of the Tombs, Shard of the Void Dragon, Canoptek Wraith, Convergence of Dominion; 3) triage Ruinous Powers (20 missing); 4) triage Tyranid (19 missing); 5) needs-engine cards. GSD's permissions (owner, Nov 4) | **ACTIVE PM / CARD REVIEW:** [#947](https://github.com/AARMOU/arcana_table/pull/947) and [#948](https://github.com/AARMOU/arcana_table/pull/948) remain draft/unmerged; Necron prep not claimed in APK. Elmer independently reviewed and verified [#971 deployment](https://github.com/AARMOU/arcana_table/issues/950#issuecomment-6661348310), while [#970](https://github.com/AARMOU/arcana_table/pull/970) full engine source review remains open. |
| ENGINE-1 | 🥫 Red Bull, **URGENT** | Real job: bring the development engine to 24 h behind upstream; fix clashes; green GitHub dry run on `gpt/argentum-weekly-fix-redbull-20261104` | **GREEN DRY RUN / ENGINE NOT ADOPTED:** Red Bull repair [draft PR #970](https://github.com/AARMOU/arcana_table/pull/970) at `61a4cbf`, [mandatory CI dry run SUCCESS](https://github.com/AARMOU/arcana_table/actions/runs/42003271575); Elmer accepted the late dry-run deliverable. Full source/rerere/docs review still open; no PR merge, new engine installed into B141, or owner engine selection. [Evidence](https://github.com/AARMOU/arcana_table/issues/950#issuecomment-6658693391) |
| RB | 🥫 Red Bull, **hourly at :35**, heavy jobs on the owner's PC | 1) PC readiness check; 2) local B141 build + full tests vs GitHub; 3) standing pre-check of new card/engine PR heads; 4) daily engine look-ahead (read-only). PC does the grinding, short summaries only | **#954 AI PERFORMANCE TRIAGE IN PROGRESS:** existing 6 baseline + 3 production-style synthetic tests passed; sampled frozen-state profiling retest reported launched, **no production fix or measured owner-game speedup**. [Issue #954](https://github.com/AARMOU/arcana_table/issues/954#issuecomment-6662037629). Nov4 preview PC card/app tests passed; no PC process-liveness claim. |
| SCHED-1 | Elmer, Red Bull | Set up your own hourly timer (see 2a) | **DONE (first-run verification):** Elmer :05 and Red Bull :35 PC timers and autonomous check-ins evidenced in worker reports; does **not** prove every future trigger. [Elmer verification](https://github.com/AARMOU/arcana_table/issues/950#issuecomment-6661348310) |
| PM | ~~Claude~~ → **Goose (watch/review) + Elmer (hands-on)** | PM duties until Sat afternoon | **ACTIVE under D28:** Goose watch/review; Elmer hands-on PM until Saturday afternoon; Claude off. Board [#971 merged/deployed](https://github.com/AARMOU/arcana_table/pull/971); owner installed-Control smoke **FAILED / PENDING RETEST**; repair [#972](https://github.com/AARMOU/arcana_table/pull/972) awaits review/publication approval. |
| CP | Copilot | Certification suites, small tasks | **LAST VERIFIED NOV4:** previous certification suites merged and feeder reported idle; **Nov5 paid queue/credit usage not independently rechecked here**. Retain existing owner spending limits and authorization gates. |
| APK-NOV4 | 🚀 Elmer integration + 🥫 Red Bull PC tests/build | **Owner: newest safe integrated Android test .apk by 5 PM MT Nov 4**, include current eligible validated work, updated What's New, verify actual APK file in Google Drive; report excluded work | **DELIVERED LATE / REDUCED SCOPE:** actual [Drive APK](https://drive.google.com/file/d/1G-YJx9zk20PK2AeNCqtVikK5up5mkSeb/view?usp=drivesdk), 41,540,790 bytes, source `326b92f`; Red Bull card 1,082 / Android app 482 tests, 0 failures. **Nov4 5 PM deadline missed; remote SHA-256 match and phone smoke unverified.** No B142/engine promotion. [Handoff](https://github.com/AARMOU/arcana_table/issues/950#issuecomment-6649183574) |
| CONTROL-951 | 🪿 Goose fix; 🚀 Elmer independent review | Control card-progress fix [PR #951](https://github.com/AARMOU/arcana_table/pull/951) | **SOURCE REVIEW DONE / PR STILL DRAFT:** Elmer reviewed [#951](https://github.com/AARMOU/arcana_table/pull/951) with 7 focused tests passing; exact-head site/installed-PWA acceptance is not established. **Do not confuse with separate #971**, which is merged and deployed. No #951 merge. |
| UI-NOV4 | 🚀 Elmer / 🥫 Red Bull PC | Owner wants hand drawer information, combat opponent filtering, tap assignment, color-coded attack lines in 5 PM app ([order](https://github.com/AARMOU/arcana_table/issues/950#issuecomment-6640840278)) | **PARTIAL PREVIEW DELIVERED LATE:** `326b92f` with PC tests green and [Drive APK](https://drive.google.com/file/d/1G-YJx9zk20PK2AeNCqtVikK5up5mkSeb/view?usp=drivesdk). Colored attack lines/blank-area redesign excluded; full requested UI scope and Android phone smoke not verified. [Evidence](https://github.com/AARMOU/arcana_table/issues/950#issuecomment-6650819149) |
| B142-PROMOTION | 🚀 Elmer / 🥫 Red Bull | Reconcile existing B142 test branch with current B141 work for Nov 4 5 PM newest-safe-tested APK; clarify actual build labeling | **NOT PROMOTED:** no verified B142/B141 reconciliation or B142 inclusion in late Nov4 delivered preview; previous branch-divergence count was Nov4-only evidence, not a fresh comparison. B142 requires separate integration/test/What’s New proof. |
| LOCAL-WATCHDOG | 🥫 Red Bull, after higher priorities | Low-cost Windows 5-minute monitor-only heartbeat watchdog for Elmer + Red Bull, proof before any bounded AI auto-recovery | **BLOCKED / OPEN [#959](https://github.com/AARMOU/arcana_table/issues/959):** the Nov4 approval-wait was later superseded by a completed/idle Red Bull turn; durable five-minute actionable wait detector + notification transport still unproved. No auto-approval or unattended-resume claim. |
| MSG-ROUTE | Elmer (interim PM) | After ENGINE-1, change the automatic engine-clash message to Red Bull → Boneknuckles → owner, identically on main and development | **PENDING:** automatic engine failure text still says Goose, contrary to owner D21 **Red Bull → Boneknuckles → owner**. Repair/upstream routing update on main/development not verified or deployed. |
| LATER-TIMERS | Claude PM, later | Make the watchdogs and timers solid and prove they work (owner, Nov 3: "note it for later"). Cloudflare timer **not approved** | **DEFERRED:** no authorized new Cloudflare timer, service, paid worker, or watchdog deployment. |
| CARD-IMAGES | Goose diagnoses, Boneknuckles fixes | Card pictures slow or missing ([order](https://github.com/AARMOU/arcana_table/issues/81#issuecomment-6621549335)) | **DIAGNOSIS DONE / FIX BLOCKED:** historical card-image findings exist, but current B141 candidate has `DeckImport.kt` and no ordered `CardImages.kt`; actual active image caller not established. No safe patch or performance measurement yet. [Boneknuckles check](https://github.com/AARMOU/arcana_table/issues/949#issuecomment-6662236955) |
| FIX-ONLINE → AI-SPEED → FIX-MANA → CHK-SLIVER | PM assigns after the drill | Online room drops after app switch; slow AI responses late game; Spawn payment refused after undo; check Slivers ([order](https://github.com/AARMOU/arcana_table/issues/81#issuecomment-6604315379)) | **AI-SPEED PICKED UP by Red Bull:** [#954](https://github.com/AARMOU/arcana_table/issues/954) profiling on supported B141, existing synthetic cases passed; no owner-game reproduction or speed fix yet. Online reconnect, mana, Slivers still queued. |

## 2a. ⏰ Worker schedule (owner, Nov 4)
| Worker | Runs | Where it runs | Who sets the timer |
|---|---|---|---|
| 🚀 Elmer (Codex) | every hour at **:05** | owner's PC | **Elmer sets it up himself** (order SCHED-1) |
| 🥫 Red Bull (Codex) | every hour at **:35** | owner's PC | **Red Bull sets it up himself** (order SCHED-1) |
| 🪿 Goose (ChatGPT) | every hour at **:50** | ChatGPT scheduled task | already set |
| 🔧 Boneknuckles (ChatGPT Work) | every hour at **:20** | ChatGPT scheduled task | already set |
| 🤖 Claude PM | **off until Saturday afternoon** (D28) | Claude scheduled tasks switched off per owner | hands-on PM: Elmer; watch/review: Goose |
| Engine upgrade | daily, automatic | GitHub | already set |

**SCHED-1 outcome:** Both local hourly timers and initial autonomous check-ins have been verified in worker reports. Do not interpret those check-ins as proof that Codex is currently running; [waiting-state visibility #959](https://github.com/AARMOU/arcana_table/issues/959) is still open. Existing :05/:35/:50/:20 schedules remain unchanged.

## 3. Waiting on the owner
- **Installed Android Control smoke (not an approval gate for the already-completed merge):** open [Arcana Control](https://aarmou.github.io/arcana_table/control/) online on the phone, refresh/reopen Work and confirm Live activity has a recent checked time and the manual snapshot is labeled separately. [Pages deployment already verified](https://github.com/AARMOU/arcana_table/actions/runs/42046931980). No new spend, engine adoption, or release approval is inferred.

## 4. Done recently
- **Board live status #971 merged and deployed** Nov5, [Pages green](https://github.com/AARMOU/arcana_table/actions/runs/42046931980), [live bot feed](https://github.com/AARMOU/arcana_table/issues/883#issuecomment-6660916658); Android installed-PWA smoke pending.
- **ENGINE-1 repaired and green dry-run accepted late** Nov5 ([PR #970](https://github.com/AARMOU/arcana_table/pull/970)); source review and actual engine adoption pending.
- **Nov4 preview APK delivered late/reduced scope** ([Drive file](https://drive.google.com/file/d/1G-YJx9zk20PK2AeNCqtVikK5up5mkSeb/view?usp=drivesdk)); phone install/hash verification pending.
- **B141 update 4 test app** (13 Necron cards), Nov 3 7:45 pm.
- **Necron 89 → 92:** Trazyn, Technomancer, The War in Heaven (GSD).
- **Necron 79 → 89:** ten cards incl. Imotekh, Biotransference, Caged Sun (GSD).
- **B9 smarter retries** merged (#880), Nov 3.

## 5. Deck progress (last reported integrated counts; not independently re-counted Nov5; unmerged work excluded)
| Deck | Cards |
|---|---|
| Eldrazi Incursion | 100 ✅ |
| Necron Dynasties | 92 |
| Eldrazi Unbound | 88 |
| Forces of the Imperium | 85 |
| Tyranid Swarm | 81 |
| The Ruinous Powers | 80 |

## 6. Next test build
- **Last delivered:** [Nov4 late partial B141 UI preview](https://drive.google.com/file/d/1G-YJx9zk20PK2AeNCqtVikK5up5mkSeb/view?usp=drivesdk) at `326b92f` (41,540,790 bytes); exact phone install and remote SHA-256 not verified.
- **Next:** integrate only independently approved card/engine/UI work, green tests, accurate What's New and exact APK/Drive validation; **no fixed date or new engine/B142 inclusion claimed**.

---
_Operational statuses reconciled by Goose from PRs, CI, and worker inboxes on Nov 5. Owner decision text preserved. Future PM updates must avoid replacing newer status evidence with historical snapshots._

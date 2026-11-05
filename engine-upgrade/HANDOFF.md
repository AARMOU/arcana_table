# Finishing a red weekly Argentum upgrade

**Daily, one day behind.** The upgrade runs every day at 11:23 UTC against upstream main as it stood 24 hours
earlier, and merges into `gpt/arcana-integration-next` by itself when every check is green (the Monday crons
are backups). If GitHub drops the 11:23 UTC run, the watchdog on `main` restarts it as the daily run
(`daily: true`: 24 h behind, alerts the same way). Upstream lands ~75 changes a day, so most days a red run is
one small clash.

**A fix branch is safe to push.** A push to `gpt/argentum-weekly-fix-*` or `claude/argentum-weekly-fix-*` runs
only this workflow's checks: `validate` has a read-only token, and `publish` and the status step are skipped on
push. Nothing merges, no app is built for release, nothing is spent.

**Who fixes a red run (owner, 2026-11-02, for cost):** 🪿 **Goose first** — the run posts on #81 once, when the
problem first appears. If the same problem is still there on the next daily run (~a day later), the run asks
**Claude** to take over. Card decisions stay GPT's. A green fix merges through a draft PR into the base;
Arcana Control shows the owner the engine status in plain words.

Every red run already contains a **Handoff** section (in the draft PR or the issue, and in the run summary)
with these commands filled in for that run: the upstream commit, the branch to start from and a fix-branch
name. This page explains them.

## Which case is it?

| The run says | Where it is reported | Start from | Lane |
|---|---|---|---|
| `blocked: patch conflict` | issue | `origin/gpt/arcana-integration-next` | engine (Claude; GPT covers) |
| `blocked: engine build failed` | issue (compiler errors listed) | `origin/bot/argentum-weekly` | engine (Claude; GPT covers) |
| `(needs work)`: custom-card collision | draft PR from `bot/argentum-weekly` | `origin/gpt/arcana-integration-next` | cards (GPT) |
| `(needs work)`: failing test or lost precon slot | draft PR | `origin/gpt/arcana-integration-next` (the bot branch carries its reconciled override file, which the clean-header test rejects) | whoever owns the failing area |

Work on a branch named `gpt/argentum-weekly-fix-<date>` (or `claude/…`). **Pushing it starts a dry run
automatically**: the full upgrade, read-only, nothing published. Iterate until that dry run is green
(apart from card decisions you have recorded), then open a draft PR from the fix branch into
`gpt/arcana-integration-next`, link the weekly run, and post on #81.

You need git and Python 3. Building the engine needs Java 21 and network access to the Gradle plugin
portal; if your environment lacks either, skip local builds and let the dry run build it.

## Patch conflict

Upstream changed the same lines as one of Arcana's patches and git could not merge them.

```sh
git fetch origin && git checkout -b gpt/argentum-weekly-fix-<date> origin/gpt/arcana-integration-next
git clone --filter=blob:none https://github.com/wingedsheep/argentum-engine argentum-engine
python3 engine-upgrade/weekly.py rebase argentum-engine <UPSTREAM_SHA> --keep-conflict
```

The conflict is left in `argentum-engine/`. For each conflicted file:

1. Read the conflict markers. "HEAD" is upstream; the other side is the Arcana patch named in the run.
2. Keep both intents: upstream's new behavior **and** what the patch was for (its commit message and
   `argentum-patches/README.md` say why it exists). Do not simply take one side.
3. `git -C argentum-engine add <file>`, then `git -C argentum-engine -c core.editor=true rebase --continue`.

Repeat until the rebase completes, then:

```sh
python3 engine-upgrade/weekly.py finish argentum-engine
git add argentum-patches engine-upgrade
git commit -m "Resolve weekly upgrade conflict in <patch>"
git push -u origin gpt/argentum-weekly-fix-<date>
```

`finish` rewrites the patch files and the pins, and copies your resolution into `engine-upgrade/rerere/`,
so the weekly robot replays it from then on.

**A modify/delete conflict** (the run shows no text diff) means an Arcana patch deletes or replaces a file
upstream just edited. Check that the replacement covers upstream's change (or port the change into it),
then add the file to `engine-upgrade/rebase-rules.json` under `keep_deleted`, with a `why` that says how the
change is covered, and run the `rebase` command again.

## Engine build failed

The patches merged as text, but the patched engine does not compile (or fails the strict audit).
Upstream renamed or reshaped something a patch uses.

```sh
git fetch origin && git checkout -b gpt/argentum-weekly-fix-<date> origin/bot/argentum-weekly
git clone --filter=blob:none https://github.com/wingedsheep/argentum-engine argentum-engine
python3 engine-upgrade/weekly.py checkout argentum-engine
```

`checkout` puts the series in `argentum-engine/` as one commit per patch (`arcana: 00xx-…`) on the new
upstream. For each compiler error in the issue:

1. Find the patch that owns the file: `git -C argentum-engine log --oneline -- <file>`.
2. Fix the code to match upstream's new API while keeping the patch's behavior.
3. `git -C argentum-engine commit -a --fixup <that patch's commit>`.

Then:

```sh
python3 engine-upgrade/weekly.py finish argentum-engine   # folds the fixups into their patches
git add argentum-patches engine-upgrade
git commit -m "Fix weekly upgrade engine build (<patch>)"
git push -u origin gpt/argentum-weekly-fix-<date>
```

`finish` refuses commits that do not belong to a patch: every change must be part of a patch.

## Custom-card collision (automatic)

Upstream now implements a card Arcana defined itself. This no longer needs a person: every Arcana custom
card is kept, and `weekly.py reconcile` (the workflow's custom-card step) decides which version is played.

1. Each collided card first plays Argentum's version: the app prefers the library, and the custom-card
   scenario tests register exactly what the app plays (`CardPool.inPlay`).
2. If a card's scenario tests fail against Argentum's version, the card is written to
   `android/app/src/main/assets/argentum/custom-overrides.txt`, so Arcana's tested version stays in
   play (app and tests), and the suite re-runs to confirm.
3. The list starts clean every week, so a card returns to Argentum's version once that version passes.

The PR body's **Custom-card reconciliation** section lists both groups. Do not delete a custom card to
clear a run; if a test fails for a reason unrelated to a collision, treat it as a failing test (below).

## Engine would not run on Android

The `android` check (`tools/check_android_engine.py`) reads the new jars themselves:

- **Bytecode newer than Java 17**: the Java 17 settings in patch 0002 (`buildSrc/src/main/kotlin/
  arcana-android-target.gradle.kts`, applied from `kotlin-jvm.gradle.kts`'s plugins block) stopped
  applying. Check that the one hook line survived the rebase.
- **A Java call phones at minSdk do not have**: only calls the shipped engine did not already make block.
  If the Android dexer backports it (D8's backported methods), add it to `tools/android-engine-allowlist.txt`
  with the reason; otherwise replace the call in the patch, or patch upstream's call with an Android-safe one.
- **Library versions / a new library**: `align` already raises kotlin / kotlinx versions in the app's build
  files. A library the app does not ship at all must be added to the app deliberately (not by the robot).

## Current build out of sync, or its cards fail

- **"engine patches the current build uses are not carried here"**: a numbered build branch (main's
  `integration/policy.json` `target`) gained an engine patch directly. Copy each named patch from that
  branch into `argentum-patches/` and the MANDATORY list here, the same day (byte-identical when the pins
  match). Checked on every daily run, up-to-date days included: an up-to-date day with an uncarried patch is
  reported as blocked and asks Goose to carry it.
- **"does not apply even on the pinned engine"**: the patch was made on a different engine (usually a
  numbered build's, which pins an older upstream). Apply it with `git apply --reject` on the pin, re-fit the
  rejected hunks to the current wording, and regenerate it the way `weekly.py` writes series patches
  (2026-11-03, 0036).
- **The current build's cards fail on the new engine**: tested in a scratch copy of that build's own
  Android tree with only the engine swapped in. Cards Argentum now also has are reconciled first, exactly
  like this base's (Arcana's version kept where Argentum's fails its tests). What still fails is a card
  regression (GPT's lane) unless the failure is in an engine patch.

## Failing test or lost precon slot

Find the root cause. Never weaken, skip or delete a test (`AGENTS.md`). A lost precon slot means a card
the six-precon manifest uses no longer resolves: find out whether upstream renamed or removed it.

## Never

- Push to `bot/argentum-weekly`. The robot only replaces that branch when its tip is the robot's own
  commit; anything else stops the next weekly run.
- Weaken or skip a test, or hand-edit `custom-overrides.txt` (the reconcile step owns it).
- Merge, release, sign or publish. Those are the owner's.

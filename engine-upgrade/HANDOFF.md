# Finishing a red weekly Argentum upgrade

For whoever picks up a weekly upgrade that did not come back green: Claude (engine lane) or GPT (covering
when Claude is unavailable, and always for card decisions). Coordination is issue #81. The owner merges.

Every red run already contains a **Handoff** section (in the draft PR or the issue, and in the run summary)
with these commands filled in for that run: the upstream commit, the branch to start from and a fix-branch
name. This page explains them.

## Which case is it?

| The run says | Where it is reported | Start from | Lane |
|---|---|---|---|
| `blocked: patch conflict` | issue | `origin/gpt/arcana-integration-next` | engine (Claude; GPT covers) |
| `blocked: engine build failed` | issue (compiler errors listed) | `origin/bot/argentum-weekly` | engine (Claude; GPT covers) |
| `(needs work)`: custom-card collision | draft PR from `bot/argentum-weekly` | `origin/bot/argentum-weekly` | cards (GPT) |
| `(needs work)`: failing test or lost precon slot | draft PR | `origin/bot/argentum-weekly` | whoever owns the failing area |

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

## Custom-card collision

Upstream now implements a card Arcana defined itself. `CustomCardDataTest.noCustomCardShadowsAnArgentumCard`
stays strict and is never exempted. For each card listed:

1. Compare upstream's implementation with Arcana's custom one (`android/custom-cards/.../cards/`).
2. Run the card's positive and negative scenarios against upstream's version.
3. If upstream is correct: retire the custom card (remove its definition and its `CustomCards.kt` entry,
   re-export with `./gradlew :custom-cards:exportCards`), keeping a scenario test that now exercises
   upstream's card. If it is not: report it on #81 and to upstream; the owner decides whether the upgrade
   waits.

## Failing test or lost precon slot

Find the root cause. Never weaken, skip or delete a test (`AGENTS.md`). A lost precon slot means a card
the six-precon manifest uses no longer resolves: find out whether upstream renamed or removed it.

## Never

- Push to `bot/argentum-weekly`. The robot only replaces that branch when its tip is the robot's own
  commit; anything else stops the next weekly run.
- Weaken or skip a test, or add a collision exemption.
- Merge, release, sign or publish. Those are the owner's.

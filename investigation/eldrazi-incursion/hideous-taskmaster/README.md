# Hideous Taskmaster: reproduced per-opponent target binding blocker

Unregistered candidate against #171's committed runtime (base `3fdaf20af1a492d16fb2e824067d1d3d9b09f3bd`).
**Not a playable card, no index increment.** No runtime jars or weekly-upgrade files changed.

Ten actual engine scenarios execute: nine pass, one fails. The failure transfers the chosen
creature from opponent A to opponent B **after target selection and before resolution**. It
must become illegal; instead the caster gains control and the temporary abilities. The setup
explicitly verifies opponent B controls the creature before resolving. The assertion is retained.

The official [LOTR release notes](https://magic.wizards.com/en/news/feature/the-lord-of-the-rings-tales-of-middle-earth-release-notes)
give this ruling for **In the Darkness Bind Them**, which has the identical per-opponent control
targeting sentence. Applying that ruling to Taskmaster is the rules interpretation used here.
The [MH3 release notes](https://magic.wizards.com/en/news/feature/modern-horizons-3-release-notes)
confirm its cast trigger is independent of the creature spell.

Pinned upstream `61e71e586b100faa661f86cac827ac3c3bbb5a78`,
`rules-engine/src/main/kotlin/com/wingedsheep/engine/mechanics/stack/ResolutionTargetValidator.kt`,
`targetsSharingAController` explicitly excludes the dynamic per-opponent distribution and notes
that which creature changed controllers is not recorded. `differentControllers` checks initial
target distribution; it does not solve this resolution-time requirement. A different opponent's
creature still matches `CreatureOpponentControls`, so merely repeating that filter is insufficient.

## Reproduction

```sh
python3 investigation/eldrazi-incursion/hideous-taskmaster/run-candidate.py
```

The runner refuses existing files, injects both candidate sources temporarily, runs the real
custom-card tests, then removes both sources even on failure. It does not register/export the
card or replace runtime jars. **Expected result today: nonzero, 10 tests / 1 failure.**
`result.xml` preserves the observed failure; no ignored test, expected-failure annotation, mock
engine or weakened acceptance assertion is used. A local standalone Gradle JVM harness with
the same source directories and exact runtime/dependencies independently reproduces the result.

Passing scenarios already cover: one creature per opponent in three players; cast trigger before
spell resolution; illegal own/duplicate-controller targets; zero targets; temporary control,
haste/trample/annihilator and expiry; printed annihilator; reanimation does not trigger; countering
the creature does not counter its trigger; disappeared target; Devoid still requires red mana.

## Required engine work

Bind each per-opponent chosen target to that opponent at target-selection time. Preserve/rebind
those associations through copies and retargeting. On resolution invalidate only the creature
that changed controllers, leaving legal targets effective. Add positive/negative engine tests,
including two opponents with one target changing control, and copied/retargeted triggers. Then
rerun these unchanged ten acceptance scenarios against a separately approved runtime candidate.

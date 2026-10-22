# Six-precon SDK gaps

These are investigated mechanic gaps, not permission to replace the printed rules.
Cards listed here remain missing until a faithful implementation passes executable
regressions and export. No placeholder definitions should be registered for them.

Inspected Android runtime fingerprints:

- `argentum-mtg-sdk.jar`: `cf519e0622a553d6f5586b43a286c0c99d78a85b5b679e1517b36afbc4f51b03`
- `argentum-rules-engine.jar`: `88cf6f8523375a0064ce39f1575defe130435567bf7fc147da851d1ec1875a7e`

Readable SDK/engine source inspected at upstream commit
`7a9663d8e4df4ce7db223023ae7714f0028e08a9`. Its surface is not necessarily
identical to the pinned Android JARs; verify binary availability as well as source.

## Granted cascade — Zhulodok, Abaddon, Sloppity Bilepiper

Existing cards such as `NoiseMarine.kt` explicitly author a self-cast trigger
with `Effects.Cascade`. Merely granting the `CASCADE` keyword does not install
that trigger in the inspected pinned engine.

Relevant upstream paths, relative to the engine repository:

- `mtg-sdk/src/main/kotlin/com/wingedsheep/sdk/scripting/SpellStaticAbilities.kt`:
  `GrantKeywordToOwnSpells` represents a keyword/filter/from-zone grant.
- `mtg-sdk/src/main/kotlin/com/wingedsheep/sdk/scripting/AbilityGrantStaticAbilities.kt`:
  `GrantTriggeredAbility` grants to a battlefield `GroupFilter`; it cannot grant
  a self-cast ability to a spell on the stack.
- `rules-engine/src/main/kotlin/com/wingedsheep/engine/handlers/actions/spell/CastTriggers.kt`:
  cast-keyword hooks handle storm and several other mechanics; the pinned class
  has no CASCADE hookup. Next-spell keyword riders similarly do not synthesize cascade.

Needed: spell-sourced cascade triggers, with each instance preserved, correct
controller/source identity, from-hand and mana-value filters, and next-spell
rider consumption. Two battlefield observer triggers on Zhulodok would change
rules interactions and are not a substitute.

## The Swarmlord — commander casting history

The pinned SDK JAR does not contain `DynamicAmount.CommanderCasts`, although the
readable upstream `scripting/values/DynamicAmount.kt` and engine
`handlers/DynamicAmountEvaluator.kt` contain a newer implementation. Its
commander-zone cast history must be available in the deployed runtime before
The Swarmlord's entry counters can be implemented completely.

## Ulalek — copying a controlled stack snapshot

`CardSource` in `scripting/effects/PipelineEffects.kt` has no source collecting
controlled spell and ability objects from the shared stack. `FromZone(STACK)`
reads ordinary player zones through `GatherCardsExecutor`, not `state.stack`.
Single-object `CopyTargetSpell` / `CopyTargetSpellOrAbility` effects do not
express the complete operation.

Needed: optional {C}{C} payment, snapshotting/copying controlled spells first,
then the other controlled activated and triggered abilities, excluding the
resolving Ulalek trigger, with individual retargeting and correct stack order,
source identities and controllers.

## Seeker of Slaanesh — attack with at least one creature

The pinned SDK has `MustAttack`, `MustAttackDefender`, and
`OpponentsMustAttackYou`. The first requires each affected creature to attack;
the last constrains the defender. Neither means an opponent must attack with
at least one creature, allowing that opponent to choose any legal defender.
A reusable combat requirement and its legal-declaration validation are needed.

## Port Town — confirmed collection-based battlefield entry gap

The `OnEnterRun` composition handles its reveal-or-enter-tapped rule when
played normally. `MoveCollectionExecutor` explicitly documents a gap: its
battlefield path does not run `PermanentEntryReplacements.runOnEnterRunEffect`.
The single-card movement path does.

The Growth Spiral scenario failed against the real pinned runtime in
https://github.com/AARMOU/arcana_table/actions/runs/39716732034
(`growthSpiralPuttingTheLandOntoBattlefieldStillRequiresAReveal`).
Normal land play passed, but Growth Spiral put the land onto the battlefield
untapped without any reveal. The candidate definition and all four scenarios are
preserved in `blocked/port-town/`, outside the registry and active source sets,
until collection-entry replacements are fixed. It must not be counted as complete.

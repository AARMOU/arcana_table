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


## Hardest-first work queue — October 22, 2026

The resumed import now prioritizes engine-blocked cards before cards expressible with existing
primitives: Ulalek first, then the granted-cascade commanders (Zhulodok and Abaddon), The Swarmlord,
Seeker of Slaanesh, and the Port Town collection-entry continuation.

Ulalek has a fourth experimental patch, `staged-engine/0004-stack-object-copying.patch`. It widens
the existing batch spell copier into a serializable chosen/controlled stack-object snapshot,
allows ordering and independent retargeting, and composes spells-first / abilities-second.
Ability copies preserve their original source, modes, X and target object identities and emit
`AbilityCopiedEvent` rather than pretending to trigger or activate again. Scenario and SDK
regressions are included in the patch. Validation is pending; see the dedicated
`argentum-stack-copy-enhancements.yml` workflow. No app JARs or live card registry changed.

The exact Ulalek definition is in `blocked/ulalek/`. A newly confirmed independent blocker is
its true colorless-hybrid casting cost: the pinned `ManaCost.parse` does not recognize `{C/W}`
(and the other four colorless/color symbols). Support must include parser/serialization, devotion
and identity, floating-pool matching, restricted mana, auto-tapping, and payment/AI behavior.
Rewriting those symbols as generic mana would be incorrect. Ulalek remains missing until both
engine features are integrated into the Java-17 Android runtime and full custom-card scenarios pass.

## Experimental engine-patch progress (not shipped)

Three independent enhancements are now staged on the isolated six-precon branch:

- `engine-patches/0001-granted-cascade.patch`: synthesizes each *granted*
  cascade instance as a spell-sourced trigger. Includes mana-value-qualified
  grants (including cast X), and retains the existing printed cascade path.
  Executable tests: `engine-patches/GrantedCascadeEngineTest.kt`.
- `staged-engine/0002-commander-casts.patch`: a serialized
  `DynamicAmount.CommanderCasts` and resolver that sums the owner's
  commander-zone cast history across partner commanders. Executable tests:
  `staged-engine/CommanderCastHistoryEngineTest.kt`.
- `staged-engine/0003-opponents-must-attack-someone.patch`: distinct combat
  requirement for each opponent to attack with at least one creature *against
  any legal defender* if able, including AI plan repair. Executable tests:
  `staged-engine/OpponentsMustAttackWithCreatureTest.kt`.

The actions `argentum-engine-enhancements.yml` and
`argentum-combat-enhancements.yml` each check out pinned upstream source in
an ephemeral runner, apply the patches, and run JVM scenario tests. Their
results must be explicitly reviewed before calling the enhancements verified;
JVM-only success does **not** establish Java-17/Android runtime compatibility.
The pinned app jars in `android/app/libs` have not been replaced. No blocked
precon card has been added to the live custom-card registry.

The remaining Ulalek stack-copying feature and Port Town collection-entry
continuation are still unimplemented: neither is safe to approximate.
The Swarmlord history amount was **not** present in either the pinned JAR or
the inspected upstream source, correcting the older note below.

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

The pinned SDK JAR does not contain `DynamicAmount.CommanderCasts`, and the inspected upstream source also lacks it. The experimental
`staged-engine/0002-commander-casts.patch` introduces the amount and its evaluator,
but cannot be shipped until tested and rebuilt for the pinned Android runtime. Its
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

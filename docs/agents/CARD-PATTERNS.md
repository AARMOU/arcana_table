# Card pattern cookbook (verified)

Every code block on this page is an **excerpt of a real file** in this repository — a card that is
registered, exported to the app and passes its scenario test on the pinned engine. `cardkit docs-check`
(run in CI) fails if an excerpt stops matching its source or if any `Effects.*` / `Triggers.*` /
`Conditions.*` / `Costs.*` / `DynamicAmounts.*` / `Filters.*` / `Targets.*` / `Patterns.*` name written here
is missing from the SDK jar. Lines shown as `// …` are elided.

Use this page for *shape*; use these for *coverage*:

| Need | Look in |
|---|---|
| Does `Facade.member` exist? What are its parameter types? | [`generated/sdk-api.md`](generated/sdk-api.md) (from the committed jar) |
| Which tested custom cards use a construct? | [`generated/pattern-index.md`](generated/pattern-index.md) |
| Which implemented card has *this exact sentence*? | `tools/cardkit/cardkit similar "<card>"` or `--text "<line>"` |
| What does a type/effect mean, all fields? | [`argentum-upstream/card-sdk-language-reference.md`](argentum-upstream/card-sdk-language-reference.md), [`argentum-upstream/sdk-index.md`](argentum-upstream/sdk-index.md) |

**The rule:** if the sentence on the card has no matching primitive in those sources, the card is
engine-blocked. Record it (`cardkit blocker`) and stop. Never approximate, never invent a method, never
edit the engine jars or `argentum-patches/` from a card task.

---

## 0. Anatomy of a card

One file per card in `android/custom-cards/src/main/kotlin/com/arcanatable/customcards/cards/`, a
top-level `val` named in PascalCase, `oracleText` copied **verbatim** from current Oracle text.

<!-- excerpt: android/custom-cards/src/main/kotlin/com/arcanatable/customcards/cards/SpaceMarineScout.kt -->
```kotlin
val SpaceMarineScout = card("Space Marine Scout") {
    manaCost = "{2}{W}"
    colorIdentity = "W"
    typeLine = "Creature — Astartes Scout"
    power = 2
    toughness = 1
    oracleText = "First strike, vigilance\nConcealed Position — When this creature enters, " +
        "if an opponent controls more lands than you, you may search your library for a Plains " +
        "card, put it onto the battlefield tapped, then shuffle."

    keywords(Keyword.FIRST_STRIKE, Keyword.VIGILANCE)

    triggeredAbility {
        trigger = Triggers.self.enters()
        interveningIf = Conditions.OpponentControlsMoreLands
        optional = true
        effect = Patterns.Library.searchLibrary(
            filter = GameObjectFilter.Land.withSubtype(Subtype.PLAINS),
            count = 1,
            destination = SearchDestination.BATTLEFIELD,
            entersTapped = true,
        )
    }

    metadata {
        rarity = Rarity.UNCOMMON
        collectorNumber = "15"
        artist = "Games Workshop"
    }
}
```

Then, and only after its scenario test passes, one line in `CustomCards.kt` (the printing's set code goes
here, not in the card):

<!-- excerpt: android/custom-cards/src/main/kotlin/com/arcanatable/customcards/CustomCards.kt -->
```kotlin
        Entry(SpaceMarineScout, "40K", "Six WotC precons"),
```

Facts that trip agents up:

- `colorIdentity` is letters in WUBRG order (`"GU"`, `""` for colourless). Lands omit `manaCost`.
- Ability words / 40K weapon names ("Concealed Position —") stay in `oracleText`; they have no rules
  meaning and need no code.
- `interveningIf` = "if …" in a trigger (checked when it triggers *and* on resolution). `optional = true`
  = "you may". These are different; the scout uses both.
- Imports: most cards use `com.wingedsheep.sdk.dsl.*`; keyword helpers can live in sub-packages
  (`import com.wingedsheep.sdk.dsl.mechanics.squad`). Copy imports from the reference card.
- `tools/cardkit/cardkit newcard "<name>"` writes this skeleton for you with the facts filled in.

## 1. Keyword-only creature

<!-- excerpt: android/custom-cards/src/main/kotlin/com/arcanatable/customcards/cards/Venomthrope.kt -->
```kotlin
val Venomthrope = card("Venomthrope") {
    manaCost = "{1}{G}{U}"
    colorIdentity = "GU"
    typeLine = "Creature — Tyranid"
    power = 2
    toughness = 2
    oracleText = "Flying, deathtouch, hexproof"

    keywords(Keyword.FLYING, Keyword.DEATHTOUCH, Keyword.HEXPROOF)
```

Keywords with a parameter are `keywordAbility(...)`, not `keywords(...)`:

<!-- excerpt: android/custom-cards/src/main/kotlin/com/arcanatable/customcards/cards/Zoanthrope.kt -->
```kotlin
    ravenous()
    keywords(Keyword.FLYING)
    keywordAbility(KeywordAbility.Ward(WardCost.Mana("{2}")))
```

## 2. "When this creature enters" with a target

"Up to one target" is `optional = true` on the target, not on the trigger:

<!-- excerpt: android/custom-cards/src/main/kotlin/com/arcanatable/customcards/cards/SpaceMarineDevastator.kt -->
```kotlin
    squad("{2}")

    triggeredAbility {
        trigger = Triggers.self.enters()
        val permanent = target(TargetFilter.ArtifactOrEnchantment, optional = true)
        effect = Effects.Destroy(permanent)
    }
```

Damage to any target, X from the cast:

<!-- excerpt: android/custom-cards/src/main/kotlin/com/arcanatable/customcards/cards/Zoanthrope.kt -->
```kotlin
    triggeredAbility {
        trigger = Triggers.self.enters()
        val victim = target(Targets.Any)
        effect = Effects.DealDamage(DynamicAmounts.castX(), victim)
    }
```

## 3. "At the beginning of …" (step triggers)

Optional upkeep trigger:

<!-- excerpt: android/custom-cards/src/main/kotlin/com/arcanatable/customcards/cards/AwakeningZone.kt -->
```kotlin
    triggeredAbility {
        trigger = Triggers.you.beginningOf(Step.UPKEEP)
        optional = true
        effect = Effects.CreateEldraziSpawn()
    }
```

Beginning of combat, another creature you control:

<!-- excerpt: android/custom-cards/src/main/kotlin/com/arcanatable/customcards/cards/ChaosTerminatorLord.kt -->
```kotlin
    triggeredAbility {
        trigger = Triggers.you.beginningOf(Step.BEGIN_COMBAT)
        val creature = target(TargetFilter.OtherCreatureYouControl)
        effect = Effects.GrantKeyword(Keyword.DOUBLE_STRIKE, creature)
    }
```

End step with an intervening "if":

<!-- excerpt: android/custom-cards/src/main/kotlin/com/arcanatable/customcards/cards/CompySwarm.kt -->
```kotlin
    triggeredAbility {
        trigger = Triggers.you.beginningOf(Step.END)
        interveningIf = Conditions.CreatureDiedThisTurn
        effect = Effects.CreateTokenCopyOfTarget(EffectTarget.Self, tapped = true)
```

## 4. Attack and dies triggers

Equipment: the trigger subject is `Triggers.attached`, and the triggering creature is
`EffectTarget.TriggeringEntity`:

<!-- excerpt: android/custom-cards/src/main/kotlin/com/arcanatable/customcards/cards/BoneSabres.kt -->
```kotlin
    triggeredAbility {
        trigger = Triggers.attached.attacks()
        effect = Effects.AddCounters(CounterType.PLUS_ONE_PLUS_ONE, 4, EffectTarget.TriggeringEntity)
    }

    equipAbility("{3}")
```

Dies, "you may", an amount read from the creature as it last existed:

<!-- excerpt: android/custom-cards/src/main/kotlin/com/arcanatable/customcards/cards/DinosaurEgg.kt -->
```kotlin
    evolve()

    triggeredAbility {
        trigger = Triggers.self.dies()
        effect = Effects.May(Effects.Discover(DynamicAmounts.triggeringToughness()))
```

## 5. Instants and sorceries

<!-- excerpt: android/custom-cards/src/main/kotlin/com/arcanatable/customcards/cards/TheirNameIsDeath.kt -->
```kotlin
    spell { effect = Effects.DestroyAll(GameObjectFilter.Creature.nonartifact()) }
```

A target plus everything related to it — the group is selected first, only the declared creature is a target:

<!-- excerpt: android/custom-cards/src/main/kotlin/com/arcanatable/customcards/cards/BileBlight.kt -->
```kotlin
    spell {
        val creature = target(TargetFilter.Creature)
        // …
        effect = Effects.ForEachInGroup(
            GroupFilter(GameObjectFilter.Creature.sharingNameWith(creature)),
            Effects.ModifyStats(-3, -3, EffectTarget.IterationEntity),
        )
    }
```

Two target groups ("up to four" = `minCount = 0`) and two effects in order with `then`:

<!-- excerpt: android/custom-cards/src/main/kotlin/com/arcanatable/customcards/cards/Rewind.kt -->
```kotlin
    spell {
        target(TargetFilter.SpellOnStack)
        val lands = targets(TargetFilter.Land, count = 4, minCount = 0)
        effect = Effects.CounterSpell() then Effects.UntapEachTarget()
    }
```

Tokens, plus flashback with an extra cost:

<!-- excerpt: android/custom-cards/src/main/kotlin/com/arcanatable/customcards/cards/AcornHarvest.kt -->
```kotlin
    spell { effect = Effects.CreateToken(1, 1, setOf(Color.GREEN), setOf("Squirrel"), count = 2) }
    keywordAbility(KeywordAbility.flashback("{1}{B}{G}", Costs.additional.PayLife(3)))
```

## 6. Static abilities

Cost reduction for a spell type, and a keyword granted to a group excluding itself:

<!-- excerpt: android/custom-cards/src/main/kotlin/com/arcanatable/customcards/cards/HeraldOfSlaanesh.kt -->
```kotlin
    staticAbility {
        ability = ModifySpellCost(
            target = SpellCostTarget.YouCast(GameObjectFilter.Creature.withSubtype("Demon")),
            modification = CostModification.ReduceGeneric(2)
        )
    }
    staticAbility {
        ability = GrantKeyword(
            Keyword.HASTE,
            GroupFilter(GameObjectFilter.Creature.withSubtype("Demon").youControl(), excludeSelf = true)
        )
    }
```

A counted bonus, and an activated attach ability:

<!-- excerpt: android/custom-cards/src/main/kotlin/com/arcanatable/customcards/cards/CranialPlating.kt -->
```kotlin
    val artifacts = DynamicAmounts.battlefield(Player.You, GameObjectFilter.Artifact).count()
    staticAbility {
        ability = GrantDynamicStats(
            filter = Filters.EquippedCreature,
            powerBonus = artifacts,
            toughnessBonus = DynamicAmount.Fixed(0),
        )
    }

    activatedAbility {
        cost = Costs.Mana("{B}{B}")
        val creature = target(TargetFilter(GameObjectFilter.Creature.youControl()))
        effect = Effects.AttachTargetEquipmentToCreature(EffectTarget.Self, creature)
    }
    equipAbility("{1}")
```

Evasion as a static:

<!-- excerpt: android/custom-cards/src/main/kotlin/com/arcanatable/customcards/cards/HexmarkDestroyer.kt -->
```kotlin
    staticAbility { ability = CantBeBlockedByFewerThan(6) }
    unearth("{4}{B}{B}")
```

## 7. Lands and mana abilities

Enters tapped, a mana ability (`manaAbility = true` **and** `timing = TimingRule.ManaAbility`), cycling:

<!-- excerpt: android/custom-cards/src/main/kotlin/com/arcanatable/customcards/cards/DesertOfTheGlorified.kt -->
```kotlin
    replacementEffect(EntersTapped())
    activatedAbility {
        cost = Costs.Tap
        effect = Effects.AddMana(Color.BLACK)
        manaAbility = true
        timing = TimingRule.ManaAbility
    }
    keywordAbility(KeywordAbility.cycling("{1}{B}"))
```

Enters with counters; composite cost:

<!-- excerpt: android/custom-cards/src/main/kotlin/com/arcanatable/customcards/cards/TendoIceBridge.kt -->
```kotlin
    replacementEffect(EntersWithCounters(counterType = CounterType.CHARGE, count = 1, selfOnly = true))
    // …
    activatedAbility {
        cost = Costs.Composite(Costs.Tap, Costs.RemoveCounterFromSelf(CounterType.CHARGE, 1))
        effect = Effects.AddAnyColorMana(1)
        manaAbility = true
        timing = TimingRule.ManaAbility
    }
```

## 8. Scenario tests

One test file per card, `<Val>ScenarioTest.kt` in `android/custom-cards/src/test/kotlin/com/arcanatable/customcards/`.
`customScenario(Card, "Library Card", …)` starts a real two-player game in player 1's main phase with
your card round-tripped through its exported JSON (exactly what the app loads). **Any Argentum library
card the test puts into play must be named in that call**; tokens, basic lands and all registered custom
cards are always available.

Cast, answer a "may" and a search, then assert on engine state:

<!-- excerpt: android/custom-cards/src/test/kotlin/com/arcanatable/customcards/SpaceMarineScoutScenarioTest.kt -->
```kotlin
        val d = customScenario(SpaceMarineScout)
        val p = d.player1
        val op = d.player2
        d.putPermanentOnBattlefield(op, "Plains")
        d.putPermanentOnBattlefield(op, "Plains")
        val scout = d.putCardInHand(p, "Space Marine Scout")
        d.giveMana(p, Color.WHITE)
        d.giveColorlessMana(p, 2)

        assertNull(d.castSpell(p, scout).error)
        d.resolveAll(Choices(yes = true, pick = listOf("Plains")))

        val newLand = d.permanentsNamed(p, "Plains").single()
        assertTrue(d.state.getEntity(newLand)!!.has<TappedComponent>())
```

…and always the negative case (here: the intervening "if" is false):

<!-- excerpt: android/custom-cards/src/test/kotlin/com/arcanatable/customcards/SpaceMarineScoutScenarioTest.kt -->
```kotlin
    fun cannotSearchWhenNobodyHasMoreLands() {
        // …
        assertNull(d.castSpell(p, scout).error)
        d.resolveAll()
        assertTrue(d.permanentsNamed(p, "Plains").isEmpty())
    }
```

Step triggers — advance the real turn structure, choose targets through `Choices`:

<!-- excerpt: android/custom-cards/src/test/kotlin/com/arcanatable/customcards/ChaosTerminatorLordScenarioTest.kt -->
```kotlin
        val d = customScenario(ChaosTerminatorLord, "Grizzly Bears")
        val p = d.player1
        val lord = d.putCreatureOnBattlefield(p, "Chaos Terminator Lord")
        val bear = d.putCreatureOnBattlefield(p, "Grizzly Bears")
        d.passPriorityUntil(Step.BEGIN_COMBAT)
        d.resolveAll(Choices(targets = listOf(bear)))
        assertTrue(d.projected.hasKeyword(bear, Keyword.DOUBLE_STRIKE))
        assertFalse(d.projected.hasKeyword(lord, Keyword.DOUBLE_STRIKE))
```

Upkeep on a specific player's turn, and declining "may":

<!-- excerpt: android/custom-cards/src/test/kotlin/com/arcanatable/customcards/AwakeningZoneScenarioTest.kt -->
```kotlin
        d.putPermanentOnBattlefield(p, "Awakening Zone")
        d.passPriorityUntil(Step.UPKEEP, activePlayer = p)
        d.resolveAll(Choices(yes = false))
        assertTrue(d.permanentsNamed(p, "Eldrazi Spawn").isEmpty())
```

Combat:

<!-- excerpt: android/custom-cards/src/test/kotlin/com/arcanatable/customcards/BoneSabresScenarioTest.kt -->
```kotlin
        d.removeSummoningSickness(equippedAttacker)
        d.removeSummoningSickness(otherAttacker)

        d.passPriorityUntil(Step.DECLARE_ATTACKERS)
        assertNull(d.declareAttackers(p, listOf(equippedAttacker, otherAttacker), d.player2).error)
        d.passPriorityUntil(Step.POSTCOMBAT_MAIN)
        d.resolveAll()

        assertEquals(6, d.projected.getPower(equippedAttacker))
```

Activated abilities (index = order of `activatedAbility` blocks), counters and mana pool:

<!-- excerpt: android/custom-cards/src/test/kotlin/com/arcanatable/customcards/TendoIceBridgeScenarioTest.kt -->
```kotlin
        val bridge = d.findPermanent(p, "Tendo Ice Bridge")!!
        assertEquals(1, d.state.getEntity(bridge)!!.get<CountersComponent>()!!.getCount(CounterType.CHARGE))
        assertNull(d.activate(p, bridge, index = 1).error)
        d.resolveAll(Choices(color = Color.GREEN))
```

Targeting a spell on the stack (the opponent casts first):

<!-- excerpt: android/custom-cards/src/test/kotlin/com/arcanatable/customcards/RewindScenarioTest.kt -->
```kotlin
        val spellOnStack = d.getTopOfStack()!!
        assertNull(d.castSpellWithTargets(d.player1, rewind, listOf(ChosenTarget.Spell(spellOnStack))).error)
        d.resolveAll()
```

Helpers live in `android/custom-cards/src/test/kotlin/com/arcanatable/customcards/support/`
(`Scenario.kt`: `resolveAll`, `Choices`, `activate`, `permanentsNamed`, `zoneNames`, `manaPool`,
`projected`; `GameTestDriver.kt`: zones, mana, combat, life). Read them before writing a new helper.

What a reviewer rejects:

- a test that only checks the card loads, or asserts the oracle text;
- no negative case for "may", "up to", an intervening "if", or a restriction;
- calling engine internals to fake an effect (e.g. moving cards yourself instead of resolving the ability);
- a shared `…BatchScenarioTest` instead of one file per card;
- weakening, skipping (`@Ignore`) or deleting another card's test.

## 9. Known gaps and existing approximations

- Recorded engine gaps: [`tools/cardkit/data/known-blockers.json`](../../tools/cardkit/data/known-blockers.json)
  (cards matching them classify `ENGINE_BLOCKED`).
- History of omissions and the few legacy approximations (do **not** add new ones):
  [`android/docs/CUSTOM_CARDS_HANDOFF.md`](../../android/docs/CUSTOM_CARDS_HANDOFF.md).
- Engine primitive work is Claude's lane and tracked in issue #141.

## 10. Mechanics added by Arcana engine patches (recipes)

Each entry is a mechanic that a patch in [`argentum-patches/`](../../argentum-patches/) added to the engine, shown
through the shortest real card that uses it. Copy the card's shape and its scenario test; the patch's own engine test
is in the patch file. If your card needs the same thing with a different filter, zone or timing, change only those
arguments.

### Free cast "once each turn", including opponents' turns (patch 0026)

Use for "Once each turn, you may pay {0} rather than pay the mana cost for …" and "Once each turn, you may cast … without
paying its mana cost". `oncePerTurn = true` alone means "once during each of **your** turns" (Zaffai and the Tempests);
`onAnyTurn = true` makes the use available on every turn, and the use refreshes each turn. It never changes timing: on
another player's turn only instants and flash spells can use it. Additional costs still apply and X is 0 (CR 118.9).

<!-- excerpt: android/custom-cards/src/main/kotlin/com/arcanatable/customcards/cards/DarksteelMonolith.kt -->
```kotlin
    staticAbility {
        ability = MayCastWithoutPayingManaCost(
            controllerOnly = true,
            spellFilter = GameObjectFilter.Any.withCardPredicate(CardPredicate.IsColorless),
            oncePerTurn = true,
            onAnyTurn = true,
            fromZones = setOf(Zone.HAND),
        )
    }
```

Test: [`DarksteelMonolithScenarioTest.kt`](../../android/custom-cards/src/test/kotlin/com/arcanatable/customcards/DarksteelMonolithScenarioTest.kt)
(opponent-turn instant, once-per-turn on both turns, timing, filter, zone, controller, two sources, additional costs).
Unlocks among missing six-deck cards: Darksteel Monolith.

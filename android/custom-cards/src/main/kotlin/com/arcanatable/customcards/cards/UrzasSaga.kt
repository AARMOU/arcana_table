package com.arcanatable.customcards.cards

import com.wingedsheep.sdk.core.*
import com.wingedsheep.sdk.dsl.*
import com.wingedsheep.sdk.model.*
import com.wingedsheep.sdk.scripting.*
import com.wingedsheep.sdk.scripting.conditions.ComparisonOperator
import com.wingedsheep.sdk.scripting.effects.*
import com.wingedsheep.sdk.scripting.events.*
import com.wingedsheep.sdk.scripting.predicates.CardPredicate
import com.wingedsheep.sdk.scripting.filters.unified.*
import com.wingedsheep.sdk.scripting.references.*
import com.wingedsheep.sdk.scripting.targets.*
import com.wingedsheep.sdk.scripting.values.*

/**
 * Urza's Saga. Arcana's pinned engine only runs Saga chapters for Sagas that enter from the stack or
 * by an effect, never one played as a land, so this keeps its own lore counters: one as it enters
 * (chapter I), then one as each of your precombat main phases begins (chapters II and III), exactly
 * when a Saga gets them. Chapter abilities resolve the same way; the only difference is that the lore
 * counter and the chapter happen together.
 */
val UrzasSaga = card("Urza's Saga") {
    colorIdentity = ""
    typeLine = "Enchantment Land — Urza's Saga"
    oracleText = "(As this Saga enters and after your draw step, add a lore counter. Sacrifice after III.)\n" +
        "I — This Saga gains \"{T}: Add {C}.\"\n" +
        "II — This Saga gains \"{2}, {T}: Create a 0/0 colorless Construct artifact creature token with " +
        "'This token gets +1/+1 for each artifact you control.'\"\n" +
        "III — Search your library for an artifact card with mana cost {0} or {1}, put it onto the battlefield, then shuffle."

    val artifacts = DynamicAmounts.battlefield(Player.You, GameObjectFilter.Artifact).count()
    val tapForColorless = ActivatedAbility(
        id = AbilityId.next(),
        cost = Costs.Tap,
        effect = Effects.AddColorlessMana(1),
        isManaAbility = true,
        timing = TimingRule.ManaAbility,
    )
    val makeConstruct = ActivatedAbility(
        id = AbilityId.next(),
        cost = Costs.Composite(Costs.Mana("{2}"), Costs.Tap),
        effect = Effects.CreateToken(
            power = 0,
            toughness = 0,
            creatureTypes = setOf("Construct"),
            artifactToken = true,
            staticAbilities = listOf(GrantDynamicStats(filter = GroupFilter.source(), powerBonus = artifacts, toughnessBonus = artifacts)),
        ),
    )

    // As it enters: its first lore counter (an effect that puts it onto the battlefield already gives one).
    replacementEffect(
        OnEnterRun(
            Effects.If(
                Conditions.SourceCounterCountAtMost(CounterType.LORE, 0),
                Effects.AddCounters(CounterType.LORE, 1, EffectTarget.Self),
            ),
        ),
    )

    triggeredAbility {
        trigger = Triggers.self.enters()
        effect = Effects.GrantActivatedAbility(tapForColorless, EffectTarget.Self, Duration.Permanent)
        description = "I — This Saga gains \"{T}: Add {C}.\""
    }

    triggeredAbility {
        trigger = Triggers.you.beginningOf(Step.PRECOMBAT_MAIN)
        effect = Effects.AddCounters(CounterType.LORE, 1, EffectTarget.Self) then
            Effects.If(
                Conditions.SourceCounterCountAtMost(CounterType.LORE, 2),
                Effects.GrantActivatedAbility(makeConstruct, EffectTarget.Self, Duration.Permanent),
                Patterns.Library.searchLibrary(
                    filter = GameObjectFilter.Artifact.manaValueAtMost(1).withCardPredicate(CardPredicate.IsNonland),
                    count = 1,
                    destination = SearchDestination.BATTLEFIELD,
                    shuffleAfter = true,
                ) then Effects.SacrificeTarget(EffectTarget.Self),
            )
        description = "After your draw step, add a lore counter. II — gains \"{2}, {T}: Create a 0/0 Construct\". " +
            "III — Search for an artifact with mana cost {0} or {1}, put it onto the battlefield; then sacrifice this Saga."
    }

    metadata {
        rarity = Rarity.RARE
        collectorNumber = "259"
        imageUri = "https://cards.scryfall.io/normal/front/c/1/c1e0f201-42cb-46a1-901a-65bb4fc18f6c.jpg?1783926791"
        artist = "Bryan Sola"
    }
}

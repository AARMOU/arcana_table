package com.arcanatable.customcards.cards

import com.wingedsheep.sdk.core.*
import com.wingedsheep.sdk.dsl.*
import com.wingedsheep.sdk.model.CardDefinition
import com.wingedsheep.sdk.model.Rarity
import com.wingedsheep.sdk.scripting.*
import com.wingedsheep.sdk.scripting.effects.ManaSpellRider
import com.wingedsheep.sdk.scripting.targets.EffectTarget

private val PrimalAmuletFront = card("Primal Amulet") {
    manaCost = "{4}"
    colorIdentity = ""
    typeLine = "Artifact"
    oracleText = "Instant and sorcery spells you cast cost {1} less to cast.\n" +
        "Whenever you cast an instant or sorcery spell, put a charge counter on this artifact. Then if there are four or more charge counters on it, you may remove those counters and transform it."

    staticAbility {
        ability = ModifySpellCost(
            target = SpellCostTarget.YouCast(GameObjectFilter.InstantOrSorcery),
            modification = CostModification.ReduceGeneric(1),
        )
    }
    triggeredAbility {
        trigger = Triggers.you.casts(GameObjectFilter.InstantOrSorcery)
        effect = Effects.AddCounters(CounterType.CHARGE, 1, EffectTarget.Self) then
            Effects.If(
                Conditions.SourceCounterCountAtLeast(CounterType.CHARGE, 4),
                Effects.May(
                    Effects.RemoveCounters(CounterType.CHARGE, DynamicAmounts.countersOnSelf(CounterType.CHARGE), EffectTarget.Self) then
                        Effects.Transform(EffectTarget.Self)
                ),
            )
        description = "Whenever you cast an instant or sorcery spell, put a charge counter on this artifact. Then if there are four or more charge counters on it, you may remove those counters and transform it."
    }
    metadata {
        rarity = Rarity.RARE
        collectorNumber = "243a"
        imageUri = "https://cards.scryfall.io/normal/front/d/4/d4d379b5-7f56-4a7d-a4ac-131fc3d579c6.jpg?1783935709"
    }
}

private val PrimalWellspring = card("Primal Wellspring") {
    manaCost = ""
    colorIdentity = ""
    typeLine = "Land"
    oracleText = "{T}: Add one mana of any color. When that mana is spent to cast an instant or sorcery spell, copy that spell and you may choose new targets for the copy."
    activatedAbility {
        cost = Costs.Tap
        manaAbility = true
        effect = Effects.AddManaOfChoice(
            riders = setOf(ManaSpellRider.CopySpellWhenSpent(GameObjectFilter.InstantOrSorcery)),
        )
    }
    metadata {
        rarity = Rarity.RARE
        collectorNumber = "243b"
        imageUri = "https://cards.scryfall.io/normal/back/d/4/d4d379b5-7f56-4a7d-a4ac-131fc3d579c6.jpg?1783935709"
    }
}

val PrimalAmulet: CardDefinition = CardDefinition.doubleFacedPermanent(
    frontFace = PrimalAmuletFront,
    backFace = PrimalWellspring,
)

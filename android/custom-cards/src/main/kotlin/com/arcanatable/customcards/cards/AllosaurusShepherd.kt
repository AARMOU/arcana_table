package com.arcanatable.customcards.cards

import com.wingedsheep.sdk.core.Color
import com.wingedsheep.sdk.dsl.Costs
import com.wingedsheep.sdk.dsl.Effects
import com.wingedsheep.sdk.dsl.card
import com.wingedsheep.sdk.model.Rarity
import com.wingedsheep.sdk.scripting.GameObjectFilter
import com.wingedsheep.sdk.scripting.GrantCantBeCountered
import com.wingedsheep.sdk.scripting.Duration
import com.wingedsheep.sdk.scripting.filters.unified.GroupFilter
import com.wingedsheep.sdk.scripting.targets.EffectTarget

val AllosaurusShepherd = card("Allosaurus Shepherd") {
    manaCost = "{G}"
    colorIdentity = "G"
    typeLine = "Creature — Elf Shaman"
    power = 1
    toughness = 1
    oracleText = "This spell can't be countered.\n" +
        "Green spells you control can't be countered.\n" +
        "{4}{G}{G}: Until end of turn, each Elf creature you control has base power and toughness " +
        "5/5 and becomes a Dinosaur in addition to its other creature types."

    cantBeCountered = true

    staticAbility {
        ability = GrantCantBeCountered(
            filter = GameObjectFilter.Any.withColor(Color.GREEN).youControl(),
        )
    }

    activatedAbility {
        cost = Costs.Mana("{4}{G}{G}")
        effect = Effects.ForEachInGroup(
            filter = GroupFilter(GameObjectFilter.Creature.withSubtype("Elf").youControl()),
            effect = Effects.SetBasePowerAndToughness(
                power = 5,
                toughness = 5,
                target = EffectTarget.IterationEntity,
                duration = Duration.EndOfTurn,
            ) then Effects.AddSubtype(
                subtype = "Dinosaur",
                target = EffectTarget.IterationEntity,
                duration = Duration.EndOfTurn,
            ),
        )
    }

    metadata {
        rarity = Rarity.MYTHIC
        collectorNumber = "132"
        artist = "Randy Vargas"
        imageUri = "https://cards.scryfall.io/normal/front/7/a/7afa0ee5-a0b8-472e-9991-09402ddb5de3.jpg?1783921876"
    }
}

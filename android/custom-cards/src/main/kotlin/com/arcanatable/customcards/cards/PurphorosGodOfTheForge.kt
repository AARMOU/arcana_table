package com.arcanatable.customcards.cards

import com.wingedsheep.sdk.core.*
import com.wingedsheep.sdk.dsl.*
import com.wingedsheep.sdk.model.Rarity
import com.wingedsheep.sdk.scripting.*
import com.wingedsheep.sdk.scripting.conditions.ComparisonOperator
import com.wingedsheep.sdk.scripting.effects.*
import com.wingedsheep.sdk.scripting.filters.unified.GroupFilter
import com.wingedsheep.sdk.scripting.references.Player
import com.wingedsheep.sdk.scripting.targets.EffectTarget

val PurphorosGodOfTheForge = card("Purphoros, God of the Forge") {
    manaCost = "{3}{R}"
    colorIdentity = "R"
    typeLine = "Legendary Enchantment Creature — God"
    power = 6
    toughness = 5
    oracleText = "Indestructible\n" +
        "As long as your devotion to red is less than five, Purphoros isn't a creature.\n" +
        "Whenever another creature you control enters, Purphoros deals 2 damage to each opponent.\n" +
        "{2}{R}: Creatures you control get +1/+0 until end of turn."

    keywords(Keyword.INDESTRUCTIBLE)

    staticAbility {
        ability = RemoveCardType("CREATURE")
        condition = Conditions.CompareAmounts(
            DynamicAmounts.devotionTo(Color.RED),
            ComparisonOperator.LT,
            5,
        )
    }

    triggeredAbility {
        trigger = Triggers.another(GameObjectFilter.Creature.youControl()).enters()
        effect = Effects.DealDamage(2, EffectTarget.PlayerRef(Player.EachOpponent))
        description = "Whenever another creature you control enters, Purphoros deals 2 damage to each opponent."
    }

    activatedAbility {
        cost = Costs.Mana("{2}{R}")
        effect = ForEachEffect(
            IterationSpace.Group(GroupFilter(GameObjectFilter.Creature.youControl())),
            Effects.ModifyStats(1, 0, EffectTarget.IterationEntity, Duration.EndOfTurn),
        )
        description = "{2}{R}: Creatures you control get +1/+0 until end of turn."
    }

    metadata {
        rarity = Rarity.MYTHIC
        collectorNumber = "246"
        artist = "Eric Deschamps"
        imageUri = "https://cards.scryfall.io/normal/front/4/7/4736a2c4-c89c-48db-a104-6303e7e2eee8.jpg?1783915646"
    }
}

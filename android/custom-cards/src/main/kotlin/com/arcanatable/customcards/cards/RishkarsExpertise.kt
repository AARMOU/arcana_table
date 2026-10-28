package com.arcanatable.customcards.cards

import com.wingedsheep.sdk.dsl.*
import com.wingedsheep.sdk.scripting.effects.CardSource
import com.wingedsheep.sdk.scripting.references.Player
import com.wingedsheep.sdk.scripting.GameObjectFilter
import com.wingedsheep.sdk.core.Zone
import com.wingedsheep.sdk.model.Rarity

/**
 * Rishkar's Expertise — LCC #254.
 */
val RishkarsExpertise = card("Rishkar's Expertise") {
    manaCost = "{4}{G}{G}"
    colorIdentity = "G"
    typeLine = "Sorcery"
    oracleText = "Draw cards equal to the greatest power among creatures you control.\nYou may cast a spell with mana value 5 or less from your hand without paying its mana cost."

    spell {
        effect = Effects.DrawCards(DynamicAmounts.battlefield(Player.You, GameObjectFilter.Creature).maxPower()) then
            Effects.Pipeline {
                val hand = gather(CardSource.FromZone(Zone.HAND, Player.You))
                val castable = filter(hand, GameObjectFilter.Nonland.manaValueAtMost(5))
                val chosen = chooseUpTo(1, castable)
                run(Effects.CastFromCollectionWithoutPayingCost(chosen))
            }
    }

    metadata {
        rarity = Rarity.RARE
        collectorNumber = "254"
        artist = "Magali Villeneuve"
        imageUri = "https://cards.scryfall.io/normal/front/e/f/efa9d2dc-b3b8-4475-bbfc-2db457ee4ffd.jpg?1783913855"
    }
}

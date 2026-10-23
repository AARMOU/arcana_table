package com.arcanatable.customcards.cards

import com.wingedsheep.sdk.core.*
import com.wingedsheep.sdk.dsl.*
import com.wingedsheep.sdk.model.Rarity
import com.wingedsheep.sdk.scripting.GameObjectFilter
import com.wingedsheep.sdk.scripting.targets.EffectTarget

val BrigoneSoldierOfMeletis = card("Brigone, Soldier of Meletis") {
    manaCost = "{1}{W}"
    colorIdentity = "W"
    typeLine = "Legendary Creature — Human Soldier"
    power = 2
    toughness = 2
    oracleText = "Vigilance\nHeroic — Whenever you cast a spell that targets Brigone, put a +1/+1 counter on Brigone.\n{T}, Remove a +1/+1 counter from Brigone: Draw a card."

    keywords(Keyword.VIGILANCE)

    triggeredAbility {
        trigger = Triggers.self.becomesTarget(
            of = GameObjectFilter.Any,
            byYou = true,
            spellsOnly = true,
        )
        effect = Effects.AddCounters(CounterType.PLUS_ONE_PLUS_ONE, 1, EffectTarget.Self)
        description = "Whenever you cast a spell that targets Brigone, put a +1/+1 counter on Brigone."
    }

    activatedAbility {
        cost = Costs.Composite(Costs.Tap, Costs.RemoveCounterFromSelf(CounterType.PLUS_ONE_PLUS_ONE, 1))
        effect = Effects.DrawCards(1)
        description = "{T}, Remove a +1/+1 counter from Brigone: Draw a card."
    }

    metadata {
        rarity = Rarity.RARE
        collectorNumber = "30"
        imageUri = "https://api.scryfall.com/cards/named?exact=Brigone%2C%20Soldier%20of%20Meletis&format=image"
    }
}

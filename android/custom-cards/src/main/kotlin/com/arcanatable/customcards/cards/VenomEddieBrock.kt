package com.arcanatable.customcards.cards

import com.wingedsheep.sdk.core.*
import com.wingedsheep.sdk.dsl.*
import com.wingedsheep.sdk.model.Rarity
import com.wingedsheep.sdk.scripting.*
import com.wingedsheep.sdk.scripting.targets.EffectTarget

val VenomEddieBrock = card("Venom, Eddie Brock") {
    manaCost = "{4}{B}{B}"
    colorIdentity = "B"
    typeLine = "Legendary Creature — Symbiote Villain"
    power = 6
    toughness = 4
    oracleText = "Menace (This creature can't be blocked except by two or more creatures.)\n" +
        "Whenever another creature dies, put a +1/+1 counter on Venom. If that creature was a Villain, draw a card."

    keywords(Keyword.MENACE)

    triggeredAbility {
        trigger = Triggers.another(GameObjectFilter.Creature).dies()
        effect = Effects.AddCounters(CounterType.PLUS_ONE_PLUS_ONE, 1, EffectTarget.Self)
        description = "Whenever another creature dies, put a +1/+1 counter on Venom."
    }

    triggeredAbility {
        trigger = Triggers.another(GameObjectFilter.Creature.withSubtype("Villain")).dies()
        effect = Effects.DrawCards(1)
        description = "If that creature was a Villain, draw a card."
    }

    metadata {
        rarity = Rarity.MYTHIC
        collectorNumber = "12"
        artist = "Simon Dominic"
        imageUri = "https://cards.scryfall.io/normal/front/e/a/eab647ce-fe86-450d-b5e8-ebcda631f6a1.jpg?1783905371"
    }
}

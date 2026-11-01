package com.arcanatable.customcards.cards

import com.wingedsheep.sdk.core.CounterType
import com.wingedsheep.sdk.dsl.*
import com.wingedsheep.sdk.model.Rarity
import com.wingedsheep.sdk.scripting.EntersWithDynamicCounters
import com.wingedsheep.sdk.scripting.GameObjectFilter

val TheSwarmlord = card("The Swarmlord") {
    manaCost = "{3}{G}{U}{R}"
    colorIdentity = "GUR"
    typeLine = "Legendary Creature — Tyranid"
    power = 5
    toughness = 5
    oracleText = "Rapid Regeneration — The Swarmlord enters with two +1/+1 counters on it for each time you've cast your commander from the command zone this game.\n" +
        "Xenos Cunning — Whenever another creature you control with a counter on it dies, draw a card."

    replacementEffect(
        EntersWithDynamicCounters(
            counterType = CounterType.PLUS_ONE_PLUS_ONE,
            count = com.wingedsheep.sdk.scripting.values.DynamicAmount.Multiply(
                DynamicAmounts.commanderCastsFromCommandZone(),
                2,
            ),
        ),
    )

    triggeredAbility {
        trigger = Triggers.another(
            GameObjectFilter.Creature.youControl().withCounter(CounterType.PLUS_ONE_PLUS_ONE),
        ).dies()
        effect = Effects.DrawCards(1)
    }

    metadata { rarity = Rarity.MYTHIC }
}

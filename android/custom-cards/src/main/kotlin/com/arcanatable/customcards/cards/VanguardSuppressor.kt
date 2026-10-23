package com.arcanatable.customcards.cards

import com.wingedsheep.sdk.core.Keyword
import com.wingedsheep.sdk.dsl.*
import com.wingedsheep.sdk.dsl.mechanics.squad
import com.wingedsheep.sdk.model.Rarity
import com.wingedsheep.sdk.scripting.events.Recipient

/** Squad uses the engine's repeatable additional-cost and token-copy mechanics. */
val VanguardSuppressor = card("Vanguard Suppressor") {
    manaCost = "{3}{U}"
    colorIdentity = "U"
    typeLine = "Creature — Astartes Warrior"
    power = 3
    toughness = 2
    oracleText = "Squad {2}\nFlying\nSuppressing Fire — Whenever this creature deals combat damage " +
        "to a player, draw a card."

    squad("{2}")
    keywords(Keyword.FLYING)

    triggeredAbility {
        trigger = Triggers.self.dealsCombatDamage(Recipient.AnyPlayer)
        effect = Effects.DrawCards(1)
    }

    metadata {
        rarity = Rarity.RARE
        collectorNumber = "27"
        artist = "Fajareka Setiawan"
    }
}

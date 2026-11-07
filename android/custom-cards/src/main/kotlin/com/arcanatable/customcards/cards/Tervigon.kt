package com.arcanatable.customcards.cards

import com.wingedsheep.sdk.core.Color
import com.wingedsheep.sdk.core.Keyword
import com.wingedsheep.sdk.dsl.*
import com.wingedsheep.sdk.model.Rarity
import com.wingedsheep.sdk.scripting.events.Recipient

val Tervigon = card("Tervigon") {
    manaCost = "{X}{1}{G}"
    colorIdentity = "G"
    typeLine = "Creature — Tyranid"
    power = 0
    toughness = 0
    keywords(Keyword.TRAMPLE)
    oracleText = "Ravenous (This creature enters the battlefield with X +1/+1 counters on it. If X is 5 or more, draw a card when it enters.)\n" +
        "Trample\n" +
        "Spawn Termagants — Whenever this creature deals combat damage to a player, create that many 1/1 green Tyranid creature tokens."

    ravenous()

    triggeredAbility {
        trigger = Triggers.self.dealsCombatDamage(Recipient.AnyPlayer)
        effect = Effects.CreateToken(
            DynamicAmounts.triggerDamageAmount(),
            power = 1,
            toughness = 1,
            colors = setOf(Color.GREEN),
            creatureTypes = setOf("Tyranid"),
            name = "Tyranid",
        )
    }

    metadata {
        rarity = Rarity.RARE
        collectorNumber = "100"
        artist = "Xavier Ribeiro"
    }
}

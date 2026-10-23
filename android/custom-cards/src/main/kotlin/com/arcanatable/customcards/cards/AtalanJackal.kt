package com.arcanatable.customcards.cards
import com.wingedsheep.sdk.core.Keyword
import com.wingedsheep.sdk.dsl.*
import com.wingedsheep.sdk.model.Rarity
import com.wingedsheep.sdk.scripting.GameObjectFilter
import com.wingedsheep.sdk.scripting.effects.SearchDestination
import com.wingedsheep.sdk.scripting.events.Recipient
val AtalanJackal = card("Atalan Jackal") {
    manaCost = "{1}{R}{G}"
    colorIdentity = "RG"
    typeLine = "Creature — Human Tyranid Scout"
    power = 2
    toughness = 2
    oracleText = "Trample, haste\nSkilled Outrider — Whenever this creature deals combat damage to a player, you may search your library for a basic land card, put it onto the battlefield tapped, then shuffle."
    keywords(Keyword.TRAMPLE, Keyword.HASTE)
    triggeredAbility {
        trigger = Triggers.self.dealsCombatDamage(Recipient.AnyPlayer)
        optional = true
        effect = Patterns.Library.searchLibrary(
            filter = GameObjectFilter.BasicLand,
            destination = SearchDestination.BATTLEFIELD,
            entersTapped = true
        )
    }
    metadata { rarity = Rarity.RARE; collectorNumber = "105"; artist = "David Astruga" }
}

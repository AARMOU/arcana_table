package com.arcanatable.customcards.cards

import com.wingedsheep.sdk.core.Keyword
import com.wingedsheep.sdk.core.Zone
import com.wingedsheep.sdk.dsl.Costs
import com.wingedsheep.sdk.dsl.Effects
import com.wingedsheep.sdk.dsl.card
import com.wingedsheep.sdk.model.Rarity
import com.wingedsheep.sdk.scripting.effects.ZonePlacement
import com.wingedsheep.sdk.scripting.filters.unified.TargetFilter

val EldraziDisplacer = card("Eldrazi Displacer") {
    manaCost = "{2}{W}"
    colorIdentity = "W"
    typeLine = "Creature — Eldrazi"
    power = 3
    toughness = 3
    oracleText = "Devoid (This card has no color.)\n" +
        "{2}{C}: Exile another target creature, then return it to the battlefield tapped under its owner's control. " +
        "({C} represents colorless mana.)"
    keywords(Keyword.DEVOID)
    activatedAbility {
        cost = Costs.Mana("{2}{C}")
        val creature = target(TargetFilter.OtherCreature)
        effect = Effects.Move(creature, Zone.EXILE) then
            Effects.Move(creature, Zone.BATTLEFIELD, placement = ZonePlacement.Tapped, fromZone = Zone.EXILE)
    }
    metadata {
        rarity = Rarity.RARE
        collectorNumber = "13"
        artist = "Daarken"
        imageUri = "https://cards.scryfall.io/normal/front/f/0/f0bb1a5c-0f59-4951-827f-fe9df968232d.jpg?1783937928"
    }
}

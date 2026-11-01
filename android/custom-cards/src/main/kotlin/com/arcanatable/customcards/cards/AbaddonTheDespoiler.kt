package com.arcanatable.customcards.cards

import com.wingedsheep.sdk.core.Keyword
import com.wingedsheep.sdk.core.Zone
import com.wingedsheep.sdk.dsl.*
import com.wingedsheep.sdk.model.Rarity
import com.wingedsheep.sdk.scripting.GameObjectFilter
import com.wingedsheep.sdk.scripting.events.SpellCastPredicate
import com.wingedsheep.sdk.scripting.references.Player

val AbaddonTheDespoiler = card("Abaddon the Despoiler") {
    manaCost = "{2}{U}{B}{R}"
    colorIdentity = "UBR"
    typeLine = "Legendary Creature — Astartes Warrior"
    power = 5
    toughness = 5
    oracleText = "Trample\nMark of Chaos Ascendant — During your turn, spells you cast from your hand with mana value X or less have cascade, where X is the total amount of life your opponents have lost this turn."

    keywords(Keyword.TRAMPLE)

    triggeredAbility {
        trigger = Triggers.you.casts(
            GameObjectFilter.Any.manaValueAtMostDynamic(
                DynamicAmounts.lifeLostThisTurn(Player.EachOpponent)
            ),
            setOf(SpellCastPredicate.CastFromZone(Zone.HAND)),
        )
        triggerRestriction = Conditions.IsYourTurn
        effect = Effects.Cascade
        description = "Cascade"
    }

    metadata {
        rarity = Rarity.MYTHIC
        collectorNumber = "2"
        artist = "Johan Grenier"
        imageUri = "https://cards.scryfall.io/normal/front/c/9/c9f1fc97-00c0-492b-a4a3-b179afc2f95d.jpg?1783920953"
    }
}

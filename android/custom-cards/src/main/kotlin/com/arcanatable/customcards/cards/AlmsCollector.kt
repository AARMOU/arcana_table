package com.arcanatable.customcards.cards

import com.wingedsheep.sdk.core.Keyword
import com.wingedsheep.sdk.dsl.Effects
import com.wingedsheep.sdk.dsl.card
import com.wingedsheep.sdk.model.Rarity
import com.wingedsheep.sdk.scripting.EventPattern
import com.wingedsheep.sdk.scripting.ReplaceDrawWith
import com.wingedsheep.sdk.scripting.references.Player
import com.wingedsheep.sdk.scripting.targets.EffectTarget

/**
 * Alms Collector (Commander 2017 #1).
 *
 * The replacement watches the draw *instruction* (`DrawCardsEvent`, CR 121.2a), not single
 * cards: one "draw two or more" from an opponent becomes "you draw a card, that player draws a
 * card". Needs engine patch 0042, which makes an announcement-level replacement replace the whole
 * instruction. Inside a replacement the context's controller is the affected player (the drawer),
 * so "you" is `ControllerOfSource` and "that player" is `EffectTarget.Controller`.
 */
val AlmsCollector = card("Alms Collector") {
    manaCost = "{3}{W}"
    colorIdentity = "W"
    typeLine = "Creature — Cat Cleric"
    power = 3
    toughness = 4
    oracleText = "Flash\n" +
        "If an opponent would draw two or more cards, instead you and that player each draw a card."

    keywords(Keyword.FLASH)

    replacementEffect(
        ReplaceDrawWith(
            replacementEffect = Effects.DrawCards(1, EffectTarget.PlayerRef(Player.ControllerOfSource)) then
                Effects.DrawCards(1, EffectTarget.Controller),
            appliesTo = EventPattern.DrawCardsEvent(player = Player.EachOpponent, amount = 2),
        )
    )

    metadata {
        rarity = Rarity.RARE
        collectorNumber = "1"
        artist = "Bram Sels"
        imageUri = "https://api.scryfall.com/cards/named?exact=Alms%20Collector&format=image"
    }
}

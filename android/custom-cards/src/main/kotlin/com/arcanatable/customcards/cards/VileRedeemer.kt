package com.arcanatable.customcards.cards

import com.wingedsheep.sdk.core.Keyword
import com.wingedsheep.sdk.dsl.*
import com.wingedsheep.sdk.scripting.references.Player
import com.wingedsheep.sdk.core.ManaCost
import com.wingedsheep.sdk.model.Rarity

/**
 * Vile Redeemer — OGW #125.
 */
val VileRedeemer = card("Vile Redeemer") {
    manaCost = "{2}{G}"
    colorIdentity = "G"
    typeLine = "Creature — Eldrazi"
    power = 3
    toughness = 3
    oracleText = "Devoid (This card has no color.)\nFlash\nWhen you cast this spell, you may pay {C}. If you do, create a 1/1 colorless Eldrazi Scion creature token for each nontoken creature that died under your control this turn. Those tokens have \"Sacrifice this token: Add {C}.\""

    keywords(Keyword.DEVOID, Keyword.FLASH)

    triggeredAbility {
        trigger = Triggers.self.isCast()
        effect = Effects.MayPay(
            ManaCost.parse("{C}"),
            createEldraziScions(DynamicAmounts.nonTokenCreaturesDiedThisTurn(Player.You)),
        )
    }

    metadata {
        rarity = Rarity.RARE
        collectorNumber = "125"
        artist = "Jason Rainville"
        imageUri = "https://cards.scryfall.io/normal/front/f/4/f48b0613-127a-4338-aea4-ad0b8aa96d77.jpg?1783937903"
    }
}

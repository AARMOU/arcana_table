package com.arcanatable.customcards.cards

import com.wingedsheep.sdk.core.*
import com.wingedsheep.sdk.dsl.*
import com.wingedsheep.sdk.model.Rarity
import com.wingedsheep.sdk.scripting.*
import com.wingedsheep.sdk.scripting.effects.WardCost
import com.wingedsheep.sdk.scripting.references.Player
import com.wingedsheep.sdk.scripting.targets.EffectTarget

/** Lord of Change — Warhammer 40,000 Commander #24. */
val LordOfChange = card("Lord of Change") {
    manaCost = "{6}{U}"
    colorIdentity = "U"
    typeLine = "Creature — Demon"
    power = 6
    toughness = 6
    oracleText = "Flying, ward {3}\nArchitect of Deception — When this creature enters, draw three cards."

    keywords(Keyword.FLYING)
    keywordAbility(KeywordAbility.Ward(WardCost.Mana("{3}")))
    triggeredAbility {
        trigger = Triggers.self.enters()
        effect = Effects.DrawCards(3)
    }

    metadata {
        rarity = Rarity.RARE
        collectorNumber = "24"
        artist = "Games Workshop"
    }
}

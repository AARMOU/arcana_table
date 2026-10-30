package com.arcanatable.customcards.cards

import com.wingedsheep.sdk.dsl.*
import com.wingedsheep.sdk.model.Rarity
import com.wingedsheep.sdk.scripting.KeywordAbility

val SisterRepentia = card("Sister Repentia") {
    manaCost = "{3}{W}{B}"
    colorIdentity = "WB"
    typeLine = "Creature — Human Warrior"
    power = 5
    toughness = 1
    oracleText = "Martyrdom — When this creature dies, you gain 2 life and draw two cards.\nMiracle {W}{B} (You may cast this card for its miracle cost when you draw it if it's the first card you drew this turn.)"

    triggeredAbility {
        trigger = Triggers.self.dies()
        effect = Effects.GainLife(2) then Effects.DrawCards(2)
    }

    keywordAbility(KeywordAbility.miracle("{W}{B}"))

    metadata {
        rarity = Rarity.RARE
        collectorNumber = "142"
        artist = "Seseon Yoon"
        imageUri = "https://cards.scryfall.io/normal/front/7/4/743c5111-9e94-4f08-9c01-92411e8a2317.jpg?1783920832"
    }
}

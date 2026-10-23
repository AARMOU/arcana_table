package com.arcanatable.customcards.cards

import com.wingedsheep.sdk.core.Color
import com.wingedsheep.sdk.core.Keyword
import com.wingedsheep.sdk.dsl.*
import com.wingedsheep.sdk.model.Rarity
import com.wingedsheep.sdk.scripting.KeywordAbility

val NightScythe = card("Night Scythe") {
    manaCost = "{3}"
    colorIdentity = ""
    typeLine = "Artifact — Vehicle"
    power = 3
    toughness = 1
    oracleText = "Flying\nInvasion Beams — When this Vehicle enters, create a 2/2 black Necron Warrior artifact creature token.\nCrew 2"

    keywords(Keyword.FLYING)
    triggeredAbility {
        trigger = Triggers.self.enters()
        effect = Effects.CreateToken(
            power = 2,
            toughness = 2,
            colors = setOf(Color.BLACK),
            creatureTypes = setOf("Necron", "Warrior"),
            artifactToken = true,
        )
    }
    keywordAbility(KeywordAbility.crew(2))

    metadata {
        rarity = Rarity.UNCOMMON
        collectorNumber = "162"
        artist = "Calder Moore"
    }
}

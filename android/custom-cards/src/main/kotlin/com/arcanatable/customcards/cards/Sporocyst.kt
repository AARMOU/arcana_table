package com.arcanatable.customcards.cards

import com.wingedsheep.sdk.core.Keyword
import com.wingedsheep.sdk.dsl.*
import com.wingedsheep.sdk.model.Rarity
import com.wingedsheep.sdk.scripting.GameObjectFilter
import com.wingedsheep.sdk.scripting.effects.SearchDestination

val Sporocyst = card("Sporocyst") {
    manaCost = "{X}{X}{G}"
    colorIdentity = "G"
    typeLine = "Creature — Tyranid"
    power = 0
    toughness = 0
    oracleText = "Ravenous (This creature enters with X +1/+1 counters on it. If X is 5 or more, draw a card when it enters.)\nDefender\nSpore Chimney — When this creature enters, search your library for up to X basic land cards, put them onto the battlefield tapped, then shuffle."

    ravenous()
    keywords(Keyword.DEFENDER)

    triggeredAbility {
        trigger = Triggers.self.enters()
        effect = Patterns.Library.searchLibrary(
            filter = GameObjectFilter.BasicLand,
            count = DynamicAmounts.castX(),
            destination = SearchDestination.BATTLEFIELD,
            entersTapped = true,
        )
    }

    metadata {
        rarity = Rarity.RARE
        collectorNumber = "98"
        artist = "Fajareka Setiawan"
        imageUri = "https://cards.scryfall.io/normal/front/7/2/72101d5f-f3a4-4490-a6f5-c320f3aeb242.jpg?1783920872"
    }
}

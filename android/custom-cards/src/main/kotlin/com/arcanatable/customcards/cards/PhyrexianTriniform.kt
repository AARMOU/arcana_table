package com.arcanatable.customcards.cards

import com.wingedsheep.sdk.dsl.*
import com.wingedsheep.sdk.model.Rarity

val PhyrexianTriniform = card("Phyrexian Triniform") {
    manaCost = "{9}"
    colorIdentity = ""
    typeLine = "Artifact Creature — Phyrexian Golem"
    power = 9
    toughness = 9
    oracleText = "When this creature dies, create three 3/3 colorless Phyrexian Golem artifact creature tokens.\nEncore {12} ({12}, Exile this card from your graveyard: For each opponent, create a token copy that attacks that opponent this turn if able. They gain haste. Sacrifice them at the beginning of the next end step. Activate only as a sorcery.)"

    triggeredAbility {
        trigger = Triggers.self.dies()
        effect = Effects.CreateToken(
            power = 3,
            toughness = 3,
            colors = emptySet(),
            creatureTypes = setOf("Phyrexian", "Golem"),
            name = "Phyrexian Golem",
            count = 3,
            artifactToken = true,
        )
    }

    encore("{12}")

    metadata {
        rarity = Rarity.MYTHIC
        collectorNumber = "969"
        artist = "Adam Paquette"
        imageUri = "https://cards.scryfall.io/normal/front/b/5/b55fcddd-e7c8-4f57-8f49-dd86e2d5fe49.jpg?1783915399"
    }
}

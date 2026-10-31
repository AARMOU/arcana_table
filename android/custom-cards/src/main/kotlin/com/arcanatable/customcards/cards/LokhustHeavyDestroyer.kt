package com.arcanatable.customcards.cards

import com.wingedsheep.sdk.core.Keyword
import com.wingedsheep.sdk.dsl.*
import com.wingedsheep.sdk.model.Rarity
import com.wingedsheep.sdk.scripting.GameObjectFilter
import com.wingedsheep.sdk.scripting.references.Player
import com.wingedsheep.sdk.scripting.targets.EffectTarget

val LokhustHeavyDestroyer = card("Lokhust Heavy Destroyer") {
    manaCost = "{1}{B}{B}{B}"
    colorIdentity = "B"
    typeLine = "Artifact Creature — Necron"
    power = 3
    toughness = 2
    oracleText = "Flying\nEnmitic Exterminator — When this creature enters, each player sacrifices a creature of their choice.\nUnearth {5}{B}{B}{B} ({5}{B}{B}{B}: Return this card from your graveyard to the battlefield. It gains haste. Exile it at the beginning of the next end step or if it would leave the battlefield. Unearth only as a sorcery.)"

    keywords(Keyword.FLYING)
    triggeredAbility {
        trigger = Triggers.self.enters()
        effect = Effects.Sacrifice(
            GameObjectFilter.Creature,
            1,
            EffectTarget.PlayerRef(Player.Each),
        )
    }
    unearth("{5}{B}{B}{B}")

    metadata {
        rarity = Rarity.RARE
        collectorNumber = "38"
        artist = "Adrián Rodríguez Pérez"
        imageUri = "https://cards.scryfall.io/normal/front/4/6/4665fa5d-37ea-4898-a4a4-7bc5311075de.jpg?1783920923"
    }
}

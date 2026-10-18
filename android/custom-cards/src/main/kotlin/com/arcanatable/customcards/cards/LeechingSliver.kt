package com.arcanatable.customcards.cards

import com.wingedsheep.sdk.core.*
import com.wingedsheep.sdk.dsl.*
import com.wingedsheep.sdk.model.*
import com.wingedsheep.sdk.scripting.*
import com.wingedsheep.sdk.scripting.references.Player
import com.wingedsheep.sdk.scripting.effects.*
import com.wingedsheep.sdk.scripting.filters.unified.*
import com.wingedsheep.sdk.scripting.targets.*

val LeechingSliver = card("Leeching Sliver") {
    manaCost = "{1}{B}"
    colorIdentity = "B"
    typeLine = "Creature — Sliver"
    power = 1
    toughness = 1
    oracleText = "Whenever a Sliver you control attacks, defending player loses 1 life."

    val slivers = GroupFilter(GameObjectFilter.Creature.withSubtype("Sliver"))
    val sliversYouControl = GroupFilter(GameObjectFilter.Creature.withSubtype("Sliver").youControl())
    staticAbility {
        ability = GrantTriggeredAbility(
            ability = grantedTriggeredAbility {
                trigger = Triggers.self.attacks()
                effect = Effects.LoseLife(1, EffectTarget.PlayerRef(Player.DefendingPlayer))
            },
            filter = sliversYouControl,
        )
    }

    metadata {
        rarity = Rarity.RARE
        collectorNumber = "102"
        imageUri = "https://cards.scryfall.io/normal/front/8/8/888ca52b-1270-4587-804d-1d08b07d7c5d.jpg?1783939183"
    }
}

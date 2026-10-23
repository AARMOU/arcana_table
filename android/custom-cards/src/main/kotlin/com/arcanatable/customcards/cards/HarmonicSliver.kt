package com.arcanatable.customcards.cards

import com.wingedsheep.sdk.core.*
import com.wingedsheep.sdk.dsl.*
import com.wingedsheep.sdk.model.*
import com.wingedsheep.sdk.scripting.*
import com.wingedsheep.sdk.scripting.effects.*
import com.wingedsheep.sdk.scripting.filters.unified.*
import com.wingedsheep.sdk.scripting.targets.*

val HarmonicSliver = card("Harmonic Sliver") {
    manaCost = "{1}{G}{W}"
    colorIdentity = "GW"
    typeLine = "Creature — Sliver"
    power = 1
    toughness = 1
    oracleText = "All Slivers have \"When this permanent enters, destroy target artifact or enchantment.\""

    val slivers = GroupFilter(GameObjectFilter.Creature.withSubtype("Sliver"))
    val sliversYouControl = GroupFilter(GameObjectFilter.Creature.withSubtype("Sliver").youControl())
    staticAbility {
        ability = GrantTriggeredAbility(
            ability = grantedTriggeredAbility {
                trigger = Triggers.self.enters()
                val artifactOrEnchantment = target(TargetFilter.ArtifactOrEnchantment)
                effect = Effects.Destroy(artifactOrEnchantment)
            },
            filter = slivers,
        )
    }

    metadata {
        rarity = Rarity.RARE
        collectorNumber = "924"
        imageUri = "https://cards.scryfall.io/normal/front/1/2/1228d068-a5cb-4fd5-9e6a-5dc6ac9376a4.jpg?1783915416"
    }
}

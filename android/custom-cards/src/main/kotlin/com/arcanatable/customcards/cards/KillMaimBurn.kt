package com.arcanatable.customcards.cards

import com.wingedsheep.sdk.dsl.*
import com.wingedsheep.sdk.model.Rarity
import com.wingedsheep.sdk.scripting.filters.unified.TargetFilter

val KillMaimBurn = card("Kill! Maim! Burn!") {
    manaCost = "{3}{B}{R}"
    colorIdentity = "BR"
    typeLine = "Instant"
    oracleText = "Choose one or more —\n• Destroy target artifact.\n• Destroy target creature.\n• Kill! Maim! Burn! deals 3 damage to target player."

    spell {
        modal(chooseCount = 3, minChooseCount = 1) {
            mode("Destroy target artifact") {
                val artifact = target(TargetFilter.Artifact)
                effect = Effects.Destroy(artifact)
            }
            mode("Destroy target creature") {
                val creature = target(TargetFilter.Creature)
                effect = Effects.Destroy(creature)
            }
            mode("Kill! Maim! Burn! deals 3 damage to target player") {
                val player = target(Targets.Player)
                effect = Effects.DealDamage(3, player)
            }
        }
    }

    metadata {
        rarity = Rarity.RARE
        collectorNumber = "128"
        artist = "Anthony Devine"
        imageUri = "https://cards.scryfall.io/normal/front/7/c/7c48e10d-2b70-4f87-b5ce-6fb0d56452eb.jpg?1783920846"
    }
}

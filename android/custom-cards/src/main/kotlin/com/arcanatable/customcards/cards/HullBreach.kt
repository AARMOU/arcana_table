package com.arcanatable.customcards.cards
import com.wingedsheep.sdk.dsl.*
import com.wingedsheep.sdk.model.Rarity
import com.wingedsheep.sdk.scripting.filters.unified.TargetFilter
val HullBreach = card("Hull Breach") {
    manaCost = "{R}{G}"
    colorIdentity = "RG"
    typeLine = "Sorcery"
    oracleText = "Choose one —\n• Destroy target artifact.\n• Destroy target enchantment.\n• Destroy target artifact and target enchantment."
    spell {
        modal(chooseCount = 1) {
            mode("Destroy target artifact") {
                val artifact = target(TargetFilter.Artifact)
                effect = Effects.Destroy(artifact)
            }
            mode("Destroy target enchantment") {
                val enchantment = target(TargetFilter.Enchantment)
                effect = Effects.Destroy(enchantment)
            }
            mode("Destroy target artifact and target enchantment") {
                val artifact = target(TargetFilter.Artifact)
                val enchantment = target(TargetFilter.Enchantment)
                effect = Effects.Destroy(artifact) then Effects.Destroy(enchantment)
            }
        }
    }
    metadata { rarity = Rarity.COMMON; collectorNumber = "111"; artist = "Brian Snoddy" }
}

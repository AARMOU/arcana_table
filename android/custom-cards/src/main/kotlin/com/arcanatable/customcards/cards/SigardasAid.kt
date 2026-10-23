package com.arcanatable.customcards.cards

import com.wingedsheep.sdk.core.*
import com.wingedsheep.sdk.dsl.*
import com.wingedsheep.sdk.model.*
import com.wingedsheep.sdk.scripting.*
import com.wingedsheep.sdk.scripting.targets.EffectTarget
import com.wingedsheep.sdk.scripting.filters.unified.TargetFilter

val SigardasAid = card("Sigarda's Aid") {
    manaCost = "{W}"
    colorIdentity = "W"
    typeLine = "Enchantment"
    oracleText = "You may cast Aura and Equipment spells as though they had flash.\n" +
        "Whenever an Equipment you control enters, you may attach it to target creature you control."

    staticAbility {
        ability = GrantFlashToSpellType(
            filter = GameObjectFilter.Enchantment.withSubtype("Aura"),
            controllerOnly = true,
        )
    }
    staticAbility {
        ability = GrantFlashToSpellType(
            filter = GameObjectFilter.Artifact.withSubtype("Equipment"),
            controllerOnly = true,
        )
    }
    triggeredAbility {
        trigger = Triggers.a(GameObjectFilter.Artifact.withSubtype("Equipment").youControl()).enters()
        val creature = target(TargetFilter.CreatureYouControl, optional = true)
        effect = Effects.AttachTargetEquipmentToCreature(EffectTarget.TriggeringEntity, creature)
        description = "Whenever an Equipment you control enters, you may attach it to target creature you control."
    }

    metadata {
        rarity = Rarity.RARE
        collectorNumber = "384"
        imageUri = "https://cards.scryfall.io/normal/front/5/3/531da950-dc96-4050-94a8-e01b73ddd965.jpg?1783928726"
    }
}

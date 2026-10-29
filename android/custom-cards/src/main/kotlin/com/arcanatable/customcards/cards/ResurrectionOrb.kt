package com.arcanatable.customcards.cards

import com.wingedsheep.sdk.core.Keyword
import com.wingedsheep.sdk.core.Step
import com.wingedsheep.sdk.core.Zone
import com.wingedsheep.sdk.dsl.*
import com.wingedsheep.sdk.model.Rarity
import com.wingedsheep.sdk.scripting.GrantKeyword
import com.wingedsheep.sdk.scripting.targets.EffectTarget

val ResurrectionOrb = card("Resurrection Orb") {
    manaCost = "{2}"
    colorIdentity = ""
    typeLine = "Artifact — Equipment"
    oracleText = "Equipped creature has lifelink.\nWhenever equipped creature dies, return that card to the battlefield under its owner's control at the beginning of the next end step.\nEquip {4} ({4}: Attach to target creature you control. Equip only as a sorcery.)"

    staticAbility {
        ability = GrantKeyword(Keyword.LIFELINK, Filters.EquippedCreature)
    }

    triggeredAbility {
        trigger = Triggers.attached.dies()
        effect = Effects.CreateDelayedTrigger(
            step = Step.END,
            effect = Effects.Move(EffectTarget.TriggeringEntity, Zone.BATTLEFIELD),
        )
    }

    equipAbility("{4}")

    metadata {
        rarity = Rarity.RARE
        collectorNumber = "165"
        artist = "Hector Ortiz"
        imageUri = "https://cards.scryfall.io/normal/front/2/d/2d6209c8-9fa1-4f0a-ab65-ac2fedfcd09b.jpg?1783920812"
    }
}

package com.arcanatable.customcards.cards

import com.wingedsheep.sdk.core.*
import com.wingedsheep.sdk.dsl.*
import com.wingedsheep.sdk.model.Rarity
import com.wingedsheep.sdk.scripting.*
import com.wingedsheep.sdk.scripting.filters.unified.TargetFilter

/**
 * Mirrorpool — CMM #1010.
 */
val Mirrorpool = card("Mirrorpool") {
    colorIdentity = ""
    typeLine = "Land"
    oracleText = "This land enters tapped.\n{T}: Add {C}.\n{2}{C}, {T}, Sacrifice this land: Copy target instant or sorcery spell you control. You may choose new targets for the copy.\n{4}{C}, {T}, Sacrifice this land: Create a token that's a copy of target creature you control."

    replacementEffect(EntersTapped())

    activatedAbility {
        cost = Costs.Tap
        effect = Effects.AddColorlessMana(1)
        manaAbility = true
        timing = TimingRule.ManaAbility
    }

    activatedAbility {
        val spell = target(
            TargetFilter(GameObjectFilter.InstantOrSorcery.youControl(), zone = Zone.STACK)
        )
        cost = Costs.Composite(Costs.Mana("{2}{C}"), Costs.Tap, Costs.SacrificeSelf)
        effect = Effects.CopyTargetSpell(spell)
    }

    activatedAbility {
        val creature = target(TargetFilter.CreatureYouControl)
        cost = Costs.Composite(Costs.Mana("{4}{C}"), Costs.Tap, Costs.SacrificeSelf)
        effect = Effects.CreateTokenCopyOfTarget(creature)
    }

    metadata {
        rarity = Rarity.MYTHIC
        collectorNumber = "1010"
        artist = "Cliff Childs"
    }
}

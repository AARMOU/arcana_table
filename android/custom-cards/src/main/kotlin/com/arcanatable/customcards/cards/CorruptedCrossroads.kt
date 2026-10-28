package com.arcanatable.customcards.cards

import com.wingedsheep.sdk.dsl.*
import com.wingedsheep.sdk.model.Rarity
import com.wingedsheep.sdk.scripting.TimingRule
import com.wingedsheep.sdk.scripting.effects.ManaRestriction

/**
 * Corrupted Crossroads, Eldrazi Incursion.
 *
 * Needs ManaRestriction.DevoidSpellsOnly from engine patch
 * engine-upgrade/eldrazi-combined/patches/0002 (applied after the Temple patch).
 * A colorless non-devoid spell must NOT be able to spend the colored mode.
 * Registered for B140 together with runtime jars rebuilt with that patch
 * (engine-upgrade/build140); it does not load on jars without it.
 */
val CorruptedCrossroads = card("Corrupted Crossroads") {
    colorIdentity = ""
    typeLine = "Land"
    oracleText = "{T}: Add {C}.\n{T}, Pay 1 life: Add one mana of any color. Spend this mana only to cast a spell with devoid."

    activatedAbility {
        cost = Costs.Tap
        effect = Effects.AddColorlessMana(1)
        manaAbility = true
        timing = TimingRule.ManaAbility
        description = "{T}: Add {C}."
    }

    activatedAbility {
        cost = Costs.Composite(Costs.Tap, Costs.PayLife(1))
        effect = Effects.AddAnyColorMana(1, ManaRestriction.DevoidSpellsOnly)
        manaAbility = true
        timing = TimingRule.ManaAbility
        description = "{T}, Pay 1 life: Add one mana of any color. Spend this mana only to cast a spell with devoid."
    }

    metadata {
        rarity = Rarity.RARE
        collectorNumber = "332"
        artist = "Ryan Pancoast"
    }
}

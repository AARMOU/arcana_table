package com.arcanatable.customcards.cards

import com.wingedsheep.sdk.dsl.*
import com.wingedsheep.sdk.model.Rarity
import com.wingedsheep.sdk.scripting.TimingRule
import com.wingedsheep.sdk.scripting.effects.ManaRestriction

/**
 * Corrupted Crossroads — original Eldrazi Incursion land.
 *
 * Engine candidate ONLY: DevoidSpellsOnly is introduced by the isolated
 * source patch in engine-upgrade/corrupted-crossroads/patches/.
 * A colorless non-devoid spell must NOT be able to spend the colored mode.
 * Do not register/export against the committed Android runtime jars yet.
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

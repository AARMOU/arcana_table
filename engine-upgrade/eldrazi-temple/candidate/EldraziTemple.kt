package com.arcanatable.customcards.cards

import com.wingedsheep.sdk.dsl.*
import com.wingedsheep.sdk.model.Rarity
import com.wingedsheep.sdk.scripting.TimingRule
import com.wingedsheep.sdk.scripting.effects.ManaRestriction

/**
 * #152 — source-only candidate. Requires the reviewed pinned-engine mana restriction patch.
 * Do not register or export with Arcana's committed (unpatched) runtime jars.
 */
val EldraziTemple = card("Eldrazi Temple") {
    colorIdentity = ""
    typeLine = "Land"
    oracleText = "{T}: Add {C}.\n{T}: Add {C}{C}. Spend this mana only to cast colorless " +
        "Eldrazi spells or activate abilities of colorless Eldrazi."

    activatedAbility {
        cost = Costs.Tap
        effect = Effects.AddColorlessMana(1)
        manaAbility = true
        timing = TimingRule.ManaAbility
    }

    activatedAbility {
        cost = Costs.Tap
        effect = Effects.AddColorlessMana(
            2,
            restriction = ManaRestriction.ColorlessSubtypeSpellsOrAbilitiesOnly("Eldrazi"),
        )
        manaAbility = true
        timing = TimingRule.ManaAbility
    }

    metadata {
        rarity = Rarity.UNCOMMON
        collectorNumber = "227"
    }
}

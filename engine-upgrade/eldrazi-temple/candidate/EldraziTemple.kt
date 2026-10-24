package com.arcanatable.customcards.cards

import com.wingedsheep.sdk.dsl.*
import com.wingedsheep.sdk.scripting.TimingRule
import com.wingedsheep.sdk.scripting.effects.ManaRestriction

/**
 * Eldrazi Temple — staged Oracle-exact card for #152.
 *
 * Depends on the source-tested ColorlessSubtypeSpellsOrAbilitiesOnly restriction
 * in PR #158. Do not register/export against the committed Build 131 runtime jars
 * until the combined source-built card tests pass and the runtime upgrade is
 * separately authorized.
 */
val EldraziTemple = card("Eldrazi Temple") {
    colorIdentity = ""
    typeLine = "Land"
    oracleText = "{T}: Add {C}.\n{T}: Add {C}{C}. Spend this mana only to cast colorless Eldrazi spells or activate abilities of colorless Eldrazi."

    // Unrestricted mode: this colorless can fund any legal cost.
    activatedAbility {
        cost = Costs.Tap
        effect = Effects.AddColorlessMana(1)
        manaAbility = true
        timing = TimingRule.ManaAbility
        description = "{T}: Add {C}."
    }

    // Restricted mode: includes the activated ability SOURCE's colorlessness,
    // not merely the spell color or the Eldrazi subtype.
    activatedAbility {
        cost = Costs.Tap
        effect = Effects.AddColorlessMana(
            2,
            restriction = ManaRestriction.ColorlessSubtypeSpellsOrAbilitiesOnly("Eldrazi")
        )
        manaAbility = true
        timing = TimingRule.ManaAbility
        description = "{T}: Add {C}{C}. Spend this mana only to cast colorless Eldrazi spells or activate abilities of colorless Eldrazi."
    }
}
